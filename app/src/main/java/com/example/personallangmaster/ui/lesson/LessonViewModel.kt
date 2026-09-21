package com.example.personallangmaster.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.ai.live.FunctionCall
import com.example.personallangmaster.ai.live.LiveErrorKind
import com.example.personallangmaster.ai.live.LiveEvent
import com.example.personallangmaster.ai.live.LiveSession
import com.example.personallangmaster.ai.live.LiveSessionConfig
import com.example.personallangmaster.ai.live.LessonAudioSink
import com.example.personallangmaster.ai.live.LiveSessionState
import com.example.personallangmaster.ai.live.LiveTools
import com.example.personallangmaster.ai.live.LiveTools.int
import com.example.personallangmaster.ai.live.LiveTools.string
import com.example.personallangmaster.ai.prompt.TutorContext
import com.example.personallangmaster.ai.prompt.TutorPromptBuilder
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.core.cost.PricingTable
import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.domain.BudgetAction
import com.example.personallangmaster.domain.BudgetPolicy
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.AudioRecording
import com.example.personallangmaster.data.prefs.MicMode
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.LessonRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/** Реплика в ленте субтитров. */
data class SubtitleItem(
    val speaker: Speaker,
    val text: String,
    val isPartial: Boolean,
)

/** Карточка поправки, показанная поверх субтитров. */
data class CorrectionItem(
    val id: Long,
    val original: String,
    val corrected: String,
    val explanation: String?,
)

/** Карточка-подсказка, которую тренер вывел вызовом `show_card`. */
data class HintCard(
    val textEn: String,
    val textRu: String?,
)

/**
 * Что показать перед тестом уровня.
 *
 * Тест переставляет уровень всего профиля, поэтому запускать его молча,
 * одним нажатием, неправильно: человек должен понимать, что сейчас будет
 * и что изменится после.
 */
data class PlacementPrompt(
    val currentLevel: com.example.personallangmaster.data.db.Cefr,
    /** Уровень зафиксирован в настройках — результат покажем, но не применим. */
    val levelLocked: Boolean,
    /** Тест уже проходили: значит это пересдача. */
    val alreadyPassed: Boolean,
    val minutes: Int,
    val estimatedCostUsd: Double,
)

data class LessonUiState(
    val sessionState: LiveSessionState = LiveSessionState.Idle,
    val mode: LessonMode = LessonMode.FREE_TALK,
    val micMode: MicMode = MicMode.HOLD,
    /** Сценарий, выбранный до начала урока: показывается на панели запуска. */
    val scenario: com.example.personallangmaster.data.db.entity.ScenarioEntity? = null,
    val subtitles: List<SubtitleItem> = emptyList(),
    val corrections: List<CorrectionItem> = emptyList(),
    val hints: List<HintCard> = emptyList(),
    val elapsedSeconds: Int = 0,
    val micLevelDbfs: Double = -100.0,
    val tutorLevelDbfs: Double = -100.0,
    val costUsd: Double = 0.0,
    val chatMode: Boolean = false,
    val draft: String = "",
    val budgetWarning: String? = null,
    /** Открыто объяснение теста уровня; null — диалога нет. */
    val placement: PlacementPrompt? = null,
    val finished: Boolean = false,
) {
    val isActive: Boolean
        get() = sessionState !is LiveSessionState.Idle &&
            sessionState !is LiveSessionState.Closed &&
            sessionState !is LiveSessionState.Error

    val canTalk: Boolean
        get() = sessionState is LiveSessionState.Ready ||
            sessionState is LiveSessionState.Speaking ||
            sessionState is LiveSessionState.Thinking
}

/**
 * Экран урока: связывает живую сессию, базу и интерфейс.
 *
 * Сессия отдаёт поток событий, а ViewModel решает, что из этого показать
 * и что сохранить. Всё, что приходит от тренера через инструменты, обрабатывается
 * мгновенно и без подтверждений — иначе рвётся темп разговора.
 */
class LessonViewModel(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val lessonRepository: LessonRepository,
    private val statsRepository: com.example.personallangmaster.data.repo.StatsRepository,
    private val audioFileStore: com.example.personallangmaster.core.audio.AudioFileStore,
    private val tts: com.example.personallangmaster.core.speech.TtsController,
    /** Ставит законченный урок в очередь на разбор — он не должен теряться. */
    private val analysisScheduler: com.example.personallangmaster.domain.AnalysisScheduler,
    /** Живёт дольше экрана: в нём дописывается урок, если модель успели очистить. */
    private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    private val session = LiveSession(viewModelScope)

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private var lessonId: Long? = null

    /** Идентификатор последнего урока — по нему открывается разбор. */
    var lastLessonId: Long? = null
        private set
    private var profileId: Long? = null
    private var settings: AppSettings = AppSettings()
    private var startedAtMillis = 0L
    private var timerJob: Job? = null

    private var tokensIn = 0L
    private var tokensOut = 0L
    private val transcript = mutableListOf<LessonRepository.TurnRecord>()
    private var pendingTutorText = StringBuilder()

    /**
     * Расшифровка речи ученика приходит кусками, а `turnComplete` — только
     * вместе с ответом тренера. Поэтому реплику копим здесь и закрываем сами:
     * иначе она навсегда остаётся «частичной» и не попадает ни в транскрипт,
     * ни в разбор урока.
     */
    private var pendingUserText = StringBuilder()

    /** Где в записи началась текущая реплика. */
    private var turnAudioStartMs = 0L

    init {
        session.state
            .onEach { live -> _state.update { it.copy(sessionState = live) } }
            .launchIn(viewModelScope)

        session.micLevelDbfs
            .onEach { level -> _state.update { it.copy(micLevelDbfs = level) } }
            .launchIn(viewModelScope)

        session.tutorLevelDbfs
            .onEach { level -> _state.update { it.copy(tutorLevelDbfs = level) } }
            .launchIn(viewModelScope)

        session.events
            .onEach(::handleEvent)
            .launchIn(viewModelScope)
    }

    /**
     * Объяснение перед тестом уровня: сколько длится, что будет с уровнем
     * и сколько это стоит. Сам тест запускается уже из диалога.
     */
    fun requestPlacement() {
        viewModelScope.launch {
            val profile = profileRepository.current()
            _state.update {
                it.copy(
                    placement = PlacementPrompt(
                        currentLevel = profile?.cefrOverall
                            ?: com.example.personallangmaster.data.db.Cefr.A1,
                        levelLocked = profile?.levelLocked == true,
                        alreadyPassed = profile?.createdFromPlacement == true,
                        minutes = PLACEMENT_MINUTES,
                        estimatedCostUsd = CostCalculator.estimateLessonCostUsd(
                            PLACEMENT_MINUTES.toDouble()
                        ),
                    )
                )
            }
        }
    }

    fun dismissPlacement() = _state.update { it.copy(placement = null) }

    /** Запуск урока. Бюджет проверяется до подключения, чтобы не тратить ни токена зря. */
    fun startLesson(mode: LessonMode = LessonMode.FREE_TALK, scenarioId: String? = null) {
        _state.update { it.copy(placement = null) }
        viewModelScope.launch {
            settings = settingsRepository.current()
            val profile = profileRepository.current()
            profileId = profile?.id

            val apiKey = settingsRepository.apiKey()
            if (apiKey.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        sessionState = LiveSessionState.Error(
                            LiveErrorKind.NO_KEY,
                            "Ключ Gemini не задан — откройте настройки",
                        )
                    )
                }
                return@launch
            }

            val spentToday = profile?.id?.let {
                lessonRepository.spentTodayUsd(it, startOfToday())
            } ?: 0.0

            val spentMonth = profile?.id?.let {
                lessonRepository.spentTodayUsd(it, startOfMonth())
            } ?: 0.0

            val decision = BudgetPolicy.beforeLesson(
                spentTodayUsd = spentToday,
                spentMonthUsd = spentMonth,
                dailyLimitUsd = settings.dailyLimitUsd,
                monthlyLimitUsd = settings.monthlyLimitUsd,
                behavior = settings.limitBehavior,
            )

            if (decision.action == BudgetAction.BLOCK) {
                _state.update {
                    it.copy(
                        sessionState = LiveSessionState.Error(
                            LiveErrorKind.BUDGET_LIMIT,
                            decision.reason.orEmpty(),
                        )
                    )
                }
                return@launch
            }
            if (decision.action == BudgetAction.WARN) {
                _state.update { it.copy(budgetWarning = decision.reason) }
            }

            val scenario = scenarioId?.let { lessonRepository.scenario(it) }
            val memory = profile?.id?.let { lessonRepository.tutorMemory(it, weekAgo()) }

            val prompt = TutorPromptBuilder.build(
                TutorContext(
                    profile = profile,
                    settings = settings,
                    mode = mode,
                    scenario = scenario,
                    recentSummaries = memory?.summaries.orEmpty(),
                    recurringMistakes = memory?.mistakes.orEmpty(),
                    weakPhonemes = memory?.phonemes.orEmpty(),
                    activeVocabulary = memory?.vocabulary.orEmpty(),
                )
            )

            lessonId = profile?.id?.let { id ->
                lessonRepository.startLesson(
                    profileId = id,
                    mode = mode,
                    scenarioId = scenarioId,
                    personaId = settings.personaId,
                    strictness = settings.strictness,
                    modelId = settings.liveModelId,
                )
            }

            transcript.clear()
            tokensIn = 0
            tokensOut = 0
            startedAtMillis = System.currentTimeMillis()
            turnAudioStartMs = 0

            val recordingLessonId = lessonId
            if (settings.audioRecording != AudioRecording.NONE && recordingLessonId != null) {
                audioFileStore.start(recordingLessonId)
                session.audioSink = object : LessonAudioSink {
                    override fun onUserPcm(pcm16k: ByteArray) = audioFileStore.writeUser(pcm16k)

                    override fun onTutorPcm(pcm24k: ByteArray) {
                        // Режим «только свою речь»: тренера в файл не пишем.
                        if (settings.audioRecording == AudioRecording.FULL) {
                            audioFileStore.writeTutor(pcm24k)
                        }
                    }
                }
            } else {
                session.audioSink = null
            }

            _state.update {
                LessonUiState(
                    mode = mode,
                    micMode = settings.micMode,
                    sessionState = LiveSessionState.Connecting,
                    chatMode = it.chatMode,
                )
            }

            session.start(
                LiveSessionConfig(
                    apiKey = apiKey,
                    model = settings.liveModelId,
                    systemInstruction = prompt,
                    voiceName = settings.voiceName,
                    temperature = settings.temperature,
                    transcription = settings.transcriptionEnabled,
                    contextCompression = settings.contextCompression,
                    sessionResumption = settings.sessionResumption,
                    manualActivity = settings.micMode != MicMode.HANDS_FREE,
                    autoEndOnSilence = settings.micMode == MicMode.TAP,
                    bargeInEnabled = settings.bargeInEnabled,
                    noiseSuppression = settings.noiseSuppression,
                    vadThresholdDb = settings.vadThresholdDb,
                    silenceHangoverMs = settings.silenceHangoverMs,
                    tutorVolume = settings.tutorVolume,
                )
            )

            startTimer()
        }
    }

    // --- Микрофон ---

    fun onMicPress() = session.startTalking()

    fun onMicRelease() = session.stopTalking()

    /** Тап-режим: одно нажатие начинает реплику, второе заканчивает. */
    fun onMicTap() {
        if (_state.value.sessionState is LiveSessionState.Listening) {
            session.stopTalking()
        } else {
            session.startTalking()
        }
    }

    // --- Текст ---

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun toggleChatMode() = _state.update { it.copy(chatMode = !it.chatMode) }

    fun sendDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        session.sendText(text)
        addSubtitle(Speaker.USER, text, isPartial = false)
        _state.update { it.copy(draft = "") }
    }

    // --- Карточки ---

    /**
     * Карточка исчезает сама.
     *
     * Во время разговора некогда закрывать всплывшие поправки руками, а
     * висящая карточка закрывает субтитры — поэтому она живёт несколько секунд
     * и уходит. Всё равно все ошибки целиком собраны в разборе урока.
     */
    private fun autoDismiss(item: CorrectionItem) {
        viewModelScope.launch {
            delay(CORRECTION_VISIBLE_MS)
            dismissCorrection(item.id)
        }
    }

    /** Проговаривает правильный вариант системным голосом — бесплатно и сразу. */
    fun speakCorrection(text: String) = tts.speak(text)

    fun dismissCorrection(id: Long) = _state.update { current ->
        current.copy(corrections = current.corrections.filterNot { it.id == id })
    }

    fun dismissHint(card: HintCard) = _state.update { current ->
        current.copy(hints = current.hints - card)
    }

    fun addCorrectionToVocab(item: CorrectionItem) {
        val profile = profileId ?: return
        viewModelScope.launch {
            lessonRepository.saveVocab(
                profileId = profile,
                lessonId = lessonId,
                term = item.corrected,
                translationRu = item.explanation.orEmpty(),
                example = null,
            )
            dismissCorrection(item.id)
        }
    }

    // --- Завершение ---

    fun endLesson() {
        timerJob?.cancel()
        // Последняя реплика часто остаётся незакрытой: урок обрывают в середине хода.
        finalizeUserTurn()
        finalizeTutorTurn()
        session.stop()

        val profile = profileId
        val lesson = lessonId
        val elapsed = _state.value.elapsedSeconds
        lastLessonId = lesson

        val audioPath = audioFileStore.finish()
        session.audioSink = null

        // Дописываем в живущей дольше экрана области: пользователь может уйти
        // с экрана сразу после нажатия «Завершить».
        appScope.launch {
            if (lesson != null) {
                lessonRepository.finishLesson(
                    lessonId = lesson,
                    durationSec = elapsed,
                    userSpeakSec = 0,
                    aiSpeakSec = 0,
                    tokensIn = tokensIn,
                    tokensOut = tokensOut,
                    costUsd = _state.value.costUsd,
                    audioPath = audioPath,
                )
            }
            if (profile != null && lesson != null && (tokensIn > 0 || tokensOut > 0)) {
                val pricing = settings.toPricingTable()
                lessonRepository.logUsage(
                    profileId = profile,
                    lessonId = lesson,
                    promptTokens = tokensIn,
                    responseTokens = tokensOut,
                    promptCostUsd = CostCalculator.costUsd(tokensIn, pricing.audioInputPerMTok),
                    responseCostUsd = CostCalculator.costUsd(tokensOut, pricing.audioOutputPerMTok),
                )
            }
            if (profile != null && elapsed > 0) {
                statsRepository.recordLesson(
                    profileId = profile,
                    minutes = elapsed / 60.0,
                    costUsd = _state.value.costUsd,
                    goalMinutes = profileRepository.current()?.dailyGoalMinutes ?: 0,
                )
            }

            // Разбор запускается сам: если ждать открытия экрана разбора,
            // урок, после которого приложение просто закрыли, пропадает зря.
            if (lesson != null && hasSomethingToAnalyze) {
                analysisScheduler.schedule(lesson)
            }

            _state.update { it.copy(finished = true) }
        }
    }

    /**
     * Есть ли что разбирать. Разбор пустого урока — потраченный впустую вызов
     * модели; при выключенном хранении транскрипта разбирать нечего в принципе,
     * потому что реплики в базу не попадают.
     */
    private val hasSomethingToAnalyze: Boolean
        get() = settings.transcriptRetentionAllowed &&
            transcript.any { it.speaker == Speaker.USER }

    /** Загружает сценарий, выбранный в каталоге, чтобы показать его перед стартом. */
    fun prepareScenario(scenarioId: String?) {
        if (scenarioId == null) {
            _state.update { it.copy(scenario = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(scenario = lessonRepository.scenario(scenarioId)) }
        }
    }

    /** Сброс экрана к панели запуска: история прошлого урока уже сохранена в базе. */
    fun resetForNewLesson() {
        transcript.clear()
        pendingTutorText = StringBuilder()
        pendingUserText = StringBuilder()
        tokensIn = 0
        tokensOut = 0
        _state.value = LessonUiState(
            chatMode = _state.value.chatMode,
            micMode = _state.value.micMode,
            scenario = _state.value.scenario,
        )
    }

    /**
     * Экран уничтожен вместе с моделью — например, система убила приложение
     * в фоне. Урок при этом уже наполовину прожит, поэтому закрываем его
     * в живущей дольше области, а не теряем.
     */
    override fun onCleared() {
        timerJob?.cancel()
        tts.stop()
        session.stop()

        val lesson = lessonId
        val elapsed = _state.value.elapsedSeconds
        val cost = _state.value.costUsd
        val audioPath = audioFileStore.finish()

        val analysable = hasSomethingToAnalyze
        if (lesson != null && !_state.value.finished) {
            appScope.launch {
                lessonRepository.finishLesson(
                    lessonId = lesson,
                    durationSec = elapsed,
                    userSpeakSec = 0,
                    aiSpeakSec = 0,
                    tokensIn = tokensIn,
                    tokensOut = tokensOut,
                    costUsd = cost,
                    audioPath = audioPath,
                )
                if (analysable) analysisScheduler.schedule(lesson)
            }
        }
        super.onCleared()
    }

    // --- Внутреннее ---

    private fun handleEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.UserTranscript -> {
                pendingUserText.append(event.text)
                addSubtitle(Speaker.USER, pendingUserText.toString(), isPartial = !event.isFinal)
                if (event.isFinal) finalizeUserTurn()
            }

            is LiveEvent.TutorTranscript -> {
                // Заговорил тренер — значит, реплика ученика закончилась.
                finalizeUserTurn()
                pendingTutorText.append(event.text)
                addSubtitle(Speaker.TUTOR, pendingTutorText.toString(), isPartial = true)
            }

            LiveEvent.TurnComplete -> {
                finalizeUserTurn()
                finalizeTutorTurn()
            }

            LiveEvent.Interrupted -> {
                pendingTutorText = StringBuilder()
            }

            is LiveEvent.Usage -> {
                tokensIn += event.promptTokens
                tokensOut += event.responseTokens
                val pricing = settings.toPricingTable()
                val cost = CostCalculator.costUsd(tokensIn, pricing.audioInputPerMTok) +
                    CostCalculator.costUsd(tokensOut, pricing.audioOutputPerMTok)
                _state.update { it.copy(costUsd = cost) }
                checkBudget(cost)
            }

            is LiveEvent.Tool -> handleTool(event.call)

            is LiveEvent.GoingAway -> _state.update {
                it.copy(budgetWarning = "Соединение скоро обновится — урок продолжится")
            }

            is LiveEvent.Failed -> timerJob?.cancel()
        }
    }

    private fun handleTool(call: FunctionCall) {
        val profile = profileId
        viewModelScope.launch {
            when (call.name) {
                LiveTools.SAVE_VOCAB -> {
                    val term = call.string("term")
                    if (profile != null && term != null) {
                        lessonRepository.saveVocab(
                            profileId = profile,
                            lessonId = lessonId,
                            term = term,
                            translationRu = call.string("translation_ru").orEmpty(),
                            example = call.string("example"),
                        )
                    }
                }

                LiveTools.LOG_MISTAKE -> {
                    val original = call.string("original")
                    val corrected = call.string("corrected")
                    if (profile != null && original != null && corrected != null) {
                        val id = lessonRepository.logMistake(
                            profileId = profile,
                            lessonId = lessonId,
                            type = call.string("type"),
                            original = original,
                            corrected = corrected,
                            explanation = call.string("explanation"),
                            grammarTopic = call.string("grammar_topic"),
                            phoneme = call.string("phoneme"),
                            severity = call.int("severity"),
                        )
                        val item = CorrectionItem(
                            id = id,
                            original = original,
                            corrected = corrected,
                            // Длинное объяснение читать некогда: разговор идёт.
                            explanation = call.string("explanation")?.take(EXPLANATION_LIMIT),
                        )
                        _state.update { current ->
                            current.copy(
                                corrections = (current.corrections + item)
                                    .takeLast(MAX_VISIBLE_CORRECTIONS)
                            )
                        }
                        autoDismiss(item)
                    }
                }

                LiveTools.SHOW_CARD -> {
                    call.string("text_en")?.let { textEn ->
                        _state.update { current ->
                            current.copy(
                                hints = (current.hints + HintCard(textEn, call.string("text_ru")))
                                    .takeLast(MAX_VISIBLE_HINTS)
                            )
                        }
                    }
                }

                // set_difficulty и suggest_drill пока только подтверждаем:
                // их эффект появится вместе с разбором урока.
                else -> Unit
            }

            session.respondToTool(call)
        }
    }

    /** Закрывает реплику ученика: она уходит в субтитры и в транскрипт. */
    private fun finalizeUserTurn() {
        if (pendingUserText.isBlank()) return
        val text = pendingUserText.toString().trim()
        pendingUserText = StringBuilder()
        addSubtitle(Speaker.USER, text, isPartial = false)
    }

    private fun finalizeTutorTurn() {
        if (pendingTutorText.isEmpty()) return
        val text = pendingTutorText.toString().trim()
        pendingTutorText = StringBuilder()
        addSubtitle(Speaker.TUTOR, text, isPartial = false)
    }

    private fun addSubtitle(speaker: Speaker, text: String, isPartial: Boolean) {
        if (text.isBlank()) return
        _state.update { current ->
            val items = current.subtitles.toMutableList()
            val last = items.lastOrNull()
            if (last != null && last.speaker == speaker && last.isPartial) {
                items[items.lastIndex] = SubtitleItem(speaker, text, isPartial)
            } else {
                items += SubtitleItem(speaker, text, isPartial)
            }
            current.copy(subtitles = items.takeLast(MAX_SUBTITLES))
        }

        if (!isPartial) {
            val record = LessonRepository.TurnRecord(
                speaker = speaker,
                text = text,
                startMs = elapsedMillis(),
                // Смещение в записи запоминаем в момент закрытия реплики: по нему
                // разбор урока потом находит нужное место в аудио.
                audioOffsetMs = turnAudioStartMs.takeIf { audioFileStore.isRecording },
            )
            transcript += record
            turnAudioStartMs = audioFileStore.positionMs

            // Пишем реплику сразу: если процесс убьют, разговор не пропадёт.
            val lesson = lessonId
            if (lesson != null && settings.transcriptRetentionAllowed) {
                val index = transcript.size - 1
                appScope.launch { lessonRepository.appendTurn(lesson, index, record) }
            }
        }
    }

    /**
     * Реакция на приближение к лимиту.
     *
     * Урок обрывается только если пользователь сам выбрал такое поведение;
     * правило целиком живёт в [BudgetPolicy] и покрыто тестами.
     */
    private fun checkBudget(cost: Double) {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = cost,
            dailyLimitUsd = settings.dailyLimitUsd,
            behavior = settings.limitBehavior,
        )

        if (decision.action == BudgetAction.ALLOW) return

        _state.update { it.copy(budgetWarning = decision.reason) }
        if (decision.action == BudgetAction.BLOCK) endLesson()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val seconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt()
                _state.update { it.copy(elapsedSeconds = seconds) }

                // Тест уровня не зависит от длины обычного урока: пять минут
                // хватает, чтобы оценить речь, а дальше это уже просто разговор.
                val limit = if (_state.value.mode == LessonMode.PLACEMENT) {
                    PLACEMENT_MINUTES
                } else {
                    settings.lessonMinutes
                }
                if (limit > 0 && seconds >= limit * 60 + LESSON_GRACE_SECONDS) {
                    endLesson()
                }
            }
        }
    }

    private fun elapsedMillis(): Long = System.currentTimeMillis() - startedAtMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun weekAgo(): Long = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

    companion object {
        private const val MAX_SUBTITLES = 50
        private const val MAX_VISIBLE_CORRECTIONS = 1
        private const val CORRECTION_VISIBLE_MS = 12_000L
        private const val EXPLANATION_LIMIT = 90
        private const val MAX_VISIBLE_HINTS = 2
        private const val LESSON_GRACE_SECONDS = 60
        private const val PLACEMENT_MINUTES = 5

        fun factory(
            settingsRepository: SettingsRepository,
            profileRepository: ProfileRepository,
            lessonRepository: LessonRepository,
            statsRepository: com.example.personallangmaster.data.repo.StatsRepository,
            audioFileStore: com.example.personallangmaster.core.audio.AudioFileStore,
            tts: com.example.personallangmaster.core.speech.TtsController,
            analysisScheduler: com.example.personallangmaster.domain.AnalysisScheduler,
            appScope: kotlinx.coroutines.CoroutineScope,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LessonViewModel(
                settingsRepository, profileRepository, lessonRepository, statsRepository,
                audioFileStore, tts, analysisScheduler, appScope,
            ) as T
        }
    }
}

/** Тарифы берём из настроек: пользователь может их поправить. */
private fun AppSettings.toPricingTable() = PricingTable(
    textInputPerMTok = priceTextInPerMTok,
    textOutputPerMTok = priceTextOutPerMTok,
    audioInputPerMTok = priceAudioInPerMTok,
    audioOutputPerMTok = priceAudioOutPerMTok,
)

/** Хранить ли транскрипт этого урока. */
private val AppSettings.transcriptRetentionAllowed: Boolean
    get() = transcriptRetention != com.example.personallangmaster.data.prefs.TranscriptRetention.NEVER
