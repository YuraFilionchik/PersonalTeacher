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
)

data class ReviewUiState(
    val loading: Boolean = true,
    val lesson: LessonEntity? = null,
    val mistakes: List<MistakeRow> = emptyList(),
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

    fun load(lessonId: Long, analyzeIfNeeded: Boolean = true) {
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

            when (val result = analyzeLesson(lessonId)) {
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

    /** Повторить разбор: полезно, когда первый раз не было сети. */
    fun retry(lessonId: Long) = load(lessonId)

    // --- Запись урока ---

    /** Играет весь урок с начала. */
    fun playLesson() {
        val path = _state.value.lesson?.audioPath ?: return
        player.play(path)
    }

    /** Играет то место записи, где прозвучала ошибка. */
    fun playMistake(row: MistakeRow) {
        val path = _state.value.lesson?.audioPath ?: return
        // Отступаем на секунду назад: фраза почти всегда начинается чуть раньше
        // того места, где детектор речи закрыл предыдущую реплику.
        val from = (row.audioOffsetMs ?: 0L) - PREROLL_MS
        player.play(path, fromMs = from.coerceAtLeast(0L))
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
            .map { mistake -> MistakeRow(mistake, findOffset(turns, mistake)) }

        _state.update {
            it.copy(
                loading = false,
                lesson = lesson,
                mistakes = mistakes,
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
    private fun findOffset(
        turns: List<com.example.personallangmaster.data.db.entity.TurnEntity>,
        mistake: MistakeEntity,
    ): Long? {
        val needle = normalize(mistake.original)
        if (needle.isEmpty()) return null

        return turns
            .filter { it.speaker == Speaker.USER && it.audioOffsetMs != null }
            .firstOrNull { turn -> normalize(turn.text).contains(needle) }
            ?.audioOffsetMs
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
