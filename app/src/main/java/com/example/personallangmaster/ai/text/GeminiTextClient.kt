package com.example.personallangmaster.ai.text

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Результат текстового вызова: либо разобранный ответ, либо понятная причина отказа. */
sealed interface TextResult<out T> {
    data class Success<T>(val value: T, val promptTokens: Int, val responseTokens: Int) : TextResult<T>
    data class Failure(val reason: String, val invalidKey: Boolean = false) : TextResult<Nothing>
}

@Serializable
private data class GenerateRequest(
    val contents: List<RequestContent>,
    val systemInstruction: RequestContent? = null,
    val generationConfig: RequestGenerationConfig? = null,
)

@Serializable
private data class RequestContent(
    val role: String? = null,
    val parts: List<RequestPart>,
)

@Serializable
private data class RequestPart(val text: String)

@Serializable
private data class RequestGenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
)

@Serializable
private data class GenerateResponse(
    val candidates: List<Candidate> = emptyList(),
    val usageMetadata: TextUsage? = null,
)

@Serializable
private data class Candidate(val content: RequestContent? = null, val finishReason: String? = null)

@Serializable
private data class TextUsage(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)

/**
 * Обычный (не потоковый) вызов Gemini для задач, которым не нужен голос:
 * разбор урока, генерация упражнений, переводы.
 *
 * Работает через структурированный вывод: модель обязана вернуть JSON по схеме,
 * поэтому ответ не приходится разбирать регулярками и догадками.
 */
class GeminiTextClient(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val apiVersion: String = "v1beta",
) {

    suspend fun <T> generateStructured(
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String? = null,
        schema: JsonObject,
        temperature: Float = 0.3f,
        parse: (String) -> T,
    ): TextResult<T> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext TextResult.Failure("Ключ не задан", invalidKey = true)

        val payload = GenerateRequest(
            contents = listOf(RequestContent(role = "user", parts = listOf(RequestPart(prompt)))),
            systemInstruction = systemInstruction?.let {
                RequestContent(parts = listOf(RequestPart(it)))
            },
            generationConfig = RequestGenerationConfig(
                temperature = temperature,
                responseMimeType = "application/json",
                responseSchema = schema,
            ),
        )

        val request = Request.Builder()
            .url("$baseUrl/$apiVersion/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val decoded = json.decodeFromString<GenerateResponse>(body)
                        val text = decoded.candidates
                            .firstOrNull()?.content?.parts?.joinToString("") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?: return@use TextResult.Failure("Модель вернула пустой ответ")

                        runCatching { parse(text) }.fold(
                            onSuccess = { value ->
                                TextResult.Success(
                                    value = value,
                                    promptTokens = decoded.usageMetadata?.promptTokenCount ?: 0,
                                    responseTokens = decoded.usageMetadata?.candidatesTokenCount ?: 0,
                                )
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Не разобрали ответ модели: ${error.message}")
                                TextResult.Failure("Ответ модели не соответствует схеме")
                            },
                        )
                    }

                    response.code == 400 || response.code == 401 || response.code == 403 ->
                        TextResult.Failure("Ключ не принят", invalidKey = true)

                    response.code == 429 -> TextResult.Failure("Квота исчерпана")

                    else -> TextResult.Failure("Сервер ответил ${response.code}")
                }
            }
        } catch (error: IOException) {
            TextResult.Failure("Нет соединения")
        } catch (error: Exception) {
            TextResult.Failure(error.message ?: "Неизвестная ошибка")
        }
    }

    private companion object {
        const val TAG = "GeminiTextClient"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Разбор урока — длинный ответ, минуты хватает с запасом.
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
