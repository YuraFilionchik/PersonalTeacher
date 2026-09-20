package com.example.personallangmaster.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonStatus
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

data class ReviewUiState(
    val loading: Boolean = true,
    val lesson: LessonEntity? = null,
    val mistakes: List<MistakeEntity> = emptyList(),
    val vocabAdded: Int = 0,
    val nextFocus: List<String> = emptyList(),
    val praise: String = "",
    val levelChangedTo: Cefr? = null,
    val error: String? = null,
)

/**
 * Экран разбора урока.
 *
 * Если урок ещё не разобран, разбор запускается здесь же: так он не теряется,
 * даже когда приложение закрыли сразу после разговора.
 */
class LessonReviewViewModel(
    private val lessonDao: LessonDao,
    private val analyzeLesson: AnalyzeLessonUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

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
            }
        }
    }

    /** Повторить разбор: полезно, когда первый раз не было сети. */
    fun retry(lessonId: Long) = load(lessonId)

    private suspend fun showStored(lessonId: Long) {
        val lesson = lessonDao.getById(lessonId)
        val mistakes = lessonDao.unresolvedMistakes(
            profileId = lesson?.profileId ?: return,
            limit = 100,
        ).filter { it.lessonId == lessonId }

        _state.update {
            it.copy(
                loading = false,
                lesson = lesson,
                mistakes = mistakes.sortedByDescending { mistake -> mistake.severity },
            )
        }
    }

    companion object {
        fun factory(
            lessonDao: LessonDao,
            analyzeLesson: AnalyzeLessonUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LessonReviewViewModel(lessonDao, analyzeLesson) as T
        }
    }
}
