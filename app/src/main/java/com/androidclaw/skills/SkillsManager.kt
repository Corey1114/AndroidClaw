package com.androidclaw.skills

import android.content.Context
import com.androidclaw.models.Skill
import java.io.File

/**
 * SkillsManager
 *
 * Loads SKILL.md files from:
 * 1. Built-in assets/skills/ (bundled with app)
 * 2. External: /sdcard/AndroidClaw/skills/ (user-added)
 * 3. In-app created skills
 *
 * Same SKILL.md format as OpenClaw — fully compatible.
 */
class SkillsManager(private val context: Context) {

    private val skills = mutableMapOf<String, Skill>()
    private val externalSkillsDir = File(
        context.getExternalFilesDir(null), "skills"
    )

    init {
        externalSkillsDir.mkdirs()
        loadAllSkills()
    }

    fun loadAllSkills() {
        skills.clear()
        loadBuiltInSkills()
        loadExternalSkills()
    }

    // ─── Built-in Skills (from assets) ───────────────────────────────────────

    private fun loadBuiltInSkills() {
        try {
            val assetFiles = context.assets.list("skills") ?: return
            assetFiles.filter { it.endsWith(".md") }.forEach { filename ->
                val content = context.assets.open("skills/$filename").bufferedReader().readText()
                parseAndRegisterSkill(content, filename, isBuiltIn = true)
            }
        } catch (e: Exception) {
            // No built-in skills
        }
    }

    // ─── External Skills (user-added) ────────────────────────────────────────

    private fun loadExternalSkills() {
        externalSkillsDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.forEach { file ->
                parseAndRegisterSkill(file.readText(), file.name, isBuiltIn = false)
            }
    }

    private fun parseAndRegisterSkill(content: String, filename: String, isBuiltIn: Boolean) {
        val lines = content.lines()
        val name = lines.firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")?.trim()
            ?: filename.removeSuffix(".md")

        val description = lines.firstOrNull { it.startsWith("> ") || it.startsWith("Description:") }
            ?.removePrefix("> ")?.removePrefix("Description:")?.trim()
            ?: ""

        val trigger = lines.firstOrNull { it.startsWith("Trigger:") || it.contains("trigger:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?: name.lowercase()

        skills[name.lowercase()] = Skill(
            name = name,
            description = description,
            trigger = trigger,
            content = content,
            filePath = filename,
            isBuiltIn = isBuiltIn
        )
    }

    // ─── Skill Access ─────────────────────────────────────────────────────────

    fun findRelevantSkill(goal: String): Skill? {
        val goalLower = goal.lowercase()
        return skills.values.firstOrNull { skill ->
            goalLower.contains(skill.trigger.lowercase()) ||
            goalLower.contains(skill.name.lowercase())
        }
    }

    fun getSkillsSummary(): String {
        if (skills.isEmpty()) return ""
        return skills.values.joinToString("\n") { skill ->
            "- **${skill.name}**: ${skill.description}"
        }
    }

    fun getAllSkills(): List<Skill> = skills.values.toList()

    fun getExternalSkillsPath(): String = externalSkillsDir.absolutePath

    fun createSkill(name: String, description: String, trigger: String, instructions: String) {
        val content = """
# $name
> $description
Trigger: $trigger

## Instructions
$instructions
        """.trimIndent()

        val file = File(externalSkillsDir, "${name.lowercase().replace(" ", "_")}.md")
        file.writeText(content)
        loadAllSkills()
    }

    fun deleteSkill(name: String) {
        val file = File(externalSkillsDir, "${name.lowercase().replace(" ", "_")}.md")
        file.delete()
        skills.remove(name.lowercase())
    }
}
