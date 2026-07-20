package com.mobileagent.agent

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

sealed class AgentAction {
    data class Tap(
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("description") val description: String? = null
    ) : AgentAction()

    data class LongPress(
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("description") val description: String? = null
    ) : AgentAction()

    data class TypeText(
        @SerializedName("text") val text: String,
        @SerializedName("description") val description: String? = null
    ) : AgentAction()

    data class Scroll(
        @SerializedName("direction") val direction: String,
        @SerializedName("distance") val distance: Int? = null,
        @SerializedName("description") val description: String? = null
    ) : AgentAction()

    data class PressButton(
        @SerializedName("button") val button: String,
        @SerializedName("description") val description: String? = null
    ) : AgentAction()

    data class Done(
        @SerializedName("summary") val summary: String
    ) : AgentAction()

    data class Error(
        @SerializedName("message") val message: String
    ) : AgentAction()

    data class Unknown(
        @SerializedName("raw") val raw: String
    ) : AgentAction()
}

object ActionParser {

    private val gson = Gson()

    private val DESTRUCTIVE_KEYWORDS = listOf(
        "delete", "remove", "uninstall", "payment", "purchase", "buy",
        "send money", "transfer", "confirm purchase", "pay", "checkout",
        "erase", "factory reset", "clear all", "format"
    )

    fun parse(llmResponse: String): AgentAction {
        return try {
            val jsonStr = extractJson(llmResponse) ?: llmResponse
            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

            when (val type = jsonObj.get("type")?.asString?.lowercase()) {
                "tap" -> gson.fromJson(jsonObj, AgentAction.Tap::class.java)
                "long_press", "longpress" -> gson.fromJson(jsonObj, AgentAction.LongPress::class.java)
                "type", "type_text", "input" -> gson.fromJson(jsonObj, AgentAction.TypeText::class.java)
                "scroll" -> gson.fromJson(jsonObj, AgentAction.Scroll::class.java)
                "press", "press_button", "button" -> gson.fromJson(jsonObj, AgentAction.PressButton::class.java)
                "done", "complete", "finished" -> gson.fromJson(jsonObj, AgentAction.Done::class.java)
                else -> AgentAction.Unknown(raw = llmResponse.take(500))
            }
        } catch (e: Exception) {
            val lower = llmResponse.lowercase()
            if (lower.contains("done") || lower.contains("complete") || lower.contains("finished")) {
                AgentAction.Done(summary = llmResponse.take(200))
            } else {
                AgentAction.Unknown(raw = llmResponse.take(500))
            }
        }
    }

    fun isDestructive(action: AgentAction): Boolean {
        val text = when (action) {
            is AgentAction.Tap -> action.description ?: ""
            is AgentAction.TypeText -> action.description ?: ""
            is AgentAction.LongPress -> action.description ?: ""
            else -> ""
        }
        return DESTRUCTIVE_KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) }
    }

    private fun extractJson(text: String): String? {
        // Try ```json ... ```
        val jsonBlockRegex = Regex("""```json\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
        jsonBlockRegex.find(text)?.groupValues?.get(1)?.let { return it.trim() }

        // Try ``` ... ```
        val codeBlockRegex = Regex("""```\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
        codeBlockRegex.find(text)?.groupValues?.get(1)?.let { return it.trim() }

        // Try raw { ... }
        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1).trim()
        }

        return null
    }
}