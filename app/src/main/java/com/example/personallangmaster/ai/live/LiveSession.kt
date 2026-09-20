package com.example.personallangmaster.ai.live

import android.util.Base64
import android.util.Log
import com.example.personallangmaster.core.audio.AudioPlayer
import com.example.personallangmaster.core.audio.AudioRecorder
import com.example.personallangmaster.core.audio.EnergyVad
import com.example.personallangmaster.core.audio.Pcm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Всё, что нужно знать сессии перед подключением. */
data class LiveSessionConfig(
    val apiKey: String,
    val model: String,
    val systemInstruction: String,
    val voiceName: String,
    val languageCode: String = "en-US",
    val temperature: Float = 0.8f,
    val transcription: Boolean = true,
    val contextCompression: Boolean = true,
    val sessionResumption: Boolean = true,
    /** true — границы реплики задаёт кнопка, false — серверный VAD (hands-free). */
    val manualActivity: Boolean = true,
    /**
     * Закрывать реплику самостоятельно, когда ученик замолчал.
     *
     * Нужен для режима одиночного нажатия: нажал, сказал, отпустил телефон —
     * второй раз тянуться к кнопке, чтобы «отправить», неестественно в разговоре.
     */
    val autoEndOnSilence: Boolean = false,
    val bargeInEnabled: Boolean = true,
    val noiseSuppression: Boolean = true,
    /** Локальный порог тишины: молчание на сервер не отправляется. */
    val vadThresholdDb: Double = -38.0,
    val silenceHangoverMs: Int = 800,
    /** Громкость речи тренера, 0..1. */
    val tutorVolume: Float = 1.0f,
)

/**
 * Живая сессия урока: соединение, звук и состояние в одном месте.
 *
 * Сюда стекается всё, что делает разговор разговором — открытие микрофона,
 * перебивание, восстановление после обрыва. UI получает только состояние и события.
 */
/** Куда отдавать звук урока, если включена запись. */
interface LessonAudioSink {
    fun onUserPcm(pcm16k: ByteArray)
    fun onTutorPcm(pcm24k: ByteArray)
}

class LiveSession(
    private val scope: CoroutineScope,
    private val recorder: AudioRecorder = AudioRecorder(),
    private val player: AudioPlayer = AudioPlayer(),
    private val clientFactory: () -> LiveWebSocketClient = { LiveWebSocketClient() },
) {

    private val _state = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    private val _micLevelDbfs = MutableStateFlow(-100.0)
    val micLevelDbfs: StateFlow<Double> = _micLevelDbfs.asStateFlow()

    /** Уровень речи тренера — для той же волны на экране. */
    val tutorLevelDbfs: StateFlow<Double> get() = player.levelDbfs

    private var client: LiveWebSocketClient? = null
    private var socketJob: Job? = null
    private var micJob: Job? = null
    private var config: LiveSessionConfig? = null

    /** Приёмник звука для записи урока. Пока не задан — ничего не пишется. */
    var audioSink: LessonAudioSink? = null

    private var resumptionHandle: String? = null
    private var lastFailureReason: String? = null
    private var stopped = false
    private var talking = false
    private var reconnectAttempt = 0

    // --- Жизненный цикл ---

    fun start(config: LiveSessionConfig) {
        if (config.apiKey.isBlank()) {
            fail(LiveErrorKind.NO_KEY, "Ключ Gemini не задан")
            return
        }
        this.config = config
        stopped = false
        reconnectAttempt = 0
        resumptionHandle = null
        player.start(
            volume = config.tutorVolume,
            // В hands-free микрофон открыт всегда, и эхоподавлению нужен разговорный поток.
            output = if (config.manualActivity) {
                AudioPlayer.Output.MEDIA
            } else {
                AudioPlayer.Output.VOICE_COMMUNICATION
            },
        )
        connect()
    }

    fun stop() {
        stopped = true
        stopTalking()
        micJob?.cancel()
        micJob = null
        socketJob?.cancel()
        socketJob = null
        client?.close()
        client = null
        player.stop()
        _state.value = LiveSessionState.Closed
    }

    private fun connect() {
        val current = config ?: return
        val socket = clientFactory()
        client = socket
        _state.value = if (reconnectAttempt == 0) {
            LiveSessionState.Connecting
        } else {
            LiveSessionState.Reconnecting(reconnectAttempt)
        }

        socketJob?.cancel()
        socketJob = socket.connect(current.apiKey)
            .onEach { event -> handleSocketEvent(event, current) }
            .catch { error -> scheduleReconnect(error.message ?: "обрыв соединения") }
            .launchIn(scope)
    }

    private fun handleSocketEvent(event: LiveSocketEvent, config: LiveSessionConfig) {
        when (event) {
            LiveSocketEvent.Opened -> sendSetup(config)
            is LiveSocketEvent.Message -> handleServerMessage(event.message)
            is LiveSocketEvent.Failure -> {
                val detail = serverDetail(event.body) ?: event.error.message ?: "нет связи"
                lastFailureReason = listOfNotNull(event.httpCode?.let { "HTTP $it" }, detail)
                    .joinToString(": ")

                when (event.httpCode) {
                    400 -> fail(LiveErrorKind.PROTOCOL, "Сервер отклонил запрос. $detail")
                    401, 403 -> fail(LiveErrorKind.INVALID_KEY, "Ключ не принят сервером. $detail")
                    429 -> fail(
                        LiveErrorKind.QUOTA,
                        "Лимит запросов исчерпан. Живой режим у бесплатного ключа " +
                            "ограничен жёстче обычного. $detail",
                    )
                    else -> scheduleReconnect(lastFailureReason ?: detail)
                }
            }
            is LiveSocketEvent.Closed -> {
                if (stopped) return
                // Код закрытия и текст причины — единственное, что объясняет,
                // почему сессия не поднялась: без них остаётся «нет связи».
                lastFailureReason = buildString {
                    append("код ${event.code}")
                    if (event.reason.isNotBlank()) append(": ${event.reason}")
                }
                
                // Если сервер ругается на некорректное состояние (1007) или упал (1011),
                // скорее всего текущий resumptionHandle "протух" или сломан. 
                // Сбрасываем его, чтобы следующая попытка переподключения начала чистую сессию.
                if (event.code == 1007 || event.code == 1011) {
                    resumptionHandle = null
                }
                
                scheduleReconnect(lastFailureReason!!)
            }
        }
    }

    private fun sendSetup(config: LiveSessionConfig) {
        val setup = Setup(
            model = "models/${config.model}",
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                temperature = config.temperature,
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(PrebuiltVoiceConfig(config.voiceName)),
                    languageCode = config.languageCode,
                ),
            ),
            systemInstruction = Content(parts = listOf(Part(text = config.systemInstruction))),
            tools = LiveTools.declarations,
            realtimeInputConfig = if (config.manualActivity) {
                RealtimeInputConfig(AutomaticActivityDetection(disabled = true))
            } else {
                null
            },
            sessionResumption = if (config.sessionResumption) {
                SessionResumption(handle = resumptionHandle)
            } else {
                null
            },
            contextWindowCompression = if (config.contextCompression) {
                ContextWindowCompression()
            } else {
                null
            },
            // Без транскрипции не будет ни субтитров, ни материала для разбора урока.
            inputAudioTranscription = if (config.transcription) Empty() else null,
            outputAudioTranscription = if (config.transcription) Empty() else null,
        )

        val sent = client?.send(SetupMessage(setup)) ?: false
        Log.i(TAG, "setup отправлен=$sent model=${config.model} voice=${config.voiceName}")
    }

    private fun handleServerMessage(message: ServerMessage) {
        message.setupComplete?.let {
            reconnectAttempt = 0
            _state.value = LiveSessionState.Ready
        }

        message.sessionResumptionUpdate?.let { update ->
            if (update.resumable == true) resumptionHandle = update.newHandle
        }

        message.usageMetadata?.let { usage ->
            emit(
                LiveEvent.Usage(
                    promptTokens = usage.promptTokenCount ?: 0,
                    responseTokens = usage.responseTokenCount ?: 0,
                )
            )
        }

        message.goAway?.let { emit(LiveEvent.GoingAway(it.timeLeft)) }

        message.serverContent?.let(::handleServerContent)

        message.toolCall?.functionCalls?.forEach { call -> emit(LiveEvent.Tool(call)) }
    }

    private fun handleServerContent(content: ServerContent) {
        if (content.interrupted == true) {
            player.flush()
            emit(LiveEvent.Interrupted)
        }

        content.inputTranscription?.text?.takeIf { it.isNotBlank() }?.let { text ->
            emit(LiveEvent.UserTranscript(text, isFinal = content.turnComplete == true))
        }

        content.outputTranscription?.text?.takeIf { it.isNotBlank() }?.let { text ->
            emit(LiveEvent.TutorTranscript(text, isFinal = false))
        }

        content.modelTurn?.parts?.forEach { part ->
            part.inlineData?.let { blob ->
                if (blob.mimeType.startsWith("audio/")) {
                    runCatching { Base64.decode(blob.data, Base64.DEFAULT) }
                        .onSuccess { pcm ->
                            audioSink?.onTutorPcm(pcm)
                            player.enqueue(pcm)
                            if (_state.value !is LiveSessionState.Error) {
                                _state.value = LiveSessionState.Speaking
                            }
                        }
                }
            }
            part.text?.takeIf { it.isNotBlank() }?.let { text ->
                emit(LiveEvent.TutorTranscript(text, isFinal = false))
            }
        }

        if (content.turnComplete == true) {
            emit(LiveEvent.TurnComplete)
            if (!talking) _state.value = LiveSessionState.Ready
        }
    }

    // --- Микрофон ---

    /** Ученик начал говорить: открываем микрофон и, если надо, перебиваем тренера. */
    fun startTalking() {
        val current = config ?: return
        if (talking) return
        talking = true

        if (current.bargeInEnabled && player.isPlaying.value) {
            player.flush()
        }

        if (current.manualActivity) {
            client?.send(RealtimeInputMessage(RealtimeInput(activityStart = Empty())))
        }
        _state.value = LiveSessionState.Listening

        val vad = EnergyVad(
            sampleRate = AudioRecorder.SAMPLE_RATE,
            startThresholdDb = current.vadThresholdDb,
            stopThresholdDb = current.vadThresholdDb - 7.0,
            silenceHangoverMs = current.silenceHangoverMs,
        )

        // Небольшой предбуфер: иначе VAD съедает первые слоги, пока набирает уверенность.
        val preRoll = ArrayDeque<ByteArray>()
        // Пока ученик не сказал ни слова, тишина не считается концом реплики.
        var spokeAtLeastOnce = false

        micJob?.cancel()
        micJob = recorder.chunks(applyEffects = current.noiseSuppression)
            .onEach { chunk ->
                _micLevelDbfs.value = Pcm.dbfs(chunk)
                val wasSpeaking = vad.isSpeaking
                vad.process(chunk)

                when {
                    // Тишину не отправляем: самая дешёвая экономия входных токенов.
                    !vad.isSpeaking -> {
                        preRoll.addLast(chunk)
                        while (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
                    }

                    !wasSpeaking -> {
                        spokeAtLeastOnce = true
                        while (preRoll.isNotEmpty()) sendAudio(preRoll.removeFirst())
                        sendAudio(chunk)
                    }

                    else -> sendAudio(chunk)
                }

                // Ученик договорил — закрываем реплику сами, не дожидаясь кнопки.
                if (current.autoEndOnSilence && spokeAtLeastOnce && wasSpeaking && !vad.isSpeaking) {
                    stopTalking()
                }
            }
            .catch { error ->
                Log.w(TAG, "Микрофон остановлен: ${error.message}")
                fail(LiveErrorKind.AUDIO_DEVICE, "Не удалось открыть микрофон")
            }
            .launchIn(scope)
    }

    /** Ученик закончил реплику. */
    fun stopTalking() {
        if (!talking) return
        talking = false
        micJob?.cancel()
        micJob = null
        _micLevelDbfs.value = -100.0

        config?.let { current ->
            if (current.manualActivity) {
                client?.send(RealtimeInputMessage(RealtimeInput(activityEnd = Empty())))
            }
        }

        if (_state.value is LiveSessionState.Listening) {
            _state.value = LiveSessionState.Thinking
        }
    }

    private fun sendAudio(pcm: ByteArray) {
        // В запись попадает ровно то, что ушло на сервер: тишина не нужна и там, и там.
        audioSink?.onUserPcm(pcm)
        val encoded = Base64.encodeToString(pcm, Base64.NO_WRAP)
        client?.send(
            RealtimeInputMessage(
                RealtimeInput(
                    audio = Blob(
                        mimeType = "audio/pcm;rate=${AudioRecorder.SAMPLE_RATE}",
                        data = encoded,
                    )
                )
            )
        )
    }

    // --- Текст и инструменты ---

    /** Реплика текстом: тот же диалог, просто без микрофона. */
    fun sendText(text: String) {
        if (text.isBlank()) return
        client?.send(
            ClientContentMessage(
                ClientContent(
                    turns = listOf(Content(parts = listOf(Part(text = text)), role = "user")),
                    turnComplete = true,
                )
            )
        )
        _state.value = LiveSessionState.Thinking
    }

    /** Ответ на вызов функции. Должен уходить сразу, иначе рвётся темп речи. */
    fun respondToTool(call: FunctionCall, response: JsonObject = LiveTools.ok()) {
        client?.send(
            ToolResponseMessage(
                ToolResponse(
                    functionResponses = listOf(
                        FunctionResponse(id = call.id, name = call.name, response = response)
                    )
                )
            )
        )
    }

    // --- Восстановление ---

    private fun scheduleReconnect(reason: String) {
        if (stopped) return
        Log.w(TAG, "Переподключение (${reconnectAttempt + 1}): $reason")
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            fail(LiveErrorKind.NETWORK, "Не удалось подключиться. $reason")
            return
        }
        reconnectAttempt++
        _state.value = LiveSessionState.Reconnecting(reconnectAttempt)
        scope.launch {
            delay(RECONNECT_BASE_MS * (1L shl (reconnectAttempt - 1)))
            if (!stopped) connect()
        }
    }

    /** Вытаскивает человеческую причину из JSON-ошибки Google. */
    private fun serverDetail(body: String?): String? {
        val raw = body?.takeIf { it.isNotBlank() } ?: return null
        val parsed = runCatching {
            val error = liveJson.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
            val status = error?.get("status")?.jsonPrimitive?.contentOrNull
            val message = error?.get("message")?.jsonPrimitive?.contentOrNull
            listOfNotNull(status, message).joinToString(": ").ifBlank { null }
        }.getOrNull()
        return parsed ?: raw.take(300)
    }

    private fun fail(kind: LiveErrorKind, message: String) {
        Log.w(TAG, "Сессия остановлена: $kind — $message")
        _state.value = LiveSessionState.Error(kind, message)
        emit(LiveEvent.Failed(kind, message))
    }

    private fun emit(event: LiveEvent) {
        if (!_events.tryEmit(event)) {
            scope.launch { _events.emit(event) }
        }
    }

    private companion object {
        const val TAG = "LiveSession"
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_BASE_MS = 1_000L
        const val PRE_ROLL_CHUNKS = 3
    }
}
