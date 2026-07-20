package com.mobileagent.llm

import com.google.gson.Gson
import com.mobileagent.agent.AgentAction
import com.mobileagent.agent.ActionParser
import com.mobileagent.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * LLM client compatible with any OpenAI-compatible API:
 * OpenRouter, DeepSeek, Ollama, OpenAI, etc.
 */
class LlmClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    private val SYSTEM_PROMPT = """
You are an Android phone automation agent. You receive a description of what is currently visible on the user's screen and a task to perform.

Based on the screen layout, decide the NEXT action to take. You MUST respond with ONLY a single JSON object (no markdown, no explanation, no extra text).

Available action types:
1. {"type": "tap", "x": <centerX>, "y": <centerY>, "description": "tapped on <element>"}
2. {"type": "long_press", "x": <centerX>, "y": <centerY>, "description": "long pressed <element>"}
3. {"type": "type_text", "text": "<text to type>", "description": "typed <text>"}
4. {"type": "scroll", "direction": "<up|down|left|right>", "description": "scrolled <direction>"}
5. {"type": "press_button", "button": "<back|home|recents>", "description": "pressed <button>"}
6. {"type": "done", "summary": "<brief summary of what was accomplished>"}

Rules:
- Use the CENTER coordinates from the element's bounds for taps.
- For text input, use type_text ONLY when a text field is focused or has just been tapped.
- After tapping a text field, wait for the next step to type text.
- Scroll when the target element is not visible on screen.
- Use "done" when the task is fully completed.
- Use "back" button to navigate back if needed.
- Include a brief description of what you're doing and why.
- Do NOT explain your reasoning. Output ONLY the JSON object.
- If you cannot proceed, output: {"type": "done", "summary": "Cannot complete: <reason>"}""".trimIndent()

    /**
     * Resolve the correct API key.
     * If the main apiKey is set, use it.
     * Otherwise, fall back to the provider-specific key based on the current baseUrl.
     */
    private fun resolveApiKey(baseUrl: String): String {
        val mainKey = Prefs.apiKey
        if (mainKey.isNotBlank()) return mainKey

        // Fallback: match baseUrl to a known provider and use its dedicated key
        return when {
            baseUrl.contains("openrouter.ai") -> Prefs.openRouterKey
            baseUrl.contains("nvidia.com") -> Prefs.nimKey
            baseUrl.contains("deepseek.com") -> Prefs.apiKey  // uses main field for DeepSeek
            else -> ""
        }
    }

    suspend fun decideAction(
        task: String,
        screenDescription: String,
        history: List<Pair<String, String>>
    ): AgentAction = withContext(Dispatchers.IO) {
        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", SYSTEM_PROMPT))

        val userPrompt = "Task: $task\n\nCurrent screen:\n$screenDescription\n\nWhat is the next action?"
        messages.add(ChatMessage("user", userPrompt))

        // Add recent history for context (last 10 exchanges)
        val recentHistory = history.takeLast(10)
        for ((prevScreen, prevAction) in recentHistory) {
            messages.add(ChatMessage("user", "Previous screen:\n$prevScreen"))
            messages.add(ChatMessage("assistant", prevAction))
        }

        val baseUrl = Prefs.baseUrl.trimEnd('/')
        val apiKey = resolveApiKey(baseUrl)
        if (apiKey.isBlank()) {
            throw IllegalStateException("No API key configured. Please set an API key in Settings.")
        }

        val fullUrl = "$baseUrl/chat/completions"
        val maskedKey = if (apiKey.length > 8) "${apiKey.take(4)}...${apiKey.takeLast(4)}" else "***"

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")

        // OpenRouter-specific headers
        if (baseUrl.contains("openrouter.ai")) {
            requestBuilder.addHeader("HTTP-Referer", "https://mobileagent.local")
                .addHeader("X-Title", "MobileAgent")
        }

        val request = requestBuilder.post(buildRequestBody(messages)).build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IllegalStateException("Empty response from LLM API")

        if (!response.isSuccessful) {
            throw IllegalStateException("LLM API error ${response.code} at $fullUrl (key: $maskedKey): $responseBody")
        }

        // Parse the OpenAI response format
        val parsed = gson.fromJson(responseBody, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val choices = parsed["choices"] as? List<Map<String, Any>>
        val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
        val text = message?.get("content") as? String
            ?: throw IllegalStateException("Could not extract content from LLM response: $responseBody")

        ActionParser.parse(text)
    }

    private fun buildRequestBody(messages: List<ChatMessage>): okhttp3.RequestBody {
        val body = mapOf(
            "model" to Prefs.model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to 0.1,
            "max_tokens" to 500
        )
        return gson.toJson(body).toRequestBody(JSON)
    }
}