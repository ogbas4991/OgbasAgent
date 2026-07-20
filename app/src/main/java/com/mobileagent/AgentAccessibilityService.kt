package com.mobileagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.MutableLiveData
import com.mobileagent.data.AgentLog
import com.mobileagent.data.LogType

/**
 * The core accessibility service. Provides:
 * - Screen tree access via rootInActiveWindow
 * - Gesture dispatch (taps, swipes, long presses)
 * - Global actions (back, home, recents)
 * - Live log stream for the UI
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentService"

        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    /** Observable log list for the UI to consume. */
    val logs = MutableLiveData<List<AgentLog>>(emptyList())

    private val _logsInternal = mutableListOf<AgentLog>()
    private val logsLock = Any()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "AgentAccessibilityService connected")

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        addLog(AgentLog("Accessibility service connected and ready", LogType.INFO))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: track significant screen changes
        // For now, we poll the screen tree from the agent loop instead of reacting to events.
        // This avoids issues with cascading events during automated actions.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    /** Add a log entry and notify observers. Thread-safe. */
    fun addLog(log: AgentLog) {
        synchronized(logsLock) {
            _logsInternal.add(log)
            logs.postValue(_logsInternal.toList())
        }
    }

    /** Clear all logs. */
    fun clearLogs() {
        synchronized(logsLock) {
            _logsInternal.clear()
            logs.postValue(emptyList())
        }
    }
}