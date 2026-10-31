package com.jarvis.supreme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps Jarvis listening for "Hey Jarvis" or "Jarvis"
 * even when the Redmi Note 15 5G screen is OFF (using a Partial WakeLock).
 * Uses the upgraded Dual-Mode JarvisVoiceEngine (Iron Man Cinematic + Ultra-Realistic Cloud AI).
 */
class JarvisWakeWordService : Service() {

    companion object {
        private var voiceEngine: JarvisVoiceEngine? = null

        fun speakOut(text: String) {
            voiceEngine?.speak(text)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var biometricVerifier: VoiceBiometricVerifier
    private lateinit var brain: JarvisBrain
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        memoryStore = EncryptedMemoryStore(this)
        biometricVerifier = VoiceBiometricVerifier(memoryStore)
        brain = JarvisBrain(this, memoryStore, JarvisHardwareController(this))
        voiceEngine = JarvisVoiceEngine(this, memoryStore)

        // Acquire Partial WakeLock so Jarvis stays active even when screen is turned OFF
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisSupreme::AlwaysOnWakeLock")
        wakeLock.acquire()

        startForeground(1001, buildForegroundNotification())

        // Greet owner on startup after brief TTS warm-up
        serviceScope.launch {
            kotlinx.coroutines.delay(1200)
            speakOut(brain.buildStartupWelcomeMessage(getBatteryPercentage()))
        }

        startContinuousWakeWordLoop()
    }

    private fun startContinuousWakeWordLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                speechRecognizer?.startListening(recognizerIntent)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""
                handleSpokenInput(spokenText)
                speechRecognizer?.startListening(recognizerIntent)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun handleSpokenInput(spokenText: String) {
        if (spokenText.isBlank()) return
        val lower = spokenText.lowercase()

        // If Jarvis was manually turned off, ONLY wake up when the owner says "Hey Jarvis" or "Jarvis"
        if (!memoryStore.isJarvisActive) {
            if (lower.contains("hey jarvis") || lower.contains("jarvis on")) {
                memoryStore.isJarvisActive = true
                speakOut("Online and ready, Sir.")
            }
            return
        }

        serviceScope.launch {
            val reply = brain.processOwnerCommand(
                utterance = spokenText,
                detectedMood = "Calm / Normal",
                activeForegroundApp = JarvisAccessibilityService.currentForegroundPackage
            )
            speakOut(reply)
        }
    }

    private fun getBatteryPercentage(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "jarvis_supreme_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Always-On Core",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("JARVIS Supreme AI • Dual Voice Active")
            .setContentText("Iron Man Sci-Fi + Ultra-Realistic Cloud Voice • Listening for 'Hey Jarvis'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        speechRecognizer?.destroy()
        voiceEngine?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
