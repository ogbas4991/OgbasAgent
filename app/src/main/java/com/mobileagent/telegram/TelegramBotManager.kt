package com.mobileagent.telegram

import android.content.Context
import android.util.Log
import com.mobileagent.AgentAccessibilityService
import com.mobileagent.AgentForegroundService
import com.mobileagent.data.Prefs
import com.google.gson.Gson
import com.google.gson.JsonParser
import android.content.Intent
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Telegram bot integration using long-polling.
 * Users can send tasks via Telegram and the agent executes them on the phone.
 */
object TelegramBotManager {

    private const val TAG = "TelegramBot"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var pollingJob: Job? = null
    private var scope: CoroutineScope? = null

    fun start(context: Context) {
        stop()
        if (Prefs.telegramBotToken.isBlank()) {
            Log.w(TAG, "Telegram bot token not set")
            return
        }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollingJob = scope?.launch {
            Log.i(TAG, "Telegram polling started")
            while (isActive) {
                try {
                    pollUpdates(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        scope?.cancel()
        scope = null
    }

    private suspend fun pollUpdates(context: Context) {
        val token = Prefs.telegramBotToken
        val offset = Prefs.telegramOffset
        val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=30&allowed_updates=[\"message\"]"

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return

        val json = JsonParser.parseString(body).asJsonObject
        val ok = json.get("ok")?.asBoolean ?: return
        if (!ok) return

        val results = json.getAsJsonArray("result")
        for (result in results) {
            val update = result.asJsonObject
            val updateId = update.get("update_id")?.asLong ?: continue
            Prefs.telegramOffset = updateId + 1

            val message = update.getAsJsonObject("message")
            val text = message.get("text")?.asString
            val chatId = message.getAsJsonObject("chat")?.get("id")?.asLong ?: continue

            if (!text.isNullOrBlank()) {
                Log.i(TAG, "Received: $text")
                handleCommand(text, chatId, context)
            }
        }
    }

    private fun handleCommand(text: String, chatId: Long, context: Context) {
        if (AgentAccessibilityService.instance == null) {
            sendMessage(chatId, "Error: Accessibility service is not running.")
            return
        }

        if (AgentForegroundService.isRunning) {
            sendMessage(chatId, "Agent is already running. Please wait.")
            return
        }

        sendMessage(chatId, "Starting task: $text")

        val intent = Intent(context, AgentForegroundService::class.java).apply {
            putExtra("task", text)
        }
        context.startForegroundService(intent)
    }

    fun sendStatus(message: String) {
        // In a production app, you'd store the chatId from the last command
        // and send status updates there. For simplicity, we skip auto-sending.
        // The result is sent via the onComplete callback in AgentForegroundService.
    }

    private fun sendMessage(chatId: Long, text: String) {
        try {
            val token = Prefs.telegramBotToken
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val payload = mapOf("chat_id" to chatId, "text" to text)
            val json = gson.toJson(payload)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
        }
    }
}