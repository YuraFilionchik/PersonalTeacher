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
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.StatsRepository
import com.example.personallangmaster.data.repo.VocabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
) {
    /** Вперёд дальше текущего месяца ходить некуда: будущих уроков не бывает. */
    val canGoForward: Boolean get() = month < YearMonth.now()
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
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    /** Уроки показываемого месяца; на экран попадает их отфильтрованная часть. */
    private var allLessons: List<LessonEntity> = emptyList()

    private val month = MutableStateFlow(YearMonth.now())

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

    private fun applyFilters() {
        val current = _state.value
        val filter = current.filter
        val selectedDay = current.selectedDay

        val visible = allLessons
            .filter { selectedDay == null || LessonCalendar.lessonDate(it) == selectedDay }
            .filter { lesson ->
                when (filter) {
                    LessonFilter.ALL -> true
                    LessonFilter.ANALYZED -> lesson.status == LessonStatus.ANALYZED
                    // «Не разобран» — это и завершённые без разбора, и несостоявшиеся:
                    // и те и другие ждут решения, разбирать их или забыть.
                    LessonFilter.NOT_ANALYZED -> lesson.status != LessonStatus.ANALYZED
                }
            }

        _state.update {
            it.copy(
                recentLessons = visible,
                lessonsTotal = allLessons.size,
                calendar = LessonCalendar.buildMonth(it.month, allLessons, LocalDate.now()),
            )
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
            LessonStatus.ACTIVE -> "Идёт"
            LessonStatus.SKIPPED -> "Закрыт без разбора"
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
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProgressViewModel(profileRepository, statsRepository, vocabRepository, lessonDao) as T
        }
    }
}
