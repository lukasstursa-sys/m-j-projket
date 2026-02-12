package cz.stursa.speechnotes.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class CzechSpeechRecognizer(
    private val context: Context,
    private val listener: SpeechResultListener
) {

    interface SpeechResultListener {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(errorMessage: String)
        fun onListeningStarted()
        fun onListeningStopped()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val czechLocale = Locale("cs", "CZ")

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            listener.onListeningStopped()
        }

        override fun onError(error: Int) {
            isListening = false
            listener.onListeningStopped()

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Chyba zvuku"
                SpeechRecognizer.ERROR_CLIENT -> "Chyba klienta"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Nedostatečná oprávnění"
                SpeechRecognizer.ERROR_NETWORK -> "Chyba sítě"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Časový limit sítě"
                SpeechRecognizer.ERROR_NO_MATCH -> "Řeč nebyla rozpoznána"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Rozpoznávač je zaneprázdněn"
                SpeechRecognizer.ERROR_SERVER -> "Chyba serveru"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nebyla detekována řeč"
                else -> "Neznámá chyba ($error)"
            }
            listener.onError(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                listener.onFinalResult(matches[0])
            }
            isListening = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                listener.onPartialResult(matches[0])
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Rozpoznávání řeči není na tomto zařízení dostupné")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, czechLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, czechLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, czechLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Allow longer speech segments - more tolerance for pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
        }

        isListening = true
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            listener.onListeningStopped()
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    fun isCurrentlyListening(): Boolean = isListening
}
