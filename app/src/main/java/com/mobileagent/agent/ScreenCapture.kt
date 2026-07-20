package com.mobileagent.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class UiElement(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isEnabled: Boolean
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    val label: String
        get() {
            val parts = mutableListOf<String>()
            text?.takeIf { it.isNotBlank() }?.let { parts.add("text=\"$it\"") }
            contentDescription?.takeIf { it.isNotBlank() }?.let { parts.add("desc=\"$it\"") }
            className?.substringAfterLast(".")?.let { parts.add("class=$it") }
            if (isCheckable) parts.add("checked=$isChecked")
            if (isEditable) parts.add("editable")
            if (isScrollable) parts.add("scrollable")
            return parts.joinToString(", ")
        }

    fun toPromptLine(index: Int): String {
        val b = bounds
        return "[$index] $label | bounds=(${b.left},${b.top},${b.right},${b.bottom}) center=($centerX,$centerY)"
    }
}

object ScreenCapture {

    fun capture(root: AccessibilityNodeInfo?): List<UiElement> {
        val elements = mutableListOf<UiElement>()
        walk(root, elements, 0)
        return elements
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<UiElement>, depth: Int) {
        if (node == null) return
        if (!node.isVisibleToUser) return

        val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isLongClickable
        val hasText = !node.text.isNullOrBlank()
        val hasDesc = !node.contentDescription.isNullOrBlank()
        val meaningful = isInteractive || (hasText && depth < 10) || (hasDesc && depth < 10)

        if (meaningful) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            out.add(
                UiElement(
                    id = node.viewIdResourceName,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    className = node.className?.toString(),
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked,
                    isEnabled = node.isEnabled
                )
            )
        }

        if (depth < 30) {
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), out, depth + 1)
            }
        }
    }

    fun formatForPrompt(elements: List<UiElement>, screenWidth: Int, screenHeight: Int): String {
        val sb = StringBuilder()
        sb.appendLine("Screen: ${screenWidth}x${screenHeight} | ${elements.size} interactive elements found:")
        sb.appendLine("---")
        elements.forEachIndexed { index, el ->
            sb.appendLine(el.toPromptLine(index))
        }
        return sb.toString()
    }
}