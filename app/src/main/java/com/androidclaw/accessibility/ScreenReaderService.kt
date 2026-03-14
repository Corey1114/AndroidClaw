package com.androidclaw.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.androidclaw.models.ScreenElement
import com.androidclaw.models.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ScreenReaderService
 *
 * The perception layer of AndroidClaw.
 * Reads every screen as structured data — no screenshots, no OCR needed.
 * Converts Android's AccessibilityNodeInfo tree into HTML-style representation
 * that LLMs understand natively (they're trained on HTML).
 */
class ScreenReaderService : AccessibilityService() {

    companion object {
        // Singleton access from agent
        private val _screenState = MutableStateFlow<ScreenState?>(null)
        val screenState: StateFlow<ScreenState?> = _screenState

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        // Action execution channel
        private val _pendingAction = MutableStateFlow<AccessibilityAction?>(null)
        val pendingAction: StateFlow<AccessibilityAction?> = _pendingAction

        fun requestAction(action: AccessibilityAction) {
            _pendingAction.value = action
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true

        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Update screen state on relevant events
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                captureScreen(event.packageName?.toString() ?: "")
            }
        }

        // Execute any pending action
        _pendingAction.value?.let { action ->
            _pendingAction.value = null
            executeAction(action)
        }
    }

    override fun onInterrupt() {
        _isRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }

    // ─── Screen Capture ───────────────────────────────────────────────────────

    private fun captureScreen(packageName: String) {
        val root = rootInActiveWindow ?: return
        val elements = mutableListOf<ScreenElement>()
        var idCounter = 0

        traverseNode(root, elements, idCounter)
        root.recycle()

        val activityName = try {
            windows?.firstOrNull()?.root?.className?.toString() ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        _screenState.value = ScreenState(
            elements = elements.take(300), // cap at 300 elements
            packageName = packageName,
            activityName = activityName
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<ScreenElement>,
        idCounter: Int
    ): Int {
        var counter = idCounter

        // Skip invisible or empty nodes
        if (!node.isVisibleToUser) return counter

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        // Only include nodes with text, description, or interaction capability
        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            elements.add(
                ScreenElement(
                    id = counter++,
                    type = getElementType(node),
                    text = text,
                    contentDescription = desc,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    bounds = "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]",
                    packageName = node.packageName?.toString() ?: "",
                    className = node.className?.toString()?.substringAfterLast('.') ?: ""
                )
            )
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            counter = traverseNode(child, elements, counter)
            child.recycle()
        }

        return counter
    }

    private fun getElementType(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.') ?: ""
        return when {
            node.isEditable -> "input"
            className.contains("Button", ignoreCase = true) -> "button"
            className.contains("EditText", ignoreCase = true) -> "input"
            className.contains("CheckBox", ignoreCase = true) -> "checkbox"
            className.contains("Switch", ignoreCase = true) -> "switch"
            className.contains("Image", ignoreCase = true) -> "image"
            className.contains("List", ignoreCase = true) -> "list"
            node.isClickable -> "clickable"
            node.isScrollable -> "scrollable"
            else -> "text"
        }
    }

    // ─── Action Execution ─────────────────────────────────────────────────────

    private fun executeAction(action: AccessibilityAction) {
        when (action) {
            is AccessibilityAction.TapById -> tapById(action.elementId)
            is AccessibilityAction.TapByText -> tapByText(action.text)
            is AccessibilityAction.TypeText -> typeText(action.elementId, action.text)
            is AccessibilityAction.ScrollDown -> scrollDown()
            is AccessibilityAction.ScrollUp -> scrollUp()
            is AccessibilityAction.PressBack -> performGlobalAction(GLOBAL_ACTION_BACK)
            is AccessibilityAction.PressHome -> performGlobalAction(GLOBAL_ACTION_HOME)
            is AccessibilityAction.OpenNotifications -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            is AccessibilityAction.TakeScreenshot -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    private fun tapById(elementId: Int) {
        val root = rootInActiveWindow ?: return
        val elements = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, elements)

        val target = elements.getOrNull(elementId)
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        elements.forEach { it.recycle() }
        root.recycle()
    }

    private fun tapByText(text: String) {
        val root = rootInActiveWindow ?: return
        val results = root.findAccessibilityNodeInfosByText(text)
        results?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        results?.forEach { it.recycle() }
        root.recycle()
    }

    private fun typeText(elementId: Int, text: String) {
        val root = rootInActiveWindow ?: return
        val elements = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, elements)

        val target = elements.getOrNull(elementId)
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        target?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        elements.forEach { it.recycle() }
        root.recycle()
    }

    private fun scrollDown() {
        val root = rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        root.recycle()
    }

    private fun scrollUp() {
        val root = rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        root.recycle()
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodes(child, list)
        }
    }
}

// ─── Action Sealed Class ──────────────────────────────────────────────────────

sealed class AccessibilityAction {
    data class TapById(val elementId: Int) : AccessibilityAction()
    data class TapByText(val text: String) : AccessibilityAction()
    data class TypeText(val elementId: Int, val text: String) : AccessibilityAction()
    object ScrollDown : AccessibilityAction()
    object ScrollUp : AccessibilityAction()
    object PressBack : AccessibilityAction()
    object PressHome : AccessibilityAction()
    object OpenNotifications : AccessibilityAction()
    object TakeScreenshot : AccessibilityAction()
}
