package com.mobileagent.data

import java.text.SimpleDateFormat
import java.util.*

enum class LogType { ACTION, RESULT, ERROR, INFO }

data class AgentLog(
    val message: String,
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val tag: String
        get() = when (type) {
            LogType.ACTION -> "ACTION"
            LogType.RESULT -> "RESULT"
            LogType.ERROR  -> "ERROR"
            LogType.INFO   -> "INFO"
        }
}