package com.example.personallangmaster.ui.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.core.speech.SpeechEvent
import com.example.personallangmaster.core.speech.SpeechInput
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.data.db.entity.MinimalPairEntity
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.VocabRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Три способа тренировать произношение. */
enum class DrillMode {
    /** Слышим эталон и повторяем. */
    REPEAT,

    /** Слышим одно слово из пары и выбираем, какое прозвучало. */
    MINIMAL_PAIRS,

    /** Повторяем слова на самые слабые звуки. */
    WEAK_SOUNDS,
}

/** Что происходит с попыткой произнести. */
sealed interface AttemptState {
    data object Idle : AttemptState
    data object Listening : AttemptState
    data class Heard(val text: String, val correct: Boolean) : AttemptState
    data class Failed(val reason: String) : AttemptState
}

/** Одно задание дрилла. */
data class DrillItem(
    val text: String,
    val translation: String? = null,
    val phoneme: String? = null,
    /** Второе слово минимальной пары — только для режима пар. */
    val counterpart: String? = null,
    val hintRu: String? = null,
)

data class PronunciationUiState(
    val loading: Boolean = true,
    val phonemes: List<PhonemeEntity> = emptyList(),
    val scores: Map<String, Int> = emptyMap(),
    val selectedPhoneme: PhonemeEntity? = null,
    val mode: DrillMode? = null,
    val items: List<DrillItem> = emptyList(),
    val index: Int = 0,
    val attempt: AttemptState = AttemptState.Idle,
    /** В режиме пар: какое слово прозвучало на самом деле. */
    val playedWord: String? = null,
    val pairAnswer: String? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val current: DrillItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
}

/**
 * Тренажёр произношения.
 *
 * Оценка звука — не приговор, а направление: одна попытка сдвигает её постепенно.
 * Поэтому в интерфейсе нет точных процентов «правильности», только «стало лучше».
 */
class PronunciationViewModel(
    private val contentRepository: ContentRepository,
    private val vocabRepository: VocabRepository,
    private val profileRepository: ProfileRepository,
    private val tts: TtsController,
    private val speechInput: SpeechInput,
) : ViewModel() {

    private val _state = MutableStateFlow(PronunciationUiState())
    val state: StateFlow<PronunciationUiState> = _state.asStateFlow()

    private var profileId: Long? = null
    private var scoreEntities: Map<String, PhonemeScoreEntity> = emptyMap()
    private var listenJob: Job? = null

    init {
        viewModelScope.launch {
            val profile = profileRepository.current()
            profileId = profile?.id

            contentRepository.observePhonemes()
                .onEach { phonemes -> _state.update { it.copy(phonemes = phonemes, loading = false) } }
                .launchIn(viewModelScope)

            profile?.id?.let { id ->
                contentRepository.observePhonemeScores(id)
                    .onEach { scores ->
                        scoreEntities = scores.associateBy { it.phoneme }
                        _state.update { current ->
                            current.copy(scores = scores.associate { it.phoneme to it.score })
                        }
                    }
                    .launchIn(viewModelScope)
            }
        }
    }

    fun selectPhoneme(phoneme: PhonemeEntity?) =
        _state.update { it.copy(selectedPhoneme = phoneme) }

    // --- Запуск дриллов ---

    fun startDrill(mode: DrillMode, phoneme: String? = null) {
        viewModelScope.launch {
            val items = when (mode) {
                DrillMode.REPEAT -> repeatItems()
                DrillMode.MINIMAL_PAIRS -> pairItems(phoneme)
                DrillMode.WEAK_SOUNDS -> weakSoundItems()
            }

            if (items.isEmpty()) {
                _state.update {
                    it.copy(message = "Пока нечего тренировать — проведите урок или добавьте слова")
                }
                return@launch
            }

            _state.update {
                it.copy(
                    mode = mode,
                    items = items,
                    index = 0,
                    attempt = AttemptState.Idle,
                    pairAnswer = null,
                    correctCount = 0,
                    finished = false,
                    message = null,
                    selectedPhoneme = null,
                )
            }
            presentCurrent()
        }
    }

    fun exitDrill() {
        listenJob?.cancel()
        tts.stop()
        _state.update {
            it.copy(mode = null, items = emptyList(), index = 0, finished = false, message = null)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // --- Ход дрилла ---

    /** Повторно проигрывает эталон: в режиме пар — то же самое слово. */
    fun play(slow: Boolean = false) {
        val state = _state.value
        val text = when (state.mode) {
            DrillMode.MINIMAL_PAIRS -> state.playedWord
            else -> state.current?.text
        } ?: return
        tts.speak(text, rate = if (slow) SLOW_RATE else 1.0f)
    }

    fun startSpeaking() {
        val item = _state.value.current ?: return
        listenJob?.cancel()
        _state.update { it.copy(attempt = AttemptState.Listening) }

        listenJob = speechInput.listen()
            .onEach { event ->
                when (event) {
                    SpeechEvent.Ready, is SpeechEvent.Partial -> Unit

                    is SpeechEvent.Final -> {
                        val correct = SpeechInput.matches(event.text, item.text)
                        _state.update {
                            it.copy(
                                attempt = AttemptState.Heard(event.text, correct),
                                correctCount = it.correctCount + if (correct) 1 else 0,
                            )
                        }
                        record(item, correct)
                    }

                    is SpeechEvent.Failed -> _state.update {
                        it.copy(attempt = AttemptState.Failed(event.reason))
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Ответ в режиме минимальных пар. */
    fun answerPair(word: String) {
        val state = _state.value
        val item = state.current ?: return
        val correct = word == state.playedWord

        _state.update {
            it.copy(
                pairAnswer = word,
                correctCount = it.correctCount + if (correct) 1 else 0,
            )
        }
        record(item.copy(text = state.playedWord ?: item.text), correct)
    }

    fun next() {
        listenJob?.cancel()
        tts.stop()

        _state.update { current ->
            val nextIndex = current.index + 1
            current.copy(
                index = nextIndex,
                attempt = AttemptState.Idle,
                pairAnswer = null,
                finished = nextIndex >= current.items.size,
            )
        }

        if (!_state.value.finished) presentCurrent()
    }

    // --- Внутреннее ---

    private fun presentCurrent() {
        val state = _state.value
        val item = state.current ?: return

        if (state.mode == DrillMode.MINIMAL_PAIRS) {
            // Какое из двух слов прозвучит — решаем случайно, иначе ученик
            // быстро начинает угадывать по порядку, а не по слуху.
            val played = listOfNotNull(item.text, item.counterpart).random()
            _state.update { it.copy(playedWord = played) }
            tts.speak(played)
        } else {
            _state.update { it.copy(playedWord = item.text) }
            tts.speak(item.text)
        }
    }

    private fun record(item: DrillItem, correct: Boolean) {
        val profile = profileId ?: return
        viewModelScope.launch {
            contentRepository.recordPronunciation(
                profileId = profile,
                phoneme = item.phoneme,
                targetText = item.text,
                success = correct,
                previous = item.phoneme?.let { scoreEntities[it] },
            )
        }
    }

    /** Повторяем то, что ученик уже учит: примеры из словаря и сами слова. */
    private suspend fun repeatItems(): List<DrillItem> {
        val profile = profileId ?: return emptyList()
        val words = vocabRepository.observeAll(profile).first()
        return words
            .shuffled()
            .take(DRILL_SIZE)
            .map { word ->
                DrillItem(
                    text = word.exampleEn?.takeIf { it.split(" ").size in 2..8 } ?: word.term,
                    translation = word.translationRu,
                    phoneme = null,
                )
            }
    }

    private suspend fun pairItems(phoneme: String?): List<DrillItem> {
        val pairs: List<MinimalPairEntity> = if (phoneme != null) {
            contentRepository.pairsFor(phoneme)
        } else {
            val weak = profileId?.let { contentRepository.weakestPhonemes(it, limit = 3) }.orEmpty()
            val fromWeak = weak.flatMap { contentRepository.pairsFor(it) }
            fromWeak.ifEmpty { defaultPairs() }
        }

        return pairs.shuffled().take(DRILL_SIZE).map { pair ->
            DrillItem(
                text = pair.word1,
                translation = pair.translation1,
                phoneme = pair.phoneme1,
                counterpart = pair.word2,
            )
        }
    }

    /** Самые слабые звуки: слова берём из минимальных пар на этот звук. */
    private suspend fun weakSoundItems(): List<DrillItem> {
        val profile = profileId ?: return emptyList()
        val weak = contentRepository.weakestPhonemes(profile, limit = 3)
        if (weak.isEmpty()) return emptyList()

        val phonemeHints = _state.value.phonemes.associate {
            ContentRepository.normalizePhoneme(it.ipa) to it.hintRu
        }

        return weak.flatMap { phoneme ->
            contentRepository.pairsFor(phoneme).take(WORDS_PER_PHONEME).map { pair ->
                val normalized = ContentRepository.normalizePhoneme(phoneme)
                val firstMatches = ContentRepository.normalizePhoneme(pair.phoneme1) == normalized
                val target = if (firstMatches) pair.word1 else pair.word2
                val translation = if (firstMatches) pair.translation1 else pair.translation2
                DrillItem(
                    text = target,
                    translation = translation,
                    phoneme = phoneme,
                    hintRu = phonemeHints[ContentRepository.normalizePhoneme(phoneme)],
                )
            }
        }.take(DRILL_SIZE)
    }

    private suspend fun defaultPairs(): List<MinimalPairEntity> =
        HARD_FOR_RUSSIAN.flatMap { contentRepository.pairsFor(it) }

    override fun onCleared() {
        listenJob?.cancel()
        tts.stop()
        super.onCleared()
    }

    companion object {
        private const val DRILL_SIZE = 10
        private const val WORDS_PER_PHONEME = 4
        private const val SLOW_RATE = 0.6f

        /** Звуки, на которых чаще всего спотыкаются русскоязычные. */
        private val HARD_FOR_RUSSIAN = listOf("iː", "ɪ", "θ", "ð", "w", "æ")

        fun factory(
            contentRepository: ContentRepository,
            vocabRepository: VocabRepository,
            profileRepository: ProfileRepository,
            tts: TtsController,
            speechInput: SpeechInput,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PronunciationViewModel(
                contentRepository, vocabRepository, profileRepository, tts, speechInput,
            ) as T
        }
    }
}
