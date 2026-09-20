package com.example.personallangmaster.ai.live

/** Что именно пошло не так — от этого зависит текст и кнопка, которые видит ученик. */
enum class LiveErrorKind {
    NO_KEY,
    INVALID_KEY,
    QUOTA,
    NETWORK,
    PROTOCOL,
    AUDIO_DEVICE,
    BUDGET_LIMIT,
}

/** Состояние живой сессии. Ровно одно в каждый момент времени. */
sealed interface LiveSessionState {
    data object Idle : LiveSessionState
    data object Connecting : LiveSessionState

    /** `setupComplete` получен, можно говорить. */
    data object Ready : LiveSessionState

    /** Микрофон открыт, аудио уходит на сервер. */
    data object Listening : LiveSessionState

    /** Реплика закрыта, ждём ответ. */
    data object Thinking : LiveSessionState

    /** Играем аудио тренера. */
    data object Speaking : LiveSessionState

    data class Reconnecting(val attempt: Int) : LiveSessionState
    data class Error(val kind: LiveErrorKind, val message: String) : LiveSessionState
    data object Closed : LiveSessionState
}

/** События, которые сессия отдаёт наружу. */
sealed interface LiveEvent {
    /** Расшифровка речи ученика: `isFinal` — реплика закончена. */
    data class UserTranscript(val text: String, val isFinal: Boolean) : LiveEvent

    /** Расшифровка речи тренера. */
    data class TutorTranscript(val text: String, val isFinal: Boolean) : LiveEvent

    /** Тренер вызвал функцию приложения. */
    data class Tool(val call: FunctionCall) : LiveEvent

    /** Ученик перебил тренера — воспроизведение сброшено. */
    data object Interrupted : LiveEvent

    /** Ход тренера завершён. */
    data object TurnComplete : LiveEvent

    /** Учёт израсходованных токенов. */
    data class Usage(val promptTokens: Int, val responseTokens: Int) : LiveEvent

    /** Сервер предупредил о скором закрытии соединения. */
    data class GoingAway(val timeLeft: String?) : LiveEvent

    data class Failed(val kind: LiveErrorKind, val message: String) : LiveEvent
}
