package com.myra.assistant.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.databinding.ActivityMainBinding
import com.myra.assistant.model.ChatMessage
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.viewmodel.MainViewModel
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var geminiClient: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private val commandParser = CommandParser()
    private val chatAdapter = ChatAdapter()

    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()
    private var isInCallMode = false

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isInCallMode = false
            runOnUiThread { binding.statusText.text = "Sun rahi hoon..." }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initSystem()
        } else {
            Toast.makeText(this, "All permissions are required!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chatRecycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.chatRecycler.adapter = chatAdapter

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.orbView.setOnClickListener {
            toggleListening()
        }

        binding.sendBtn.setOnClickListener {
            sendTypedMessage()
        }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendTypedMessage()
                true
            } else {
                false
            }
        }

        val filter = IntentFilter("com.myra.CALL_ENDED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, filter)
        }

        checkPermissions()
        handleCallIntent(intent)
    }

    private fun startOverlayServiceIfPermitted() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, MyraOverlayService::class.java))
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            initSystem()
        }
    }

    private var lastApiKey: String? = null

    private fun initSystem() {
        startService(Intent(this, CallMonitorService::class.java))
        startOverlayServiceIfPermitted()

        viewModel.commandResult.observe(this) { result ->
            if (!result.isNullOrBlank()) {
                geminiClient?.sendText(result)
            }
        }

        connectToGemini()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
            val currentKey = prefs.getString("api_key", "") ?: ""
            if (lastApiKey != null && lastApiKey != currentKey) {
                // Settings changed while app was in background — reconnect with fresh values
                geminiClient?.disconnect()
                audioEngine?.release()
                connectToGemini()
            }
        }
    }

    private fun connectToGemini() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "YOUR_GEMINI_API_KEY") ?: ""
        lastApiKey = apiKey
        val model = prefs.getString("gemini_model", "models/gemini-3.1-flash-live-preview") ?: ""
        val voice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        val name = prefs.getString("user_name", "Boss") ?: "Boss"
        val personality = prefs.getString("personality_mode", "GF") ?: "GF"

        // TEMP DEBUG: show last 6 chars of the key actually being used, and the model name
        val keyTail = if (apiKey.length >= 6) apiKey.takeLast(6) else apiKey
        chatAdapter.addMessage(
            ChatMessage("DEBUG key-end:$keyTail len:${apiKey.length} model:$model", false)
        )

        val personalityBlock = when (personality) {
            "PROFESSIONAL" -> "Speak formal English only. Be precise and efficient, no emojis, max 2 sentences."
            "ASSISTANT" -> "Speak friendly Hinglish or English, balanced and helpful, max 2-3 sentences."
            else -> "Speak warm, caring Hinglish (Hindi + English mix), emotionally expressive, use words like 'tumhara', 'haan', 'acha', 'bilkul', max 2-3 sentences, feel free to use light emojis."
        }
        val prompt = "Your name is MYRA, an AI companion talking to $name. $personalityBlock " +
            "You are speaking ALOUD — keep responses natural and conversational."

        audioEngine = AudioEngine(
            onMicData = { pcm -> geminiClient?.sendAudio(pcm) },
            onAmplitudeChanged = { rms -> runOnUiThread { binding.waveformView.setAmplitude(rms) } },
            onSpeakingStarted = {
                runOnUiThread {
                    binding.orbView.setState(OrbAnimationView.State.SPEAKING)
                    binding.statusText.text = "Bol rahi hoon..."
                    binding.redOverlay.animate().alpha(0.08f).setDuration(300).start()
                }
            },
            onSpeakingStopped = {
                runOnUiThread {
                    if (audioEngine?.isRecording == true) {
                        binding.orbView.setState(OrbAnimationView.State.LISTENING)
                        binding.statusText.text = "Sun rahi hoon..."
                    } else {
                        binding.orbView.setState(OrbAnimationView.State.IDLE)
                        binding.statusText.text = "Tap karke bolo"
                    }
                    binding.redOverlay.animate().alpha(0f).setDuration(500).start()
                }
            }
        )

        geminiClient = GeminiLiveClient(this, apiKey, model, voice, prompt, object : GeminiLiveClient.Listener {
            override fun onConnected() {
                runOnUiThread {
                    audioEngine?.startPlayback()
                    binding.orbView.setState(OrbAnimationView.State.IDLE)
                    binding.statusText.text = "Tap karke bolo"
                    binding.waveformView.startAnimation()
                    val greeting = when (personality) {
                        "PROFESSIONAL" -> "Good day $name. MYRA is online and ready to assist you."
                        "ASSISTANT" -> "Hello $name! Main MYRA hoon. Kaise help karun aapki?"
                        else -> "Hey $name! Main aa gayi hoon. Kya help chahiye tumhe?"
                    }
                    geminiClient?.sendText(greeting)
                }
            }

            override fun onDisconnected() {}
            override fun onAudioReceived(pcmBytes: ByteArray) {
                audioEngine?.queueAudio(pcmBytes)
            }

            override fun onInputTranscript(text: String) {
                inputBuffer.append(text)
            }

            override fun onOutputTranscript(text: String) {
                outputBuffer.append(text)
            }

            override fun onTurnComplete() {
                runOnUiThread {
                    val userText = inputBuffer.toString().trim()
                    val myraText = outputBuffer.toString().trim()
                    if (userText.isNotEmpty()) {
                        chatAdapter.addMessage(ChatMessage(userText, true))
                        commandParser.parse(userText)?.let { cmd -> viewModel.executeCommand(cmd) }
                    }
                    if (myraText.isNotEmpty()) {
                        chatAdapter.addMessage(ChatMessage(myraText, false))
                    }
                    inputBuffer.clear()
                    outputBuffer.clear()
                }
            }

            override fun onError(error: String) {
                runOnUiThread { Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show() }
            }
        })

        geminiClient?.connect()
    }

    private fun sendTypedMessage() {
        val text = binding.messageInput.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        chatAdapter.addMessage(ChatMessage(text, true))
        geminiClient?.sendText(text)
        binding.messageInput.text?.clear()
        // Hide keyboard after sending
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
    }

    private fun toggleListening() {
        val engine = audioEngine ?: return
        if (engine.isRecording) {
            engine.stopRecording()
            binding.orbView.setState(OrbAnimationView.State.IDLE)
            binding.statusText.text = "Tap karke bolo"
        } else {
            // Tapping while MYRA is speaking also interrupts her
            if (engine.isMyraSpeaking) {
                audioEngine?.clearPlaybackQueue()
                geminiClient?.interrupt()
            }
            engine.startRecording()
            binding.orbView.setState(OrbAnimationView.State.LISTENING)
            binding.statusText.text = "Sun rahi hoon..."
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("INCOMING_CALL", false) == true) {
            val name = intent.getStringExtra("CALLER_NAME") ?: "Someone"
            isInCallMode = true
            geminiClient?.sendText("$name ka call aa raha hai. Uthau ya cut karu?")

            binding.root.postDelayed({ listenForCallDecision() }, 4500)
        }
    }

    private fun listenForCallDecision() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val response = matches?.firstOrNull()?.lowercase() ?: ""
                if (response.contains("uthao") || response.contains("haan") || response.contains("accept")) {
                    viewModel.acceptCall()
                } else if (response.contains("cut") || response.contains("reject") || response.contains("mat")) {
                    viewModel.rejectCall()
                }
                isInCallMode = false
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { isInCallMode = false }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        geminiClient?.disconnect()
        audioEngine?.release()
        try { unregisterReceiver(callEndedReceiver) } catch (_: Exception) {}
    }
}