package com.example.personallangmaster.ai.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Протокол Live API проверяем на записанных сообщениях: живое соединение стоит
 * денег и недоступно в CI, а ошибка в одном имени поля ломает весь урок молча.
 */
class LiveProtocolTest {

    private val json = Json { prettyPrint = false }

    // --- Разбор сообщений сервера ---

    @Test
    fun `setupComplete распознаётся`() {
        val message = liveJson.decodeFromString<ServerMessage>("""{"setupComplete":{}}""")
        assertNotNull(message.setupComplete)
    }

    @Test
    fun `аудио и расшифровка тренера приходят вместе`() {
        val raw = """
            {"serverContent":{
              "modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAAA"}}]},
              "outputTranscription":{"text":"How was your day?"}
            }}
        """.trimIndent()

        val content = liveJson.decodeFromString<ServerMessage>(raw).serverContent
        assertNotNull(content)
        assertEquals("audio/pcm;rate=24000", content!!.modelTurn?.parts?.first()?.inlineData?.mimeType)
        assertEquals("AAAA", content.modelTurn?.parts?.first()?.inlineData?.data)
        assertEquals("How was your day?", content.outputTranscription?.text)
    }

    @Test
    fun `расшифровка речи ученика читается`() {
        val raw = """{"serverContent":{"inputTranscription":{"text":"I go to school","languageCode":"en-US"}}}"""
        val content = liveJson.decodeFromString<ServerMessage>(raw).serverContent
        assertEquals("I go to school", content?.inputTranscription?.text)
    }

    @Test
    fun `перебивание распознаётся`() {
        val raw = """{"serverContent":{"interrupted":true}}"""
        assertEquals(true, liveJson.decodeFromString<ServerMessage>(raw).serverContent?.interrupted)
    }

    @Test
    fun `вызов инструмента разбирается вместе с аргументами`() {
        val raw = """
            {"toolCall":{"functionCalls":[
              {"id":"call_1","name":"log_mistake",
               "args":{"type":"TENSE","original":"I go yesterday","corrected":"I went yesterday","severity":2}}
            ]}}
        """.trimIndent()

        val call = liveJson.decodeFromString<ServerMessage>(raw).toolCall?.functionCalls?.single()
        assertNotNull(call)
        assertEquals("log_mistake", call!!.name)
        assertEquals("call_1", call.id)
        assertEquals("I go yesterday", call.args?.get("original")?.jsonPrimitive?.content)
        assertEquals("2", call.args?.get("severity")?.jsonPrimitive?.content)
    }

    @Test
    fun `служебные сообщения сессии разбираются`() {
        val raw = """
            {"usageMetadata":{"promptTokenCount":120,"responseTokenCount":340},
             "sessionResumptionUpdate":{"newHandle":"handle-42","resumable":true},
             "goAway":{"timeLeft":"30s"}}
        """.trimIndent()

        val message = liveJson.decodeFromString<ServerMessage>(raw)
        assertEquals(120, message.usageMetadata?.promptTokenCount)
        assertEquals(340, message.usageMetadata?.responseTokenCount)
        assertEquals("handle-42", message.sessionResumptionUpdate?.newHandle)
        assertEquals(true, message.sessionResumptionUpdate?.resumable)
        assertEquals("30s", message.goAway?.timeLeft)
    }

    @Test
    fun `неизвестные поля сервера не ломают разбор`() {
        val raw = """{"serverContent":{"turnComplete":true,"somethingBrandNew":{"x":1}},"futureField":42}"""
        val message = liveJson.decodeFromString<ServerMessage>(raw)
        assertEquals(true, message.serverContent?.turnComplete)
    }

    // --- Сборка сообщений клиента ---

    @Test
    fun `setup содержит всё, без чего урок не работает`() {
        val setup = Setup(
            model = "models/gemini-3.8-live",
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                temperature = 0.8f,
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(PrebuiltVoiceConfig("Puck")),
                    languageCode = "en-US",
                ),
            ),
            systemInstruction = Content(parts = listOf(Part(text = "You are a tutor"))),
            tools = LiveTools.declarations,
            realtimeInputConfig = RealtimeInputConfig(AutomaticActivityDetection(disabled = true)),
            contextWindowCompression = ContextWindowCompression(),
            inputAudioTranscription = Empty(),
            outputAudioTranscription = Empty(),
        )

        val encoded = liveJson.encodeToString(SetupMessage(setup))
        val tree = json.parseToJsonElement(encoded).jsonObject["setup"]!!.jsonObject

        assertEquals("models/gemini-3.8-live", tree["model"]?.jsonPrimitive?.content)
        // Без этих двух полей не будет ни субтитров, ни материала для разбора урока.
        assertTrue(encoded.contains("\"inputAudioTranscription\":{}"))
        assertTrue(encoded.contains("\"outputAudioTranscription\":{}"))
        // Ручные границы реплики — основа режима push-to-talk.
        assertEquals(
            true,
            tree["realtimeInputConfig"]?.jsonObject
                ?.get("automaticActivityDetection")?.jsonObject
                ?.get("disabled")?.jsonPrimitive?.content?.toBoolean(),
        )
        assertTrue(encoded.contains("\"slidingWindow\":{}"))
        assertTrue(encoded.contains("log_mistake"))
    }

    @Test
    fun `аудио уходит в поле realtimeInput audio`() {
        val encoded = liveJson.encodeToString(
            RealtimeInputMessage(
                RealtimeInput(audio = Blob(mimeType = "audio/pcm;rate=16000", data = "QUJD"))
            )
        )

        val audio = json.parseToJsonElement(encoded)
            .jsonObject["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject

        assertEquals("audio/pcm;rate=16000", audio["mimeType"]?.jsonPrimitive?.content)
        assertEquals("QUJD", audio["data"]?.jsonPrimitive?.content)
        // Пустые поля не сериализуются: лишний JSON в каждом чанке аудио стоит денег.
        assertTrue(!encoded.contains("null"))
        assertTrue(!encoded.contains("activityStart"))
    }

    @Test
    fun `границы реплики сериализуются пустыми объектами`() {
        val start = liveJson.encodeToString(RealtimeInputMessage(RealtimeInput(activityStart = Empty())))
        val end = liveJson.encodeToString(RealtimeInputMessage(RealtimeInput(activityEnd = Empty())))

        assertEquals("""{"realtimeInput":{"activityStart":{}}}""", start)
        assertEquals("""{"realtimeInput":{"activityEnd":{}}}""", end)
    }

    @Test
    fun `текстовая реплика закрывает ход`() {
        val encoded = liveJson.encodeToString(
            ClientContentMessage(
                ClientContent(
                    turns = listOf(Content(parts = listOf(Part(text = "Hello")), role = "user")),
                    turnComplete = true,
                )
            )
        )

        assertTrue(encoded.contains("\"turnComplete\":true"))
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue(encoded.contains("Hello"))
    }

    @Test
    fun `ответ на инструмент несёт идентификатор вызова`() {
        val encoded = liveJson.encodeToString(
            ToolResponseMessage(
                ToolResponse(
                    functionResponses = listOf(
                        FunctionResponse(id = "call_1", name = "save_vocab", response = LiveTools.ok())
                    )
                )
            )
        )

        val response = json.parseToJsonElement(encoded)
            .jsonObject["toolResponse"]!!.jsonObject["functionResponses"]!!
        assertTrue(response.toString().contains("call_1"))
        assertTrue(response.toString().contains("\"ok\":true"))
    }

    @Test
    fun `объявления инструментов покрывают весь набор`() {
        val names = LiveTools.declarations
            .flatMap { it.functionDeclarations }
            .map { it.name }

        assertEquals(
            listOf(
                LiveTools.SAVE_VOCAB,
                LiveTools.LOG_MISTAKE,
                LiveTools.SET_DIFFICULTY,
                LiveTools.SHOW_CARD,
                LiveTools.SUGGEST_DRILL,
                LiveTools.END_LESSON,
            ),
            names,
        )

        val logMistake = LiveTools.declarations.first().functionDeclarations
            .first { it.name == LiveTools.LOG_MISTAKE }
        val required = logMistake.parameters?.get("required").toString()
        assertTrue(required.contains("original"))
        assertTrue(required.contains("corrected"))
    }

    @Test
    fun `пустая схема параметров не содержит required`() {
        val endLesson = LiveTools.declarations.first().functionDeclarations
            .first { it.name == LiveTools.END_LESSON }
        val params: JsonObject? = endLesson.parameters
        assertNotNull(params)
        assertNull(params!!["required"])
    }
}
