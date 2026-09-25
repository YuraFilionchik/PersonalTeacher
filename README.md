<div align="center">

# PersonalLangMaster

**An open-source AI English tutor for Android that you actually talk to.**

Real-time voice lessons on the Gemini Live API, corrections as strict as you want them,
and a post-lesson review that turns *your own mistakes* into flashcards, grammar drills
and pronunciation practice.

**English** · [Русский](README.ru.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Gemini](https://img.shields.io/badge/Gemini-Live%20API-886FBF?logo=googlegemini&logoColor=white)](https://ai.google.dev/gemini-api/docs/live)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Tests](https://img.shields.io/badge/unit%20tests-167-success)](#testing)

<img src="docs/screenshots/home.png" width="190">&nbsp;
<img src="docs/screenshots/practice.png" width="190">&nbsp;
<img src="docs/screenshots/scenarios.png" width="190">&nbsp;
<img src="docs/screenshots/pronunciation.png" width="190">

<sub>The interface is in Russian, the language you learn is English. Grammar explanations switch between Russian and English.</sub>

</div>

---

## Why this exists

Voice tutors like Speak, Praktika or Duolingo Max charge a monthly subscription, send your
voice to their servers and decide for you how hard to push. PersonalLangMaster takes a
different route:

| | Typical subscription tutor | PersonalLangMaster |
|:--|:--|:--|
| Price | $10–30 / month | Pay Google directly, **≈ $0.02 per minute** of conversation |
| Your data | Their cloud, their account | **On your phone.** No backend, no account, no telemetry |
| Corrections | Fixed by the product | **Strictness slider** from "never interrupt" to drill mode |
| Tutor | One persona | 5 personas + voice, accent, pace, or your own system prompt |
| Practice content | Generic course | Built from **mistakes you made** in your own lessons |
| Offline | Rarely | Vocabulary, pronunciation, grammar and history work without a network |
| Source | Closed | Open, readable Kotlin |

It started as a tutor for one family: each member installs it on their own phone with
their own API key, and a parent PIN guards the key, model and spending limits.

---

## How it works

```
Voice lesson with the tutor (Gemini Live, real-time audio)
   │  instant corrections, as strict as the slider allows
   │  the tutor quietly saves new words and logs mistakes via tool calls
   ▼
Post-lesson review (structured JSON)
   │  mistakes · CEFR estimate · words to move into active use
   ▼
        ┌──────────────┬───────────────┬──────────────────┐
   vocabulary      grammar        pronunciation      progress
   spaced          topics and     minimal pairs      minutes, streak,
   repetition      exercises      and sounds         spending
        └──────────────┴───────────────┴──────────────────┘
   ▼
Your profile updates → the next lesson knows you better
```

---

## Features

### 🎙️ Live lessons
- Real-time voice over the **Gemini Live API** WebSocket: 16 kHz PCM in, 24 kHz out.
- Push-to-talk, tap-and-auto-send on silence, or hands-free. Interrupt the tutor any time.
- Subtitles for both sides and correction cards right in the conversation.
- Keeps going with the screen off via a foreground service; reconnects without losing context.
- A local voice-activity detector never sends silence, which cuts input tokens several times over.

### 🧠 Teaching that adapts
- **Strictness slider**: five levels, controlling both *when* and *how deeply* you get corrected.
- **Placement test**: a five-minute interview with no corrections that assigns your CEFR level.
  After that the level moves carefully: only after a run of consistent scores, one step at a time.
- **Tutor memory**: past lessons, recurring mistakes, weak sounds and words in progress are fed
  into every new lesson's system prompt.

### 📚 Practice between lessons, fully offline
- **Vocabulary** with SM-2 spaced repetition and four modes: recognize, recall, say it aloud,
  hear and understand. Uses the system TTS and speech recognition, so it costs nothing.
- **30 role-play scenarios** (café, job interview, doctor, argument) with roles, goals and success
  criteria. Describe a new one in a single sentence and the AI builds it.
- **Pronunciation trainer**: shadowing, minimal pairs, focus on your weak sounds and a map of
  all 44 English phonemes colored by mastery.
- **40 grammar topics**, ranked by where you actually make mistakes. Exercises are generated
  in batches from your own errors and then work offline.

### 🔒 Cost control and privacy
- Token and money counter with **daily and monthly limits**: warn, gently wrap up, or block until tomorrow.
- The API key is encrypted with an Android Keystore key and never leaves the device.
- Transcripts and recordings are kept exactly as long as you choose; audio is off by default.
- Reminders, streaks and a daily minutes goal; the reminder stays silent once the goal is met.

---

## What it costs

Only the conversation with the model is billed, by Google, to your own key.

| What | Approx. |
|:--|:--|
| One minute of voice conversation | **$0.021** |
| A 10-minute lesson | **$0.21** |
| An hour of non-stop talking | **$1.26** |
| Lesson review, exercise generation | fractions of a cent |
| Vocabulary, pronunciation, grammar practice | $0 |

Prices are editable in the settings, since Google's pricing changes more often than app
releases. The full calculation lives in [`docs/gemini_live_api.md`](docs/gemini_live_api.md).

---

## Quick start

**You need:** Android Studio (AGP 9.4+), JDK 17, Android SDK 37, a phone on Android 10+,
and a free **Gemini API key** from [Google AI Studio](https://aistudio.google.com) → *Get API key*.

```bash
git clone https://github.com/YuraFilionchik/PersonalTeacher.git
cd PersonalTeacher

./gradlew installDebug           # build and install on a connected phone
./gradlew testDebugUnitTest      # run the unit tests
```

The key never goes into the repository. Enter it during onboarding or later in
*Settings → Model and API key*; the **Check key** button makes a free model-list request
and tells you right away whether the key works.

**First run:** onboarding (name → interests → tutor persona → level → key → microphone),
then *Lesson → Placement test*. After that, just talk: words and mistakes flow into the
Practice tab on their own.

---

## Architecture

Unidirectional data flow: `UI (Compose) ← StateFlow ← ViewModel ← UseCase ← Repository ← (Room | DataStore | Gemini)`.
Dependencies are wired by hand in `AppContainer`: the graph is small and flat, so code generation would add nothing.

```
com.example.personallangmaster
├── ai/
│   ├── live/        Live API WebSocket protocol, session state machine, tutor tools
│   ├── text/        regular model calls with structured output
│   └── prompt/      system instruction builder, personas, strictness levels
├── core/
│   ├── audio/       PCM capture and playback, energy-based VAD
│   ├── speech/      system TTS and speech recognition
│   ├── crypto/      Android Keystore for the API key and PIN
│   ├── srs/         spaced repetition scheduler
│   ├── cost/        pricing and cost accounting
│   └── progress/    streak and daily goal
├── data/            Room (18 tables), DataStore, repositories, seed content from assets
├── domain/          lesson review, leveling, budget, scenario and exercise generation
├── ui/              Compose screens and components
└── work/            reminders and nightly maintenance (WorkManager)
```

### Design decisions

| Decision | Why |
|:--|:--|
| User supplies the API key | No key in the APK; everyone pays only for what they use |
| Push-to-talk by default | Hands-free costs more and drains the battery faster |
| Practice runs on system TTS/STT | Reviews must work on the subway and cost nothing |
| Lesson review is a separate text call | Structured JSON instead of regex over speech |
| Learner mistakes use the `tertiary` color, not `error` | It's a teaching moment, not an app failure |
| Manual DI instead of Hilt | Flat graph, one entry point, cheap to migrate later |
| Model names live in `ModelCatalog` and settings | They change more often than app versions ship |

**Stack:** Kotlin 2.2 · Jetpack Compose (Material 3) · Navigation 3 · Room 2.7 (KSP) ·
DataStore · OkHttp WebSocket · kotlinx.serialization · WorkManager · Coroutines/Flow.
`minSdk 29` · `targetSdk 36` · `compileSdk 37`.

---

## Testing

**167 unit tests**, none of them touching the network. They cover the things that break quietly and expensively:

- the Live API protocol, replayed from recorded server responses;
- system prompt assembly: strictness and mode settings really reach the instruction;
- the SM-2 scheduler and the bridge between database cards and the scheduler;
- leveling rules and spending limits;
- cost accounting, checked against the calculations in the docs;
- PCM processing and the voice detector, on synthetic signals.

Debug builds include a recorded lesson and a fake session that replays it with real timings,
so the live UI can be debugged without spending a cent.

---

## Roadmap

- [x] Project skeleton, profile, encrypted key, full settings
- [x] Live Gemini session, lesson review, CEFR leveling
- [x] Vocabulary with spaced repetition
- [x] Scenarios, pronunciation trainer, grammar
- [x] Progress, limits, reminders
- [ ] Tablet layouts, profile transfer, release build
- [ ] English UI localization

Detailed plan: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) ·
known gaps: [`docs/TODO.md`](docs/TODO.md).

---

## Contributing

Issues and pull requests are welcome, especially:

- **English UI translation**: moving the remaining hardcoded strings into `res/values/strings.xml`
  and filling `res/values-en/`;
- new role-play scenarios in `app/src/main/assets/scenarios.json`;
- testing on devices and Android versions not yet covered.

<div align="center">

If you find this project useful or interesting, a ⭐ helps others discover it.

</div>
