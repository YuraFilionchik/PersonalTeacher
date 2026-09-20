package com.example.personallangmaster.ui.settings

import com.example.personallangmaster.data.prefs.Accent
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.AudioOutput
import com.example.personallangmaster.data.prefs.AudioRecording
import com.example.personallangmaster.data.prefs.CorrectionLanguage
import com.example.personallangmaster.data.prefs.ExplanationLanguage
import com.example.personallangmaster.data.prefs.Initiative
import com.example.personallangmaster.data.prefs.LimitBehavior
import com.example.personallangmaster.data.prefs.MicMode
import com.example.personallangmaster.data.prefs.NativeLanguageUse
import com.example.personallangmaster.data.prefs.ProgressionPace
import com.example.personallangmaster.data.prefs.SubtitleMode
import com.example.personallangmaster.data.prefs.ThemeMode
import com.example.personallangmaster.data.prefs.TranscriptRetention
import com.example.personallangmaster.data.prefs.Verbosity
import com.example.personallangmaster.data.prefs.WaveStyle

/**
 * Русские подписи для вариантов настроек.
 *
 * Заголовки самих пунктов живут в strings.xml; подписи вариантов пока здесь,
 * чтобы не редактировать ресурсный файл параллельно с контентной задачей.
 */

fun accentTitle(value: Accent): String = when (value) {
    Accent.AMERICAN -> "Американский"
    Accent.BRITISH -> "Британский"
    Accent.NEUTRAL -> "Нейтральный"
}

fun verbosityTitle(value: Verbosity): String = when (value) {
    Verbosity.SHORT -> "Короткие реплики"
    Verbosity.MEDIUM -> "Средние реплики"
    Verbosity.DETAILED -> "Развёрнутые объяснения"
}

fun speechRateTitle(rate: Int): String = when (rate.coerceIn(1, 5)) {
    1 -> "Очень медленно"
    2 -> "Медленно"
    3 -> "Обычно"
    4 -> "Быстро"
    else -> "Как с носителем"
}

fun correctionLanguageTitle(value: CorrectionLanguage): String = when (value) {
    CorrectionLanguage.RU -> "Объяснять по-русски"
    CorrectionLanguage.EN -> "Только по-английски"
    CorrectionLanguage.MIXED -> "Смешанно"
}

fun explanationLanguageTitle(value: ExplanationLanguage): String = when (value) {
    ExplanationLanguage.RU -> "Русский"
    ExplanationLanguage.EN -> "Английский"
}

fun nativeLanguageUseTitle(value: NativeLanguageUse): String = when (value) {
    NativeLanguageUse.ENGLISH_ONLY -> "Только английский"
    NativeLanguageUse.ASK_ALLOWED -> "Можно переспросить"
    NativeLanguageUse.FREE -> "Свободно"
}

fun initiativeTitle(value: Initiative): String = when (value) {
    Initiative.TUTOR -> "Ведёт тренер"
    Initiative.STUDENT -> "Ведёт ученик"
    Initiative.BALANCED -> "Поровну"
}

fun progressionPaceTitle(value: ProgressionPace): String = when (value) {
    ProgressionPace.CONSERVATIVE -> "Консервативный"
    ProgressionPace.NORMAL -> "Обычный"
    ProgressionPace.AGGRESSIVE -> "Агрессивный"
}

fun micModeTitle(value: MicMode): String = when (value) {
    MicMode.HOLD -> "Удерживать кнопку"
    MicMode.TAP -> "Нажать и говорить"
    MicMode.HANDS_FREE -> "Hands-free (авто)"
}

/** Подпись под ползунком паузы: в разных режимах она значит разное. */
fun silenceHangoverHint(value: MicMode): String = when (value) {
    MicMode.TAP -> "Столько тишины должно пройти, чтобы реплика отправилась сама"
    MicMode.HANDS_FREE -> "Столько тишины отделяет одну вашу реплику от следующей"
    MicMode.HOLD -> "Влияет только на отсечение тишины внутри реплики"
}

fun micModeHint(value: MicMode): String = when (value) {
    MicMode.HOLD -> "Микрофон открыт, пока кнопка зажата: отпустили — отправили"
    MicMode.TAP -> "Нажали один раз и говорите — реплика отправится сама, " +
        "как только вы замолчите. Нажатие во время ответа перебивает тренера"
    MicMode.HANDS_FREE -> "Тренер слушает постоянно; дороже и сильнее сажает батарею"
}

fun audioOutputTitle(value: AudioOutput): String = when (value) {
    AudioOutput.AUTO -> "Автоматически"
    AudioOutput.SPEAKER -> "Динамик"
    AudioOutput.HEADSET -> "Наушники"
}

fun limitBehaviorTitle(value: LimitBehavior): String = when (value) {
    LimitBehavior.WARN -> "Только предупредить"
    LimitBehavior.SOFT_STOP -> "Мягко завершить урок"
    LimitBehavior.BLOCK -> "Заблокировать до завтра"
}

fun transcriptRetentionTitle(settings: AppSettings): String = when (settings.transcriptRetention) {
    TranscriptRetention.FOREVER -> "Транскрипты хранятся всегда"
    TranscriptRetention.DAYS -> "Транскрипты ${settings.transcriptRetentionDays} дней"
    TranscriptRetention.NEVER -> "Транскрипты не хранятся"
}

fun transcriptRetentionOption(value: TranscriptRetention): String = when (value) {
    TranscriptRetention.FOREVER -> "Хранить всегда"
    TranscriptRetention.DAYS -> "Хранить ограниченное время"
    TranscriptRetention.NEVER -> "Не хранить"
}

fun audioRecordingTitle(value: AudioRecording): String = when (value) {
    AudioRecording.NONE -> "Не записывать"
    AudioRecording.USER_ONLY -> "Только свою речь"
    AudioRecording.FULL -> "Весь урок"
}

fun subtitleModeTitle(value: SubtitleMode): String = when (value) {
    SubtitleMode.ALWAYS -> "Всегда"
    SubtitleMode.ON_TAP -> "По тапу"
    SubtitleMode.USER_ONLY -> "Только свои реплики"
    SubtitleMode.NEVER -> "Никогда"
}

fun themeModeTitle(value: ThemeMode): String = when (value) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

fun themeTitle(settings: AppSettings): String =
    themeModeTitle(settings.themeMode) + if (settings.dynamicColor) " · динамические цвета" else ""

fun waveStyleTitle(value: WaveStyle): String = when (value) {
    WaveStyle.WAVE -> "Волна"
    WaveStyle.PULSE -> "Пульсация"
    WaveStyle.MINIMAL -> "Минимализм"
}

fun weekdayShort(day: Int): String = when (day) {
    1 -> "Пн"; 2 -> "Вт"; 3 -> "Ср"; 4 -> "Чт"; 5 -> "Пт"; 6 -> "Сб"; else -> "Вс"
}
