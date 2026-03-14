package com.androidclaw.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidclaw.accessibility.ScreenReaderService
import com.androidclaw.agent.AgentService
import com.androidclaw.agent.AgentStatus

// ─── Theme Colors ─────────────────────────────────────────────────────────────
val Background = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF141414)
val Surface2 = Color(0xFF1E1E1E)
val AccentGreen = Color(0xFF00FF88)
val AccentOrange = Color(0xFFFF6B35)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF888888)
val ErrorRed = Color(0xFFFF4444)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if setup is done
        val prefs = getSharedPreferences("androidclaw_config", MODE_PRIVATE)
        val setupDone = prefs.getBoolean("setup_complete", false)

        if (!setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Start agent service
        AgentService.start(this)

        setContent {
            AndroidClawTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun AndroidClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Surface1,
            primary = AccentGreen,
            onPrimary = Background,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current

    val agentStatus by AgentService.isRunning.collectAsState()
    val accessibilityRunning by ScreenReaderService.isRunning.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AndroidClaw",
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusDot(active = agentStatus)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1),
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SetupActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Log") },
                    label = { Text("Log") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Cron") },
                    label = { Text("Cron") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Extension, contentDescription = "Skills") },
                    label = { Text("Skills") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            // Accessibility warning
            if (!accessibilityRunning) {
                AccessibilityWarning(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            when (selectedTab) {
                0 -> ChatTab(inputText, { inputText = it })
                1 -> LogTab()
                2 -> CronTab()
                3 -> SkillsTab()
            }
        }
    }
}

@Composable
fun ChatTab(inputText: String, onInputChange: (String) -> Unit) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() } // text, isUser
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🤖", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "AndroidClaw is ready",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Send a goal and I'll execute it on your phone",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ExampleChips { chip ->
                            AgentService.execute(context, chip)
                            messages.add(Pair(chip, true))
                        }
                    }
                }
            }

            items(messages) { (text, isUser) ->
                ChatBubble(text = text, isUser = isUser)
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Give me a goal...", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = Surface2,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentGreen,
                    containerColor = Surface2
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotEmpty()) {
                        AgentService.execute(context, inputText)
                        messages.add(Pair(inputText, true))
                        onInputChange("")
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice button
            IconButton(
                onClick = {
                    val intent = Intent(context, com.androidclaw.voice.VoiceService::class.java)
                    intent.action = "START_LISTENING"
                    context.startService(intent)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Surface2, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice", tint = AccentGreen)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send button
            IconButton(
                onClick = {
                    if (inputText.isNotEmpty()) {
                        AgentService.execute(context, inputText)
                        messages.add(Pair(inputText, true))
                        onInputChange("")
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (inputText.isNotEmpty()) AccentGreen else Surface2,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotEmpty()) Background else TextSecondary
                )
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isUser) AccentGreen.copy(alpha = 0.15f) else Surface2,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = text,
                color = if (isUser) AccentGreen else TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ExampleChips(onChipClick: (String) -> Unit) {
    val examples = listOf(
        "Open WhatsApp",
        "Search Google for tiffin services",
        "Take a screenshot",
        "Open Settings > WiFi"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        examples.forEach { example ->
            SuggestionChip(
                onClick = { onChipClick(example) },
                label = { Text(example, fontSize = 13.sp) },
                modifier = Modifier.padding(vertical = 2.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Surface2,
                    labelColor = TextPrimary
                )
            )
        }
    }
}

@Composable
fun LogTab() {
    val log by AgentService.isRunning.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Agent Log", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Log output will appear here during task execution.",
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun CronTab() {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Scheduled Tasks", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Add tasks that run automatically at set times.", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun SkillsTab() {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Skills", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Drop SKILL.md files into AndroidClaw/skills/ folder.", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = if (active) AccentGreen else ErrorRed,
                shape = RoundedCornerShape(4.dp)
            )
    )
}

@Composable
fun AccessibilityWarning(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A1500))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Accessibility service not enabled",
            color = AccentOrange,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text("Enable", color = AccentOrange, fontSize = 12.sp)
        }
    }
}
