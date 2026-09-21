package com.example.personallangmaster.domain

import com.example.personallangmaster.ai.text.LessonAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Разбор урока — платный вызов модели, поэтому повторы считаем строго:
 * лишний повтор тратит деньги, пропущенный теряет разбор навсегда.
 */
class AnalysisRetryPolicyTest {

    @Test
    fun `удачный разбор закрывает задачу`() {
        val result = AnalysisResult.Success(LessonAnalysis(), levelChangedTo = null)

        assertEquals(AnalysisOutcome.DONE, AnalysisRetryPolicy.decide(result, attempt = 0))
    }

    @Test
    fun `уже разобранный урок не разбирается второй раз`() {
        assertEquals(
            AnalysisOutcome.ALREADY_DONE,
            AnalysisRetryPolicy.decide(AnalysisResult.AlreadyAnalyzed, attempt = 0),
        )
    }

    @Test
    fun `обрыв сети — повторяем`() {
        val result = AnalysisResult.Failure("Нет соединения", retriable = true)

        assertEquals(AnalysisOutcome.RETRY, AnalysisRetryPolicy.decide(result, attempt = 0))
    }

    @Test
    fun `неверный ключ повторять бесполезно`() {
        val result = AnalysisResult.Failure("Ключ не принят", retriable = false)

        assertEquals(AnalysisOutcome.GIVE_UP, AnalysisRetryPolicy.decide(result, attempt = 0))
    }

    @Test
    fun `пустой транскрипт не повторяем ни при какой попытке`() {
        val result = AnalysisResult.Failure("Разговор не сохранился")

        assertEquals(AnalysisOutcome.GIVE_UP, AnalysisRetryPolicy.decide(result, attempt = 0))
    }

    @Test
    fun `после трёх попыток сдаёмся, даже если ошибка временная`() {
        val result = AnalysisResult.Failure("Квота исчерпана", retriable = true)

        assertEquals(AnalysisOutcome.RETRY, AnalysisRetryPolicy.decide(result, attempt = 1))
        assertEquals(AnalysisOutcome.GIVE_UP, AnalysisRetryPolicy.decide(result, attempt = 2))
        assertEquals(AnalysisOutcome.GIVE_UP, AnalysisRetryPolicy.decide(result, attempt = 9))
    }
}
