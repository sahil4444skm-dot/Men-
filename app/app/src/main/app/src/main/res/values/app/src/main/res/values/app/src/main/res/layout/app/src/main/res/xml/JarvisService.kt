package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

private const val CHANNEL_ID = "jarvis_channel"
private const val LISTEN_TIME = 5 * 60 * 1000L   // 5 minute

class JarvisService : Service(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var processor: CommandProcessor
    private var ttsReady = false
    private var running = false

    // 5 minute baad apne-aap band
    private val autoOff = Runnable {
        say("Paanch minute ho gaye. Jarvis band ho raha hai.") { stopAll() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val notification = buildNotification("Jarvis sun raha hai (5 minute)")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }

        running = true
        processor = CommandProcessor(this)
        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        handler.postDelayed(autoOff, LISTEN_TIME)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts.setLanguage(Locale.forLanguageTag("hi-IN")) < 0) {
                tts.setLanguage(Locale.US)
            }
            ttsReady = true
            say("Jarvis online hai. Boliye.") { listen() }
        }
    }

    // Bolna aur bolne ke baad kuch karna
    private fun say(text: String, after: () -> Unit = {}) {
        if (!ttsReady) {
            after()
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { handler.post { after() } }
            override fun onError(utteranceId: String?) { handler.post { after() } }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    private fun listen() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        recognizer.startListening(intent)
    }

    private fun handleText(text: String) {
        if (text.isBlank()) {
            listen()
            return
        }

        val stopWords = listOf("jarvis band", "jarvis off", "off jarvis", "jarvis ko band")
        if (stopWords.any { text.contains(it) }) {
            say("Theek hai, Jarvis band ho raha hai.") { stopAll() }
            return
        }

        val reply = processor.handle(text)
        if (reply != null) {
            // Phone ka apna command
            say(reply) { listen() }
        } else {
            // Gemini AI se jawab (internet), background thread mein
            Thread {
                val answer = AiHelper.ask(applicationContext, text)
                handler.post { say(answer) { listen() } }
            }.start()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase(Locale.getDefault()) ?: ""
            handleText(text)
        }

        override fun onError(error: Int) {
            // Kuch sun nahi paya ya busy tha: dobara sunna shuru
            if (running) handler.postDelayed({ listen() }, 800)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun stopAll() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
