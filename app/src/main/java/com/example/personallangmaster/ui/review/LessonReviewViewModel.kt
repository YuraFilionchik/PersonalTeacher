package com.example.personallangmaster.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.core.audio.LessonPlayer
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.TurnEntity
import com.example.personallangmaster.domain.AnalysisResult
import com.example.personallangmaster.domain.AnalyzeLessonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Ошибка вместе с местом в записи, где она прозвучала. */
data class MistakeRow(
    val mistake: MistakeEntity,
    val audioOffsetMs: Long?,
    /** Где реплика кончилась: начало следующей. null — последняя в записи. */
    val audioEndMs: Long? = null,
)

data class ReviewUiState(
    val loading: Boolean = true,
    val lesson: LessonEntity? = null,
    val mistakes: List<MistakeRow> = emptyList(),
    val turns: List<TurnEntity> = emptyList(),
    val vocabAdded: Int = 0,
    val nextFocus: List<String> = emptyList(),
    val praise: String = "",
    val levelChangedTo: Cefr? = null,
    val hasRecording: Boolean = false,
    val playing: Boolean = false,
    val error: String? = null,
)

/**
 * Экран разбора урока.
 *
 * Если урок ещё не разобран, разбор запускается здесь же: так он не теряется,
 * даже когда приложение закрыли сразу после разговора. Когда урок записывался,
 * к каждой ошибке добавляется её место в записи — можно услышать себя.
 */
class LessonReviewViewModel(
    private val lessonDao: LessonDao,
    private val analyzeLesson: AnalyzeLessonUseCase,
    private val player: LessonPlayer,
    private val tts: TtsController,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            player.playing.collect { playing -> _state.update { it.copy(playing = playing) } }
        }
    }

    /**
     * @param force разобрать, даже если урок закрыт без разбора (SKIPPED) —
     * без этого параметра [AnalyzeLessonUseCase] сам откажет в повторном платном
     * вызове, и кнопка «Разобрать всё равно» иначе была бы бутафорской.
     */
    fun load(lessonId: Long, analyzeIfNeeded: Boolean = true, force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val lesson = lessonDao.getById(lessonId)
            if (lesson == null) {
                _state.update { it.copy(loading = false, error = "Урок не найден") }
                return@launch
            }

            if (lesson.status == LessonStatus.ANALYZED || !analyzeIfNeeded) {
                showStored(lessonId)
                return@launch
            }

            when (val result = analyzeLesson(lessonId, force)) {
                is AnalysisResult.Success -> {
                    showStored(lessonId)
                    _state.update {
                        it.copy(
                            vocabAdded = result.analysis.vocab.size,
                            nextFocus = result.analysis.next_lesson_focus,
                            praise = result.analysis.praise_ru,
                            levelChangedTo = result.levelChangedTo,
                        )
                    }
                }

                is AnalysisResult.Failure -> {
                    showStored(lessonId)
                    _state.update { it.copy(error = result.reason) }
                }

                // Разбор успела сделать фоновая задача, пока экран открывался.
                AnalysisResult.AlreadyAnalyzed -> showStored(lessonId)
            }
        }
    }

    /**
     * Повторить разбор: полезно, когда первый раз не было сети, а для урока,
     * закрытого без разбора, [force] — это единственный путь всё же его разобрать.
     */
    fun retry(lessonId: Long, force: Boolean = false) = load(lessonId, force = force)

    // --- Запись урока ---

    /** Играет весь урок с начала. */
    fun playLesson() {
        val path = _state.value.lesson?.audioPath ?: return
        player.play(path)
    }

    /** Играет только ту реплику, где прозвучала ошибка, а не остаток урока. */
    fun playMistake(row: MistakeRow) {
        val path = _state.value.lesson?.audioPath ?: return
        val start = row.audioOffsetMs ?: 0L
        // Отступаем на секунду назад: фраза почти всегда начинается чуть раньше
        // того места, где детектор речи закрыл предыдущую реплику.
        val from = (start - PREROLL_MS).coerceAtLeast(0L)
        val until = (row.audioEndMs ?: (start + MAX_FRAGMENT_MS)) + POSTROLL_MS
        player.play(path, fromMs = from, untilMs = until)
    }

    fun pausePlayback() = player.pause()

    /** Как эта фраза звучит правильно — системным голосом, без затрат. */
    fun speakCorrection(text: String) = tts.speak(text)

    private suspend fun showStored(lessonId: Long) {
        val lesson = lessonDao.getById(lessonId) ?: return
        val turns = lessonDao.getTurns(lessonId)

        val mistakes = lessonDao.unresolvedMistakes(profileId = lesson.profileId, limit = 100)
            .filter { it.lessonId == lessonId }
            .sortedByDescending { it.severity }
            .map { mistake -> mistakeRow(turns, mistake) }

        _state.update {
            it.copy(
                loading = false,
                lesson = lesson,
                mistakes = mistakes,
                turns = turns,
                hasRecording = player.isAvailable(lesson.audioPath),
            )
        }
    }

    /**
     * Ищет реплику, в которой ученик это сказал.
     *
     * Сравниваем по нормализованному тексту: расшифровка редко совпадает с тем,
     * что записала модель в разборе, дословно.
     */
    private fun mistakeRow(
        turns: List<TurnEntity>,
        mistake: MistakeEntity,
    ): MistakeRow {
        val needle = normalize(mistake.original)
        if (needle.isEmpty()) return MistakeRow(mistake, audioOffsetMs = null)

        val withAudio = turns.filter { it.audioOffsetMs != null }
        val index = withAudio.indexOfFirst { turn ->
            turn.speaker == Speaker.USER && normalize(turn.text).contains(needle)
        }
        if (index < 0) return MistakeRow(mistake, audioOffsetMs = null)

        // Смещение реплики записывается в момент её начала, поэтому следующая
        // реплика — чья угодно — и есть конец нужной фразы.
        return MistakeRow(
            mistake = mistake,
            audioOffsetMs = withAudio[index].audioOffsetMs,
            audioEndMs = withAudio.getOrNull(index + 1)?.audioOffsetMs,
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .replace(Regex("\\s+"), " ")
        .trim()

    override fun onCleared() {
        player.release()
        tts.stop()
        super.onCleared()
    }

    companion object {
        private const val PREROLL_MS = 1_000L

        /** Хвост после реплики: её конец отмечен с той же неточностью, что и начало. */
        private const val POSTROLL_MS = 500L

        /** Если за ошибкой реплик нет — сколько играть, чтобы не крутить урок до конца. */
        private const val MAX_FRAGMENT_MS = 15_000L

        fun factory(
            lessonDao: LessonDao,
            analyzeLesson: AnalyzeLessonUseCase,
            player: LessonPlayer,
            tts: TtsController,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LessonReviewViewModel(lessonDao, analyzeLesson, player, tts) as T
        }
    }
}
