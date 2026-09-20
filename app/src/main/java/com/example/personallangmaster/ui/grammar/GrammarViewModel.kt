package com.example.personallangmaster.ui.grammar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.data.db.ExerciseKind
import com.example.personallangmaster.data.db.entity.ExerciseEntity
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.domain.ExercisesResult
import com.example.personallangmaster.domain.GenerateExercisesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Тема с прогрессом и числом ошибок — строка каталога. */
data class TopicRow(
    val topic: GrammarTopicEntity,
    val mastery: Int,
    val mistakeCount: Int,
)

/** Ответ на упражнение: что ввёл ученик и совпало ли. */
data class AnswerResult(
    val given: String,
    val correct: Boolean,
)

data class GrammarUiState(
    val loading: Boolean = true,
    val recommended: List<TopicRow> = emptyList(),
    val all: List<TopicRow> = emptyList(),
    val openTopic: GrammarTopicEntity? = null,
    val topicMistakes: List<MistakeEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val index: Int = 0,
    val answer: AnswerResult? = null,
    val correctCount: Int = 0,
    val generating: Boolean = false,
    val practicing: Boolean = false,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val current: ExerciseEntity? get() = exercises.getOrNull(index)
    val total: Int get() = exercises.size
}

/**
 * Грамматика: темы, объяснения и упражнения.
 *
 * Порядок тем задаёт не учебник, а собственные ошибки ученика — то, что он
 * реально путает в разговоре, поднимается наверх.
 */
class GrammarViewModel(
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val generateExercises: GenerateExercisesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(GrammarUiState())
    val state: StateFlow<GrammarUiState> = _state.asStateFlow()

    private var profileId: Long? = null
    private var mastery: Map<String, Int> = emptyMap()
    private var mistakeCounts: Map<String, Int> = emptyMap()
    private var topics: List<GrammarTopicEntity> = emptyList()
    private var shownAt = 0L

    init {
        viewModelScope.launch {
            val profile = profileRepository.current()
            profileId = profile?.id

            profile?.id?.let { id ->
                val recommended = contentRepository.recommendedTopics(id, since = monthAgo())
                mistakeCounts = recommended.associate { it.topic.id to it.mistakeCount }

                contentRepository.observeProgress(id)
                    .onEach { progress ->
                        mastery = progress.associate { it.topicId to it.mastery }
                        regroup()
                    }
                    .launchIn(viewModelScope)
            }

            contentRepository.observeTopics()
                .onEach { list ->
                    topics = list
                    regroup()
                }
                .launchIn(viewModelScope)
        }
    }

    fun openTopic(topic: GrammarTopicEntity) {
        viewModelScope.launch {
            val profile = profileId
            val mistakes = profile?.let { contentRepository.mistakesForTopic(it, topic.id) }.orEmpty()
            val ready = profile?.let { contentRepository.pendingExercises(it, topic.id) }.orEmpty()

            _state.update {
                it.copy(
                    openTopic = topic,
                    topicMistakes = mistakes,
                    exercises = ready,
                    index = 0,
                    answer = null,
                    correctCount = 0,
                    practicing = false,
                    finished = false,
                )
            }
        }
    }

    fun closeTopic() = _state.update {
        it.copy(openTopic = null, exercises = emptyList(), practicing = false, finished = false)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Готовит упражнения: если запас есть, начинаем сразу и не тратим токены. */
    fun practice() {
        val topic = _state.value.openTopic ?: return
        val profile = profileId ?: return

        viewModelScope.launch {
            val ready = contentRepository.pendingExercises(profile, topic.id)
            if (ready.isNotEmpty()) {
                startPractice(ready)
                return@launch
            }

            _state.update { it.copy(generating = true, message = null) }
            when (val result = generateExercises(topic.id)) {
                is ExercisesResult.Success -> {
                    val fresh = contentRepository.pendingExercises(profile, topic.id)
                    _state.update { it.copy(generating = false) }
                    startPractice(fresh)
                }

                is ExercisesResult.Failure -> _state.update {
                    it.copy(generating = false, message = result.reason)
                }
            }
        }
    }

    fun submit(given: String) {
        val exercise = _state.value.current ?: return
        if (_state.value.answer != null) return

        val correct = isCorrect(given, exercise.answer)
        val latency = System.currentTimeMillis() - shownAt

        _state.update {
            it.copy(
                answer = AnswerResult(given, correct),
                correctCount = it.correctCount + if (correct) 1 else 0,
            )
        }

        viewModelScope.launch {
            contentRepository.recordAttempt(exercise.id, given, correct, latency)
        }
    }

    fun next() {
        val state = _state.value
        val nextIndex = state.index + 1
        shownAt = System.currentTimeMillis()

        if (nextIndex >= state.exercises.size) {
            finishPractice()
        } else {
            _state.update { it.copy(index = nextIndex, answer = null) }
        }
    }

    private fun startPractice(exercises: List<ExerciseEntity>) {
        if (exercises.isEmpty()) {
            _state.update { it.copy(message = "Упражнения не получились, попробуйте ещё раз") }
            return
        }
        shownAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                exercises = exercises,
                index = 0,
                answer = null,
                correctCount = 0,
                practicing = true,
                finished = false,
            )
        }
    }

    /**
     * Освоенность темы — скользящее среднее: одна удачная серия не объявляет
     * тему выученной, но и один провал не обнуляет её.
     */
    private fun finishPractice() {
        val state = _state.value
        val topic = state.openTopic ?: return
        val profile = profileId ?: return

        val share = if (state.total == 0) 0 else state.correctCount * 100 / state.total
        val previous = mastery[topic.id] ?: 0
        val updated = previous + ((share - previous) * MASTERY_SMOOTHING).toInt()

        viewModelScope.launch {
            contentRepository.updateMastery(
                profileId = profile,
                topicId = topic.id,
                mastery = updated,
                now = System.currentTimeMillis(),
            )
        }

        _state.update { it.copy(practicing = false, finished = true) }
    }

    private fun regroup() {
        val rows = topics.map { topic ->
            TopicRow(
                topic = topic,
                mastery = mastery[topic.id] ?: 0,
                mistakeCount = mistakeCounts[topic.id] ?: 0,
            )
        }

        _state.update { current ->
            current.copy(
                loading = false,
                recommended = rows.filter { it.mistakeCount > 0 }
                    .sortedByDescending { it.mistakeCount },
                all = rows.sortedWith(compareBy({ it.topic.cefr.ordinal }, { it.topic.titleRu })),
            )
        }
    }

    private fun monthAgo(): Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

    companion object {
        private const val MASTERY_SMOOTHING = 0.5

        /**
         * Ответ засчитывается без придирок к регистру и пунктуации: проверяем
         * знание правила, а не аккуратность набора на телефоне.
         */
        fun isCorrect(given: String, expected: String): Boolean {
            fun normalize(value: String) = value
                .lowercase(Locale.US)
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' }
                .replace(Regex("\\s+"), " ")
                .trim()

            return normalize(given).isNotEmpty() && normalize(given) == normalize(expected)
        }

        /** Подсказка для поля ввода: что именно ждут от ученика. */
        fun placeholder(kind: ExerciseKind): String = when (kind) {
            ExerciseKind.FILL_GAP -> "Слово вместо пропуска"
            ExerciseKind.TRANSLATE -> "Перевод на английский"
            ExerciseKind.REORDER -> "Соберите предложение"
            ExerciseKind.SPEAK -> "Произнесите фразу"
            ExerciseKind.CHOICE -> ""
        }

        fun factory(
            contentRepository: ContentRepository,
            profileRepository: ProfileRepository,
            generateExercises: GenerateExercisesUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GrammarViewModel(contentRepository, profileRepository, generateExercises) as T
        }
    }
}
