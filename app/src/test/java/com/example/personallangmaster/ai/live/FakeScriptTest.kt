package com.example.personallangmaster.ai.live

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сценарий фейкового урока проверяем тем же сериализатором, что и приложение.
 *
 * Расхождение в имени поля ломает только один экран и только в отладке, поэтому
 * дешевле поймать его здесь, чем искать вручную на устройстве.
 */
class FakeScriptTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Сценарий лежит в debug-наборе ресурсов, поэтому читаем его как обычный файл. */
    private fun script(): FakeScript {
        val file = listOf(
            File("src/debug/assets/fake_session.json"),
            File("app/src/debug/assets/fake_session.json"),
        ).firstOrNull { it.exists() }

        assertNotNull("fake_session.json не найден (cwd=${File("").absolutePath})", file)
        return json.decodeFromString(file!!.readText())
    }

    private fun FakeScript.allEvents(): List<FakeEvent> =
        initialEvents + replies.flatMap { it.events }

    @Test
    fun `сценарий разбирается и покрывает все виды событий`() {
        val script = script()
        val events = script.allEvents()

        assertTrue("вступление пустое", script.initialEvents.isNotEmpty())
        assertTrue("нет ни одного ответа тренера", script.replies.size >= 3)

        // Что не задействовано в сценарии — то на экране урока нечем отлаживать.
        FakeEventType.entries.forEach { type ->
            assertTrue("в сценарии нет события $type", events.any { it.type == type })
        }

        assertTrue("сценарий проигрывается быстрее секунды", events.sumOf { it.delayMs } > 1_000)
    }

    @Test
    fun `у каждого события заполнены поля своего вида`() {
        script().allEvents().forEach { event ->
            assertTrue("отрицательная пауза: $event", event.delayMs >= 0)

            when (event.type) {
                FakeEventType.STATE -> assertNotNull("нет состояния: $event", event.state)

                FakeEventType.TUTOR_TEXT -> assertFalse("пустая реплика: $event", event.text.isNullOrBlank())

                FakeEventType.TOOL_MISTAKE -> {
                    assertFalse("не указан тип ошибки: $event", event.mistakeType.isNullOrBlank())
                    assertFalse("нет исходной фразы: $event", event.original.isNullOrBlank())
                    assertFalse("нет исправленной фразы: $event", event.corrected.isNullOrBlank())
                }

                FakeEventType.TOOL_VOCAB -> {
                    assertFalse("нет слова: $event", event.term.isNullOrBlank())
                    assertFalse("нет перевода: $event", event.translationRu.isNullOrBlank())
                }

                FakeEventType.TOOL_CARD -> assertFalse("пустая карточка: $event", event.textEn.isNullOrBlank())

                FakeEventType.TURN_COMPLETE -> Unit
            }
        }
    }
}
