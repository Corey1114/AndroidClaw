package com.androidclaw.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidclaw.models.FREE_MODELS
import com.androidclaw.models.FreeModel
import com.androidclaw.models.LLMProvider
import com.androidclaw.security.SecurityManager

/**
 * SetupActivity
 *
 * 4-step setup wizard. Total time: ~2 minutes.
 * Step 1: Pick your LLM (free models shown first)
 * Step 2: Enter API key (or skip for Pollinations)
 * Step 3: Telegram bot (optional)
 * Step 4: Enable Accessibility + Done
 */
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidClawTheme {
                SetupWizard(
                    onComplete = {
                        getSharedPreferences("androidclaw_config", MODE_PRIVATE)
                            .edit().putBoolean("setup_complete", true).apply()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SetupWizard(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var selectedModel by remember { mutableStateOf(FREE_MODELS[0]) }
    var apiKey by remember { mutableStateOf("") }
    var telegramToken by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Spacer(modifier = Modifier.height(32.dp))
        Text("🤖", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "AndroidClaw",
            color = AccentGreen,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "AI Agent for Android",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Progress steps
        StepIndicator(currentStep = step, totalSteps = 4)

        Spacer(modifier = Modifier.height(32.dp))

        // Step content
        when (step) {
            0 -> StepPickModel(
                selectedModel = selectedModel,
                onModelSelected = { selectedModel = it },
                onNext = { step = 1 }
            )
            1 -> StepApiKey(
                model = selectedModel,
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                onNext = {
                    // Save API key encrypted
                    if (apiKey.isNotEmpty()) {
                        SecurityManager.storeApiKey(context, selectedModel.provider.name.lowercase(), apiKey)
                    }
                    // Save model selection
                    context.getSharedPreferences("androidclaw_config", Context.MODE_PRIVATE)
                        .edit()
                        .putString("llm_provider", selectedModel.provider.name)
                        .putString("llm_model", selectedModel.modelId)
                        .apply()
                    step = 2
                },
                onSkip = { step = 2 }
            )
            2 -> StepTelegram(
                token = telegramToken,
                chatId = telegramChatId,
                onTokenChange = { telegramToken = it },
                onChatIdChange = { telegramChatId = it },
                onNext = {
                    if (telegramToken.isNotEmpty()) {
                        SecurityManager.storeTelegramToken(context, telegramToken)
                    }
                    if (telegramChatId.isNotEmpty()) {
                        SecurityManager.storeTelegramChatId(context, telegramChatId)
                    }
                    step = 3
                },
                onSkip = { step = 3 }
            )
            3 -> StepAccessibility(
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onDone = onComplete
            )
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .background(
                        color = if (index <= currentStep) AccentGreen else Surface2,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun StepPickModel(
    selectedModel: FreeModel,
    onModelSelected: (FreeModel) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Choose your AI model", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("All free options shown first", color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        FREE_MODELS.forEach { model ->
            val isSelected = model == selectedModel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onModelSelected(model) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentGreen.copy(alpha = 0.1f) else Surface2
                ),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(1.dp, AccentGreen)
                else null
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(model.notes, color = TextSecondary, fontSize = 12.sp)
                        Row {
                            Chip(model.contextWindow)
                            Spacer(modifier = Modifier.width(4.dp))
                            Chip(model.speed)
                        }
                    }
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        GreenButton("Next →", onClick = onNext)
    }
}

@Composable
fun StepApiKey(
    model: FreeModel,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    Column {
        Text("Enter API Key", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        if (model.provider == LLMProvider.POLLINATIONS) {
            InfoCard("✅ Pollinations needs no API key. You're all set!")
            Spacer(modifier = Modifier.height(24.dp))
            GreenButton("Continue →", onClick = onSkip)
        } else {
            Text("For: ${model.displayName}", color = AccentGreen, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(model.notes, color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key", color = TextSecondary) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = Surface2,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    containerColor = Surface2
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            InfoCard("🔒 Key is encrypted with AES-256 and stored only on this device.")

            Spacer(modifier = Modifier.height(24.dp))
            GreenButton("Save & Continue →", onClick = onNext, enabled = apiKey.isNotEmpty())
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = TextSecondary)
            }
        }
    }
}

@Composable
fun StepTelegram(
    token: String,
    chatId: String,
    onTokenChange: (String) -> Unit,
    onChatIdChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        Text("Telegram Channel", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Optional — control your agent remotely", color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        InfoCard("1. Open Telegram → search @BotFather\n2. Send /newbot → get token\n3. Message your bot → use @userinfobot to get your chat ID")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bot Token", color = TextSecondary) },
            placeholder = { Text("1234567890:AAF...", color = TextSecondary.copy(0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = Surface2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                containerColor = Surface2
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = chatId,
            onValueChange = onChatIdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your Chat ID", color = TextSecondary) },
            placeholder = { Text("123456789", color = TextSecondary.copy(0.5f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = Surface2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                containerColor = Surface2
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))
        GreenButton("Save & Continue →", onClick = onNext, enabled = token.isNotEmpty() && chatId.isNotEmpty())
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip — use in-app chat only", color = TextSecondary)
        }
    }
}

@Composable
fun StepAccessibility(onOpenSettings: () -> Unit, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enable Screen Access", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Required for AndroidClaw to see and control your screen", color = TextSecondary, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(24.dp))

        InfoCard(
            "1. Tap 'Open Settings' below\n" +
            "2. Find 'AndroidClaw Screen Reader'\n" +
            "3. Toggle it ON\n" +
            "4. Come back and tap 'All Done'"
        )

        Spacer(modifier = Modifier.height(16.dp))
        InfoCard("🔒 AndroidClaw never uploads your screen data. Everything stays on your device.")

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AccentGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Accessibility Settings", color = AccentGreen)
        }

        Spacer(modifier = Modifier.height(12.dp))
        GreenButton("✅ All Done — Launch AndroidClaw", onClick = onDone)
    }
}

// ─── Reusable Components ──────────────────────────────────────────────────────

@Composable
fun GreenButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGreen,
            disabledContainerColor = Surface2
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, color = Background, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun Chip(text: String) {
    Box(
        modifier = Modifier
            .background(Surface1, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = TextSecondary, fontSize = 10.sp)
    }
}
