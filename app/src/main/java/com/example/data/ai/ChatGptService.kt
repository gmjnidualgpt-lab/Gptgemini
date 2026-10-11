package com.example.data.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    @field:Json(name = "role") val role: String,
    @field:Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    @field:Json(name = "model") val model: String,
    @field:Json(name = "messages") val messages: List<OpenAiMessage>,
    @field:Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    @field:Json(name = "message") val message: OpenAiMessage? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    @field:Json(name = "choices") val choices: List<OpenAiChoice>? = null,
    @field:Json(name = "error") val error: OpenAiError? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiError(
    @field:Json(name = "message") val message: String? = null
)

class ChatGptService {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(OpenAiChatRequest::class.java)
    private val responseAdapter = moshi.adapter(OpenAiChatResponse::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(
        prompt: String,
        customApiKey: String? = null,
        model: String = "gpt-4o",
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val resolvedKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.OPENAI_API_KEY.isNotBlank() && BuildConfig.OPENAI_API_KEY != "MY_OPENAI_API_KEY" -> BuildConfig.OPENAI_API_KEY
            else -> null
        }

        if (resolvedKey == null) {
            // Intelligent fallback mode when no OpenAI key is configured yet
            val fallbackResponse = generateLocalChatGptResponse(prompt, model, systemInstruction)
            return@withContext Result.success(fallbackResponse)
        }

        val messages = mutableListOf<OpenAiMessage>()
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(OpenAiMessage(role = "system", content = systemInstruction))
        }
        messages.add(OpenAiMessage(role = "user", content = prompt))

        val reqObj = OpenAiChatRequest(
            model = model,
            messages = messages,
            temperature = 0.7f
        )

        val jsonBody = requestAdapter.toJson(reqObj)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $resolvedKey")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                val err = try {
                    responseAdapter.fromJson(responseBody ?: "")?.error?.message
                } catch (e: Exception) { null }
                return@withContext Result.failure(
                    Exception("Błąd ChatGPT HTTP ${response.code}: ${err ?: responseBody ?: response.message}")
                )
            }

            val parsed = responseAdapter.fromJson(responseBody)
            val reply = parsed?.choices?.firstOrNull()?.message?.content
            if (!reply.isNullOrBlank()) {
                Result.success(reply)
            } else {
                Result.failure(Exception("ChatGPT zwróciło pustą odpowiedź."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun crossCritique(
        userPrompt: String,
        editedText: String,
        sourceModel: String,
        customApiKey: String? = null,
        customInstruction: String? = null
    ): Result<String> {
        val instruction = customInstruction ?: "Użytkownik wprowadził poprawki do odpowiedzi z $sourceModel. " +
                "Jako ChatGPT oceń te zmiany, wskaż co zostało ulepszone, popraw ewentualne niedociągnięcia " +
                "i przedstaw zoptymalizowaną kontynuację rozmowy."

        val prompt = """
            Zadanie: Analiza i optymalizacja edytowanej odpowiedzi.
            Zapytanie pierwotne: $userPrompt
            
            Zmodyfikowana treść od $sourceModel:
            $editedText
            
            Twoje zadanie:
            $instruction
        """.trimIndent()

        return generate(
            prompt = prompt,
            customApiKey = customApiKey,
            model = "gpt-4o",
            systemInstruction = "Jesteś modelem ChatGPT współpracującym z Gemini. Twoim celem jest rzetelna ocena edytowanej treści i dopracowanie wyników."
        )
    }

    private fun generateLocalChatGptResponse(prompt: String, model: String, systemInstruction: String?): String {
        return """
            [ChatGPT ($model) - Tryb Demonstracyjny / Wprowadź klucz OpenAI w Ustawieniach]
            
            Odpowiedź na Twoje zapytanie:
            "$prompt"
            
            Z perspektywy architektury ChatGPT:
            • Kluczowe punkty zostały przeanalizowane pod kątem przejrzystości i struktury.
            • Rozwiązanie łączy podejście algorytmiczne z praktycznym studium przypadku.
            • Wskazówka: Możesz edytować tę odpowiedź klikając ikonę ołówka i odesłać ją do Gemini, aby Gemini oceniło zmiany i zaproponowało dodatkowe usprawnienia!
            
            (Aby korzystać z oficjalnego API OpenAI na żywo, kliknij ikonę zębatki ⚙️ i podaj swój klucz API).
        """.trimIndent()
    }
}
