package cz.stursa.speechnotes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cz.stursa.speechnotes.MainActivity
import cz.stursa.speechnotes.R
import cz.stursa.speechnotes.speech.CzechSpeechRecognizer

class SpeechRecordingService : Service(), CzechSpeechRecognizer.SpeechResultListener {

    companion object {
        const val CHANNEL_ID = "speech_recording_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "cz.stursa.speechnotes.STOP_RECORDING"
    }

    private lateinit var speechRecognizer: CzechSpeechRecognizer
    private val binder = LocalBinder()
    private val transcribedText = StringBuilder()
    var serviceListener: ServiceCallback? = null
    var isRecording = false
        private set

    interface ServiceCallback {
        fun onTranscriptionUpdate(fullText: String, partialText: String)
        fun onRecordingError(message: String)
        fun onRecordingStopped(fullText: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): SpeechRecordingService = this@SpeechRecordingService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        speechRecognizer = CzechSpeechRecognizer(this, this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startRecording()
        return START_STICKY
    }

    fun startRecording() {
        if (isRecording) return
        transcribedText.clear()
        isRecording = true
        speechRecognizer.startListening()
        updateNotification("Poslouchám...")
    }

    fun stopRecording() {
        isRecording = false
        speechRecognizer.stopListening()
        serviceListener?.onRecordingStopped(transcribedText.toString())
    }

    fun getTranscribedText(): String = transcribedText.toString()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nahrávání řeči",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zobrazuje se při nahrávání řeči na pozadí"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String = "Nahrávání řeči..."): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SpeechRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hlasové Poznámky")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Zastavit", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    // --- SpeechResultListener ---

    override fun onPartialResult(text: String) {
        serviceListener?.onTranscriptionUpdate(transcribedText.toString(), text)
    }

    override fun onFinalResult(text: String) {
        if (transcribedText.isNotEmpty()) transcribedText.append(" ")
        transcribedText.append(text)
        serviceListener?.onTranscriptionUpdate(transcribedText.toString(), "")
        updateNotification(
            if (transcribedText.length > 60)
                "..." + transcribedText.substring(transcribedText.length - 60)
            else transcribedText.toString()
        )

        if (isRecording) {
            speechRecognizer.startListening()
        }
    }

    override fun onError(errorMessage: String) {
        if ((errorMessage.contains("Řeč nebyla rozpoznána") ||
             errorMessage.contains("Nebyla detekována řeč")) && isRecording
        ) {
            speechRecognizer.startListening()
        } else {
            serviceListener?.onRecordingError(errorMessage)
        }
    }

    override fun onListeningStarted() {}
    override fun onListeningStopped() {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
