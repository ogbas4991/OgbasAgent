package com.mobileagent

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.mobileagent.agent.AgentLoop
import com.mobileagent.data.AgentLog
import com.mobileagent.data.LogType
import com.mobileagent.data.Prefs
import com.mobileagent.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that runs the agent loop.
 * Keeps the process alive while the agent is working on a task.
 * Shows a persistent notification so the user knows the agent is active.
 */
class AgentForegroundService : Service() {

    companion object {
        private const val TAG = "AgentFGService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mobileagent_channel"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentTask: String = ""
            private set

        private var currentLoop: AgentLoop? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val task = intent?.getStringExtra("task")
        if (task.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentTask = task
        isRunning = true

        // Start foreground with notification
        val notification = buildNotification(task)
        startForeground(NOTIFICATION_ID, notification)

        Log.i(TAG, "Foreground service started with task: $task")

        // Start the agent loop
        startAgentLoop(task)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        currentLoop?.stop()
        currentLoop = null
        isRunning = false
        currentTask = ""
        serviceScope.cancel()
        Log.i(TAG, "Foreground service destroyed")
        super.onDestroy()
    }

    /** Called from the UI to stop the agent. */
    fun stopAgent() {
        currentLoop?.stop()
        currentLoop = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAgentLoop(task: String) {
        // Stop any previous loop
        currentLoop?.stop()

        val svc = AgentAccessibilityService.instance
        if (svc == null) {
            Log.e(TAG, "Cannot start loop: AccessibilityService not connected")
            stopSelf()
            return
        }

        val loop = AgentLoop(
            context = this,
            task = task,
            onLog = { log ->
                svc.addLog(log)
                // Also notify Telegram if enabled
                if (Prefs.telegramEnabled) {
                    TelegramBotManager.sendStatus(log.message)
                }
            },
            onComplete = { success, summary ->
                val msg = if (success) "Task completed: $summary"
                          else "Task failed: $summary"
                svc.addLog(AgentLog(msg, if (success) LogType.RESULT else LogType.ERROR))

                // Notify Telegram
                if (Prefs.telegramEnabled) {
                    TelegramBotManager.sendStatus(msg)
                }

                // Stop the foreground service
                isRunning = false
                currentTask = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )

        currentLoop = loop
        loop.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MobileAgent task execution"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(task: String): Notification {
        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(task)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_delete,
                    "Stop",
                    stopPendingIntent
                )
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(task)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build()
        }
    }
}