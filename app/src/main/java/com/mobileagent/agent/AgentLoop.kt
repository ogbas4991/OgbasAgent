package com.mobileagent.agent

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import com.mobileagent.AgentAccessibilityService
import com.mobileagent.data.AgentLog
import com.mobileagent.data.LogType
import com.mobileagent.data.Prefs
import com.mobileagent.llm.LlmClient
import kotlinx.coroutines.*

/**
 * The core agent loop:
 *   capture screen -> send to LLM -> parse action -> execute -> observe -> repeat
 *
 * Runs as a suspend function so it can be launched from a coroutine in the
 * foreground service or from any scope.
 */
class AgentLoop(
    private val context: Context,
    private val task: String,
    private val onLog: (AgentLog) -> Unit,
    private val onComplete: (Boolean, String) -> Unit
) {
    companion object {
        private const val TAG = "AgentLoop"
    }

    private val llmClient = LlmClient()
    private var isRunning = false
    private var job: Job? = null

    // History of (screenDescription, llmRawResponse) for multi-turn context
    private val history = mutableListOf<Pair<String, String>>()

    fun start() {
        if (isRunning) return
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            runLoop()
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        onLog(AgentLog("Starting task: $task", LogType.INFO))

        val maxSteps = Prefs.maxSteps
        val stepDelay = Prefs.stepDelayMs

        for (step in 1..maxSteps) {
            if (!isRunning) {
                onLog(AgentLog("Agent stopped by user", LogType.INFO))
                onComplete(false, "Stopped by user")
                return
            }

            // 1. Capture screen
            val svc = AgentAccessibilityService.instance
            if (svc == null) {
                onLog(AgentLog("AccessibilityService disconnected, aborting", LogType.ERROR))
                onComplete(false, "Service disconnected")
                return
            }

            val root = svc.rootInActiveWindow
            if (root == null) {
                onLog(AgentLog("Cannot access screen (root is null), waiting...", LogType.INFO))
                delay(stepDelay)
                continue
            }

            val dm: DisplayMetrics = context.resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels

            val elements = ScreenCapture.capture(root)
            val screenDescription = ScreenCapture.formatForPrompt(elements, screenWidth, screenHeight)

            onLog(AgentLog("Step $step/$maxSteps: Captured ${elements.size} elements", LogType.ACTION))

            // 2. Send to LLM
            var action: AgentAction
            try {
                action = llmClient.decideAction(task, screenDescription, history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLog(AgentLog("LLM error: ${e.message}", LogType.ERROR))
                delay(2000)
                try {
                    action = llmClient.decideAction(task, screenDescription, history)
                } catch (e2: Exception) {
                    onLog(AgentLog("LLM retry failed: ${e2.message}", LogType.ERROR))
                    onComplete(false, "LLM error: ${e2.message}")
                    return
                }
            }

            // 3. Check for done
            if (action is AgentAction.Done) {
                onLog(AgentLog("Task completed: ${action.summary}", LogType.RESULT))
                onComplete(true, action.summary)
                return
            }

            if (action is AgentAction.Error) {
                onLog(AgentLog("LLM returned error: ${action.message}", LogType.ERROR))
                onComplete(false, action.message)
                return
            }

            if (action is AgentAction.Unknown) {
                onLog(AgentLog("Unparseable action, retrying... Raw: ${action.raw.take(150)}", LogType.ERROR))
                delay(stepDelay)
                continue
            }

            // 4. Destructive action confirmation
            if (Prefs.confirmDestructive && ActionParser.isDestructive(action)) {
                onLog(AgentLog("BLOCKED: Destructive action detected. ${action.javaClass.simpleName}", LogType.ERROR))
                // In a real app, show a dialog here. For now, skip and log.
                delay(stepDelay)
                continue
            }

            // 5. Execute the action
            val result = ActionExecutor.execute(action)
            onLog(AgentLog(result, LogType.RESULT))

            // Store in history for context
            history.add(screenDescription to "Action: $action")

            // 6. Wait for UI to settle
            delay(stepDelay)
        }

        // Exceeded max steps
        onLog(AgentLog("Max steps ($maxSteps) reached without completion", LogType.ERROR))
        onComplete(false, "Max steps reached")
    }
}