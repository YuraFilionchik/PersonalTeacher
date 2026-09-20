# Задачи для параллельного ИИ-агента

Документ для случая, когда параллельно со мной работает второй, более слабый агент.
Принцип разделения: **ему — изолированные файлы с точной спецификацией и проверяемым
результатом, мне — всё, где нужен контекст всего приложения.**

Главное правило: мы не редактируем одни и те же файлы. Границы владения ниже.

---

## Разделение владения файлами

| Владелец | Пути |
| :-- | :-- |
| **Я** | `ai/**`, `data/db/**`, `data/prefs/**`, `di/**`, `domain/**`, `ui/screens/**`, `ui/navigation/**`, `work/**`, `core/audio/AudioRecorder.kt`, `core/audio/AudioPlayer.kt`, `MainActivity.kt` |
| **Агент** | `app/src/main/assets/**`, `core/srs/**`, `core/cost/**`, `core/export/**`, `core/audio/Pcm.kt`, `core/audio/EnergyVad.kt`, `ui/components/**`, `ui/theme/Type.kt`, `ui/theme/Shape.kt`, `res/values*/strings.xml`, `app/src/test/**` для своих задач |
| **Никто без согласования** | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `AndroidManifest.xml` |

---

## Общая преамбула (вставлять в начало каждого промпта агенту)

```
Проект: E:\Study\programming\PersonalTeacher — Android-приложение PersonalLangMaster
(персональный ИИ-репетитор английского). Kotlin, Jetpack Compose, Material 3,
пакет com.example.personallangmaster, minSdk 29, Java 17, kotlinx.serialization.

Общий план проекта: docs/IMPLEMENTATION_PLAN.md — прочитай нужный раздел перед работой.

Жёсткие правила:
1. Создавай и меняй ТОЛЬКО файлы, перечисленные в задаче. Ничего больше не трогай.
2. НЕ редактируй app/build.gradle.kts, gradle/libs.versions.toml, settings.gradle.kts,
   AndroidManifest.xml. Новые зависимости добавлять нельзя — используй только то,
   что уже подключено (Compose, Room, DataStore, OkHttp, kotlinx.serialization,
   coroutines, WorkManager, Coil).
3. Код на Kotlin, без Java. Никаких RxJava, Hilt, Dagger, Retrofit, Moshi, Gson.
4. Интерфейс приложения на русском языке; учебный контент — на английском с русским
   переводом там, где это указано в задаче.
5. Публичные функции документируй KDoc в одну-две строки. Комментарии по делу, без воды.
6. Если спецификация чего-то не покрывает — выбери простое решение и напиши об этом
   в конце отчёта, не изобретай новых сущностей.
7. В конце выведи список созданных файлов и результат команды проверки из задачи.
```

---

## Волна A — можно запускать прямо сейчас, задачи независимы друг от друга

### P1. Сид-контент в `assets/` (самая объёмная и самая безопасная задача)

**Почему агенту:** это данные, а не логика. Объём большой, ошибка дёшево правится,
на код приложения не влияет.

**Файлы:** `app/src/main/assets/seed/scenarios.json`, `grammar_topics.json`,
`minimal_pairs.json`, `phonemes.json`.

**Промпт:**
```
<преамбула>

Задача: подготовить учебный сид-контент в JSON. Схемы полей смотри в
docs/IMPLEMENTATION_PLAN.md §3 (таблицы scenario, grammar_topic) и §7.

1) assets/seed/scenarios.json — 30 ролевых сценариев разговора.
   Поля: code (snake_case), titleRu, titleEn, descriptionRu, cefrMin, cefrMax
   (из A1,A2,B1,B2,C1), systemPromptExtra (на английском: роль тренера, роль ученика,
   цель разговора, как себя вести), openingLine (первая реплика тренера на английском),
   tags (массив), iconKey (одно слово: cafe, airport, hotel, doctor, interview, ...),
   vocabHints (8-12 полезных слов/выражений с полями term и translationRu),
   successCriteria (2-4 пункта на русском: по чему считать, что цель достигнута).
   Покрой бытовые, рабочие, путешествия и эмоционально нагруженные ситуации
   (жалоба, спор, отказ, извинение). Уровни распредели равномерно.

2) assets/seed/grammar_topics.json — 40 грамматических тем уровней A1–B2.
   Поля: code, titleRu, titleEn, cefr, explanationRu (150-250 слов, живым языком,
   с 3-5 примерами на английском и переводом, с явным указанием типичной ошибки
   русскоязычного ученика), explanationEn (60-100 слов), commonMistakes (массив
   объектов wrong/right/whyRu), relatedCodes (массив code смежных тем).
   Обязательно включи: времена группы Simple/Continuous/Perfect, артикли,
   предлоги места и времени, модальные глаголы, условные предложения,
   порядок слов, исчисляемые/неисчисляемые, степени сравнения, пассив,
   косвенную речь, герундий и инфинитив.

3) assets/seed/minimal_pairs.json — 60 минимальных пар для тренировки слуха и
   произношения. Поля: id, word1, word2, ipa1, ipa2, phonemeFocus (IPA одного звука),
   hintRu (в чём разница, как поставить артикуляцию), exampleSentence (одно
   предложение, где обе формы звучат осмысленно, либо два отдельных примера).
   Приоритет — пары, трудные именно для русскоязычных: /iː/–/ɪ/, /æ/–/e/, /θ/–/s/,
   /ð/–/z/, /w/–/v/, /ŋ/–/n/, /r/, /æ/–/ʌ/, /ɜː/–/ɔː/.

4) assets/seed/phonemes.json — все фонемы английского (RP + General American, ~44).
   Поля: ipa, kind (vowel/diphthong/consonant), exampleWords (3 слова),
   articulationRu (1-2 предложения: что делать языком, губами, голосом),
   russianTrapRu (типичная ошибка русскоязычного, 1 предложение),
   difficultyForRu (1-3, где 3 — самый трудный).

Требования к результату: валидный JSON в UTF-8 без BOM, отступ 2 пробела,
никаких комментариев внутри JSON, массив верхнего уровня.
Проверка: для каждого файла выполни `python -c "import json;json.load(open(PATH, encoding='utf-8'))"`
и покажи, что ошибок нет, а также выведи количество элементов в каждом файле.
```

**Приёмка:** JSON валиден, количество элементов совпадает, выборочно 3 темы грамматики
читаются как нормальный человеческий текст.

---

### P2. Алгоритм интервального повторения SM-2

**Почему агенту:** чистая функция, классический алгоритм, полностью покрывается тестами.

**Файлы:** `core/srs/Sm2Scheduler.kt`, `core/srs/SrsModels.kt`,
`app/src/test/java/com/example/personallangmaster/core/srs/Sm2SchedulerTest.kt`.

**Промпт:**
```
<преамбула>

Задача: реализовать планировщик интервальных повторений SM-2 как чистый Kotlin-код
без зависимостей от Android.

core/srs/SrsModels.kt:
  enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }   // 0..3
  enum class SrsState { NEW, LEARNING, REVIEW, MATURE, SUSPENDED }
  data class SrsCard(
      val ease: Double,          // фактор лёгкости, стартовый 2.5, минимум 1.3
      val intervalDays: Int,
      val repetitions: Int,
      val lapses: Int,
      val state: SrsState,
      val dueAtEpochDay: Long,
      val lastReviewedEpochDay: Long?,
  )

core/srs/Sm2Scheduler.kt:
  object Sm2Scheduler {
      fun newCard(todayEpochDay: Long): SrsCard
      fun schedule(card: SrsCard, grade: ReviewGrade, todayEpochDay: Long): SrsCard
      fun isDue(card: SrsCard, todayEpochDay: Long): Boolean
  }

Правила:
- AGAIN: repetitions = 0, lapses + 1, интервал 0 (сегодня же), ease - 0.20,
  состояние LEARNING.
- HARD: интервал * 1.2, ease - 0.15.
- GOOD: первое успешное повторение — 1 день, второе — 6 дней, дальше
  интервал * ease.
- EASY: как GOOD, но результат * 1.3 и ease + 0.15.
- ease всегда зажат в диапазон 1.3..3.0, интервал не больше 365 дней.
- MATURE — когда intervalDays >= 21, REVIEW — когда repetitions >= 2 и интервал < 21.
- SUSPENDED планировщик не трогает: возвращает карточку без изменений.

Тесты (не меньше 15 кейсов): путь новой карточки через GOOD,GOOD,GOOD до MATURE;
сброс на AGAIN с ростом lapses; зажим ease снизу после серии AGAIN; зажим сверху
после серии EASY; потолок интервала; поведение SUSPENDED; isDue на границе дня.
Проверка: gradlew :app:testDebugUnitTest --tests "*Sm2SchedulerTest*"
```

**Приёмка:** тесты зелёные, ease и интервалы не выходят за границы.

---

### P3. PCM-утилиты и энергетический VAD

**Почему агенту:** чистая арифметика над `ByteArray`, тестируется синтетическим сигналом.
Я подключу это к `AudioRecorder`, который пишу сам.

**Файлы:** `core/audio/Pcm.kt`, `core/audio/EnergyVad.kt`,
`app/src/test/java/com/example/personallangmaster/core/audio/PcmTest.kt`,
`.../EnergyVadTest.kt`.

**Промпт:**
```
<преамбула>

Задача: утилиты обработки звука. Формат везде: PCM 16 бит, моно, little-endian,
частота задаётся параметром (вход 16000 Гц, выход тренера 24000 Гц).
Android API использовать нельзя — только чистый Kotlin, чтобы работало в unit-тестах.

core/audio/Pcm.kt — object Pcm:
  fun rms(pcm: ByteArray): Double                       // среднеквадратичное, 0.0..1.0
  fun dbfs(pcm: ByteArray): Double                      // -100.0..0.0
  fun peak(pcm: ByteArray): Double
  fun durationMs(byteCount: Int, sampleRate: Int): Long
  fun bytesForMs(ms: Int, sampleRate: Int): Int         // кратно 2
  fun chunk(pcm: ByteArray, chunkBytes: Int): List<ByteArray>
  fun applyGain(pcm: ByteArray, gain: Double): ByteArray // с защитой от клиппинга
  fun toFloatSamples(pcm: ByteArray): FloatArray        // -1.0..1.0, для визуализации
  fun downsampleForWaveform(pcm: ByteArray, buckets: Int): FloatArray
      // амплитуды по корзинам, ровно buckets значений, для рисования волны

core/audio/EnergyVad.kt — class EnergyVad(
      val sampleRate: Int = 16000,
      val startThresholdDb: Double = -38.0,   // громче этого = речь
      val stopThresholdDb: Double = -45.0,    // тише этого = тишина (гистерезис)
      val minSpeechMs: Int = 120,
      val silenceHangoverMs: Int = 800,
  ) {
      sealed interface Event { object SpeechStart : Event
                               object SpeechEnd : Event
                               data class Level(val dbfs: Double) : Event }
      fun process(frame: ByteArray): List<Event>   // кадр 10-30 мс
      fun reset()
      val isSpeaking: Boolean
  }
Логика: речь начинается, когда громкость выше startThreshold дольше minSpeechMs;
заканчивается, когда тише stopThreshold дольше silenceHangoverMs. Level отдавать
на каждом кадре — он нужен для анимации волны.

Тесты: тишина не даёт SpeechStart; синусоида даёт ровно один SpeechStart;
короткий щелчок короче minSpeechMs игнорируется; пауза короче hangover не рвёт речь;
пауза длиннее hangover даёт SpeechEnd; rms и dbfs на известных сигналах
(тишина, полная амплитуда, половина амплитуды); chunk не теряет байты;
downsampleForWaveform возвращает ровно buckets значений.
Проверка: gradlew :app:testDebugUnitTest --tests "*core.audio*"
```

**Приёмка:** тесты зелёные, сигнатуры точно как в спецификации (я на них закладываюсь).

---

### P4. Строковые ресурсы

**Почему агенту:** механическая, но объёмная работа, которая иначе будет тормозить каждый экран.

**Файлы:** `res/values/strings.xml` (русский, по умолчанию), `res/values-en/strings.xml`.

**Промпт:**
```
<преамбула>

Задача: собрать строковые ресурсы приложения. Русский — язык по умолчанию
(res/values/strings.xml), английский — res/values-en/strings.xml с теми же ключами.

Источник текстов: docs/IMPLEMENTATION_PLAN.md — разделы §4 (каталог настроек,
все секции и пункты), §8.2 и §8.3 (экраны и подписи кнопок).

Соглашение об именах ключей: <экран>_<элемент>, например settings_tutor_voice_title,
settings_tutor_voice_subtitle, lesson_mic_hold_hint, lesson_state_thinking,
onboarding_api_key_help, review_mistakes_empty.

Покрой: названия пяти разделов навигации, все заголовки и подписи настроек §4,
состояния экрана урока, тексты ошибок соединения из §5.2 (каждому ErrorKind —
заголовок и объяснение, что делать), онбординг, экран разбора урока, словарь,
произношение, грамматика, прогресс, диалоги подтверждения, названия пяти персон
тренера и пяти уровней строгости с их описаниями.

Требования: без склеивания строк из кусков, для чисел использовать plurals
(например «N слов на повторение»), форматные аргументы именовать через %1$s/%1$d.
Существующий ключ app_name не менять. Комментарии <!-- --> для группировки секций.
Проверка: оба файла — валидный XML (python -c "import xml.dom.minidom as m;m.parse(PATH)"),
наборы ключей в двух файлах совпадают ровно (покажи результат сравнения).
```

**Приёмка:** ключи совпадают в обоих файлах, русский текст живой, без кальки с английского.

---

### P5. Калькулятор стоимости запросов

**Почему агенту:** формулы уже описаны, нужен аккуратный перенос в код и тесты.

**Файлы:** `core/cost/PricingTable.kt`, `core/cost/CostCalculator.kt`,
`app/src/test/java/com/example/personallangmaster/core/cost/CostCalculatorTest.kt`.

**Промпт:**
```
<преамбула>

Задача: посчитать стоимость использования Gemini API. Исходные тарифы и коэффициенты —
docs/gemini_live_api.md §4 и §5. Чистый Kotlin, без Android.

core/cost/PricingTable.kt:
  data class PricingTable(
      val textInputPerMTok: Double = 0.75,
      val textOutputPerMTok: Double = 4.50,
      val audioInputPerMTok: Double = 3.00,
      val audioOutputPerMTok: Double = 12.00,
      val cacheCreatePerMTok: Double = 0.075,
      val cacheStoragePerMTokHour: Double = 0.50,
  ) — значения должны легко подменяться, потому что тарифы меняются.
  Константы: AUDIO_INPUT_TOKENS_PER_SECOND = 25, AUDIO_OUTPUT_TOKENS_PER_SECOND = 50.

core/cost/CostCalculator.kt — object CostCalculator:
  fun audioInputTokens(seconds: Double): Long
  fun audioOutputTokens(seconds: Double): Long
  fun costUsd(tokens: Long, perMTok: Double): Double
  fun estimateLessonCostUsd(minutes: Double, userSpeakShare: Double = 0.5,
                            systemPromptTokens: Long = 250,
                            table: PricingTable = PricingTable()): Double
  fun formatUsd(amount: Double): String        // "$0.21", для очень малых — "<$0.01"
  fun formatUsdRub(amount: Double, rate: Double): String  // "$0.21 (~19 ₽)"

Тесты: сверь с расчётами из docs/gemini_live_api.md — минута голосового диалога
должна выходить ≈ $0.021, десятиминутный урок ≈ $0.21, час ≈ $1.26
(допуск 5%). Проверь граничные случаи: ноль минут, дробные секунды,
подмена тарифов в PricingTable, форматирование очень маленькой суммы.
Проверка: gradlew :app:testDebugUnitTest --tests "*CostCalculatorTest*"
```

**Приёмка:** цифры сходятся с документом, тарифы выносятся параметром.

---

### P6. Stateless UI-компоненты с превью

**Почему агенту:** это чистая вёрстка по макету из плана, без бизнес-логики.
Я потом подключу их к ViewModel.

**Файлы:** `ui/components/MicButton.kt`, `WaveformVisualizer.kt`, `CorrectionCard.kt`,
`SubtitleLine.kt`, `LevelBadge.kt`, `StatTile.kt`, `SettingRow.kt`, `StreakRing.kt`.

**Промпт:**
```
<преамбула>

Задача: набор переиспользуемых Compose-компонентов. Все — stateless: состояние
приходит параметрами, события уходят лямбдами. Никаких ViewModel, репозиториев,
корутин и обращений к данным. Каждому компоненту — минимум два @Preview
(светлая и тёмная тема). Макет экрана урока: docs/IMPLEMENTATION_PLAN.md §8.2,
цветовые роли: §8.4.

1. MicButton(state: MicButtonState, levelDbfs: Double, onPress, onRelease, onTap, modifier)
   где MicButtonState = READY | LISTENING | THINKING | SPEAKING | DISABLED.
   Большая круглая кнопка 96dp, вокруг — кольцо, реагирующее на levelDbfs.
   READY — подпись «Держите, чтобы говорить»; LISTENING — пульсация;
   THINKING — индикатор ожидания; SPEAKING — вид «Перебить».
   Поддержи оба жеста: удержание (onPress/onRelease) и одиночный тап (onTap).

2. WaveformVisualizer(amplitudes: FloatArray, modifier, color) — рисование на Canvas,
   симметричные столбики от центра, плавная анимация изменения высот.

3. CorrectionCard(original: String, corrected: String, explanation: String?,
   onListen, onAddToVocab, onDismiss) — карточка поправки.
   Ошибку НЕ красим в error-цвет: используем tertiary (это учебная поправка,
   а не сбой приложения). Свайп в сторону — onDismiss.

4. SubtitleLine(speaker: Speaker, text: String, isPartial: Boolean) —
   реплика тренера слева, ученика справа; при isPartial в конце мигающий курсор.

5. LevelBadge(cefr: String, trend: Trend /*UP|FLAT|DOWN*/) — компактный бейдж уровня.

6. StatTile(title: String, value: String, caption: String?, icon: ImageVector) —
   плитка статистики для экранов «Главная» и «Прогресс».

7. SettingRow(title, subtitle, icon, trailing: @Composable () -> Unit, onClick) —
   строка настройки; сделай перегрузки-обёртки SettingSwitchRow и SettingSliderRow.

8. StreakRing(daysDone: Int, goal: Int, streak: Int) — кольцо прогресса дня
   с числом дней подряд в центре.

Требования: Material 3, только цвета из MaterialTheme.colorScheme (никаких
захардкоженных Color(0xFF...)), размеры кратны 4dp, все кликабельные элементы
не меньше 48dp, у каждого — contentDescription. Тексты брать параметрами
или из stringResource, не хардкодить в компоненте.
Проверка: gradlew :app:compileDebugKotlin
```

**Приёмка:** собирается, превью рисуются в Android Studio, нет захардкоженных цветов.

---

## Волна B — после того, как я сделаю M1 (каркас данных)

### P7. Экспорт и импорт данных

**Файлы:** `core/export/VocabCsv.kt`, `core/export/ProfileBackup.kt`, тесты к ним.

Экспорт словаря в CSV, совместимый с Anki (разделитель — табуляция, экранирование
кавычек и переводов строк), импорт обратно с валидацией. Бэкап профиля в JSON через
kotlinx.serialization, с полем `version` и проверкой при импорте.
Работа идёт с промежуточными data-классами, которые агент определяет сам, —
маппинг на Room-сущности делаю я.

### P8. Тесты стрика, целей и дневной статистики

**Файлы:** `core/progress/StreakCalculator.kt`, `core/progress/DailyGoal.kt`, тесты.

Чистая логика на `LocalDate`: продолжение и обрыв серии, «заморозки» (2 в месяц),
смена часового пояса, выполнение дневной цели по минутам, недельная сводка.
Граничные случаи вокруг полуночи обязательны.

---

## Волна C — после того, как я сделаю M3 (Live-сессия)

### P9. Запись сессии для офлайн-разработки UI

**Файлы:** `app/src/debug/assets/fake_session.json`, `ai/live/FakeLiveSession.kt`
(создаётся агентом по интерфейсу, который к тому моменту уже будет зафиксирован мной).

Сценарий диалога с таймингами, чтобы экран урока можно было отлаживать без единого
платного запроса к API. Это заметно экономит и время, и деньги.

---

## Что нельзя отдавать слабому агенту

| Область | Почему |
| :-- | :-- |
| `LiveWebSocketClient`, `LiveProtocol`, state-машина сессии | Протокол не описан ни в одном учебнике, ошибки проявляются только на живом соединении и стоят денег |
| `AudioRecorder` / `AudioPlayer` / foreground service | Гонки потоков, аудиофокус, barge-in — типичное место, где слабая модель делает правдоподобный, но нерабочий код |
| `TutorPromptBuilder`, `StrictnessPolicy`, схемы разбора | Это и есть педагогика продукта; качество промпта определяет ценность приложения целиком |
| `AppContainer`, навигация, экраны настроек | Связывают всё вместе, нужен контекст всего проекта |
| Любые правки build-файлов и манифеста | Параллельные правки гарантированно конфликтуют |
| Обновления версий библиотек | Цепные поломки, которые потом дороже разбирать, чем сделать самому |

---

## Как принимать работу агента

1. `gradlew :app:compileDebugKotlin` — собирается.
2. `gradlew :app:testDebugUnitTest` — тесты зелёные.
3. `git diff --stat` (после `git init`) — изменены только файлы из задачи.
4. Выборочно прочитать 2–3 куска: нет ли выдуманных зависимостей, захардкоженных
   цветов и строк, «заглушек» вида `TODO()` в теле функции.
5. Для контентных задач (P1, P4) — прочитать 3–5 случайных элементов целиком:
   слабые модели склонны деградировать к концу длинного файла и начинать копировать
   один и тот же шаблон.

> Совет: заводи под каждую задачу отдельную ветку или хотя бы отдельный запуск агента.
> Один агент — одна задача из этого списка, без «заодно поправь ещё вот это».
