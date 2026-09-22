package com.example.personallangmaster.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.dao.LessonDao

import com.example.personallangmaster.data.db.dao.UsageByKind
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.repo.DeleteScope
import com.example.personallangmaster.data.repo.LessonHistoryRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.StatsRepository
import com.example.personallangmaster.data.repo.VocabRepository
import com.example.personallangmaster.domain.LessonHousekeeping
import com.example.personallangmaster.domain.LessonSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Сколько ошибок одного типа накопилось за период. */
data class MistakeTotal(
    val type: MistakeType,
    val total: Int,
)

/** Какие уроки показывать: все или только в определённом состоянии. */
enum class LessonFilter { ALL, ANALYZED, NOT_ANALYZED }

/**
 * Сообщение внизу экрана. Отменять можно только удаление — пока оно не выполнено.
 *
 * [token] — счётчик, растущий на каждое новое сообщение. Без него два одинаковых
 * по содержимому сообщения (например, «Урок удалён» после двух разных удалений)
 * не различить как data class: `LaunchedEffect(state.message)` не перезапустится
 * на равном значении, второй снекбар не покажется, а «Отменить» у первого будет
 * отменять уже не тот урок.
 */
data class HistoryMessage(val text: String, val undoable: Boolean = false, val token: Long = 0L)

/** Открытый диалог пометки вместе с тем, что в нём уже написано. */
data class NoteRequest(val lessonId: Long, val text: String)

data class ProgressUiState(
    val days: List<DayBar> = emptyList(),
    val minutesWeek: Int = 0,
    val lessonsWeek: Int = 0,
    val vocabByState: Map<VocabState, Int> = emptyMap(),
    val topMistakes: List<MistakeTotal> = emptyList(),
    val spentTodayUsd: Double = 0.0,
    val spentMonthUsd: Double = 0.0,
    val usageBreakdown: List<UsageByKind> = emptyList(),
    val recentLessons: List<LessonEntity> = emptyList(),
    val lessonsTotal: Int = 0,
    val month: YearMonth = YearMonth.now(),
    val calendar: List<CalendarDay> = emptyList(),
    val selectedDay: LocalDate? = null,
    val filter: LessonFilter = LessonFilter.ALL,
    val selection: LessonSelection = LessonSelection(),
    val note: NoteRequest? = null,
    val message: HistoryMessage? = null,
    val shareText: String? = null,
    /** Размер записи по уроку. Урока нет в карте — значит записи у него нет. */
    val recordingSizes: Map<Long, Long> = emptyMap(),
    /** Сколько несостоявшихся уроков в месяце — счётчик в пункте меню. */
    val failedCount: Int = 0,
) {
    /** Вперёд дальше текущего месяца ходить некуда: будущих уроков не бывает. */
    val canGoForward: Boolean get() = month < YearMonth.now()
    val recordingBytes: Long get() = recordingSizes.values.sum()
    val recordingLessons: Int get() = recordingSizes.size
}

/**
 * Прогресс: дни, словарь, ошибки и расходы.
 *
 * Показываем только то, что можно честно измерить: минуты, карточки, число
 * ошибок и потраченные деньги. Никаких синтетических «баллов прогресса».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
    private val vocabRepository: VocabRepository,
    private val lessonDao: LessonDao,
    private val history: LessonHistoryRepository,
    /**
     * Область, переживающая экран: отложенное удаление должно довестись до конца,
     * даже если человек сразу ушёл с экрана.
     */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    /** Уроки показываемого месяца; на экран попадает их отфильтрованная часть. */
    private var allLessons: List<LessonEntity> = emptyList()

    private val month = MutableStateFlow(YearMonth.now())

    /** Уроки, которые уже исчезли с экрана, но ещё не удалены из базы. */
    private var hidden: Set<Long> = emptySet()

    private var pendingDelete: PendingDelete? = null
    private var pendingJob: Job? = null

    /** Счётчик для [HistoryMessage.token] — см. его kdoc. */
    private var messageToken = 0L

    private fun historyMessage(text: String, undoable: Boolean = false): HistoryMessage {
        messageToken += 1
        return HistoryMessage(text, undoable, messageToken)
    }

    private data class PendingDelete(val ids: List<Long>, val scope: DeleteScope)

    init {
        val profiles = profileRepository.activeProfile.filterNotNull()

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeRange(profile.id, LocalDate.now().toEpochDay() - DAYS + 1)
            }
            .onEach { stats ->
                val today = LocalDate.now()
                val byDay = stats.associateBy { it.epochDay }

                val bars = (0 until DAYS).map { offset ->
                    val date = today.minusDays((DAYS - 1 - offset).toLong())
                    val stat = byDay[date.toEpochDay()]
                    DayBar(
                        label = date.dayOfWeek
                            .getDisplayName(TextStyle.SHORT, Locale("ru"))
                            .replaceFirstChar(Char::uppercase),
                        minutes = stat?.minutesSpoken ?: 0.0,
                        isToday = date == today,
                    )
                }

                _state.update {
                    it.copy(
                        days = bars,
                        minutesWeek = stats.sumOf { stat -> stat.minutesSpoken }.toInt(),
                        lessonsWeek = stats.sumOf { stat -> stat.lessonsCount },
                    )
                }
            }
            .launchIn(viewModelScope)

        VocabState.entries.forEach { state ->
            profiles
                .flatMapLatest { profile -> vocabRepository.observeCountByState(profile.id, state) }
                .onEach { count ->
                    _state.update { current ->
                        current.copy(vocabByState = current.vocabByState + (state to count))
                    }
                }
                .launchIn(viewModelScope)
        }

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeCostSince(profile.id, StatsRepository.startOfToday())
            }
            .onEach { spent -> _state.update { it.copy(spentTodayUsd = spent) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeCostSince(profile.id, StatsRepository.startOfMonth())
            }
            .onEach { spent -> _state.update { it.copy(spentMonthUsd = spent) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeUsageByKind(profile.id, StatsRepository.startOfMonth())
            }
            .onEach { usage -> _state.update { it.copy(usageBreakdown = usage) } }
            .launchIn(viewModelScope)

        combine(profiles, month) { profile, month -> profile to month }
            .flatMapLatest { (profile, month) ->
                lessonDao.observeBetween(profile.id, startOf(month), startOf(month.plusMonths(1)))
            }
            .onEach { lessons ->
                // Урок в состоянии ACTIVE — это либо идущий прямо сейчас разговор,
                // либо след убитого процесса: в истории ему делать нечего.
                allLessons = lessons.filter { it.status != LessonStatus.ACTIVE }
                applyFilters()
            }
            .launchIn(viewModelScope)

        profiles
            .onEach { profile -> refreshLessons(profile.id) }
            .launchIn(viewModelScope)
    }

    fun setFilter(filter: LessonFilter) {
        _state.update { it.copy(filter = filter) }
        applyFilters()
    }

    /** Сдвиг по месяцам: выбранный день теряет смысл в новом месяце. */
    fun shiftMonth(months: Long) {
        val target = month.value.plusMonths(months)
        if (target > YearMonth.now()) return
        month.value = target
        _state.update { it.copy(month = target, selectedDay = null) }
        applyFilters()
    }

    /** Возврат к текущему месяцу без выбранного дня. */
    fun goToToday() {
        val target = YearMonth.now()
        month.value = target
        _state.update { it.copy(month = target, selectedDay = null) }
        applyFilters()
    }

    /** Повторный тап по дню снимает выбор и возвращает список за весь месяц. */
    fun selectDay(date: LocalDate) {
        _state.update { it.copy(selectedDay = if (it.selectedDay == date) null else date) }
        applyFilters()
    }

    // --- Выбор нескольких уроков ---

    fun startSelection(lessonId: Long) {
        _state.update { it.copy(selection = it.selection.start(lessonId)) }
    }

    fun toggleSelection(lessonId: Long) {
        _state.update { it.copy(selection = it.selection.toggle(lessonId)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = it.selection.clear()) }
    }

    // --- Массовые операции ---

    /**
     * Отбор идёт по всем урокам месяца, а не по тому, что осталось после фильтра
     * и выбранного дня: пункт меню обещает «все», и обмануть здесь легко.
     */
    fun failedLessonIds(): List<Long> =
        LessonHousekeeping.failed(allLessons.filterNot { it.id in hidden }).map { it.id }

    fun recordingLessonIds(): List<Long> = _state.value.recordingSizes.keys.toList()

    // --- Удаление ---

    /**
     * Прячет уроки сразу, а удаляет через паузу: несколько секунд на «Отменить»
     * стоят дешевле, чем безвозвратно потерянный разговор.
     */
    fun deleteLessons(ids: List<Long>, scope: DeleteScope) {
        if (ids.isEmpty()) return

        // Одно отложенное удаление за раз: второе подтверждение означает,
        // что первое человек уже не отменит. Отменяем и сам таймер первого —
        // иначе он всё равно долетит и подтвердит уже второе удаление раньше срока.
        pendingJob?.cancel()
        commitPendingDelete()

        hidden = hidden + ids
        pendingDelete = PendingDelete(ids, scope)
        // Main.immediate — чтобы таймер трогал pendingDelete/pendingJob с того же
        // потока, что deleteLessons/onCleared: без этого гонка между потоками
        // может удвоить удаление одних и тех же уроков.
        pendingJob = appScope.launch(Dispatchers.Main.immediate) {
            delay(UNDO_MILLIS)
            commitPendingDelete()
        }

        _state.update {
            it.copy(
                selection = it.selection.clear(),
                message = historyMessage(
                    text = if (ids.size == 1) "Урок удалён" else "Удалено уроков: ${ids.size}",
                    undoable = true,
                ),
            )
        }
        applyFilters()
    }

    fun undoDelete() {
        pendingJob?.cancel()
        pendingJob = null

        val restored = pendingDelete?.ids.orEmpty()
        pendingDelete = null
        hidden = hidden - restored.toSet()

        _state.update { it.copy(message = null) }
        applyFilters()
    }

    /** Доводит отложенное удаление до базы. Вызывается по таймеру и при уходе с экрана. */
    private fun commitPendingDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        // Урок закоммичен — держать его id в hidden больше незачем: без этого
        // набор рос бы всю жизнь экрана.
        hidden = hidden - pending.ids.toSet()
        appScope.launch { history.delete(pending.ids, pending.scope) }
    }

    fun deleteRecordings(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val freed = history.deleteRecordings(ids)
            _state.update {
                it.copy(
                    selection = it.selection.clear(),
                    message = historyMessage(
                        "Освободилось ${LessonHousekeeping.formatSize(freed)}"
                    ),
                )
            }
        }
    }

    // --- Состояние и пометка ---

    fun markSkipped(lessonId: Long) {
        viewModelScope.launch {
            history.markSkipped(lessonId)
            _state.update { it.copy(message = historyMessage("Урок закрыт без разбора")) }
        }
    }

    fun openNote(lessonId: Long) {
        val lesson = allLessons.firstOrNull { it.id == lessonId } ?: return
        _state.update { it.copy(note = NoteRequest(lessonId, lesson.note.orEmpty())) }
    }

    fun saveNote(lessonId: Long, text: String) {
        viewModelScope.launch {
            history.setNote(lessonId, text)
            _state.update { it.copy(note = null) }
        }
    }

    fun dismissNote() {
        _state.update { it.copy(note = null) }
    }

    // --- Отправка транскрипта ---

    fun requestShare(lessonId: Long) {
        viewModelScope.launch {
            val text = history.transcript(lessonId)
            _state.update {
                if (text.isBlank()) {
                    it.copy(message = historyMessage("Транскрипт этого урока не сохранился"))
                } else {
                    it.copy(shareText = text)
                }
            }
        }
    }

    fun consumeShare() {
        _state.update { it.copy(shareText = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        // Экран закрывают — ждать отмены больше негде.
        pendingJob?.cancel()
        commitPendingDelete()
        super.onCleared()
    }

    private fun applyFilters() {
        val current = _state.value
        val filter = current.filter
        val selectedDay = current.selectedDay

        // Урок, удаление которого ещё можно отменить, с экрана уже пропал:
        // иначе кнопка «Отменить» не имеет видимого смысла.
        val lessons = allLessons.filterNot { it.id in hidden }

        val visible = lessons
            .filter { selectedDay == null || LessonCalendar.lessonDate(it) == selectedDay }
            .filter { lesson ->
                when (filter) {
                    LessonFilter.ALL -> true
                    LessonFilter.ANALYZED -> lesson.status == LessonStatus.ANALYZED
                    // «Без разбора» — это те, по кому решение ещё не принято.
                    // Закрытые вручную сюда не попадают: решение по ним уже есть.
                    LessonFilter.NOT_ANALYZED -> LessonHousekeeping.needsAnalysis(lesson) ||
                        lesson.status == LessonStatus.FAILED
                }
            }

        val withRecording = LessonHousekeeping.withRecording(lessons)

        _state.update {
            it.copy(
                recentLessons = visible,
                lessonsTotal = lessons.size,
                calendar = LessonCalendar.buildMonth(it.month, lessons, LocalDate.now()),
                selection = it.selection.retain(lessons.map { lesson -> lesson.id }.toSet()),
                failedCount = LessonHousekeeping.failed(lessons).size,
            )
        }

        refreshRecordingSizes(withRecording)
    }

    /** Размеры записей читаются с диска, поэтому считаются отдельно от раскладки списка. */
    private fun refreshRecordingSizes(withRecording: List<LessonEntity>) {
        viewModelScope.launch {
            val sizes = history.recordingSizes(withRecording)
            _state.update { it.copy(recordingSizes = sizes) }
        }
    }

    private fun startOf(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun refreshLessons(profileId: Long) {
        viewModelScope.launch {
            val month = System.currentTimeMillis() - 30 * StatsRepository.DAY_MILLIS
            _state.update {
                it.copy(
                    // Запрос группирует ещё и по теме, поэтому один тип приходит
                    // несколькими строками — складываем их, прежде чем показывать.
                    topMistakes = lessonDao.topMistakes(profileId, month, limit = 50)
                        .groupBy { frequency -> frequency.type }
                        .map { (type, rows) -> MistakeTotal(type, rows.sumOf { it.total }) }
                        .sortedByDescending { it.total }
                        .take(5),
                )
            }
        }
    }

    companion object {
        private const val DAYS = 7

        /**
         * Сколько времени у человека есть на «Отменить».
         *
         * Не private: экран показывает снекбар ровно на этот срок, чтобы кнопка
         * «Отменить» не пропадала с экрана раньше, чем удаление реально отменяемо.
         */
        const val UNDO_MILLIS = 5_000L

        fun mistakeTitle(type: MistakeType): String = when (type) {
            MistakeType.GRAMMAR -> "Грамматика"
            MistakeType.VOCAB -> "Выбор слова"
            MistakeType.PRONUNCIATION -> "Произношение"
            MistakeType.WORD_ORDER -> "Порядок слов"
            MistakeType.ARTICLE -> "Артикли"
            MistakeType.TENSE -> "Времена"
            MistakeType.PREPOSITION -> "Предлоги"
            MistakeType.STYLE -> "Стиль"
        }

        /** «Сентябрь 2026» — заголовок месяца в календаре. */
        fun monthTitle(month: YearMonth): String = month.month
            .getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            .replaceFirstChar(Char::uppercase) + " " + month.year

        fun filterTitle(filter: LessonFilter): String = when (filter) {
            LessonFilter.ALL -> "Любые"
            LessonFilter.ANALYZED -> "Разобранные"
            LessonFilter.NOT_ANALYZED -> "Без разбора"
        }

        /** Подпись состояния урока в истории. */
        fun lessonStatusTitle(status: LessonStatus): String = when (status) {
            LessonStatus.ANALYZED -> "Разобран"
            LessonStatus.COMPLETED -> "Не разобран"
            LessonStatus.FAILED -> "Не состоялся"
            LessonStatus.SKIPPED -> "Закрыт без разбора"
            LessonStatus.ACTIVE -> "Идёт"
        }

        fun vocabStateTitle(state: VocabState): String = when (state) {
            VocabState.NEW -> "Новые"
            VocabState.LEARNING -> "Учатся"
            VocabState.REVIEW -> "На повторении"
            VocabState.MATURE -> "Освоены"
            VocabState.SUSPENDED -> "Скрыты"
        }

        fun factory(
            profileRepository: ProfileRepository,
            statsRepository: StatsRepository,
            vocabRepository: VocabRepository,
            lessonDao: LessonDao,
            history: LessonHistoryRepository,
            appScope: CoroutineScope,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgressViewModel(
                profileRepository,
                statsRepository,
                vocabRepository,
                lessonDao,
                history,
                appScope,
            ) as T
        }
    }
}
