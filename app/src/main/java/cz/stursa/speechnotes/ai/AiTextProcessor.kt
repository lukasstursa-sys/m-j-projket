package cz.stursa.speechnotes.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiTextProcessor(
    private val apiKey: String,
    private val apiUrl: String = "https://api.openai.com/v1/chat/completions",
    private val model: String = "gpt-3.5-turbo"
) {

    enum class Action(val systemPrompt: String) {
        SUMMARIZE(
            "Jsi pomocný asistent. Uživatel ti dá český text. " +
            "Vytvoř stručné shrnutí v češtině. Odpověz pouze shrnutím, nic dalšího."
        ),
        BULLET_POINTS(
            "Jsi pomocný asistent. Uživatel ti dá český text. " +
            "Převeď ho na přehledné odrážky v češtině. Každý bod začni na novém řádku znakem •. " +
            "Odpověz pouze odrážkami, nic dalšího."
        ),
        CORRECT_GRAMMAR(
            "Jsi pomocný asistent. Uživatel ti dá český text z přepisu řeči. " +
            "Oprav gramatiku, interpunkci a překlepy. Zachovej původní význam. " +
            "Odpověz pouze opraveným textem, nic dalšího."
        ),
        TRANSLATE(
            "Jsi pomocný asistent a překladatel. Uživatel ti dá český text. " +
            "Přelož ho do angličtiny. Zachovej formátování. " +
            "Odpověz pouze překladem, nic dalšího."
        ),
        CUSTOM("")
    }

    data class Result(
        val success: Boolean,
        val text: String,
        val error: String? = null
    )

    suspend fun process(text: String, action: Action, customPrompt: String = ""): Result {
        return withContext(Dispatchers.IO) {
            try {
                val systemPrompt = if (action == Action.CUSTOM) {
                    customPrompt
                } else {
                    action.systemPrompt
                }

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        })
                    })
                    put("max_tokens", 2000)
                    put("temperature", 0.3)
                }

                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 60000
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                        it.readText()
                    }
                    val jsonResponse = JSONObject(response)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    Result(success = true, text = content)
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val error = BufferedReader(InputStreamReader(errorStream)).use {
                        it.readText()
                    }
                    Result(
                        success = false,
                        text = "",
                        error = "HTTP $responseCode: $error"
                    )
                }
            } catch (e: Exception) {
                Result(
                    success = false,
                    text = "",
                    error = e.message ?: "Neznámá chyba"
                )
            }
        }
    }

    suspend fun translateTo(text: String, targetLanguage: String): Result {
        val prompt = "Jsi překladatel. Přelož následující text do jazyka: $targetLanguage. " +
            "Zachovej formátování. Odpověz pouze překladem."
        return process(text, Action.CUSTOM, prompt)
    }
}
