package com.example.personallangmaster.ai.live

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/** Что приходит из сокета. */
sealed interface LiveSocketEvent {
    data object Opened : LiveSocketEvent
    data class Message(val message: ServerMessage) : LiveSocketEvent
    data class Failure(
        val error: Throwable,
        val httpCode: Int?,
        /** Тело ответа сервера: именно там лежит настоящая причина отказа. */
        val body: String? = null,
    ) : LiveSocketEvent

    data class Closed(val code: Int, val reason: String) : LiveSocketEvent
}

/**
 * Тонкая обёртка над WebSocket: отвечает только за транспорт и разбор JSON.
 *
 * Состояние урока, аудио и повторные подключения живут в [LiveSession] —
 * здесь намеренно нет никакой логики, чтобы протокол можно было тестировать
 * на записанных ответах сервера.
 */
class LiveWebSocketClient(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private var webSocket: WebSocket? = null

    /**
     * Открывает соединение и отдаёт поток событий. Поток живёт, пока жив сокет;
     * закрытие потока закрывает соединение.
     */
    fun connect(apiKey: String): Flow<LiveSocketEvent> = callbackFlow {
        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Соединение открыто (${response.code})")
                trySend(LiveSocketEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { trySend(LiveSocketEvent.Message(it)) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Сервер может прислать тот же JSON бинарным кадром.
                parse(bytes.utf8())?.let { trySend(LiveSocketEvent.Message(it)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Тело читаем один раз и сразу: без него причина отказа теряется,
                // а «не удалось подключиться» ничего не объясняет.
                val body = runCatching { response?.body?.string() }.getOrNull()
                Log.w(TAG, "Обрыв: code=${response?.code} ${t.message} body=${body?.take(500)}")
                trySend(LiveSocketEvent.Failure(t, response?.code, body))
                close()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Сервер закрывает соединение: code=$code reason=$reason")
                webSocket.close(NORMAL_CLOSE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Соединение закрыто: code=$code reason=$reason")
                trySend(LiveSocketEvent.Closed(code, reason))
                close()
            }
        }

        webSocket = client.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(NORMAL_CLOSE, "session finished")
            webSocket = null
        }
    }

    /** Отправляет уже сериализованное сообщение. Возвращает false, если сокет закрыт. */
    fun sendRaw(json: String): Boolean {
        val socket = webSocket ?: return false
        return socket.send(json)
    }

    inline fun <reified T> send(message: T): Boolean =
        sendRaw(liveJson.encodeToString(message))

    fun close(reason: String = "closed by user") {
        webSocket?.close(NORMAL_CLOSE, reason)
        webSocket = null
    }

    private fun parse(raw: String): ServerMessage? = runCatching {
        liveJson.decodeFromString<ServerMessage>(raw)
    }.onFailure {
        Log.w(TAG, "Не разобрали сообщение сервера: ${it.message}")
    }.getOrNull()

    companion object {
        const val DEFAULT_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        private const val TAG = "LiveWebSocket"
        private const val NORMAL_CLOSE = 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Поток аудио идёт непрерывно, чтение не должно отваливаться по таймауту.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
