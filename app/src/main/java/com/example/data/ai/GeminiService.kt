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
data class GeminiPart(
    @field:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @field:Json(name = "parts") val parts: List<GeminiPart>,
    @field:Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiThinkingConfig(
    @field:Json(name = "thinkingLevel") val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @field:Json(name = "temperature") val temperature: Float? = null,
    @field:Json(name = "thinkingConfig") val thinkingConfig: GeminiThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @field:Json(name = "contents") val contents: List<GeminiContent>,
    @field:Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @field:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @field:Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @field:Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

class GeminiService {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(
        prompt: String,
        model: String = "gemini-3.1-pro-preview",
        enableHighThinking: Boolean = true,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Klucz GEMINI_API_KEY nie został skonfigurowany w panelu Secrets.")
            )
        }

        val resolvedModel = when (model) {
            "gemini-pro", "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
            "gemini-flash", "gemini-3.5-flash" -> "gemini-3.5-flash"
            "gemini-lite", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite-preview" -> "gemini-3.1-flash-lite-preview"
            else -> model
        }

        val generationConfig = if (enableHighThinking && resolvedModel == "gemini-3.1-pro-preview") {
            GeminiGenerationConfig(
                temperature = 0.7f,
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "high")
            )
        } else {
            GeminiGenerationConfig(temperature = 0.7f)
        }

        val contents = listOf(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )
        )

        val sysInst = systemInstruction?.let {
            GeminiContent(parts = listOf(GeminiPart(text = it)))
        }

        val reqObj = GeminiRequest(
            contents = contents,
            generationConfig = generationConfig,
            systemInstruction = sysInst
        )

        val jsonBody = requestAdapter.toJson(reqObj)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    Exception("Błąd Gemini HTTP ${response.code}: ${responseBody ?: response.message}")
                )
            }

            val parsed = responseAdapter.fromJson(responseBody)
            val textParts = parsed?.candidates?.firstOrNull()?.content?.parts?.mapNotNull { it.text }
            val fullText = textParts?.joinToString("\n") ?: ""

            if (fullText.isNotBlank()) {
                Result.success(fullText)
            } else {
                Result.failure(Exception("Gemini zwróciło pustą odpowiedź."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun crossCritique(
        userPrompt: String,
        editedText: String,
        sourceModel: String,
        customInstruction: String? = null
    ): Result<String> {
        val instruction = customInstruction ?: "Użytkownik edytował odpowiedź z $sourceModel. " +
                "Przeanalizuj tę zmodyfikowaną wersję, oceń poprawki, wskaż ewentualne pozostałe błędy, " +
                "lub kontynuuj wątek na podstawie tej ulepszonej bazy."

        val prompt = """
            Zadanie: Weryfikacja i optymalizacja zmodyfikowanej odpowiedzi.
            Oryginalne zapytanie użytkownika: $userPrompt
            
            Edytowana wersja od $sourceModel:
            $editedText
            
            Instrukcja użytkownika: $instruction
            
            Przedstaw swoją profesjonalną opinię, poprawki oraz zoptymalizowaną kontynuację.
        """.trimIndent()

        return generate(
            prompt = prompt,
            model = "gemini-3.1-pro-preview",
            enableHighThinking = true,
            systemInstruction = "Jesteś ekspertem analitycznym Gemini. Analizujesz zmodyfikowany tekst, korygujesz błędy i rozwijasz konwersację z ChatGPT."
        )
    }

    suspend fun synthesizeComparison(
        prompt: String,
        geminiAnswer: String,
        chatGptAnswer: String
    ): Result<String> {
        val comparisonPrompt = """
            Porównaj poniższe odpowiedzi na pytanie: "$prompt"
            
            --- Odpowiedź Gemini ---
            $geminiAnswer
            
            --- Odpowiedź ChatGPT ---
            $chatGptAnswer
            
            Przygotuj zwięzłą analizę:
            1. Kluczowe różnice w podejściu i faktach.
            2. Co Gemini zrobiło lepiej.
            3. Co ChatGPT zrobiło lepiej.
            4. Zoptymalizowana, połączona odpowiedź wyciągająca to, co najlepsze z obu modeli.
        """.trimIndent()

        return generate(
            prompt = comparisonPrompt,
            model = "gemini-3.1-pro-preview",
            enableHighThinking = true
        )
    }
}
