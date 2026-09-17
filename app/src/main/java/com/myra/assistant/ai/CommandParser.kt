package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand

class CommandParser {

    fun parse(input: String): AppCommand? {
        val text = input.lowercase().trim()

        return when {
            // Prime contact shortcuts (checked before generic call/message patterns)
            text.contains("close friend ko call") || text.contains("call my close friend") ->
                AppCommand("PRIME_CALL", mapOf("index" to "0"))

            text.contains("meri jaan ko msg") || text.contains("meri jaan ko message") ||
                text.contains("message my love") || text.contains("msg my love") ->
                AppCommand("PRIME_MSG", mapOf("index" to "0"))

            text.contains("meri jaan ko call") || text.contains("call my love") ->
                AppCommand("PRIME_CALL", mapOf("index" to "0"))

            Regex("call my (second|third|fourth) contact").containsMatchIn(text) -> {
                val ordinal = Regex("call my (second|third|fourth) contact").find(text)?.groupValues?.get(1)
                val index = when (ordinal) {
                    "second" -> 1
                    "third" -> 2
                    "fourth" -> 3
                    else -> 0
                }
                AppCommand("PRIME_CALL", mapOf("index" to index.toString()))
            }

            // App open / close
            text.startsWith("open ") || text.contains(" kholo") -> {
                val app = text.replace("open", "").replace("kholo", "").trim()
                AppCommand("OPEN_APP", mapOf("app_name" to app))
            }
            text.contains("close") || text.contains("band karo") -> {
                AppCommand("CLOSE_APP")
            }

            // WhatsApp specific
            Regex("whatsapp (par |pe )?(msg|message) (bhejo )?(.+) ko").containsMatchIn(text) ||
                (text.contains("whatsapp") && (text.contains("msg") || text.contains("message"))) -> {
                val name = extractName(text, listOf("whatsapp", "msg", "message", "bhejo", "ko", "par", "pe"))
                AppCommand("WHATSAPP_MSG", mapOf("name" to name, "message" to text))
            }
            text.contains("whatsapp") && text.contains("call") -> {
                val name = extractName(text, listOf("whatsapp", "call", "karo", "ko"))
                AppCommand("WHATSAPP_CALL", mapOf("name" to name))
            }

            // Generic call
            text.contains("ko call karo") || (text.startsWith("call ") && !text.contains("whatsapp")) -> {
                val name = extractName(text, listOf("call", "karo", "ko"))
                AppCommand("CALL", mapOf("name" to name))
            }

            // SMS
            text.contains("sms bhejo") || (text.contains("sms") && text.contains("ko")) -> {
                val name = extractName(text, listOf("sms", "bhejo", "ko", "message"))
                AppCommand("SMS", mapOf("name" to name, "message" to text))
            }

            // Flashlight
            text.contains("torch on") || text.contains("flashlight on") ->
                AppCommand("FLASHLIGHT_ON")
            text.contains("torch off") || text.contains("flashlight off") ->
                AppCommand("FLASHLIGHT_OFF")

            // WiFi
            text.contains("wifi on") ->
                AppCommand("WIFI_ON")
            text.contains("wifi off") ->
                AppCommand("WIFI_OFF")

            // Bluetooth
            text.contains("bluetooth on") ->
                AppCommand("BLUETOOTH_ON")
            text.contains("bluetooth off") ->
                AppCommand("BLUETOOTH_OFF")

            // Volume
            text.contains("volume up") || text.contains("volume badhao") ->
                AppCommand("VOLUME_UP")
            text.contains("volume down") || text.contains("volume kam karo") ->
                AppCommand("VOLUME_DOWN")

            else -> null
        }
    }

    private fun extractName(text: String, stopWords: List<String>): String {
        var result = text
        stopWords.forEach { word -> result = result.replace(word, " ") }
        return result.trim().split(Regex("\\s+")).joinToString(" ").trim()
    }
}
