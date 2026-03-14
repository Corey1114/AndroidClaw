package com.androidclaw.memory

import android.content.Context
import com.androidclaw.security.SecurityManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MemoryManager
 *
 * Persistent memory using encrypted Markdown files.
 * Same pattern as OpenClaw — human-readable, hackable, versioned.
 * All files encrypted with AES-256-GCM via SecurityManager.
 */
class MemoryManager(private val context: Context) {

    private val memoryDir = File(context.filesDir, "memory")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        memoryDir.mkdirs()
    }

    // ─── Core Memory Operations ───────────────────────────────────────────────

    fun remember(key: String, value: String, category: String = "general") {
        val filename = "memory_${category}.md"
        val existing = readMemoryFile(filename)
        val timestamp = dateFormat.format(Date())
        val entry = "- [$timestamp] **$key**: $value\n"
        writeMemoryFile(filename, existing + entry)
    }

    fun forget(key: String) {
        memoryDir.listFiles()?.forEach { file ->
            val content = readMemoryFile(file.name)
            val filtered = content.lines()
                .filter { !it.contains("**$key**") }
                .joinToString("\n")
            writeMemoryFile(file.name, filtered)
        }
    }

    fun getRecentMemory(maxChars: Int = 2000): String {
        val sb = StringBuilder()
        memoryDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
            val content = readMemoryFile(file.name)
            if (content.isNotEmpty()) {
                sb.appendLine("### ${file.nameWithoutExtension.replace("memory_", "").uppercase()}")
                // Get last 20 entries
                val lines = content.lines().filter { it.startsWith("- [") }.takeLast(20)
                lines.forEach { sb.appendLine(it) }
                sb.appendLine()
            }
        }
        return sb.toString().take(maxChars)
    }

    fun saveInteraction(goal: String, result: String) {
        remember(
            key = goal.take(60),
            value = result.take(120),
            category = "interactions"
        )
    }

    fun saveUserPreference(key: String, value: String) {
        remember(key, value, "preferences")
    }

    fun getAllMemory(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        memoryDir.listFiles()?.forEach { file ->
            result[file.nameWithoutExtension] = readMemoryFile(file.name)
        }
        return result
    }

    fun clearAll() {
        memoryDir.listFiles()?.forEach { it.delete() }
    }

    // ─── File Operations (Encrypted) ──────────────────────────────────────────

    private fun readMemoryFile(filename: String): String {
        val file = File(memoryDir, filename)
        if (!file.exists()) return ""
        return try {
            val encrypted = file.readText()
            SecurityManager.decrypt(encrypted).ifEmpty { file.readText() }
        } catch (e: Exception) {
            try { file.readText() } catch (e2: Exception) { "" }
        }
    }

    private fun writeMemoryFile(filename: String, content: String) {
        val file = File(memoryDir, filename)
        val encrypted = SecurityManager.encrypt(content)
        file.writeText(encrypted)
    }

    // ─── Skills Memory (separate, unencrypted for easy editing) ───────────────

    fun loadExternalMemory(path: String): String {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            ""
        }
    }
}
