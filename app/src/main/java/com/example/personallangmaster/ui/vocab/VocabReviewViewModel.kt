package com.example.personallangmaster.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.core.speech.SpeechEvent
import com.example.personallangmaster.core.speech.SpeechInput
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.core.srs.ReviewGrade
import com.example.personallangmaster.data.db.ReviewMode
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.VocabRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Что происходит с попыткой произнести слово. */
sealed interface SpeakState {
    data object Idle : SpeakState
    data object Listening : SpeakState
    data class Heard(val text: String, val correct: Boolean) : SpeakState
    data class Failed(val reason: String) : SpeakState
}

data class VocabReviewUiState(
    val loading: Boolean = true,
    val queue: List<VocabItemEntity> = emptyList(),
    val index: Int = 0,
    val mode: ReviewMode = ReviewMode.RECOGNIZE,
    val revealed: Boolean = false,
    val speak: SpeakState = SpeakState.Idle,
    val reviewed: Int = 0,
    val forgotten: Int = 0,
    val finished: Boolean = false,
    val nextIntervalDays: Int? = null,
) {
    val current: VocabItemEntity? get() = queue.getOrNull(index)
    val total: Int get() = queue.size
    val progress: Float get() = if (total == 0) 0f else reviewed.toFloat() / total
}

/**
 * Очередь повторений.
 *
 * Режим карточки выбирается по её состоянию: новое слово сначала нужно просто
 * узнать, знакомое — вспомнить, освоенное — произнести и услышать. Так одно и то же
 * слово каждый раз проверяется с другой стороны, а не зубрится одной парой.
 */
class VocabReviewViewModel(
    private val vocabRepository: VocabRepository,
    private val profileRepository: ProfileRepository,
    private val tts: TtsController,
    private val speechInput: SpeechInput,
) : ViewModel() {

    private val _state = MutableStateFlow(VocabReviewUiState())
    val state: StateFlow<VocabReviewUiState> = _state.asStateFlow()

    private var profileId: Long? = null
    private var shownAtMillis = 0L
    private var listenJob: Job? = null

    fun load() {
        viewModelScope.launch {
            val profile = profileRepository.current()
            profileId = profile?.id

            val queue = profile?.id?.let { vocabRepository.dueQueue(it) }.orEmpty()
            shownAtMillis = System.currentTimeMillis()

            _state.update {
                it.copy(
                    loading = false,
                    queue = queue,
                    index = 0,
                    mode = modeFor(queue.firstOrNull()),
                    revealed = false,
                    finished = queue.isEmpty(),
                )
            }

            // Режим «услышать» начинается со звука: карточку нужно сперва услышать.
            if (_state.value.mode == ReviewMode.LISTEN) speakTerm()
        }
    }

    fun reveal() {
        _state.update { it.copy(revealed = true) }
        if (_state.value.mode == ReviewMode.RECOGNIZE) speakTerm()
    }

    /** Ручная смена режима для текущей карточки. */
    fun switchMode(mode: ReviewMode) {
        listenJob?.cancel()
        _state.update { it.copy(mode = mode, revealed = false, speak = SpeakState.Idle) }
        if (mode == ReviewMode.LISTEN) speakTerm()
    }

    fun speakTerm(slow: Boolean = false) {
        val term = _state.value.current?.term ?: return
        tts.speak(term, rate = if (slow) SLOW_RATE else 1.0f)
    }

    /** Режим «произнеси слово»: слушаем и сверяем с эталоном. */
    fun startSpeaking() {
        val expected = _state.value.current?.term ?: return
        listenJob?.cancel()
        _state.update { it.copy(speak = SpeakState.Listening) }

        listenJob = speechInput.listen()
            .onEach { event ->
                when (event) {
                    SpeechEvent.Ready -> Unit
                    is SpeechEvent.Partial -> Unit
                    is SpeechEvent.Final -> _state.update {
                        it.copy(
                            speak = SpeakState.Heard(
                                text = event.text,
                                correct = SpeechInput.matches(event.text, expected),
                            ),
                            revealed = true,
                        )
                    }
                    is SpeechEvent.Failed -> _state.update {
                        it.copy(speak = SpeakState.Failed(event.reason))
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun grade(grade: ReviewGrade) {
        val item = _state.value.current ?: return
        val mode = _state.value.mode
        val latency = System.currentTimeMillis() - shownAtMillis

        viewModelScope.launch {
            val updated = vocabRepository.grade(item, grade, mode, latency)
            _state.update { current ->
                current.copy(
                    reviewed = current.reviewed + 1,
                    forgotten = current.forgotten + if (grade == ReviewGrade.AGAIN) 1 else 0,
                    nextIntervalDays = updated.intervalDays,
                )
            }
            // Забытое слово возвращается в конец сегодняшней очереди, а не ждёт завтра.
            if (grade == ReviewGrade.AGAIN) {
                _state.update { it.copy(queue = it.queue + updated) }
            }
            advance()
        }
    }

    fun postpone() {
        val item = _state.value.current ?: return
        viewModelScope.launch {
            vocabRepository.postpone(item)
            advance()
        }
    }

    fun suspendCurrent() {
        val item = _state.value.current ?: return
        viewModelScope.launch {
            vocabRepository.setSuspended(item, suspended = true)
            advance()
        }
    }

    private fun advance() {
        listenJob?.cancel()
        tts.stop()
        shownAtMillis = System.currentTimeMillis()

        _state.update { current ->
            val nextIndex = current.index + 1
            val next = current.queue.getOrNull(nextIndex)
            current.copy(
                index = nextIndex,
                mode = modeFor(next),
                revealed = false,
                speak = SpeakState.Idle,
                finished = next == null,
            )
        }

        if (!_state.value.finished && _state.value.mode == ReviewMode.LISTEN) speakTerm()
    }

    /** Чем лучше слово освоено, тем сложнее способ проверки. */
    private fun modeFor(item: VocabItemEntity?): ReviewMode = when (item?.state) {
        null, VocabState.NEW -> ReviewMode.RECOGNIZE
        VocabState.LEARNING -> ReviewMode.RECALL
        VocabState.REVIEW -> ReviewMode.SPEAK
        VocabState.MATURE -> ReviewMode.LISTEN
        VocabState.SUSPENDED -> ReviewMode.RECOGNIZE
    }

    override fun onCleared() {
        listenJob?.cancel()
        tts.stop()
        super.onCleared()
    }

    companion object {
        private const val SLOW_RATE = 0.6f

        fun factory(
            vocabRepository: VocabRepository,
            profileRepository: ProfileRepository,
            tts: TtsController,
            speechInput: SpeechInput,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VocabReviewViewModel(vocabRepository, profileRepository, tts, speechInput) as T
        }
    }
}
