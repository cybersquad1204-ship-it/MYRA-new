package com.myra.assistant.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val context: Context,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val systemPrompt: String,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onAudioReceived(pcmBytes: ByteArray)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onTurnComplete()
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(8, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            sendSilentChunk()
            handler.postDelayed(this, 8000)
        }
    }

    private val sessionRenewRunnable = Runnable {
        reconnect()
    }

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                sendSetupMessage()
                handler.post(keepAliveRunnable)
                handler.postDelayed(sessionRenewRunnable, 540000)
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                listener.onError(t.localizedMessage ?: "WebSocket failure")
                handler.postDelayed({ reconnect() }, 3000)
            }
        })
    }

    private fun sendSetupMessage() {
        val setupJson = JSONObject().apply {
            val setup = JSONObject().apply {
                put("model", model)
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().put("voice_name", voice))
                        })
                    })
                    put("temperature", 0.9)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            }
            put("setup", setup)
        }
        webSocket?.send(setupJson.toString())
    }

    fun sendAudio(data: ByteArray) {
        if (!isConnected) return
        val base64Data = Base64.encodeToString(data, Base64.NO_WRAP)
        val audioMsg = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().put(JSONObject().apply {
                    put("mime_type", "audio/pcm;rate=16000")
                    put("data", base64Data)
                }))
            })
        }
        webSocket?.send(audioMsg.toString())
    }

    fun sendText(message: String) {
        if (!isConnected) return
        val textMsg = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", message)))
                }))
                put("turn_complete", true)
            })
        }
        webSocket?.send(textMsg.toString())
    }

    fun interrupt() {
        val interruptMsg = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray())
                put("turn_complete", true)
            })
        }
        webSocket?.send(interruptMsg.toString())
    }

    private fun sendSilentChunk() {
        val silentData = ByteArray(160)
        sendAudio(silentData)
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.has("modelTurn")) {
                    val parts = serverContent.getJSONObject("modelTurn").getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            val base64 = part.getJSONObject("inlineData").getString("data")
                            val pcmBytes = Base64.decode(base64, Base64.NO_WRAP)
                            listener.onAudioReceived(pcmBytes)
                        }
                    }
                }

                if (serverContent.has("outputTranscription")) {
                    val transcript = serverContent.getJSONObject("outputTranscription").optString("text")
                    if (transcript.isNotEmpty()) listener.onOutputTranscript(transcript)
                }

                if (serverContent.has("inputTranscription")) {
                    val transcript = serverContent.getJSONObject("inputTranscription").optString("text")
                    if (transcript.isNotEmpty()) listener.onInputTranscript(transcript)
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnComplete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reconnect() {
        disconnect()
        connect()
    }

    fun disconnect() {
        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(sessionRenewRunnable)
        webSocket?.close(1000, "App closed")
        webSocket = null
        isConnected = false
    }
}