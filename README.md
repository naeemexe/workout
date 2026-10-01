# Locked In, by Locked In Labs

A simple Android workout tracker. Log what you lift; watch your strength chart like a stock ticker.

## Screens
- **Home**: Strength Index chart (drag to scrub, 1W to All, dashed projection, "Details" for per-exercise lines),
  Weight and Streak tiles, muscle map, "Next session" (exercises ready to step up), and a training calendar.
- **Log**: Session (from the active session plan, or Rest / Custom), Exercise, Sets, weight and reps, Save and a rest
  timer. The plan sets the number of sets (+ adds one for that day); Save goes set by set ("Save set 1", …), each set
  opens once the one before is saved, and Save starts the rest timer. One log per exercise per day: going back to it
  edits that log. A custom session lists only what you've logged that day. Calendar date picker for backfilling.
- **More**
  - **Session plans**: several plans, one active. 7 day slots with names, exercises (with set counts, drag to
    reorder), color tags, and an optional week-based rotation.
  - **Progression plans**: when an exercise steps up or down. One is the default for every exercise not assigned to
    another.
  - **Exercises**: the built-in catalog plus your own; set muscles for your own so they count on the muscle map.
- Avatar (top right): Profile (with Google backup), Settings (appearance), Help & support. Weight page from Home.

## How the numbers work
- **Strength Index** starts at 100. Each exercise is scored by estimated 1-rep max (Epley) relative to the first
  time you logged it; the index averages those. After 2 rest days it decays 0.8%/day. `domain/StrengthIndex.kt`.
- **Projection** is a recency-weighted linear trend over the last 28 days.
- **Progression** (`domain/Progression.kt`): your first N work sets, in order, must hit the plan's reps to step up by
  the plan's amount; sets after those are only logged. Two sessions with one of those sets under the minimum steps down.
- **Rotation** (`domain/Rotation.kt`): moves to the next session only after you train; planned rest days pass on their
  own; Rest and Custom picks don't move it. Week-based rotation turns blank days into rest days.

## Data
- **Room** is the source of truth (`data/`). Schemas are exported to `app/schemas`; every version has a migration.
  Each table has one shared live query (`data/SharedQuery.kt`), so screens don't query separately.
- **Firebase** (free Spark plan): Google sign-in plus Firestore backup (`sync/`). One small document per workout day
  (`users/{uid}/days/{yyyy-MM-dd}`: sets, session, weight) and one user document (profile, plans, progression plans,
  custom exercises). Local-first, last edit wins, incremental downloads by server time.

## Stack
Kotlin · Jetpack Compose (Material 3) · MVVM with manual DI (`LockedInApp`) · Coroutines · Room · Firebase Auth +
Firestore.

## Run
Firebase config isn't in the repo: download `google-services.json` for the Android app from the Firebase console
(project settings) and put it in `app/`. The build needs it.

Open this folder in Android Studio, let Gradle sync, then Run ▶ on an emulator or phone.
Or build from the command line: `./gradlew :app:assembleDebug` (APK in `app/build/outputs/apk/debug/`).
Unit tests: `./gradlew :app:testDebugUnitTest`.
