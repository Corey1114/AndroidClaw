package com.androidclaw.models

import com.google.gson.annotations.SerializedName

// ─── LLM Models ──────────────────────────────────────────────────────────────

enum class LLMProvider(val displayName: String, val baseUrl: String, val requiresKey: Boolean) {
    GEMINI("Google Gemini (Free)", "https://generativelanguage.googleapis.com/v1beta", true),
    GROQ("Groq (Free Tier)", "https://api.groq.com/openai/v1", true),
    OPENROUTER("OpenRouter (Free Models)", "https://openrouter.ai/api/v1", true),
    POLLINATIONS("Pollinations AI (No Key)", "https://text.pollinations.ai", false),
    OPENAI("OpenAI GPT", "https://api.openai.com/v1", true),
    GITHUB_MODELS("GitHub Models (Free)", "https://models.inference.ai.azure.com", true),
    CUSTOM("Custom / Local", "", false)
}

data class LLMConfig(
    val provider: LLMProvider = LLMProvider.GEMINI,
    val modelId: String = "gemini-1.5-flash",
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val maxTokens: Int = 2048,
    val temperature: Float = 0.1f
)

// Free model catalog shown in setup
data class FreeModel(
    val provider: LLMProvider,
    val modelId: String,
    val displayName: String,
    val contextWindow: String,
    val speed: String,
    val notes: String
)

val FREE_MODELS = listOf(
    FreeModel(LLMProvider.GEMINI, "gemini-1.5-flash", "Gemini 1.5 Flash", "1M tokens", "Fast", "Free API key from aistudio.google.com"),
    FreeModel(LLMProvider.GEMINI, "gemini-2.0-flash-exp", "Gemini 2.0 Flash", "1M tokens", "Very Fast", "Best free model for agents"),
    FreeModel(LLMProvider.GROQ, "llama-3.3-70b-versatile", "Llama 3.3 70B", "128K", "Ultra Fast", "Free at console.groq.com"),
    FreeModel(LLMProvider.GROQ, "llama3-8b-8192", "Llama 3 8B", "8K", "Fastest", "100 req/min free"),
    FreeModel(LLMProvider.GROQ, "mixtral-8x7b-32768", "Mixtral 8x7B", "32K", "Fast", "Strong reasoning"),
    FreeModel(LLMProvider.POLLINATIONS, "openai", "Pollinations (No Key)", "16K", "Medium", "Zero signup needed"),
    FreeModel(LLMProvider.OPENROUTER, "google/gemma-2-9b-it:free", "Gemma 2 9B (Free)", "8K", "Fast", "Free on OpenRouter"),
    FreeModel(LLMProvider.OPENROUTER, "meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Free)", "128K", "Fast", "Free on OpenRouter"),
    FreeModel(LLMProvider.GITHUB_MODELS, "gpt-4o", "GPT-4o (GitHub Token)", "128K", "Fast", "Free with GitHub account")
)

// ─── Agent Models ─────────────────────────────────────────────────────────────

enum class AgentStatus {
    IDLE, THINKING, ACTING, WAITING, ERROR, PAUSED
}

data class AgentMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "chat" // "telegram", "voice", "chat", "cron"
)

data class AgentAction(
    val type: ActionType,
    val target: String = "",
    val value: String = "",
    val packageName: String = "",
    val thought: String = "",
    val nextGoal: String = ""
)

enum class ActionType {
    TAP,
    TYPE,
    SCROLL_DOWN,
    SCROLL_UP,
    SWIPE,
    LONG_PRESS,
    BACK,
    HOME,
    OPEN_APP,
    WAIT,
    SCREENSHOT,
    DONE,
    ERROR
}

data class ReActStep(
    val thought: String,
    val action: AgentAction,
    val observation: String = "",
    val stepNumber: Int = 0
)

// ─── Screen Models ────────────────────────────────────────────────────────────

data class ScreenElement(
    val id: Int,
    val type: String,        // button, input, text, checkbox, etc.
    val text: String,
    val contentDescription: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: String,      // "[x1,y1][x2,y2]"
    val packageName: String,
    val className: String
)

data class ScreenState(
    val elements: List<ScreenElement>,
    val packageName: String,
    val activityName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val screenshotBase64: String? = null
) {
    // Convert to HTML-style representation for better LLM understanding
    fun toHtmlRepresentation(): String {
        val sb = StringBuilder()
        sb.appendLine("<!-- Screen: $activityName | App: $packageName -->")
        sb.appendLine("<screen>")
        elements.forEach { el ->
            val tag = when {
                el.isEditable -> "input"
                el.isClickable && el.type.contains("Button", ignoreCase = true) -> "button"
                el.isClickable -> "a"
                el.isScrollable -> "scroll"
                else -> "text"
            }
            sb.append("  <$tag id=\"${el.id}\"")
            if (el.text.isNotEmpty()) sb.append(" text=\"${el.text.take(80)}\"")
            if (el.contentDescription.isNotEmpty()) sb.append(" desc=\"${el.contentDescription.take(60)}\"")
            if (el.isClickable) sb.append(" clickable=\"true\"")
            if (el.isEditable) sb.append(" editable=\"true\"")
            sb.appendLine(" />")
        }
        sb.appendLine("</screen>")
        return sb.toString()
    }
}

// ─── Memory Models ───────────────────────────────────────────────────────────

data class MemoryEntry(
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Skill Models ────────────────────────────────────────────────────────────

data class Skill(
    val name: String,
    val description: String,
    val trigger: String,       // regex or keyword
    val content: String,       // full SKILL.md content
    val filePath: String,
    val isBuiltIn: Boolean = false
)

// ─── Cron Models ─────────────────────────────────────────────────────────────

data class CronJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val cronExpression: String,  // "HH:mm" for daily, or full cron
    val prompt: String,
    val isEnabled: Boolean = true,
    val lastRun: Long = 0,
    val channel: String = "telegram" // where to report
)

// ─── LLM Request/Response ────────────────────────────────────────────────────

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class LLMRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    @SerializedName("temperature") val temperature: Float = 0.1f,
    @SerializedName("stream") val stream: Boolean = false
)

data class LLMResponse(
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("error") val error: LLMError?
) {
    fun getText(): String = choices?.firstOrNull()?.message?.content ?: ""
}

data class Choice(
    @SerializedName("message") val message: ChatMessage
)

data class LLMError(
    @SerializedName("message") val message: String,
    @SerializedName("type") val type: String
)

// ─── Setup Models ────────────────────────────────────────────────────────────

data class SetupConfig(
    val isComplete: Boolean = false,
    val llmConfig: LLMConfig = LLMConfig(),
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val agentName: String = "AndroidClaw",
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false
)
