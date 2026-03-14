package com.androidclaw.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidclaw.gateway.TelegramGatewayService
import com.androidclaw.memory.MemoryManager
import com.androidclaw.models.LLMConfig
import com.androidclaw.models.LLMProvider
import com.androidclaw.security.SecurityManager
import com.androidclaw.skills.SkillsManager
import com.androidclaw.ui.MainActivity
import com.androidclaw.voice.VoiceService
import kotlinx.coroutines.*

/**
 * AgentService
 *
 * Foreground service — the heart of AndroidClaw.
 * Runs 24/7, coordinates all components:
 * - AgentBrain (ReAct loop)
 * - TelegramGateway (receive/send messages)
 * - VoiceService (wake word + STT)
 * - CronScheduler (scheduled tasks)
 */
class AgentService : Service() {

    companion object {
        const val CHANNEL_ID = "androidclaw_agent"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_EXECUTE = "EXECUTE"
        const val EXTRA_GOAL = "goal"

        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunning = _isRunning

        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun execute(context: Context, goal: String) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_EXECUTE
                putExtra(EXTRA_GOAL, goal)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var llmClient: LLMClient
    private lateinit var memoryManager: MemoryManager
    private lateinit var skillsManager: SkillsManager
    private lateinit var agentBrain: AgentBrain
    private lateinit var cronScheduler: CronScheduler

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize components
        llmClient = LLMClient(this)
        memoryManager = MemoryManager(this)
        skillsManager = SkillsManager(this)
        agentBrain = AgentBrain(this, llmClient, memoryManager, skillsManager)
        cronScheduler = CronScheduler(this)

        // Load saved LLM config
        val config = loadLLMConfig()
        agentBrain.setConfig(config)

        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))

        when (intent?.action) {
            ACTION_START -> {
                startGateway()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_EXECUTE -> {
                val goal = intent.getStringExtra(EXTRA_GOAL) ?: return START_STICKY
                serviceScope.launch {
                    updateNotification("Running: $goal")
                    val result = agentBrain.executeGoal(goal)
                    updateNotification("Done: ${result.take(50)}")

                    // Report back via Telegram if configured
                    reportResult(result)
                }
            }
        }

        return START_STICKY // Restart if killed
    }

    private fun startGateway() {
        val telegramToken = SecurityManager.getTelegramToken(this)
        val telegramChatId = SecurityManager.getTelegramChatId(this)

        if (telegramToken.isNotEmpty()) {
            val gatewayIntent = Intent(this, TelegramGatewayService::class.java).apply {
                putExtra("token", telegramToken)
                putExtra("chat_id", telegramChatId)
            }
            startService(gatewayIntent)
        }
    }

    private fun reportResult(result: String) {
        val token = SecurityManager.getTelegramToken(this)
        val chatId = SecurityManager.getTelegramChatId(this)
        if (token.isNotEmpty() && chatId.isNotEmpty()) {
            serviceScope.launch(Dispatchers.IO) {
                TelegramGatewayService.sendMessage(token, chatId, "✅ Done: $result")
            }
        }
    }

    private fun loadLLMConfig(): LLMConfig {
        val prefs = getSharedPreferences("androidclaw_config", Context.MODE_PRIVATE)
        val providerName = prefs.getString("llm_provider", LLMProvider.GEMINI.name) ?: LLMProvider.GEMINI.name
        val modelId = prefs.getString("llm_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"

        val provider = try { LLMProvider.valueOf(providerName) } catch (e: Exception) { LLMProvider.GEMINI }
        val apiKey = SecurityManager.getApiKey(this, provider.name.lowercase())

        return LLMConfig(
            provider = provider,
            modelId = modelId,
            apiKey = apiKey
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AndroidClaw")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AndroidClaw Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AndroidClaw autonomous agent"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isRunning.value = false
    }
}

// ─── Boot Receiver ────────────────────────────────────────────────────────────

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            val prefs = context.getSharedPreferences("androidclaw_config", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", true)
            if (autoStart) {
                AgentService.start(context)
            }
        }
    }
}
