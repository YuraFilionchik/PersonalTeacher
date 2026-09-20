package com.example.personallangmaster.ai.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Результат проверки ключа — то, что видит пользователь в настройках. */
sealed interface KeyCheckResult {
    /** Ключ рабочий; [modelsAvailable] — сколько моделей доступно этому ключу. */
    data class Valid(val modelsAvailable: Int) : KeyCheckResult
    data object Invalid : KeyCheckResult
    data object QuotaExceeded : KeyCheckResult
    data object NoNetwork : KeyCheckResult
    data class Unknown(val message: String) : KeyCheckResult
}

/**
 * Проверяет ключ Gemini самым дешёвым способом — запросом списка моделей.
 *
 * Этот вызов не тарифицируется, поэтому кнопкой «Проверить ключ» можно
 * пользоваться свободно, не тратя бюджет урока.
 */
class GeminiKeyChecker(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val apiVersion: String = "v1beta",
) {

    suspend fun check(apiKey: String): KeyCheckResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext KeyCheckResult.Invalid

        val request = Request.Builder()
            .url("$baseUrl/$apiVersion/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        // Считаем вхождения "name": — точный разбор JSON здесь избыточен.
                        val models = Regex("\"name\"\\s*:").findAll(body).count()
                        KeyCheckResult.Valid(models)
                    }
                    400, 401, 403 -> KeyCheckResult.Invalid
                    429 -> KeyCheckResult.QuotaExceeded
                    else -> KeyCheckResult.Unknown("HTTP ${response.code}")
                }
            }
        } catch (error: IOException) {
            KeyCheckResult.NoNetwork
        } catch (error: Exception) {
            KeyCheckResult.Unknown(error.message.orEmpty())
        }
    }

    private companion object {
        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
