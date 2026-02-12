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
            "Jsi pomocny asistent. Uzivatel ti da cesky text. " +
            "Vytvor strucne shrnuti v cestine. Odpovez pouze shrnutim, nic dalsiho."
        ),
        BULLET_POINTS(
            "Jsi pomocny asistent. Uzivatel ti da cesky text. " +
            "Preved ho na prehledne odrazky v cestine. Kazdy bod zacni na novem radku znakem \u2022. " +
            "Odpovez pouze odrazkami, nic dalsiho."
        ),
        CORRECT_GRAMMAR(
            "Jsi pomocny asistent. Uzivatel ti da cesky text z prepisu reci. " +
            "Oprav gramatiku, interpunkci a preklepy. Zachovej puvodni vyznam. " +
            "Odpovez pouze opravenym textem, nic dalsiho."
        ),
        TRANSLATE(
            "Jsi pomocny asistent a prekladatel. Uzivatel ti da cesky text. " +
            "Preloz ho do anglictiny. Zachovej formatovani. " +
            "Odpovez pouze prekladem, nic dalsiho."
        ),
        STRUCTURE(
            "Jsi pomocny asistent. Uzivatel ti da cesky text z hlasoveho prepisu. " +
            "Strukturuj text pro maximalni prehlednost: " +
            "1. Pridej tucne nadpisy (pouzij **text**) " +
            "2. Pridej odrazky kde to dava smysl " +
            "3. Pridej mezery mezi odstavce " +
            "4. Vloz symboly a emoji pro lepsi orientaci " +
            "5. Zachovej veskerou informaci " +
            "Odpovez pouze strukturovanym textem."
        ),
        SMART_REWRITE(
            "Jsi pomocny asistent. Uzivatel ti da cesky text z hlasoveho prepisu. " +
            "1. Prepis text srozumitelneji a profesionalneji " +
            "2. Pridej strukturu - nadpisy, odrazky, cislovani " +
            "3. Na konec navrhni: " +
            "   SLOZKA: [navrhni vhodnou slozku z: Pracovni, Doma, Nakup, Osobni, Zdravi, Finance, Diar, Jidlo, Zahrada, Narozeniny, Udalosti] " +
            "   KATEGORIE: [navrhni kategorii] " +
            "   NAZEV: [navrhni kratky nazev poznamky] " +
            "Odpovez preformatovanym textem a navrhy."
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
                val systemPrompt = if (action == Action.CUSTOM) customPrompt else action.systemPrompt

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
                    Result(success = false, text = "", error = "HTTP $responseCode: $error")
                }
            } catch (e: Exception) {
                Result(success = false, text = "", error = e.message ?: "Neznama chyba")
            }
        }
    }

    suspend fun translateTo(text: String, targetLanguage: String): Result {
        val prompt = "Jsi prekladatel. Preloz nasledujici text do jazyka: $targetLanguage. " +
            "Zachovej formatovani. Odpovez pouze prekladem."
        return process(text, Action.CUSTOM, prompt)
    }

    fun shouldOfferStructuring(text: String): Boolean {
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount > 50
    }
}
