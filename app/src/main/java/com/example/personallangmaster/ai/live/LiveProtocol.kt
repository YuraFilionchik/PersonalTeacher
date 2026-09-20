package com.example.personallangmaster.ai.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Модели протокола Gemini Live API (BidiGenerateContent, v1beta).
 *
 * Описаны только поля, которые приложение реально шлёт и читает: протокол живой,
 * и чем меньше в нём зафиксировано, тем реже его придётся чинить. Неизвестные
 * поля сервера игнорируются, необъявленные значения не сериализуются.
 */

/** Пустой объект `{}` — протокол использует его как флаг во многих местах. */
@Serializable
class Empty

// --- Общие структуры ---

@Serializable
data class Blob(
    val mimeType: String,
    val data: String,
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: Blob? = null,
)

@Serializable
data class Content(
    val parts: List<Part> = emptyList(),
    val role: String? = null,
)

// --- Клиент → сервер ---

@Serializable
data class SetupMessage(val setup: Setup)

@Serializable
data class Setup(
    val model: String,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null,
    val realtimeInputConfig: RealtimeInputConfig? = null,
    val sessionResumption: SessionResumption? = null,
    val contextWindowCompression: ContextWindowCompression? = null,
    /** Пустой объект включает расшифровку речи ученика. */
    val inputAudioTranscription: Empty? = null,
    /** Пустой объект включает расшифровку речи тренера. */
    val outputAudioTranscription: Empty? = null,
)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String>? = null,
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val speechConfig: SpeechConfig? = null,
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig? = null,
    val languageCode: String? = null,
)

@Serializable
data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoiceConfig)

@Serializable
data class PrebuiltVoiceConfig(val voiceName: String)

@Serializable
data class Tool(val functionDeclarations: List<FunctionDeclaration>)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class RealtimeInputConfig(
    val automaticActivityDetection: AutomaticActivityDetection? = null,
)

/** Отключение серверного VAD: тогда границы реплики задаёт кнопка микрофона. */
@Serializable
data class AutomaticActivityDetection(val disabled: Boolean)

@Serializable
data class ContextWindowCompression(val slidingWindow: Empty = Empty())

@Serializable
data class SessionResumption(val handle: String? = null)

@Serializable
data class RealtimeInputMessage(val realtimeInput: RealtimeInput)

@Serializable
data class RealtimeInput(
    val audio: Blob? = null,
    val text: String? = null,
    val activityStart: Empty? = null,
    val activityEnd: Empty? = null,
    val audioStreamEnd: Boolean? = null,
)

@Serializable
data class ClientContentMessage(val clientContent: ClientContent)

@Serializable
data class ClientContent(
    val turns: List<Content> = emptyList(),
    /**
     * По умолчанию false намеренно: при `encodeDefaults = false` значение,
     * совпадающее с умолчанием, не сериализуется. С `true` в умолчании поле
     * молча пропадало бы из JSON, и модель не понимала бы, что ход закончен.
     */
    val turnComplete: Boolean = false,
)

@Serializable
data class ToolResponseMessage(val toolResponse: ToolResponse)

@Serializable
data class ToolResponse(val functionResponses: List<FunctionResponse>)

@Serializable
data class FunctionResponse(
    val id: String? = null,
    val name: String? = null,
    val response: JsonObject,
)

// --- Сервер → клиент ---

@Serializable
data class ServerMessage(
    val setupComplete: Empty? = null,
    val serverContent: ServerContent? = null,
    val toolCall: ToolCall? = null,
    val toolCallCancellation: ToolCallCancellation? = null,
    val usageMetadata: UsageMetadata? = null,
    val goAway: GoAway? = null,
    val sessionResumptionUpdate: SessionResumptionUpdate? = null,
)

@Serializable
data class ServerContent(
    val modelTurn: Content? = null,
    val inputTranscription: Transcription? = null,
    val outputTranscription: Transcription? = null,
    val turnComplete: Boolean? = null,
    val interrupted: Boolean? = null,
    val generationComplete: Boolean? = null,
)

@Serializable
data class Transcription(
    val text: String? = null,
    val languageCode: String? = null,
)

@Serializable
data class ToolCall(val functionCalls: List<FunctionCall> = emptyList())

@Serializable
data class FunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonObject? = null,
)

@Serializable
data class ToolCallCancellation(val ids: List<String> = emptyList())

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int? = null,
    @SerialName("responseTokenCount") val responseTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
    val cachedContentTokenCount: Int? = null,
)

/** Сервер предупреждает, что соединение скоро закроется. */
@Serializable
data class GoAway(val timeLeft: String? = null)

/** Ключ для переподключения без потери контекста урока. */
@Serializable
data class SessionResumptionUpdate(
    val newHandle: String? = null,
    val resumable: Boolean? = null,
)

/** Единая настройка сериализации для всего протокола. */
val liveJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
