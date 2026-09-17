package com.myra.assistant.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class AudioEngine(
    private val onMicData: (ByteArray) -> Unit,
    val onAmplitudeChanged: (Float) -> Unit,
    val onSpeakingStarted: () -> Unit,
    val onSpeakingStopped: () -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    @Volatile var isRecording = false
    @Volatile var isPlaying = false
    @Volatile var isMuted = false
    @Volatile var isMyraSpeaking = false

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var recordThread: Thread? = null
    private var playThread: Thread? = null

    private val sampleRateIn = 16000
    private val sampleRateOut = 24000
    private val chunkSize = 1024

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateIn,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateIn,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize.coerceAtLeast(chunkSize * 2)
        )
        
        audioRecord?.startRecording()
        isRecording = true

        recordThread = Thread {
            val buffer = ByteArray(chunkSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val rms = calculateRms(buffer, read)
                    onAmplitudeChanged(rms)
                    if (!isMuted && !isMyraSpeaking) {
                        onMicData(buffer.copyOf(read))
                    }
                }
            }
        }.apply { start() }
    }

    fun startPlayback() {
        if (isPlaying) return
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateOut,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateOut)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize.coerceAtLeast(chunkSize * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true

        playThread = Thread {
            while (isPlaying) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    if (!isMyraSpeaking) {
                        isMyraSpeaking = true
                        onSpeakingStarted()
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isMyraSpeaking) {
                        isMyraSpeaking = false
                        onSpeakingStopped()
                    }
                    Thread.sleep(10)
                }
            }
        }.apply { start() }
    }

    fun queueAudio(data: ByteArray) {
        audioQueue.offer(data)
    }

    fun clearPlaybackQueue() {
        audioQueue.clear()
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        if (isMyraSpeaking) {
            isMyraSpeaking = false
            onSpeakingStopped()
        }
    }

    private fun calculateRms(buffer: ByteArray, readSize: Int): Float {
        var sum = 0.0
        val shortsRead = readSize / 2
        for (i in 0 until shortsRead) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            val shortVal = sample.toShort()
            sum += shortVal * shortVal
        }
        val rms = sqrt(sum / shortsRead.coerceAtLeast(1))
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun release() {
        isRecording = false
        isPlaying = false
        recordThread?.interrupt()
        playThread?.interrupt()
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        audioRecord = null
        audioTrack = null
    }
}