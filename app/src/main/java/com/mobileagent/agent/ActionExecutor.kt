package com.mobileagent.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.mobileagent.AgentAccessibilityService

/**
 * Executes parsed AgentAction objects via the AccessibilityService.
 * Handles taps, long presses, text input, scrolling, and system buttons.
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"
    private const val GESTURE_DURATION_TAP = 100L
    private const val GESTURE_DURATION_LONG_PRESS = 600L
    private const val SCROLL_DISTANCE = 500

    /**
     * Execute an action and return a result description string.
     */
    fun execute(action: AgentAction): String {
        val svc = AgentAccessibilityService.instance
            ?: return "Error: AccessibilityService not connected"

        return when (action) {
            is AgentAction.Tap -> executeTap(svc, action)
            is AgentAction.LongPress -> executeLongPress(svc, action)
            is AgentAction.TypeText -> executeTypeText(svc, action)
            is AgentAction.Scroll -> executeScroll(svc, action)
            is AgentAction.PressButton -> executePressButton(svc, action)
            is AgentAction.Done -> action.summary
            is AgentAction.Error -> "LLM error: ${action.message}"
            is AgentAction.Unknown -> "Unparseable LLM response: ${action.raw.take(100)}"
        }
    }

    private fun executeTap(svc: AgentAccessibilityService, action: AgentAction.Tap): String {
        val path = Path().apply { moveTo(action.x, action.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_TAP))
            .build()

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            var result = false
            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { result = true }
                override fun onCancelled(gestureDescription: GestureDescription?) { result = false }
            }, null)
            Thread.sleep(GESTURE_DURATION_TAP + 50)
            result
        } else {
            false
        }

        val desc = action.description ?: ""
        return if (success) "Tapped at (${action.x.toInt()}, ${action.y.toInt()}) - $desc"
        else "Failed to tap at (${action.x.toInt()}, ${action.y.toInt()})"
    }

    private fun executeLongPress(svc: AgentAccessibilityService, action: AgentAction.LongPress): String {
        val path = Path().apply { moveTo(action.x, action.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_LONG_PRESS))
            .build()

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            var result = false
            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { result = true }
                override fun onCancelled(gestureDescription: GestureDescription?) { result = false }
            }, null)
            Thread.sleep(GESTURE_DURATION_LONG_PRESS + 100)
            result
        } else {
            false
        }

        val desc = action.description ?: ""
        return if (success) "Long pressed at (${action.x.toInt()}, ${action.y.toInt()}) - $desc"
        else "Failed to long press at (${action.x.toInt()}, ${action.y.toInt()})"
    }

    private fun executeTypeText(svc: AgentAccessibilityService, action: AgentAction.TypeText): String {
        val root = svc.rootInActiveWindow
        val editableNode = findFocusedEditable(root)

        if (editableNode != null) {
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    action.text
                )
            }
            val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return if (success) "Typed: \"${action.text}\""
            else "Failed to type text into field"
        } else {
            // Fallback: try ACTION_PASTE via clipboard
            return "No focused editable field found. Cannot type text."
        }
    }

    private fun executeScroll(svc: AgentAccessibilityService, action: AgentAction.Scroll): String {
        val distance = action.distance ?: SCROLL_DISTANCE
        val displayMetrics = svc.resources.displayMetrics
        val cx = displayMetrics.widthPixels / 2f
        val cy = displayMetrics.heightPixels / 2f
        val dist = (distance * displayMetrics.density).toInt()

        val direction = action.direction.lowercase()
        val path = Path()

        when (direction) {
            "up" -> {
                path.moveTo(cx, cy + dist / 2f)
                path.lineTo(cx, cy - dist / 2f)
            }
            "down" -> {
                path.moveTo(cx, cy - dist / 2f)
                path.lineTo(cx, cy + dist / 2f)
            }
            "left" -> {
                path.moveTo(cx + dist / 2f, cy)
                path.lineTo(cx - dist / 2f, cy)
            }
            "right" -> {
                path.moveTo(cx - dist / 2f, cy)
                path.lineTo(cx + dist / 2f, cy)
            }
            else -> return "Unknown scroll direction: ${action.direction}"
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.dispatchGesture(gesture, null, null)
            Thread.sleep(400)
        }
        return "Scrolled $direction"
    }

    private fun executePressButton(svc: AgentAccessibilityService, action: AgentAction.PressButton): String {
        val result = when (action.button.lowercase()) {
            "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents", "recent_apps", "recent" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications", "notification_shade" -> {
                if (Build.VERSION.SDK_INT >= 33) svc.performGlobalAction(0x00000100) else false
            }
            "quick_settings" -> {
                if (Build.VERSION.SDK_INT >= 33) svc.performGlobalAction(0x00000200) else false
            }
            "power_dialog" -> {
                if (Build.VERSION.SDK_INT >= 33) svc.performGlobalAction(0x00000400) else false
            }
            else -> false
        }
        Thread.sleep(300)
        return if (result) "Pressed ${action.button} button"
        else "Failed to press ${action.button} button"
    }

    // ── Helper: find focused editable node ──

    private fun findFocusedEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable && root.isFocused) return root
        if (root.isFocused && root.isEditable) return root

        // Search children
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val found = findFocusedEditable(child)
            if (found != null) return found
        }

        // If no focused editable, find any editable
        return findAnyEditable(root)
    }

    private fun findAnyEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findAnyEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}