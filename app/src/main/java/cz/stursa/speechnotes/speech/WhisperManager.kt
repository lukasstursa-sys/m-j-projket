package cz.stursa.speechnotes.speech

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manager for OpenAI Whisper offline speech recognition.
 *
 * This provides infrastructure for offline Czech speech recognition
 * using Whisper models. The user needs to download a GGML-format
 * Whisper model and place it in the app's files directory.
 *
 * Supported model files (place in app files/whisper/):
 * - ggml-tiny.bin   (~75MB, fastest, lower accuracy)
 * - ggml-base.bin   (~142MB, good balance)
 * - ggml-small.bin  (~466MB, better accuracy)
 *
 * Note: This class provides the recording and file management
 * infrastructure. For actual Whisper inference, you would need
 * to integrate whisper.cpp via JNI or use ONNX Runtime.
 * The transcription is sent to a local or remote Whisper API endpoint.
 */
class WhisperManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "whisper_settings"
        private const val KEY_ENABLED = "whisper_enabled"
        private const val KEY_MODEL = "whisper_model"
        private const val KEY_API_URL = "whisper_api_url"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL, "ggml-base.bin") ?: "ggml-base.bin"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    // URL for local Whisper API (e.g., whisper.cpp server or faster-whisper-server)
    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, "http://localhost:8080/inference") ?: "http://localhost:8080/inference"
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()

    fun getModelDir(): File {
        val dir = File(context.filesDir, "whisper")
        dir.mkdirs()
        return dir
    }

    fun isModelDownloaded(): Boolean {
        val modelFile = File(getModelDir(), modelName)
        return modelFile.exists()
    }

    fun getAvailableModels(): List<String> {
        val dir = getModelDir()
        return dir.listFiles { file -> file.name.startsWith("ggml-") && file.name.endsWith(".bin") }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * Record audio to a WAV file for Whisper processing.
     */
    suspend fun recordToFile(durationMs: Long = 5000): File? {
        return withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
                )

                val outputFile = File(context.cacheDir, "whisper_recording.wav")
                val buffer = ByteArray(bufferSize)
                val audioData = mutableListOf<Byte>()

                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < durationMs) {
                    val read = audioRecord.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        audioData.addAll(buffer.take(read))
                    }
                }

                audioRecord.stop()
                audioRecord.release()

                // Write WAV file
                writeWavFile(outputFile, audioData.toByteArray())
                outputFile
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Transcribe audio using a Whisper-compatible API endpoint.
     */
    suspend fun transcribe(audioFile: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val boundary = "----WhisperBoundary${System.currentTimeMillis()}"
                val url = java.net.URL(apiUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 30000
                    readTimeout = 60000
                }

                connection.outputStream.use { out ->
                    // File part
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray())
                    out.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                    audioFile.inputStream().use { it.copyTo(out) }
                    out.write("\r\n".toByteArray())

                    // Language part
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"language\"\r\n\r\n".toByteArray())
                    out.write("cs\r\n".toByteArray())

                    out.write("--$boundary--\r\n".toByteArray())
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    // Parse JSON response - expect {"text": "..."}
                    val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(response)
                    textMatch?.groupValues?.get(1)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray) {
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2

        file.outputStream().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalDataLen))
            out.write("WAVE".toByteArray())
            // fmt chunk
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // chunk size
            out.write(shortToByteArray(1)) // PCM format
            out.write(shortToByteArray(channels.toShort()))
            out.write(intToByteArray(SAMPLE_RATE))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray((channels * 2).toShort())) // block align
            out.write(shortToByteArray(16)) // bits per sample
            // data chunk
            out.write("data".toByteArray())
            out.write(intToByteArray(pcmData.size))
            out.write(pcmData)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}
