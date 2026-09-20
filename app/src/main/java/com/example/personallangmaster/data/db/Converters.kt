package com.example.personallangmaster.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Конвертеры Room. Перечисления храним строками (читаемо при отладке и устойчиво
 * к перестановке констант), списки строк — компактным JSON.
 */
class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>?): String =
        json.encodeToString(ListSerializer(String.serializer()), value.orEmpty())

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())

    @TypeConverter fun cefrToString(value: Cefr?): String? = value?.name
    @TypeConverter fun stringToCefr(value: String?): Cefr? = Cefr.fromOrNull(value)

    @TypeConverter fun lessonModeToString(value: LessonMode): String = value.name
    @TypeConverter fun stringToLessonMode(value: String): LessonMode = LessonMode.valueOf(value)

    @TypeConverter fun lessonStatusToString(value: LessonStatus): String = value.name
    @TypeConverter fun stringToLessonStatus(value: String): LessonStatus = LessonStatus.valueOf(value)

    @TypeConverter fun speakerToString(value: Speaker): String = value.name
    @TypeConverter fun stringToSpeaker(value: String): Speaker = Speaker.valueOf(value)

    @TypeConverter fun mistakeTypeToString(value: MistakeType): String = value.name
    @TypeConverter fun stringToMistakeType(value: String): MistakeType = MistakeType.valueOf(value)

    @TypeConverter fun vocabSourceToString(value: VocabSource): String = value.name
    @TypeConverter fun stringToVocabSource(value: String): VocabSource = VocabSource.valueOf(value)

    @TypeConverter fun vocabStateToString(value: VocabState): String = value.name
    @TypeConverter fun stringToVocabState(value: String): VocabState = VocabState.valueOf(value)

    @TypeConverter fun reviewModeToString(value: ReviewMode): String = value.name
    @TypeConverter fun stringToReviewMode(value: String): ReviewMode = ReviewMode.valueOf(value)

    @TypeConverter fun exerciseKindToString(value: ExerciseKind): String = value.name
    @TypeConverter fun stringToExerciseKind(value: String): ExerciseKind = ExerciseKind.valueOf(value)

    @TypeConverter fun usageKindToString(value: UsageKind): String = value.name
    @TypeConverter fun stringToUsageKind(value: String): UsageKind = UsageKind.valueOf(value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
