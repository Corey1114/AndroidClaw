package com.androidclaw.agent

import android.content.Context
import com.androidclaw.accessibility.AccessibilityAction
import com.androidclaw.accessibility.ScreenReaderService
import com.androidclaw.memory.MemoryManager
import com.androidclaw.models.*
import com.androidclaw.skills.SkillsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * AgentBrain
 *
 * The ReAct (Reason + Act) loop — the core of AndroidClaw.
 *
 * Loop:
 * 1. Receive goal
 * 2. Load relevant memory + skills
 * 3. Send screen state + goal to LLM
 * 4. LLM returns { thought, action, next_goal }
 * 5. Execute action via AccessibilityService
 * 6. Observe result (new screen state)
 * 7. Repeat until next_goal == "DONE"
 */
class AgentBrain(
    private val context: Context,
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager,
    private val skillsManager: SkillsManager
) {

    private val _status = MutableStateFlow(AgentStatus.IDLE)
    val status: StateFlow<AgentStatus> = _status

    private val _currentTask = MutableStateFlow("")
    val currentTask: StateFlow<String> = _currentTask

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val conversationHistory = mutableListOf<ChatMessage>()
    private var config: LLMConfig = LLMConfig()

    fun setConfig(llmConfig: LLMConfig) {
        config = llmConfig
    }

    // ─── System Prompt ────────────────────────────────────────────────────────

    private fun buildSystemPrompt(): String {
        val memory = memoryManager.getRecentMemory()
        val skills = skillsManager.getSkillsSummary()

        return """
You are AndroidClaw, an autonomous AI agent running on an Android phone.
You control the phone to complete user goals by reading the screen and taking actions.

## YOUR CAPABILITIES
- Read any screen as structured HTML elements
- Tap, type, scroll, swipe, press Back/Home
- Open any app by package name
- Remember information across conversations
- Execute scheduled tasks

## HOW TO RESPOND
Always respond in this exact JSON format:
{
  "thought": "What I observe and what I plan to do",
  "action": {
    "type": "TAP|TYPE|SCROLL_DOWN|SCROLL_UP|BACK|HOME|OPEN_APP|WAIT|DONE",
    "element_id": 0,
    "element_text": "text to find if no id",
    "value": "text to type (for TYPE action)",
    "package_name": "com.example.app (for OPEN_APP)"
  },
  "next_goal": "What to do after this action, or DONE if complete",
  "message": "Optional message to report back to user"
}

## ACTION TYPES
- TAP: tap element by id or text
- TYPE: type text into an input field  
- SCROLL_DOWN / SCROLL_UP: scroll the screen
- BACK: press back button
- HOME: press home button
- OPEN_APP: launch an app by package name
- WAIT: wait 2 seconds for screen to load
- DONE: task is complete, report back

## MEMORY
${if (memory.isNotEmpty()) "Relevant memory:\n$memory" else "No relevant memory yet."}

## AVAILABLE SKILLS
${if (skills.isNotEmpty()) skills else "No custom skills loaded."}

## RULES
- Never ask for confirmation mid-task, just complete it
- If an element is not found, scroll and try again
- If stuck after 3 attempts, report failure with reason
- Always set next_goal to DONE when the task is complete
- Keep thought under 100 words
""".trimIndent()
    }

    // ─── Main ReAct Loop ──────────────────────────────────────────────────────

    suspend fun executeGoal(goal: String): String {
        _status.value = AgentStatus.THINKING
        _currentTask.value = goal
        log("🎯 Goal: $goal")

        conversationHistory.clear()
        conversationHistory.add(ChatMessage("user", "Goal: $goal"))

        var stepCount = 0
        val maxSteps = 25
        var finalMessage = ""

        try {
            while (stepCount < maxSteps) {
                stepCount++
                log("Step $stepCount/$maxSteps")

                // Get current screen state
                val screenState = ScreenReaderService.screenState.value
                val screenContext = if (screenState != null) {
                    "Current screen:\n${screenState.toHtmlRepresentation()}"
                } else {
                    "Screen state not available. Accessibility service may not be running."
                }

                // Add screen to conversation
                conversationHistory.add(
                    ChatMessage("user", "SCREEN_STATE:\n$screenContext")
                )

                // Ask LLM what to do
                _status.value = AgentStatus.THINKING
                val result = llmClient.completeWithFallback(
                    config,
                    conversationHistory,
                    buildSystemPrompt()
                )

                if (result.isFailure) {
                    log("❌ LLM error: ${result.exceptionOrNull()?.message}")
                    _status.value = AgentStatus.ERROR
                    return "LLM error: ${result.exceptionOrNull()?.message}"
                }

                val responseText = result.getOrNull() ?: ""
                log("🧠 ${responseText.take(200)}")

                // Parse LLM response
                val step = parseReActResponse(responseText)

                // Add assistant response to history
                conversationHistory.add(ChatMessage("assistant", responseText))

                // Log the thought
                if (step.thought.isNotEmpty()) {
                    log("💭 ${step.thought}")
                }

                // Execute action
                _status.value = AgentStatus.ACTING
                val actionResult = executeAction(step.action)
                log("⚡ ${step.action.type}: $actionResult")

                // Save any message for reporting
                if (step.action.type == ActionType.DONE) {
                    finalMessage = step.action.value.ifEmpty { "Task completed successfully." }
                    break
                }

                // Wait for screen to update
                delay(800)

                // Add observation
                conversationHistory.add(
                    ChatMessage("user", "Action result: $actionResult. Continue with: ${step.nextGoal}")
                )
            }

            if (stepCount >= maxSteps) {
                finalMessage = "Reached max steps ($maxSteps). Last state: ${_currentTask.value}"
            }

        } catch (e: Exception) {
            log("❌ Exception: ${e.message}")
            finalMessage = "Error: ${e.message}"
            _status.value = AgentStatus.ERROR
        }

        _status.value = AgentStatus.IDLE
        _currentTask.value = ""

        // Save to memory
        memoryManager.saveInteraction(goal, finalMessage)

        return finalMessage
    }

    // ─── Parse LLM Response ───────────────────────────────────────────────────

    private fun parseReActResponse(text: String): ReActStep {
        return try {
            // Extract JSON from response (handle markdown code blocks)
            val jsonText = text
                .replace("```json", "")
                .replace("```", "")
                .trim()
                .let { raw ->
                    val start = raw.indexOf('{')
                    val end = raw.lastIndexOf('}')
                    if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
                }

            val json = JSONObject(jsonText)
            val actionJson = json.optJSONObject("action")
            val actionTypeStr = actionJson?.optString("type", "WAIT") ?: "WAIT"

            val actionType = try {
                ActionType.valueOf(actionTypeStr.uppercase())
            } catch (e: Exception) {
                ActionType.WAIT
            }

            val action = AgentAction(
                type = actionType,
                target = actionJson?.optString("element_text", "") ?: "",
                value = actionJson?.optString("value", "") ?: json.optString("message", ""),
                packageName = actionJson?.optString("package_name", "") ?: "",
                thought = json.optString("thought", ""),
                nextGoal = json.optString("next_goal", "")
            )

            // If element_id provided, use it
            val elementId = actionJson?.optInt("element_id", -1) ?: -1
            if (elementId >= 0) {
                action.copy(target = elementId.toString())
            }

            ReActStep(
                thought = json.optString("thought", ""),
                action = action,
                nextGoal = json.optString("next_goal", "")
            )
        } catch (e: Exception) {
            // Fallback: just wait
            ReActStep(
                thought = "Parsing failed: ${e.message}",
                action = AgentAction(type = ActionType.WAIT),
                nextGoal = "continue"
            )
        }
    }

    // ─── Execute Action ───────────────────────────────────────────────────────

    private suspend fun executeAction(action: AgentAction): String {
        return when (action.type) {
            ActionType.TAP -> {
                val elementId = action.target.toIntOrNull()
                if (elementId != null) {
                    ScreenReaderService.requestAction(AccessibilityAction.TapById(elementId))
                    "Tapped element #$elementId"
                } else {
                    ScreenReaderService.requestAction(AccessibilityAction.TapByText(action.target))
                    "Tapped text: ${action.target}"
                }
            }
            ActionType.TYPE -> {
                val elementId = action.target.toIntOrNull() ?: 0
                ScreenReaderService.requestAction(
                    AccessibilityAction.TypeText(elementId, action.value)
                )
                "Typed: ${action.value}"
            }
            ActionType.SCROLL_DOWN -> {
                ScreenReaderService.requestAction(AccessibilityAction.ScrollDown)
                "Scrolled down"
            }
            ActionType.SCROLL_UP -> {
                ScreenReaderService.requestAction(AccessibilityAction.ScrollUp)
                "Scrolled up"
            }
            ActionType.BACK -> {
                ScreenReaderService.requestAction(AccessibilityAction.PressBack)
                "Pressed back"
            }
            ActionType.HOME -> {
                ScreenReaderService.requestAction(AccessibilityAction.PressHome)
                "Pressed home"
            }
            ActionType.OPEN_APP -> {
                openApp(action.packageName)
                "Opened app: ${action.packageName}"
            }
            ActionType.WAIT -> {
                delay(2000)
                "Waited 2 seconds"
            }
            ActionType.SCREENSHOT -> {
                ScreenReaderService.requestAction(AccessibilityAction.TakeScreenshot)
                "Took screenshot"
            }
            ActionType.DONE -> {
                "Task complete: ${action.value}"
            }
            ActionType.ERROR -> {
                "Error: ${action.value}"
            }
        }
    }

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    // ─── Logging ──────────────────────────────────────────────────────────────

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$timestamp] $message"
        _log.value = (_log.value + entry).takeLast(100) // keep last 100 entries
    }
}
