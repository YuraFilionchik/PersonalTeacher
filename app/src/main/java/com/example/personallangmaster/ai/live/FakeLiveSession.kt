package com.example.personallangmaster.ai.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** Виды событий записанного диалога. */
@Serializable
enum class FakeEventType {
    STATE,
    TUTOR_TEXT,
    TOOL_MISTAKE,
    TOOL_VOCAB,
    TOOL_CARD,
    TURN_COMPLETE,
}

/** Состояние сессии внутри сценария. */
@Serializable
enum class FakeSessionState {
    IDLE,
    CONNECTING,
    READY,
    LISTENING,
    THINKING,
    SPEAKING,
}

/**
 * Одно событие сценария: пауза, состояние, реплика тренера или вызов инструмента.
 *
 * Поля разных видов событий не пересекаются, поэтому неиспользуемые остаются `null` —
 * так сценарий читается как обычный JSON без вложенных объектов.
 */
@Serializable
data class FakeEvent(
    /** Пауза перед событием: этим задаётся темп живой речи. */
    val delayMs: Long = 0,
    val type: FakeEventType,
    val state: FakeSessionState? = null,
    val text: String? = null,
    @SerialName("mistakeType") val mistakeType: String? = null,
    val original: String? = null,
    val corrected: String? = null,
    val explanation: String? = null,
    val severity: Int? = null,
    @SerialName("grammarTopic") val grammarTopic: String? = null,
    val phoneme: String? = null,
    val term: String? = null,
    @SerialName("translationRu") val translationRu: String? = null,
    val example: String? = null,
    val why: String? = null,
    @SerialName("textEn") val textEn: String? = null,
    @SerialName("textRu") val textRu: String? = null,
)

/** Ответ тренера на одну реплику ученика. */
@Serializable
data class FakeReply(val events: List<FakeEvent> = emptyList())

/** Весь записанный урок: вступление и ответы по порядку. */
@Serializable
data class FakeScript(
    val initialEvents: List<FakeEvent> = emptyList(),
    val replies: List<FakeReply> = emptyList(),
)

/**
 * Фейковая сессия для офлайн-отладки экрана урока.
 *
 * Повторяет публичный API [LiveSession], но вместо Gemini Live проигрывает записанный
 * диалог из `assets/fake_session.json` — экран урока можно отлаживать без платных запросов.
 * Сценарий лежит в debug-наборе ресурсов, поэтому в release-сборке его нет.
 */
class FakeLiveSession(
    private val scope: CoroutineScope,
    private val context: Context,
) {

    private val _state = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    private val _micLevelDbfs = MutableStateFlow(SILENCE_DBFS)
    val micLevelDbfs: StateFlow<Double> = _micLevelDbfs.asStateFlow()

    private val _tutorLevelDbfs = MutableStateFlow(SILENCE_DBFS)
    val tutorLevelDbfs: StateFlow<Double> = _tutorLevelDbfs.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private var script: FakeScript? = null
    private var replyIndex = 0
    private var callCounter = 0
    private var talking = false
    private var stopped = false
    private var scriptJob: Job? = null
    private var micJob: Job? = null

    /** Загружает сценарий и проигрывает вступление. Сеть и токены не расходуются. */
    fun start(config: LiveSessionConfig) {
        stopped = false
        talking = false
        replyIndex = 0
        callCounter = 0
        _state.value = LiveSessionState.Connecting

        val loaded = loadScript()
        if (loaded == null) {
            fail("Не удалось прочитать $SCRIPT_ASSET")
            return
        }

        script = loaded
        Log.i(
            TAG,
            "Фейковый урок: модель ${config.model}, событий ${loaded.initialEvents.size}, " +
                "ответов ${loaded.replies.size}",
        )
        play(loaded.initialEvents)
    }

    /** Останавливает проигрывание и закрывает сессию. */
    fun stop() {
        stopped = true
        talking = false
        scriptJob?.cancel()
        micJob?.cancel()
        scriptJob = null
        micJob = null
        _micLevelDbfs.value = SILENCE_DBFS
        _tutorLevelDbfs.value = SILENCE_DBFS
        _state.value = LiveSessionState.Closed
    }

    /** Ученик начал говорить: тренер замолкает, микрофон «шумит» для волны на экране. */
    fun startTalking() {
        if (talking) return
        talking = true

        if (_state.value is LiveSessionState.Speaking) {
            interrupt()
        }
        _state.value = LiveSessionState.Listening

        micJob?.cancel()
        micJob = scope.launch {
            while (isActive) {
                _micLevelDbfs.value = Random.nextDouble(MIC_LEVEL_MIN_DBFS, MIC_LEVEL_MAX_DBFS)
                delay(MIC_LEVEL_TICK_MS)
            }
        }
    }

    /** Ученик закончил реплику: тренер «думает» и отвечает следующей заготовкой. */
    fun stopTalking() {
        if (!talking) return
        talking = false
        micJob?.cancel()
        micJob = null
        _micLevelDbfs.value = SILENCE_DBFS
        _state.value = LiveSessionState.Thinking
        playNextReply()
    }

    /** Реплика текстом: тот же сценарий, просто без микрофона. */
    fun sendText(text: String) {
        if (text.isBlank()) return
        emit(LiveEvent.UserTranscript(text, isFinal = true))
        _state.value = LiveSessionState.Thinking
        playNextReply()
    }

    /** Сервера нет, подтверждать вызов некому — инструмент уже применён самой сессией. */
    fun respondToTool(call: FunctionCall, response: JsonObject = LiveTools.ok()) {
        Log.d(TAG, "Вызов ${call.name} в фейковом режиме остаётся без ответа")
    }

    // --- Проигрывание ---

    /** Берёт следующий по очереди ответ; когда сценарий кончился, сессия просто ждёт. */
    private fun playNextReply() {
        val reply = script?.replies?.getOrNull(replyIndex)
        if (reply == null) {
            play(tailEvents())
            return
        }
        replyIndex++
        play(reply.events)
    }

    private fun play(events: List<FakeEvent>) {
        scriptJob?.cancel()
        scriptJob = scope.launch {
            events.forEach { event -> handle(event) }
        }
    }

    private suspend fun handle(event: FakeEvent) {
        if (stopped) return
        if (event.delayMs > 0) delay(event.delayMs)
        if (stopped) return

        when (event.type) {
            FakeEventType.STATE -> event.state?.let { _state.value = it.toLiveState() }
            FakeEventType.TUTOR_TEXT -> event.text?.let { line -> speak(line) }
            FakeEventType.TOOL_MISTAKE -> emit(LiveEvent.Tool(mistakeCall(event)))
            FakeEventType.TOOL_VOCAB -> emit(LiveEvent.Tool(vocabCall(event)))
            FakeEventType.TOOL_CARD -> emit(LiveEvent.Tool(cardCall(event)))
            FakeEventType.TURN_COMPLETE -> {
                emit(LiveEvent.TurnComplete)
                if (!talking) _state.value = LiveSessionState.Ready
            }
        }
    }

    /** Реплика тренера: текст идёт в субтитры, громкость — в волну, расход — в счётчик. */
    private suspend fun speak(text: String) {
        if (stopped) return
        _state.value = LiveSessionState.Speaking
        emit(LiveEvent.TutorTranscript(text, isFinal = false))
        emit(LiveEvent.Usage(FAKE_PROMPT_TOKENS, FAKE_RESPONSE_TOKENS))
        _tutorLevelDbfs.value = TUTOR_LEVEL_DBFS
        delay(SPEECH_TICK_MS)
        if (!talking) _tutorLevelDbfs.value = SILENCE_DBFS
    }

    /** Перебивание: остаток ответа выбрасывается, как это делает живая сессия. */
    private fun interrupt() {
        scriptJob?.cancel()
        scriptJob = null
        _tutorLevelDbfs.value = SILENCE_DBFS
        emit(LiveEvent.Interrupted)
    }

    /** Сценарий закончился: сессия жива, но новых реплик у неё нет. */
    private fun tailEvents(): List<FakeEvent> = listOf(
        FakeEvent(
            delayMs = THINKING_PAUSE_MS,
            type = FakeEventType.STATE,
            state = FakeSessionState.READY,
        ),
        FakeEvent(delayMs = THINKING_PAUSE_MS, type = FakeEventType.TUTOR_TEXT, text = TAIL_LINE),
        FakeEvent(type = FakeEventType.TURN_COMPLETE),
    )

    // --- Инструменты ---

    private fun mistakeCall(event: FakeEvent): FunctionCall =
        toolCall(LiveTools.LOG_MISTAKE, "fake_mistake") {
            put("type", event.mistakeType.orEmpty().ifBlank { "GRAMMAR" })
            put("original", event.original.orEmpty())
            put("corrected", event.corrected.orEmpty())
            event.explanation?.let { put("explanation", it) }
            event.severity?.let { put("severity", it) }
            event.grammarTopic?.let { put("grammar_topic", it) }
            event.phoneme?.let { put("phoneme", it) }
        }

    private fun vocabCall(event: FakeEvent): FunctionCall =
        toolCall(LiveTools.SAVE_VOCAB, "fake_vocab") {
            put("term", event.term.orEmpty())
            put("translation_ru", event.translationRu.orEmpty())
            event.example?.let { put("example", it) }
            event.why?.let { put("why", it) }
        }

    private fun cardCall(event: FakeEvent): FunctionCall =
        toolCall(LiveTools.SHOW_CARD, "fake_card") {
            put("kind", "PHRASE")
            put("text_en", event.textEn.orEmpty())
            event.textRu?.let { put("text_ru", it) }
        }

    /** Собирает вызов так же, как его прислал бы сервер: аргументы — тот же JSON-контракт. */
    private fun toolCall(
        name: String,
        prefix: String,
        build: JsonObjectBuilder.() -> Unit,
    ): FunctionCall {
        callCounter++
        return FunctionCall(
            id = "${prefix}_$callCounter",
            name = name,
            args = buildJsonObject(build),
        )
    }

    // --- Служебное ---

    private fun loadScript(): FakeScript? = runCatching {
        context.assets.open(SCRIPT_ASSET)
            .use { stream -> json.decodeFromString<FakeScript>(stream.readBytes().decodeToString()) }
    }.onFailure { error ->
        Log.e(TAG, "Не удалось прочитать $SCRIPT_ASSET", error)
    }.getOrNull()

    private fun fail(message: String) {
        Log.w(TAG, message)
        _state.value = LiveSessionState.Error(LiveErrorKind.PROTOCOL, message)
        emit(LiveEvent.Failed(LiveErrorKind.PROTOCOL, message))
    }

    private fun emit(event: LiveEvent) {
        if (!_events.tryEmit(event)) {
            scope.launch { _events.emit(event) }
        }
    }

    private fun FakeSessionState.toLiveState(): LiveSessionState = when (this) {
        FakeSessionState.IDLE -> LiveSessionState.Idle
        FakeSessionState.CONNECTING -> LiveSessionState.Connecting
        FakeSessionState.READY -> LiveSessionState.Ready
        FakeSessionState.LISTENING -> LiveSessionState.Listening
        FakeSessionState.THINKING -> LiveSessionState.Thinking
        FakeSessionState.SPEAKING -> LiveSessionState.Speaking
    }

    private companion object {
        const val TAG = "FakeLiveSession"
        const val SCRIPT_ASSET = "fake_session.json"
        const val SILENCE_DBFS = -100.0
        const val MIC_LEVEL_MIN_DBFS = -42.0
        const val MIC_LEVEL_MAX_DBFS = -18.0
        const val MIC_LEVEL_TICK_MS = 120L
        const val TUTOR_LEVEL_DBFS = -16.0
        const val SPEECH_TICK_MS = 350L
        const val THINKING_PAUSE_MS = 600L
        const val FAKE_PROMPT_TOKENS = 900
        const val FAKE_RESPONSE_TOKENS = 260

        /** Реплика-хвост: сценарий кончился, но сессия остаётся живой для экспериментов. */
        const val TAIL_LINE = "Let's keep going. Tell me what you did today."
    }
}
