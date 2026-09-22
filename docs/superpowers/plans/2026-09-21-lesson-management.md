# Управление уроками — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить действия над уроком в истории под календарём — удаление с отменой, чистку записей, закрытие без разбора, пометку, транскрипт и отправку текстом — плюс режим выбора нескольких уроков с массовыми операциями.

**Architecture:** Чистая логика (какой урок ждёт разбора, отбор несостоявшихся, размер файла, формат транскрипта, состояние выбора) живёт в `domain/` и покрывается JVM-тестами. Работа с базой и файлами — в новом `LessonHistoryRepository`. UI — карточка урока и диалоги вынесены из разросшегося `ProgressScreen.kt` в отдельные файлы, состояние и отложенное удаление — в `ProgressViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.7.0 (KSP), Navigation 3, ручной DI через `AppContainer`, JUnit 4 + kotlinx-coroutines-test.

**Spec:** [`docs/superpowers/specs/2026-09-21-lesson-management-design.md`](../specs/2026-09-21-lesson-management-design.md)

## Global Constraints

- Весь текст интерфейса и все KDoc-комментарии — на русском языке.
- Комментарии объясняют **почему**, а не **что**. Если комментарий пересказывает код, он не нужен. Этот стиль выдержан во всём проекте — держитесь его.
- Команда сборки и тестов: `./gradlew :app:testDebugUnitTest` (на Windows из Git Bash — та же команда).
- Команда проверки компиляции и генерации Room: `./gradlew :app:assembleDebug`.
- Тесты — JVM-юнит-тесты в `app/src/test/java/...`, имена тестов пишутся русской фразой в обратных кавычках, как в существующем `LessonCalendarTest`.
- Тестов Room и Compose в проекте нет; заводить их в рамках этой задачи не нужно.
- В рабочем дереве на момент старта уже есть незакоммиченные изменения предыдущей задачи (календарь и фильтры истории: `LessonCalendar.kt`, `LessonCalendarGrid.kt`, изменения в `ProgressScreen.kt`, `ProgressViewModel.kt`, `LessonDao.kt`). **Их нельзя откатывать.** Перед началом работы закоммитьте их отдельным коммитом, если они ещё не в истории.
- Версия базы после этой задачи — 2. Миграция обязательна: `fallbackToDestructiveMigration` в проекте не используется, накопленную историю уроков терять нельзя.
- Не добавляйте `FileProvider` и отправку аудиофайла: это сознательно вынесено за рамки задачи.
- Не добавляйте действие «Разобрать заново»: оно сознательно вынесено за рамки задачи.

---

### Task 0: Зафиксировать текущее состояние дерева

**Files:**
- Modify: ничего

- [ ] **Step 1: Посмотреть, что в рабочем дереве**

```bash
git status
```

- [ ] **Step 2: Закоммитить незавершённую работу по календарю, если она ещё не в истории**

Если `git status` показывает изменения в `ProgressScreen.kt`, `ProgressViewModel.kt`, `LessonDao.kt`, `Today.md`, `docs/TODO.md` и новые файлы `LessonCalendar.kt`, `LessonCalendarGrid.kt`, `app/src/test/java/com/example/personallangmaster/ui/progress/` — это законченная предыдущая задача. Закоммитьте её как есть:

```bash
git add app/src/main/java/com/example/personallangmaster/ui/progress app/src/test/java/com/example/personallangmaster/ui/progress app/src/main/java/com/example/personallangmaster/data/db/dao/LessonDao.kt Today.md docs/TODO.md
git commit -m "Календарь уроков и фильтры истории на экране прогресса"
```

Если `git status` чистый — пропустите шаг.

- [ ] **Step 3: Убедиться, что проект собирается до начала работы**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Если нет — остановитесь и сообщите: чинить чужую поломку в рамках этой задачи нельзя.

---

### Task 1: Состояние «закрыт без разбора» и чистая логика истории

Новое состояние урока и три чистые функции, на которых держатся фильтры и массовые операции.

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/Enums.kt`
- Create: `app/src/main/java/com/example/personallangmaster/domain/LessonHousekeeping.kt`
- Test: `app/src/test/java/com/example/personallangmaster/domain/LessonHousekeepingTest.kt`

**Interfaces:**
- Consumes: `LessonEntity`, `LessonStatus` из `com.example.personallangmaster.data.db`.
- Produces:
  - `LessonStatus.SKIPPED`
  - `LessonHousekeeping.needsAnalysis(lesson: LessonEntity): Boolean`
  - `LessonHousekeeping.failed(lessons: List<LessonEntity>): List<LessonEntity>`
  - `LessonHousekeeping.withRecording(lessons: List<LessonEntity>): List<LessonEntity>`
  - `LessonHousekeeping.formatSize(bytes: Long): String`

- [ ] **Step 1: Написать падающий тест**

Создайте `app/src/test/java/com/example/personallangmaster/domain/LessonHousekeepingTest.kt`:

```kotlin
package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Отбор уроков для массовых операций и подписи размеров: на этом держатся
 * фильтр «Без разбора», «удалить все несостоявшиеся» и «очистить все записи».
 */
class LessonHousekeepingTest {

    private fun lesson(
        id: Long,
        status: LessonStatus,
        audioPath: String? = null,
    ) = LessonEntity(
        id = id,
        profileId = 1,
        startedAt = 0,
        mode = LessonMode.FREE_TALK,
        personaId = "anna",
        strictness = 2,
        modelId = "test",
        status = status,
        audioPath = audioPath,
    )

    @Test
    fun `разбора ждёт только завершённый урок`() {
        assertTrue(LessonHousekeeping.needsAnalysis(lesson(1, LessonStatus.COMPLETED)))
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(2, LessonStatus.ANALYZED)))
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(3, LessonStatus.FAILED)))
    }

    @Test
    fun `закрытый вручную урок больше не ждёт разбора`() {
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(1, LessonStatus.SKIPPED)))
    }

    @Test
    fun `несостоявшиеся уроки отбираются отдельно от остальных`() {
        val lessons = listOf(
            lesson(1, LessonStatus.FAILED),
            lesson(2, LessonStatus.COMPLETED),
            lesson(3, LessonStatus.FAILED),
            lesson(4, LessonStatus.ANALYZED),
        )

        assertEquals(listOf(1L, 3L), LessonHousekeeping.failed(lessons).map { it.id })
    }

    @Test
    fun `урок с пустым путём к записи считается без записи`() {
        val lessons = listOf(
            lesson(1, LessonStatus.ANALYZED, audioPath = "/data/lesson_1.wav"),
            lesson(2, LessonStatus.ANALYZED, audioPath = null),
            lesson(3, LessonStatus.ANALYZED, audioPath = ""),
        )

        assertEquals(listOf(1L), LessonHousekeeping.withRecording(lessons).map { it.id })
    }

    @Test
    fun `размер записи читается человеком`() {
        assertEquals("0 МБ", LessonHousekeeping.formatSize(0))
        assertEquals("512 КБ", LessonHousekeeping.formatSize(524_288))
        assertEquals("2,0 МБ", LessonHousekeeping.formatSize(2_097_152))
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonHousekeepingTest*"`
Expected: FAIL — компиляция не проходит, `LessonHousekeeping` и `LessonStatus.SKIPPED` не существуют.

- [ ] **Step 3: Добавить состояние `SKIPPED`**

В `app/src/main/java/com/example/personallangmaster/data/db/Enums.kt` замените строку с `LessonStatus`:

```kotlin
/**
 * Жизненный цикл урока: от активной сессии до разобранного.
 *
 * `SKIPPED` — урок, который человек закрыл сам: разбирать его не нужно, и
 * напоминать о нём больше нечего. Фоновый разбор берёт только `COMPLETED`,
 * поэтому такой урок выпадает из очереди сам собой.
 */
enum class LessonStatus { ACTIVE, COMPLETED, FAILED, ANALYZED, SKIPPED }
```

- [ ] **Step 4: Написать минимальную реализацию**

Создайте `app/src/main/java/com/example/personallangmaster/domain/LessonHousekeeping.kt`:

```kotlin
package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import java.util.Locale

/** Отбор уроков для массовых операций и подписи к ним. */
object LessonHousekeeping {

    /** Урок, который всё ещё ждёт разбора: только такие напоминают о себе. */
    fun needsAnalysis(lesson: LessonEntity): Boolean = lesson.status == LessonStatus.COMPLETED

    /** Несостоявшиеся уроки: их набирается больше всего, и чистят их пачкой. */
    fun failed(lessons: List<LessonEntity>): List<LessonEntity> =
        lessons.filter { it.status == LessonStatus.FAILED }

    /** Уроки, от которых остался файл записи. */
    fun withRecording(lessons: List<LessonEntity>): List<LessonEntity> =
        lessons.filter { !it.audioPath.isNullOrBlank() }

    /** «2,0 МБ» — размер записи в подписи пункта меню. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 МБ"
        bytes < BYTES_IN_MB -> "%.0f КБ".format(RU, bytes / 1024.0)
        else -> "%.1f МБ".format(RU, bytes / BYTES_IN_MB.toDouble())
    }

    private const val BYTES_IN_MB = 1024L * 1024
    private val RU = Locale("ru")
}
```

- [ ] **Step 5: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonHousekeepingTest*"`
Expected: PASS

- [ ] **Step 6: Убедиться, что новое состояние не сломало существующий код**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Все `when` по `LessonStatus` в проекте имеют ветку `else`, поэтому компиляция не должна ругаться. Если компилятор укажет на неисчерпывающий `when` — добавьте ветку `SKIPPED` в указанном месте, не меняя остальную логику.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/data/db/Enums.kt app/src/main/java/com/example/personallangmaster/domain/LessonHousekeeping.kt app/src/test/java/com/example/personallangmaster/domain/LessonHousekeepingTest.kt
git commit -m "Состояние урока SKIPPED и чистая логика отбора уроков"
```

---

### Task 2: Транскрипт в текст

Чистая функция, на которой держатся и секция транскрипта на экране разбора, и отправка текстом.

**Files:**
- Create: `app/src/main/java/com/example/personallangmaster/domain/TranscriptFormatter.kt`
- Test: `app/src/test/java/com/example/personallangmaster/domain/TranscriptFormatterTest.kt`

**Interfaces:**
- Consumes: `TurnEntity`, `Speaker` из `com.example.personallangmaster.data.db`.
- Produces: `TranscriptFormatter.plain(turns: List<TurnEntity>): String`

- [ ] **Step 1: Написать падающий тест**

Создайте `app/src/test/java/com/example/personallangmaster/domain/TranscriptFormatterTest.kt`:

```kotlin
package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.entity.TurnEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Транскрипт, который человек читает на экране и отправляет себе в заметки. */
class TranscriptFormatterTest {

    private fun turn(index: Int, speaker: Speaker, text: String) =
        TurnEntity(lessonId = 1, index = index, speaker = speaker, text = text)

    @Test
    fun `реплики подписаны по-русски и идут по порядку`() {
        val text = TranscriptFormatter.plain(
            listOf(
                turn(0, Speaker.TUTOR, "How was your day?"),
                turn(1, Speaker.USER, "It was fine."),
            )
        )

        assertEquals("Тренер: How was your day?\nЯ: It was fine.", text)
    }

    @Test
    fun `пустой транскрипт даёт пустую строку, а не мусор`() {
        assertEquals("", TranscriptFormatter.plain(emptyList()))
    }

    @Test
    fun `пустые реплики не оставляют пустых строк`() {
        val text = TranscriptFormatter.plain(
            listOf(
                turn(0, Speaker.USER, "Hello"),
                turn(1, Speaker.TUTOR, "   "),
                turn(2, Speaker.USER, "Bye"),
            )
        )

        assertEquals("Я: Hello\nЯ: Bye", text)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranscriptFormatterTest*"`
Expected: FAIL — `TranscriptFormatter` не существует.

- [ ] **Step 3: Написать минимальную реализацию**

Создайте `app/src/main/java/com/example/personallangmaster/domain/TranscriptFormatter.kt`:

```kotlin
package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.entity.TurnEntity

/** Разговор в читаемый текст: для экрана разбора и для отправки в заметки. */
object TranscriptFormatter {

    fun plain(turns: List<TurnEntity>): String = turns
        // Распознавание иногда отдаёт пустую реплику; в тексте от неё остаётся
        // только дырка, поэтому такие выбрасываем.
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { turn ->
            val who = if (turn.speaker == Speaker.USER) "Я" else "Тренер"
            "$who: ${turn.text.trim()}"
        }
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranscriptFormatterTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/domain/TranscriptFormatter.kt app/src/test/java/com/example/personallangmaster/domain/TranscriptFormatterTest.kt
git commit -m "Форматирование транскрипта урока в текст"
```

---

### Task 3: Режим выбора нескольких уроков

Чистое состояние выбора. Реализуется до UI, потому что именно здесь прячутся ошибки вроде «выбранным остался удалённый урок».

**Files:**
- Create: `app/src/main/java/com/example/personallangmaster/domain/LessonSelection.kt`
- Test: `app/src/test/java/com/example/personallangmaster/domain/LessonSelectionTest.kt`

**Interfaces:**
- Produces: `LessonSelection(active: Boolean, ids: Set<Long>)` с методами `start(lessonId)`, `toggle(lessonId)`, `clear()`, `retain(existing: Set<Long>)` и свойством `count`.

- [ ] **Step 1: Написать падающий тест**

Создайте `app/src/test/java/com/example/personallangmaster/domain/LessonSelectionTest.kt`:

```kotlin
package com.example.personallangmaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Режим выбора нескольких уроков в истории. */
class LessonSelectionTest {

    @Test
    fun `по умолчанию режим выключен и никто не выбран`() {
        val selection = LessonSelection()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `долгое нажатие включает режим и сразу выбирает урок`() {
        val selection = LessonSelection().start(7)

        assertTrue(selection.active)
        assertEquals(setOf(7L), selection.ids)
    }

    @Test
    fun `повторный тап снимает выбор с урока`() {
        val selection = LessonSelection().start(7).toggle(8).toggle(8)

        assertEquals(setOf(7L), selection.ids)
    }

    @Test
    fun `когда снят последний выбор, режим выключается сам`() {
        val selection = LessonSelection().start(7).toggle(7)

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `удалённые уроки перестают быть выбранными`() {
        val selection = LessonSelection().start(7).toggle(8).retain(setOf(8L, 9L))

        assertTrue(selection.active)
        assertEquals(setOf(8L), selection.ids)
    }

    @Test
    fun `когда исчезли все выбранные уроки, режим выключается`() {
        val selection = LessonSelection().start(7).retain(setOf(9L))

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `сброс выключает режим целиком`() {
        val selection = LessonSelection().start(7).toggle(8).clear()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonSelectionTest*"`
Expected: FAIL — `LessonSelection` не существует.

- [ ] **Step 3: Написать минимальную реализацию**

Создайте `app/src/main/java/com/example/personallangmaster/domain/LessonSelection.kt`:

```kotlin
package com.example.personallangmaster.domain

/**
 * Какие уроки выбраны в истории.
 *
 * Пустой выбор и выключенный режим — одно и то же состояние: панель с действиями
 * над пустым списком ничего не значит и только мешает.
 */
data class LessonSelection(
    val active: Boolean = false,
    val ids: Set<Long> = emptySet(),
) {

    val count: Int get() = ids.size

    /** Долгое нажатие: включает режим и сразу выбирает урок, по которому нажали. */
    fun start(lessonId: Long): LessonSelection =
        LessonSelection(active = true, ids = setOf(lessonId))

    fun toggle(lessonId: Long): LessonSelection =
        of(if (lessonId in ids) ids - lessonId else ids + lessonId)

    fun clear(): LessonSelection = LessonSelection()

    /** Оставляет выбранными только те уроки, которые ещё есть в списке. */
    fun retain(existing: Set<Long>): LessonSelection = of(ids intersect existing)

    private fun of(next: Set<Long>): LessonSelection =
        if (next.isEmpty()) LessonSelection() else LessonSelection(active = true, ids = next)
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonSelectionTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/domain/LessonSelection.kt app/src/test/java/com/example/personallangmaster/domain/LessonSelectionTest.kt
git commit -m "Состояние режима выбора нескольких уроков"
```

---

### Task 4: Поле пометки и миграция базы на версию 2

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/entity/ProfileAndLesson.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/AppDatabase.kt`
- Создастся автоматически: `app/schemas/com.example.personallangmaster.data.db.AppDatabase/2.json`

**Interfaces:**
- Produces: поле `LessonEntity.note: String?`, константа `AppDatabase.MIGRATION_1_2`.

- [ ] **Step 1: Добавить поле в сущность урока**

В `app/src/main/java/com/example/personallangmaster/data/db/entity/ProfileAndLesson.kt` в `LessonEntity` после `val status: LessonStatus = LessonStatus.ACTIVE,` добавьте:

```kotlin
    /** Своя пометка от руки: «говорили про работу», «плохая связь». */
    val note: String? = null,
```

- [ ] **Step 2: Поднять версию базы и добавить миграцию**

В `app/src/main/java/com/example/personallangmaster/data/db/AppDatabase.kt` добавьте импорты:

```kotlin
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
```

Замените `version = 1,` на `version = 2,` и перепишите `companion object`:

```kotlin
    companion object {
        private const val NAME = "personallangmaster.db"

        /**
         * Пометка к уроку. Историю уроков терять нельзя, поэтому миграция
         * настоящая, а не сброс базы.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lesson ADD COLUMN note TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Внешние ключи чистят историю урока вместе с самим уроком.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
```

- [ ] **Step 3: Собрать проект и убедиться, что схема версии 2 сгенерировалась**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, появился файл `app/schemas/com.example.personallangmaster.data.db.AppDatabase/2.json`.

- [ ] **Step 4: Проверить, что в новой схеме есть колонка**

Run: `grep -c '"fieldPath": "note"' app/schemas/com.example.personallangmaster.data.db.AppDatabase/2.json`
Expected: `1`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/data/db/entity/ProfileAndLesson.kt app/src/main/java/com/example/personallangmaster/data/db/AppDatabase.kt app/schemas
git commit -m "Пометка к уроку и миграция базы на версию 2"
```

---

### Task 5: Запросы к базе для действий над уроками

Room проверяет SQL при компиляции, поэтому отдельным тестом эти запросы не покрываются: сборка и есть проверка.

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/dao/LessonDao.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/dao/VocabDao.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/data/db/dao/StatsDao.kt`

**Interfaces:**
- Produces:
  - `LessonDao.getByIds(lessonIds: List<Long>): List<LessonEntity>`
  - `LessonDao.deleteLessons(lessonIds: List<Long>)`
  - `LessonDao.clearAudioPaths(lessonIds: List<Long>)`
  - `LessonDao.setNote(lessonId: Long, note: String?)`
  - `LessonDao.setStatus(lessonId: Long, status: LessonStatus)`
  - `LessonDao.unlinkMistakes(lessonIds: List<Long>)`
  - `LessonDao.deleteMistakesOfLessons(lessonIds: List<Long>)`
  - `VocabDao.unlinkLessons(lessonIds: List<Long>)`
  - `VocabDao.deleteOfLessons(lessonIds: List<Long>)`
  - `StatsDao.unlinkLessons(lessonIds: List<Long>)`
  - `StatsDao.deleteOfLessons(lessonIds: List<Long>)`

- [ ] **Step 1: Добавить запросы в `LessonDao`**

В `app/src/main/java/com/example/personallangmaster/data/db/dao/LessonDao.kt` после метода `getById` добавьте:

```kotlin
    @Query("SELECT * FROM lesson WHERE id IN (:lessonIds)")
    suspend fun getByIds(lessonIds: List<Long>): List<LessonEntity>

    /** Реплики уйдут сами: у них внешний ключ на урок с каскадом. */
    @Query("DELETE FROM lesson WHERE id IN (:lessonIds)")
    suspend fun deleteLessons(lessonIds: List<Long>)

    @Query("UPDATE lesson SET audioPath = NULL WHERE id IN (:lessonIds)")
    suspend fun clearAudioPaths(lessonIds: List<Long>)

    @Query("UPDATE lesson SET note = :note WHERE id = :lessonId")
    suspend fun setNote(lessonId: Long, note: String?)

    @Query("UPDATE lesson SET status = :status WHERE id = :lessonId")
    suspend fun setStatus(lessonId: Long, status: LessonStatus)
```

После метода `markResolved` добавьте:

```kotlin
    /**
     * Обнуляет ссылку на урок, сохраняя саму ошибку: статистика и рекомендации
     * тем продолжают на неё опираться даже после удаления разговора.
     */
    @Query("UPDATE mistake SET lessonId = NULL WHERE lessonId IN (:lessonIds)")
    suspend fun unlinkMistakes(lessonIds: List<Long>)

    @Query("DELETE FROM mistake WHERE lessonId IN (:lessonIds)")
    suspend fun deleteMistakesOfLessons(lessonIds: List<Long>)
```

- [ ] **Step 2: Добавить запросы в `VocabDao`**

В `app/src/main/java/com/example/personallangmaster/data/db/dao/VocabDao.kt` после метода `delete` добавьте:

```kotlin
    /** Слово переживает удаление урока: учить его всё равно нужно. */
    @Query("UPDATE vocab_item SET sourceLessonId = NULL WHERE sourceLessonId IN (:lessonIds)")
    suspend fun unlinkLessons(lessonIds: List<Long>)

    @Query("DELETE FROM vocab_item WHERE sourceLessonId IN (:lessonIds)")
    suspend fun deleteOfLessons(lessonIds: List<Long>)
```

- [ ] **Step 3: Добавить запросы в `StatsDao`**

В `app/src/main/java/com/example/personallangmaster/data/db/dao/StatsDao.kt` после метода `deleteUsageOlderThan` добавьте:

```kotlin
    /** Деньги потрачены независимо от того, сохранился ли урок: месячный итог должен сойтись. */
    @Query("UPDATE usage_log SET lessonId = NULL WHERE lessonId IN (:lessonIds)")
    suspend fun unlinkLessons(lessonIds: List<Long>)

    @Query("DELETE FROM usage_log WHERE lessonId IN (:lessonIds)")
    suspend fun deleteOfLessons(lessonIds: List<Long>)
```

- [ ] **Step 4: Собрать проект — Room проверит SQL**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Ошибки вида «there is a problem with the query» означают опечатку в SQL — чините её, а не отключайте проверку.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/data/db/dao
git commit -m "Запросы для удаления уроков, отвязки результатов и пометок"
```

---

### Task 6: Репозиторий действий над прошедшими уроками

**Files:**
- Create: `app/src/main/java/com/example/personallangmaster/data/repo/LessonHistoryRepository.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/di/AppContainer.kt`

**Interfaces:**
- Consumes: запросы DAO из Task 5, `TranscriptFormatter.plain` из Task 2.
- Produces:
  - `enum class DeleteScope { KEEP_RESULTS, WITH_RESULTS }`
  - `LessonHistoryRepository.delete(lessonIds: List<Long>, scope: DeleteScope)`
  - `LessonHistoryRepository.deleteRecordings(lessonIds: List<Long>): Long`
  - `LessonHistoryRepository.recordingSizes(lessons: List<LessonEntity>): Map<Long, Long>`
  - `LessonHistoryRepository.markSkipped(lessonId: Long)`
  - `LessonHistoryRepository.setNote(lessonId: Long, note: String)`
  - `LessonHistoryRepository.transcript(lessonId: Long): String`
  - `AppContainer.lessonHistoryRepository`

- [ ] **Step 1: Создать репозиторий**

Создайте `app/src/main/java/com/example/personallangmaster/data/repo/LessonHistoryRepository.kt`:

```kotlin
package com.example.personallangmaster.data.repo

import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.dao.StatsDao
import com.example.personallangmaster.data.db.dao.VocabDao
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.domain.TranscriptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Что делать с ошибками, словами и расходами удаляемого урока. */
enum class DeleteScope {
    /** Отвязать: результаты учёбы остаются, пропадает только связь с уроком. */
    KEEP_RESULTS,

    /** Удалить всё, что урок после себя оставил. */
    WITH_RESULTS,
}

/**
 * Действия над прошедшими уроками: удаление, чистка записей, пометка, состояние.
 *
 * Держится отдельно от [LessonRepository]: тот отвечает за запись урока в момент
 * разговора, а здесь всё, что происходит с уроком много позже — и ошибка здесь
 * стоит человеку потерянных данных, а не сорванной реплики.
 */
class LessonHistoryRepository(
    private val lessonDao: LessonDao,
    private val vocabDao: VocabDao,
    private val statsDao: StatsDao,
) {

    /**
     * Удаляет уроки вместе с записями и репликами.
     *
     * Ошибки, слова и расходы ссылаются на урок обычным полем без внешнего ключа,
     * поэтому решение по ним принимается явно: иначе они начнут указывать в пустоту.
     */
    suspend fun delete(lessonIds: List<Long>, scope: DeleteScope) {
        if (lessonIds.isEmpty()) return

        // Файл записи не должен пережить урок: ссылки на него уже не останется.
        deleteRecordings(lessonIds)

        when (scope) {
            DeleteScope.KEEP_RESULTS -> {
                lessonDao.unlinkMistakes(lessonIds)
                vocabDao.unlinkLessons(lessonIds)
                statsDao.unlinkLessons(lessonIds)
            }

            DeleteScope.WITH_RESULTS -> {
                lessonDao.deleteMistakesOfLessons(lessonIds)
                vocabDao.deleteOfLessons(lessonIds)
                statsDao.deleteOfLessons(lessonIds)
            }
        }

        lessonDao.deleteLessons(lessonIds)
    }

    /** Удаляет файлы записей и ссылки на них. Возвращает, сколько места освободилось. */
    suspend fun deleteRecordings(lessonIds: List<Long>): Long {
        if (lessonIds.isEmpty()) return 0

        val lessons = lessonDao.getByIds(lessonIds)
        val freed = withContext(Dispatchers.IO) {
            lessons.sumOf { lesson ->
                val path = lesson.audioPath
                if (path.isNullOrBlank()) return@sumOf 0L
                val file = File(path)
                // Размер читаем до удаления: после него длина всегда ноль.
                val size = runCatching { file.length() }.getOrDefault(0L)
                runCatching { file.delete() }
                size
            }
        }

        lessonDao.clearAudioPaths(lessonIds)
        return freed
    }

    /**
     * Размер записи каждого урока. Картой, а не суммой: размер нужен и в подписи
     * пункта меню отдельного урока, и в итоге по всему месяцу, а читать длину файла
     * при каждой перерисовке списка нельзя — это диск в главном потоке.
     */
    suspend fun recordingSizes(lessons: List<LessonEntity>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            lessons.mapNotNull { lesson ->
                val path = lesson.audioPath
                if (path.isNullOrBlank()) return@mapNotNull null
                val size = runCatching { File(path).length() }.getOrDefault(0L)
                lesson.id to size
            }.toMap()
        }

    /** Урок закрыт без разбора: ошибки и слова из него не появятся. */
    suspend fun markSkipped(lessonId: Long) =
        lessonDao.setStatus(lessonId, LessonStatus.SKIPPED)

    /** Пустая пометка — это её отсутствие, а не пустая строка в карточке. */
    suspend fun setNote(lessonId: Long, note: String) =
        lessonDao.setNote(lessonId, note.trim().ifBlank { null })

    suspend fun transcript(lessonId: Long): String =
        TranscriptFormatter.plain(lessonDao.getTurns(lessonId))
}
```

- [ ] **Step 2: Зарегистрировать репозиторий в контейнере**

В `app/src/main/java/com/example/personallangmaster/di/AppContainer.kt` добавьте импорт:

```kotlin
import com.example.personallangmaster.data.repo.LessonHistoryRepository
```

И после объявления `lessonRepository` добавьте:

```kotlin
    val lessonHistoryRepository: LessonHistoryRepository by lazy {
        LessonHistoryRepository(
            lessonDao = database.lessonDao(),
            vocabDao = database.vocabDao(),
            statsDao = database.statsDao(),
        )
    }
```

- [ ] **Step 3: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/data/repo/LessonHistoryRepository.kt app/src/main/java/com/example/personallangmaster/di/AppContainer.kt
git commit -m "Репозиторий действий над прошедшими уроками"
```

---

### Task 7: Действия над уроком в ProgressViewModel

Состояние экрана и отложенное удаление с отменой. UI появится в следующих задачах — здесь только логика.

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/ui/progress/ProgressViewModel.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/ui/progress/ProgressScreen.kt:52-60` (вызов фабрики)

**Interfaces:**
- Consumes: `LessonHistoryRepository`, `DeleteScope`, `LessonHousekeeping`, `LessonSelection`, `AppContainer.appScope`.
- Produces (публичная поверхность `ProgressViewModel`):
  - поля `ProgressUiState`: `selection: LessonSelection`, `note: NoteRequest?`, `message: HistoryMessage?`, `shareText: String?`, `recordingSizes: Map<Long, Long>` и производные свойства `recordingBytes: Long`, `recordingLessons: Int`
  - `data class HistoryMessage(val text: String, val undoable: Boolean)`
  - `data class NoteRequest(val lessonId: Long, val text: String)`
  - `deleteLessons(ids: List<Long>, scope: DeleteScope)`
  - `undoDelete()`
  - `deleteRecordings(ids: List<Long>)`
  - `markSkipped(lessonId: Long)`
  - `openNote(lessonId: Long)` / `saveNote(lessonId: Long, text: String)` / `dismissNote()`
  - `requestShare(lessonId: Long)` / `consumeShare()`
  - `consumeMessage()`
  - `startSelection(lessonId: Long)` / `toggleSelection(lessonId: Long)` / `clearSelection()`
  - `failedLessonIds(): List<Long>` / `recordingLessonIds(): List<Long>`
  - поле `ProgressUiState.failedCount: Int`
  - `factory(...)` с новыми параметрами `lessonHistoryRepository` и `appScope`

- [ ] **Step 1: Расширить состояние экрана**

В `ProgressViewModel.kt` добавьте импорты:

```kotlin
import com.example.personallangmaster.data.repo.DeleteScope
import com.example.personallangmaster.data.repo.LessonHistoryRepository
import com.example.personallangmaster.domain.LessonHousekeeping
import com.example.personallangmaster.domain.LessonSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
```

Над `data class ProgressUiState` добавьте:

```kotlin
/** Сообщение внизу экрана. Отменять можно только удаление — пока оно не выполнено. */
data class HistoryMessage(val text: String, val undoable: Boolean = false)

/** Открытый диалог пометки вместе с тем, что в нём уже написано. */
data class NoteRequest(val lessonId: Long, val text: String)
```

В `ProgressUiState` добавьте поля после `val filter: LessonFilter = LessonFilter.ALL,`:

```kotlin
    val selection: LessonSelection = LessonSelection(),
    val note: NoteRequest? = null,
    val message: HistoryMessage? = null,
    val shareText: String? = null,
    /** Размер записи по уроку. Урока нет в карте — значит записи у него нет. */
    val recordingSizes: Map<Long, Long> = emptyMap(),
    /** Сколько несостоявшихся уроков в месяце — счётчик в пункте меню. */
    val failedCount: Int = 0,
```

А в тело `ProgressUiState` рядом с существующим `canGoForward` добавьте производные свойства:

```kotlin
    val recordingBytes: Long get() = recordingSizes.values.sum()
    val recordingLessons: Int get() = recordingSizes.size
```

- [ ] **Step 2: Принять новые зависимости**

Замените конструктор класса:

```kotlin
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
```

- [ ] **Step 3: Добавить поля отложенного удаления и скрытых уроков**

После `private val month = MutableStateFlow(YearMonth.now())` добавьте:

```kotlin
    /** Уроки, которые уже исчезли с экрана, но ещё не удалены из базы. */
    private var hidden: Set<Long> = emptySet()

    private var pendingDelete: PendingDelete? = null
    private var pendingJob: Job? = null

    private data class PendingDelete(val ids: List<Long>, val scope: DeleteScope)
```

- [ ] **Step 4: Учесть скрытые уроки и новое состояние в фильтрации**

Замените тело `applyFilters()` целиком:

```kotlin
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
```

- [ ] **Step 5: Добавить действия над уроками**

После метода `selectDay` добавьте:

```kotlin
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
        // что первое человек уже не отменит.
        commitPendingDelete()

        hidden = hidden + ids
        pendingDelete = PendingDelete(ids, scope)
        pendingJob = appScope.launch {
            delay(UNDO_MILLIS)
            commitPendingDelete()
        }

        _state.update {
            it.copy(
                selection = it.selection.clear(),
                message = HistoryMessage(
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
        appScope.launch { history.delete(pending.ids, pending.scope) }
    }

    fun deleteRecordings(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val freed = history.deleteRecordings(ids)
            _state.update {
                it.copy(
                    selection = it.selection.clear(),
                    message = HistoryMessage(
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
            _state.update { it.copy(message = HistoryMessage("Урок закрыт без разбора")) }
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
                    it.copy(message = HistoryMessage("Транскрипт этого урока не сохранился"))
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
```

- [ ] **Step 6: Добавить заголовок для нового состояния и обновить фабрику**

В `companion object` замените `lessonStatusTitle` и `factory`:

```kotlin
        /** Подпись состояния урока в истории. */
        fun lessonStatusTitle(status: LessonStatus): String = when (status) {
            LessonStatus.ANALYZED -> "Разобран"
            LessonStatus.COMPLETED -> "Не разобран"
            LessonStatus.FAILED -> "Не состоялся"
            LessonStatus.SKIPPED -> "Закрыт без разбора"
            LessonStatus.ACTIVE -> "Идёт"
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
```

В том же `companion object` рядом с `private const val DAYS = 7` добавьте:

```kotlin
        /** Сколько секунд у человека есть на «Отменить». */
        private const val UNDO_MILLIS = 5_000L
```

- [ ] **Step 7: Обновить вызов фабрики на экране**

В `ProgressScreen.kt` замените создание ViewModel:

```kotlin
    val viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.factory(
            container.profileRepository,
            container.statsRepository,
            container.vocabRepository,
            container.database.lessonDao(),
            container.lessonHistoryRepository,
            container.appScope,
        )
    )
```

- [ ] **Step 8: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Прогнать все тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/ui/progress
git commit -m "Действия над уроками и отложенное удаление в ProgressViewModel"
```

---

### Task 8: Диалоги подтверждения и пометки

**Files:**
- Create: `app/src/main/java/com/example/personallangmaster/ui/progress/LessonActionDialogs.kt`

**Interfaces:**
- Produces:
  - `@Composable DeleteLessonsDialog(count: Int, onDismiss: () -> Unit, onConfirm: (DeleteScope) -> Unit)`
  - `@Composable MarkSkippedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit)`
  - `@Composable ClearRecordingsDialog(count: Int, sizeLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit)`
  - `@Composable LessonNoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit)`

- [ ] **Step 1: Создать файл с диалогами**

Создайте `app/src/main/java/com/example/personallangmaster/ui/progress/LessonActionDialogs.kt`:

```kotlin
package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.data.repo.DeleteScope

/**
 * Подтверждение удаления уроков.
 *
 * Ошибки, слова и расходы ссылаются на урок без внешнего ключа, поэтому решение
 * по ним человек принимает здесь. По умолчанию они остаются: удалять историю
 * разговора и терять при этом выученные слова — не то, чего от кнопки ждут.
 */
@Composable
fun DeleteLessonsDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (DeleteScope) -> Unit,
) {
    var withResults by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (count == 1) "Удалить урок?" else "Удалить уроков: $count?")
        },
        text = {
            Column {
                Text("Разговор и его запись пропадут без возможности восстановить.")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withResults, onCheckedChange = { withResults = it })
                    Text("Удалить и результаты урока")
                }
                Text(
                    text = if (withResults) {
                        "Ошибки, слова из этого урока и записи о расходе тоже исчезнут: " +
                            "просядут рекомендации тем и цифры расходов за месяц."
                    } else {
                        "Ошибки останутся в статистике, слова — в словаре, " +
                            "а потраченное — в итоге за месяц."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        if (withResults) DeleteScope.WITH_RESULTS else DeleteScope.KEEP_RESULTS
                    )
                }
            ) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Урок закрывается без разбора — и про отсутствие ошибок и слов говорим честно. */
@Composable
fun MarkSkippedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закрыть без разбора?") },
        text = {
            Text(
                "Урок перестанет напоминать о себе. Это только смена состояния: " +
                    "ошибки и слова из него не появятся, оценок за урок не будет."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ClearRecordingsDialog(
    count: Int,
    sizeLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очистить все записи?") },
        text = {
            Text(
                "Записей: $count, занимают $sizeLabel. Транскрипты и разборы останутся — " +
                    "пропадёт только возможность переслушать разговор."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Одна строка от руки: «говорили про работу», «плохая связь». */
@Composable
fun LessonNoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пометка к уроку") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Например: говорили про работу") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
```

- [ ] **Step 2: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/ui/progress/LessonActionDialogs.kt
git commit -m "Диалоги удаления урока, закрытия без разбора и пометки"
```

---

### Task 9: Карточка урока с меню действий

Карточка переезжает из `ProgressScreen.kt` в свой файл и обзаводится меню и чекбоксом выбора.

**Files:**
- Create: `app/src/main/java/com/example/personallangmaster/ui/progress/LessonHistoryCard.kt`

`ProgressScreen.kt` эта задача **не трогает**: старую отрисовку карточки заменяет Task 10, иначе экран останется в полуразобранном виде между двумя задачами.

**Interfaces:**
- Consumes: `ProgressViewModel.lessonStatusTitle`, `LessonEntity`.
- Produces:

```kotlin
@Composable
fun LessonHistoryCard(
    lesson: LessonEntity,
    selectionActive: Boolean,
    selected: Boolean,
    recordingSize: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onOpenTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
    onEditNote: () -> Unit,
    onMarkSkipped: () -> Unit,
    onDeleteRecording: () -> Unit,
    onDelete: () -> Unit,
)
```

- [ ] **Step 1: Создать карточку**

Создайте `app/src/main/java/com/example/personallangmaster/ui/progress/LessonHistoryCard.kt`:

```kotlin
package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Урок в истории под календарём: сводка, пометка и меню действий. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonHistoryCard(
    lesson: LessonEntity,
    selectionActive: Boolean,
    selected: Boolean,
    recordingSize: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onOpenTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
    onEditNote: () -> Unit,
    onMarkSkipped: () -> Unit,
    onDeleteRecording: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionActive) {
                // Сам чекбокс не кликается: нажатие по карточке и так переключает выбор,
                // а две точки нажатия с разным поведением сбивают с толку.
                Checkbox(checked = selected, onCheckedChange = null)
            }

            Column(Modifier.weight(1f).padding(start = if (selectionActive) 8.dp else 0.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDateTime(lesson.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${lesson.durationSec / 60} мин · " +
                            CostCalculator.formatUsd(lesson.costUsd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Состояние видно сразу: неразобранный урок — это предложение
                // открыть его и разобрать, а не потеря.
                Text(
                    text = ProgressViewModel.lessonStatusTitle(lesson.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (lesson.status) {
                        LessonStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                        LessonStatus.FAILED, LessonStatus.SKIPPED ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )

                lesson.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                lesson.summaryRu?.let { summary ->
                    Text(
                        text = summary.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // В режиме выбора меню одного урока только мешает: действия наверху.
            if (!selectionActive) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с уроком")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Транскрипт") },
                            onClick = { menuOpen = false; onOpenTranscript() },
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться транскриптом") },
                            onClick = { menuOpen = false; onShareTranscript() },
                        )
                        DropdownMenuItem(
                            text = { Text("Пометка") },
                            onClick = { menuOpen = false; onEditNote() },
                        )
                        if (lesson.status != LessonStatus.ANALYZED &&
                            lesson.status != LessonStatus.SKIPPED
                        ) {
                            DropdownMenuItem(
                                text = { Text("Отметить разобранным") },
                                onClick = { menuOpen = false; onMarkSkipped() },
                            )
                        }
                        if (recordingSize != null) {
                            DropdownMenuItem(
                                text = { Text("Удалить запись") },
                                trailingIcon = {
                                    Text(
                                        text = recordingSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { menuOpen = false; onDeleteRecording() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить урок") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMMM, HH:mm"))
```

- [ ] **Step 2: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Ожидается предупреждение о неиспользуемом `LessonHistoryCard` — его подключит следующая задача.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/ui/progress/LessonHistoryCard.kt
git commit -m "Карточка урока в истории с меню действий"
```

---

### Task 10: Сборка экрана прогресса

Snackbar с отменой, панель режима выбора, массовые действия и подключение карточки и диалогов.

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/ui/progress/ProgressScreen.kt`

**Interfaces:**
- Consumes: всё, что произвели Task 7 (ViewModel), Task 8 (диалоги), Task 9 (карточка).
- Produces: `ProgressScreen(onOpenReview: (Long) -> Unit, onOpenTranscript: (Long) -> Unit)` — новый параметр навигации на транскрипт.

- [ ] **Step 1: Обновить сигнатуру и каркас экрана**

В `ProgressScreen.kt` замените сигнатуру и `Scaffold`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    onOpenReview: (Long) -> Unit,
    onOpenTranscript: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.factory(
            container.profileRepository,
            container.statsRepository,
            container.vocabRepository,
            container.database.lessonDao(),
            container.lessonHistoryRepository,
            container.appScope,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    var deleteRequest by remember { mutableStateOf<List<Long>?>(null) }
    var skipRequest by remember { mutableStateOf<Long?>(null) }
    var clearRecordingsRequest by remember { mutableStateOf(false) }

    // Сообщение и отмена живут здесь: ViewModel не должна знать про Snackbar.
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = message.text,
            actionLabel = if (message.undoable) "Отменить" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.consumeMessage()
    }

    LaunchedEffect(state.shareText) {
        val text = state.shareText ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PersonalLangMaster: транскрипт урока")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться транскриптом"))
        viewModel.consumeShare()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_progress)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
```

Добавьте импорты:

```kotlin
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.example.personallangmaster.domain.LessonHousekeeping
```

- [ ] **Step 2: Заменить заголовок секции истории на строку с меню массовых действий**

Замените блок `SectionTitle(state.selectedDay?.let { ... })` на:

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.selectedDay?.let { day -> "Уроки ${formatDay(day)}" }
                        ?: "Уроки за месяц (${state.lessonsTotal})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                var listMenuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { listMenuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Действия со списком")
                    }
                    DropdownMenu(
                        expanded = listMenuOpen,
                        onDismissRequest = { listMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Удалить все несостоявшиеся (${state.failedCount})") },
                            enabled = state.failedCount > 0,
                            onClick = {
                                listMenuOpen = false
                                deleteRequest = viewModel.failedLessonIds()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Очистить все записи · " +
                                        LessonHousekeeping.formatSize(state.recordingBytes)
                                )
                            },
                            enabled = state.recordingLessons > 0,
                            onClick = {
                                listMenuOpen = false
                                clearRecordingsRequest = true
                            },
                        )
                    }
                }
            }
```

- [ ] **Step 3: Добавить панель режима выбора перед списком уроков**

Сразу после блока с `FilterChip` добавьте:

```kotlin
            if (state.selection.active) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Выбрано: ${state.selection.count}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = { deleteRequest = state.selection.ids.toList() }) {
                            Text("Удалить")
                        }
                        TextButton(onClick = viewModel::clearSelection) { Text("Снять") }
                    }
                }
            }
```

- [ ] **Step 4: Заменить отрисовку карточек**

Замените весь блок `state.recentLessons.forEach { lesson -> Card(...) { ... } }` на:

```kotlin
            state.recentLessons.forEach { lesson ->
                LessonHistoryCard(
                    lesson = lesson,
                    selectionActive = state.selection.active,
                    selected = lesson.id in state.selection.ids,
                    recordingSize = state.recordingSizes[lesson.id]
                        ?.let(LessonHousekeeping::formatSize),
                    onOpen = {
                        if (state.selection.active) viewModel.toggleSelection(lesson.id)
                        else onOpenReview(lesson.id)
                    },
                    onLongPress = { viewModel.startSelection(lesson.id) },
                    onOpenTranscript = { onOpenTranscript(lesson.id) },
                    onShareTranscript = { viewModel.requestShare(lesson.id) },
                    onEditNote = { viewModel.openNote(lesson.id) },
                    onMarkSkipped = { skipRequest = lesson.id },
                    onDeleteRecording = { viewModel.deleteRecordings(listOf(lesson.id)) },
                    onDelete = { deleteRequest = listOf(lesson.id) },
                )
            }
```

`recordingSize` берётся из уже посчитанной карты `state.recordingSizes`, а не читается с диска при отрисовке: файловый ввод-вывод в главном потоке на каждую перерисовку списка — верный способ получить рывки при прокрутке. Урока нет в карте — значит записи у него нет, и пункт «Удалить запись» в меню не появится.

- [ ] **Step 5: Подключить диалоги в конце composable**

После закрывающей скобки `Scaffold` (перед закрывающей скобкой функции `ProgressScreen`) добавьте:

```kotlin
    deleteRequest?.let { ids ->
        DeleteLessonsDialog(
            count = ids.size,
            onDismiss = { deleteRequest = null },
            onConfirm = { scope ->
                viewModel.deleteLessons(ids, scope)
                deleteRequest = null
            },
        )
    }

    skipRequest?.let { lessonId ->
        MarkSkippedDialog(
            onDismiss = { skipRequest = null },
            onConfirm = {
                viewModel.markSkipped(lessonId)
                skipRequest = null
            },
        )
    }

    if (clearRecordingsRequest) {
        ClearRecordingsDialog(
            count = state.recordingLessons,
            sizeLabel = LessonHousekeeping.formatSize(state.recordingBytes),
            onDismiss = { clearRecordingsRequest = false },
            onConfirm = {
                viewModel.deleteRecordings(viewModel.recordingLessonIds())
                clearRecordingsRequest = false
            },
        )
    }

    state.note?.let { request ->
        LessonNoteDialog(
            initial = request.text,
            onDismiss = viewModel::dismissNote,
            onSave = { text -> viewModel.saveNote(request.lessonId, text) },
        )
    }
```

- [ ] **Step 6: Удалить осиротевшую функцию**

Приватная `formatDate` в `ProgressScreen.kt` больше не используется — карточка форматирует дату сама. Удалите её. `formatDay` остаётся: её использует заголовок секции.

- [ ] **Step 7: Обновить вызов экрана в навигации**

В `app/src/main/java/com/example/personallangmaster/ui/PersonalLangMasterApp.kt` замените `entry<Route.Progress>`:

```kotlin
                entry<Route.Progress> {
                    ProgressScreen(
                        onOpenReview = { lessonId -> backStack.add(Route.Review(lessonId)) },
                        onOpenTranscript = { lessonId ->
                            backStack.add(Route.Review(lessonId, showTranscript = true))
                        },
                    )
                }
```

Здесь используется параметр `Route.Review.showTranscript`, который добавляет Task 11. **Поэтому Task 11 выполняется раньше Task 10**: она самодостаточна (у параметра маршрута и у параметра экрана есть значения по умолчанию, старые вызовы продолжают компилироваться), а Task 10 после неё собирается целиком. Если Task 11 ещё не сделана — сделайте её первой.

- [ ] **Step 8: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/ui/progress/ProgressScreen.kt app/src/main/java/com/example/personallangmaster/ui/PersonalLangMasterApp.kt
git commit -m "Экран прогресса: меню действий, режим выбора и отмена удаления"
```

---

### Task 11: Транскрипт на экране разбора урока

**Files:**
- Modify: `app/src/main/java/com/example/personallangmaster/ui/Routes.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/ui/PersonalLangMasterApp.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/ui/review/LessonReviewViewModel.kt`
- Modify: `app/src/main/java/com/example/personallangmaster/ui/review/LessonReviewScreen.kt`

**Interfaces:**
- Consumes: `TranscriptFormatter` не нужен — на экране реплики показываются построчно из `ReviewUiState.turns`.
- Produces:
  - `Route.Review(lessonId: Long, showTranscript: Boolean = false)`
  - поле `ReviewUiState.turns: List<TurnEntity>`
  - `LessonReviewScreen(lessonId: Long, showTranscript: Boolean = false, onBack: () -> Unit)`

- [ ] **Step 1: Добавить параметр в маршрут**

В `app/src/main/java/com/example/personallangmaster/ui/Routes.kt` замените `Review`:

```kotlin
    /** Разбор конкретного урока. `showTranscript` сразу раскрывает секцию с репликами. */
    @Serializable
    data class Review(val lessonId: Long, val showTranscript: Boolean = false) : Route
```

- [ ] **Step 2: Прокинуть параметр в экран**

В `PersonalLangMasterApp.kt` замените `entry<Route.Review>`:

```kotlin
                entry<Route.Review> { key ->
                    LessonReviewScreen(
                        lessonId = key.lessonId,
                        showTranscript = key.showTranscript,
                        onBack = goBack,
                    )
                }
```

- [ ] **Step 3: Отдать реплики в состояние экрана**

В `LessonReviewViewModel.kt` добавьте импорт:

```kotlin
import com.example.personallangmaster.data.db.entity.TurnEntity
```

В `ReviewUiState` после `val mistakes: List<MistakeRow> = emptyList(),` добавьте:

```kotlin
    val turns: List<TurnEntity> = emptyList(),
```

В методе `showStored` в вызове `_state.update` добавьте `turns = turns,` рядом с `mistakes = mistakes,`.

В сигнатуре `findOffset` замените полное имя типа на короткое, раз импорт теперь есть:

```kotlin
    private fun findOffset(
        turns: List<TurnEntity>,
        mistake: MistakeEntity,
    ): Long? {
```

- [ ] **Step 4: Добавить секцию транскрипта на экран**

В `LessonReviewScreen.kt` замените сигнатуру:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonReviewScreen(
    lessonId: Long,
    showTranscript: Boolean = false,
    onBack: () -> Unit,
) {
```

Добавьте импорты:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.example.personallangmaster.data.db.Speaker
```

Внутри `LessonReviewScreen` рядом с `val state by ...` добавьте:

```kotlin
    var transcriptOpen by remember { mutableStateOf(showTranscript) }
```

Перед финальным `Spacer(Modifier.height(32.dp))` в теле `Column` добавьте:

```kotlin
            if (state.turns.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { transcriptOpen = !transcriptOpen }
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Транскрипт (реплик: ${state.turns.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (transcriptOpen) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = if (transcriptOpen) "Свернуть" else "Развернуть",
                    )
                }

                if (transcriptOpen) {
                    state.turns.forEach { turn ->
                        val mine = turn.speaker == Speaker.USER
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = if (mine) "Я" else "Тренер",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (mine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                            )
                            Text(turn.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
```

- [ ] **Step 5: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Прогнать все тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/personallangmaster/ui/Routes.kt app/src/main/java/com/example/personallangmaster/ui/PersonalLangMasterApp.kt app/src/main/java/com/example/personallangmaster/ui/review
git commit -m "Транскрипт урока сворачиваемой секцией на экране разбора"
```

---

### Task 12: Проверка на устройстве и документация

**Files:**
- Modify: `docs/TODO.md`
- Modify: `Today.md`

- [ ] **Step 1: Собрать и поставить приложение**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL. Если подключённого устройства или эмулятора нет — скажите об этом и остановитесь на шаге 3, не выдавая непроверенное за проверенное.

- [ ] **Step 2: Пройти сценарии руками**

Проверьте на экране «Прогресс»:

1. Миграция: приложение запускается на базе, созданной до изменений, история уроков на месте.
2. Меню на карточке открывается, в нём нет «Удалить запись» у урока без записи.
3. Удаление одного урока: диалог, урок исчезает, Snackbar «Урок удалён · Отменить», отмена возвращает урок.
4. Удаление без отмены: через пять секунд урок пропадает окончательно, после перезахода в раздел его нет.
5. Удаление с включённым чекбоксом «Удалить и результаты урока»: расходы за месяц уменьшаются.
6. Удаление с выключенным чекбоксом: расходы за месяц не меняются, слова остаются в словаре.
7. «Отметить разобранным»: подпись урока меняется на «Закрыт без разбора», урок пропадает из фильтра «Без разбора».
8. Пометка сохраняется и видна на карточке после перезахода в раздел.
9. «Транскрипт» открывает разбор урока с раскрытой секцией реплик.
10. «Поделиться транскриптом» открывает системный выбор приложения с текстом реплик.
11. Долгое нажатие включает режим выбора; выбор нескольких уроков и «Удалить» удаляют их пачкой.
12. «Удалить все несостоявшиеся» и «Очистить все записи» работают, размер в подписи похож на правду.

- [ ] **Step 3: Вычеркнуть сделанное из TODO**

В `docs/TODO.md` удалите раздел «Управление уроками» целиком вместе с блоком «Что важно не сломать при удалении»: он сделан. Если что-то из раздела осталось за рамками — перенесите это одной строкой в «Прочее, отмеченное по ходу»:

```markdown
- **Отправка записи урока файлом** и **повторный разбор урока** не сделаны: первое
  требует `FileProvider`, второе — чистки ошибок урока перед повторным вызовом модели,
  иначе ошибки задваиваются.
```

- [ ] **Step 4: Добавить запись в журнал**

В `Today.md` в секцию текущего дня добавьте одну строку и пересчитайте итог дня:

```markdown
- [6h] Управление уроками в истории: удаление с отменой и выбором судьбы ошибок, слов и расходов, чистка записей, закрытие урока без разбора, пометки, транскрипт и отправка его текстом, режим выбора нескольких уроков с массовыми операциями; база переведена на версию 2 с миграцией.
```

- [ ] **Step 5: Commit**

```bash
git add docs/TODO.md Today.md
git commit -m "Управление уроками: закрыт раздел TODO, запись в журнал"
```

---

## Самопроверка плана

- **Покрытие спецификации.** Удалить урок — Task 7, 8, 10. Удалить только запись — Task 6, 7, 9, 10. Отметить разобранным — Task 1, 6, 7, 8. Открыть транскрипт — Task 11. Поделиться транскриптом — Task 2, 6, 7, 10. Своя пометка — Task 4, 5, 6, 7, 8. Режим выбора — Task 3, 7, 9, 10. «Удалить все несостоявшиеся» — Task 1, 10. «Очистить все записи» — Task 1, 6, 10. Отвязка или удаление результатов — Task 5, 6, 8. Отмена удаления — Task 7, 10. Миграция — Task 4.
- **Исключения соблюдены.** `FileProvider` и отправка аудиофайла не встречаются нигде; «Разобрать заново» тоже — и про оба сказано в «Global Constraints» и в Task 12.
- **Согласованность имён.** `DeleteScope`, `LessonHistoryRepository.delete/deleteRecordings/recordingBytes/markSkipped/setNote/transcript`, `LessonHousekeeping.needsAnalysis/failed/withRecording/formatSize`, `LessonSelection.start/toggle/clear/retain/count`, `LessonHistoryCard(...)` — имена в поздних задачах совпадают с объявленными в ранних.
