package com.example.personallangmaster.data.seed

import android.content.Context
import android.util.Log
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.dao.ContentDao
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.MinimalPairEntity
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Наполняет справочные таблицы учебным контентом из `assets`.
 *
 * Вызывается при каждом старте, но реально работает только когда таблица пуста
 * или контент в файле обновился. Отсутствие или битый файл не ломает приложение:
 * без сценариев остаётся свободный разговор, без фонем — тренажёр без карты звуков.
 */
class SeedLoader(
    private val context: Context,
    private val contentDao: ContentDao,
) {

    suspend fun seedIfNeeded(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (force || contentDao.scenarioCount() == 0) {
            read<ScenarioDto>(FILE_SCENARIOS)?.let { dtos ->
                contentDao.insertScenarios(dtos.map(::toEntity))
                Log.i(TAG, "Загружено сценариев: ${dtos.size}")
            }
        }
        if (force || contentDao.topicCount() == 0) {
            read<GrammarTopicDto>(FILE_GRAMMAR)?.let { dtos ->
                contentDao.insertTopics(dtos.map(::toEntity))
                Log.i(TAG, "Загружено тем грамматики: ${dtos.size}")
            }
        }
        if (force || contentDao.phonemeCount() == 0) {
            read<PhonemeDto>(FILE_PHONEMES)?.let { dtos ->
                contentDao.insertPhonemes(dtos.map(::toEntity))
                Log.i(TAG, "Загружено фонем: ${dtos.size}")
            }
        }
        if (force || contentDao.minimalPairCount() == 0) {
            read<MinimalPairDto>(FILE_PAIRS)?.let { dtos ->
                contentDao.insertMinimalPairs(dtos.map(::toEntity))
                Log.i(TAG, "Загружено минимальных пар: ${dtos.size}")
            }
        }
    }

    private inline fun <reified T> read(fileName: String): List<T>? = runCatching {
        val raw = context.assets.open(fileName).bufferedReader().use { it.readText() }
        json.decodeFromString<List<T>>(raw)
    }.onFailure { error ->
        Log.w(TAG, "Не удалось прочитать $fileName: ${error.message}")
    }.getOrNull()

    private fun toEntity(dto: ScenarioDto) = ScenarioEntity(
        id = dto.id,
        category = dto.category,
        level = Cefr.from(dto.level),
        titleRu = dto.titleRu,
        titleEn = dto.titleEn,
        descriptionRu = dto.descriptionRu,
        durationMins = dto.durationMins,
        roleTutor = dto.roleTutor,
        roleUser = dto.roleUser,
        goal = dto.goal,
        successCriteria = dto.successCriteria,
        openingLine = dto.openingLine,
        vocabHints = dto.vocabHints,
        isCustom = false,
    )

    private fun toEntity(dto: GrammarTopicDto) = GrammarTopicEntity(
        id = dto.id,
        code = dto.code,
        titleRu = dto.titleRu,
        titleEn = dto.titleEn,
        cefr = Cefr.from(dto.cefr, Cefr.A1),
        explanationRu = dto.explanationRu,
        explanationEn = dto.explanationEn,
        isSeed = dto.isSeed,
    )

    private fun toEntity(dto: PhonemeDto) = PhonemeEntity(
        id = dto.id,
        ipa = dto.ipa,
        type = dto.type,
        hintRu = dto.hintRu,
    )

    private fun toEntity(dto: MinimalPairDto) = MinimalPairEntity(
        id = dto.id,
        word1 = dto.word1,
        word2 = dto.word2,
        phoneme1 = dto.phoneme1,
        phoneme2 = dto.phoneme2,
        translation1 = dto.translation1,
        translation2 = dto.translation2,
    )

    private companion object {
        const val TAG = "SeedLoader"
        const val FILE_SCENARIOS = "scenarios.json"
        const val FILE_GRAMMAR = "grammar_topics.json"
        const val FILE_PHONEMES = "phonemes.json"
        const val FILE_PAIRS = "minimal_pairs.json"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
