package com.androidclaw.gateway

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.androidclaw.agent.AgentService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TelegramGatewayService
 *
 * Long-polling Telegram bot receiver.
 * Receives messages → sends to AgentBrain → reports results back.
 * No webhook needed. Works behind any network/NAT.
 */
class TelegramGatewayService : Service() {

    companion object {
        private const val BASE = "https://api.telegram.org/bot"

        suspend fun sendMessage(token: String, chatId: String, text: String) {
            try {
                val client = OkHttpClient()
                val url = "${BASE}${token}/sendMessage?chat_id=$chatId&text=${
                    java.net.URLEncoder.encode(text, "UTF-8")
                }&parse_mode=Markdown"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUpdateId = 0L
    private lateinit var token: String
    private lateinit var chatId: String

    private val client = OkHttpClient.Builder()
        .readTimeout(35, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        token = intent?.getStringExtra("token") ?: return START_NOT_STICKY
        chatId = intent.getStringExtra("chat_id") ?: ""

        scope.launch {
            sendMessage(token, chatId, "🤖 *AndroidClaw is online* and ready for commands.")
            startPolling()
        }

        return START_STICKY
    }

    private suspend fun startPolling() {
        while (scope.isActive) {
            try {
                val updates = getUpdates()
                updates.forEach { update ->
                    processUpdate(update)
                }
            } catch (e: Exception) {
                delay(5000) // Wait before retry
            }
        }
    }

    private fun getUpdates(): List<JSONObject> {
        val url = "${BASE}${token}/getUpdates?offset=${lastUpdateId + 1}&timeout=30&limit=10"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        response.close()

        val json = JSONObject(body)
        if (!json.getBoolean("ok")) return emptyList()

        val results = json.getJSONArray("result")
        val updates = mutableListOf<JSONObject>()

        for (i in 0 until results.length()) {
            val update = results.getJSONObject(i)
            lastUpdateId = maxOf(lastUpdateId, update.getLong("update_id"))
            updates.add(update)
        }

        return updates
    }

    private suspend fun processUpdate(update: JSONObject) {
        val message = update.optJSONObject("message") ?: return
        val fromId = message.optJSONObject("from")?.optLong("id") ?: return
        val text = message.optString("text", "").trim()

        // Security: only accept messages from authorized chat
        if (chatId.isNotEmpty() && fromId.toString() != chatId) {
            sendMessage(token, fromId.toString(), "❌ Unauthorized")
            return
        }

        if (text.isEmpty()) return

        // Handle commands
        when {
            text == "/start" || text == "/help" -> {
                val help = """
*AndroidClaw Commands:*

Just send any goal in plain text and I'll execute it on the phone.

Examples:
• "Open WhatsApp and message Rahul: I'll be late"
• "Search Google for tiffin services in Wakad"
• "Open YouTube and search for n8n tutorial"
• "Take a screenshot"

Special commands:
/status - Check agent status
/stop - Pause agent
/memory - Show what I remember
/skills - List loaded skills
                """.trimIndent()
                sendMessage(token, fromId.toString(), help)
            }

            text == "/status" -> {
                val running = AgentService.isRunning.value
                sendMessage(token, fromId.toString(), if (running) "✅ Agent is running" else "⚠️ Agent is idle")
            }

            text == "/stop" -> {
                sendMessage(token, fromId.toString(), "⏸ Agent paused. Send any message to resume.")
            }

            text.startsWith("/") -> {
                sendMessage(token, fromId.toString(), "Unknown command. Send /help for list.")
            }

            else -> {
                // Execute as goal
                sendMessage(token, fromId.toString(), "⚡ Executing: _${text.take(80)}_")
                AgentService.execute(applicationContext, text)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
