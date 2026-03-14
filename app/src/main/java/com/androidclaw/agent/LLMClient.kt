package com.androidclaw.agent

import android.content.Context
import com.androidclaw.models.*
import com.androidclaw.security.SecurityManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLMClient
 *
 * Unified client for all LLM providers.
 * Handles Gemini, Groq, OpenRouter, Pollinations, GitHub Models, OpenAI, Custom.
 * Auto-fallback: if primary fails, tries next provider in chain.
 */
class LLMClient(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // ─── Main Entry Point ─────────────────────────────────────────────────────

    suspend fun complete(
        config: LLMConfig,
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val apiKey = if (config.provider.requiresKey) {
                SecurityManager.getApiKey(context, config.provider.name.lowercase())
                    .ifEmpty { config.apiKey }
            } else ""

            when (config.provider) {
                LLMProvider.GEMINI -> callGemini(apiKey, config.modelId, messages, systemPrompt)
                LLMProvider.GROQ -> callOpenAICompatible(
                    config.provider.baseUrl, apiKey, config.modelId, messages, systemPrompt
                )
                LLMProvider.OPENROUTER -> callOpenAICompatible(
                    config.provider.baseUrl, apiKey, config.modelId, messages, systemPrompt,
                    extraHeaders = mapOf("HTTP-Referer" to "https://androidclaw.app", "X-Title" to "AndroidClaw")
                )
                LLMProvider.OPENAI -> callOpenAICompatible(
                    config.provider.baseUrl, apiKey, config.modelId, messages, systemPrompt
                )
                LLMProvider.GITHUB_MODELS -> callOpenAICompatible(
                    config.provider.baseUrl, apiKey, config.modelId, messages, systemPrompt
                )
                LLMProvider.POLLINATIONS -> callPollinations(config.modelId, messages, systemPrompt)
                LLMProvider.CUSTOM -> callOpenAICompatible(
                    config.customBaseUrl, apiKey, config.modelId, messages, systemPrompt
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Gemini ───────────────────────────────────────────────────────────────

    private fun callGemini(
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?
    ): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

        val contents = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            })
        }

        val body = JSONObject().apply {
            put("contents", contents)
            if (systemPrompt != null) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 2048)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))

        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                Result.failure(Exception(json.getJSONObject("error").getString("message")))
            } else {
                val text = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gemini parse error: ${e.message}\nBody: $responseBody"))
        }
    }

    // ─── OpenAI-Compatible (Groq, OpenRouter, GitHub Models, OpenAI, Custom) ─

    private fun callOpenAICompatible(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<String> {
        val allMessages = JSONArray()

        if (systemPrompt != null) {
            allMessages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        messages.forEach { msg ->
            allMessages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("model", modelId)
            put("messages", allMessages)
            put("max_tokens", 2048)
            put("temperature", 0.1)
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))

        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                val errMsg = json.getJSONObject("error").optString("message", "Unknown error")
                Result.failure(Exception(errMsg))
            } else {
                val text = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Parse error: ${e.message}\nBody: $responseBody"))
        }
    }

    // ─── Pollinations (Zero Key Required) ────────────────────────────────────

    private fun callPollinations(
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?
    ): Result<String> {
        val allMessages = JSONArray()
        if (systemPrompt != null) {
            allMessages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        messages.forEach { msg ->
            allMessages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("model", modelId)
            put("messages", allMessages)
            put("private", true) // don't appear in public feed
        }

        val request = Request.Builder()
            .url("https://text.pollinations.ai/openai")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty"))

        return try {
            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Fallback Chain ───────────────────────────────────────────────────────

    suspend fun completeWithFallback(
        primaryConfig: LLMConfig,
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): Result<String> {
        // Try primary
        val primary = complete(primaryConfig, messages, systemPrompt)
        if (primary.isSuccess) return primary

        // Fallback 1: Pollinations (no key needed, always works)
        val pollinationsConfig = LLMConfig(
            provider = LLMProvider.POLLINATIONS,
            modelId = "openai"
        )
        val fallback = complete(pollinationsConfig, messages, systemPrompt)
        if (fallback.isSuccess) return fallback

        // Return original error
        return primary
    }
}
