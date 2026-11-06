# Locked In (Android workout tracker)

App name is **Locked In**. **Locked In Labs** (lockedinlabs.work) is the company; never label the app "Locked In Labs".
Package `work.lockedinlabs.tracker`. Repo: github.com/naeemexe/workout (branch `main`).

## Workflow

Every session does one backlog item and starts cold, so this file is the context. Don't explore the repo first.

1. Take the item the user names. If they just say "next", take the first item in BACKLOG.md that isn't marked
   **(you)**: Bugs first, then Before launch, then Features.
2. Find the code with the map below and grep. Read only what the item touches. Files over 350 lines (`LogScreen`,
   `PlanScreen`, `LogViewModel`, `RuleScreens`, `HomeScreen`, `SyncManager`, `MoreScreens`): grep, then read that part.
3. Build, run unit tests, and check the change on the emulator (see Build and test).
4. Update docs if behaviour changed (README.md, docs/, help FAQ in `ui/settings/SettingsScreens.kt`).
5. Remove the item from BACKLOG.md. Other problems you find go in BACKLOG.md, not in this session's change.
6. If this file was wrong or a new file or rule appeared, fix it in the same commit. Keep it short.
7. Copy the debug APK to `dist/`, then commit. Push only when the user asks. End commit messages with the
   Co-Authored-By line the session gives you.

## Stack

Kotlin 2.3, AGP 8.13, Gradle 8.14, compileSdk/targetSdk 36, minSdk 26. Jetpack Compose + Material 3, MVVM with
manual DI in `LockedInApp` (no Hilt). Room (version in `data/AppDatabase.kt`, schemas in `app/schemas`, every version has a migration).
Firebase Auth (Google sign-in via Credential Manager) + Firestore backup. JUnit unit tests. No other libraries:
don't add dependencies unless asked.

## Code map (`app/src/main/java/work/lockedinlabs/tracker/`)

- `LockedInApp.kt`: singletons (DB, repositories, sync), installs the data pack at start-up, `eraseLocalData()`.
- `MainActivity.kt`: theme, onboarding gate, tabs Home / Log / More, routes (enums) and the BackHandler.
- `pack/`: `DataPack` models + `Pack` holder, `PackParser` (reads and validates `assets/pack/*.json`).
- `domain/`: pure logic. `ExerciseCatalog` (from pack), `StrengthIndex` (per-muscle detraining, realistic
  projection), `MuscleStats` (weekly sets per muscle), `Progression` (step up / down), `Rotation` (which session is
  due), `Highlights` (`Streak`), `Format` (number text).
- `data/`: Room entities, each with its DAO in the same file (except `LogDao.kt`), and repositories (`WorkoutRepository`, `PlanRepository`, `RuleRepository`,
  `CustomExerciseRepository`, `WeightRepository`, `ProfileRepository`), `SettingsStore` (prefs),
  `SharedQuery` (one shared Room query per table).
- `sync/`: `SyncManager` (local-first, last edit wins, day docs + user doc), `SyncStore`, `AuthRepository`.
- `ui/`: `home/` (chart, tiles, muscle map, calendar), `log/` (`LogViewModel` holds the logging state machine:
  session → exercise → set-by-set save; `RestTimer` with `RestAlarm` sounds and `RestService` (foreground service during a rest or hold; holds use a second `RestTimer`), `CalendarDialog`), `plan/` (session plans, presets, progression plans),
  `more/` (More menu, exercise library), `onboarding/`, `profile/` (incl. delete account), `weight/`,
  `settings/` (Settings, Help & support FAQ), `theme/` (`LabCard`, `DayColors`), `AccountDrawer`, `SaveQueue` (latest-wins saves). UI text is inline in Compose,
  not in `strings.xml`.
- `app/src/main/assets/pack/`: exercises, preset session plans, preset progression plans, training science (with
  sources). Edit these instead of code for catalog/preset/science changes. See docs/DATA_PACK.md.
- `docs/`: DATA_PACK.md, PLAY_STORE.md (release checklist), privacy-policy.md, delete-account.md, store/ (icon),
  make_timer_sounds.py (generates `res/raw/rest_*.wav`).

## Rules the user set

- **In-app text is short and plain**: one short line per idea, no em dashes, no examples, no filler hint lines.
  Prefer no text over explanatory text.
- Keep it simple and intuitive; don't add features or settings that weren't asked for.
- **Home reads only logged data**; only the Log tab is driven by the active session plan.
- Log page: a new day follows the plan's set count; a day already logged shows its own sets. A set opens only after
  the one before is saved; Save starts the rest timer. One log entry per exercise per day (edits update it).
- Custom sessions (and no plan) list only exercises logged that day, plus "+ Add exercise".
- Selected chips everywhere use the same style: light green fill, green outline. Session and Exercise chips share a
  fixed width and the selected chip sits at the same spot in both rows.
- Units are lb (miles for cardio).
- Each exercise has a kind (`ExerciseKind`: Weight, Hold, Cardio), from the pack or set on your own exercise. Sets
  carry their own fields (`SetEntry`), so history stays readable if a kind changes. Holds and cardio stay out of
  Strength Index and progression; cardio stays off the muscle map.
- Hold and cardio time is set with a slider; the main button is Start and the set saves when the timer ends. Past
  days and saved sets show Save. No weight on either for now.
- After the last set the page moves to the next exercise in the row, never back to the first; at the end it stays.

## Recipes

- **Database change:** raise the version, add an `AutoMigration` (or a `Migration` if Room can't work it out), commit
  the new schema JSON. Never edit a shipped migration. If the data is backed up, update both directions in
  `SyncManager` and keep reading older cloud docs.
- **Play upload:** raise `versionCode`, then follow docs/PLAY_STORE.md.

## Gotchas

- Compose state that `init` touches must be declared **above** `init` in a ViewModel (a delegated property declared
  below caused a crash).
- Screens outside the main `Scaffold` must sit in a `Surface`, or text turns black in dark mode.
- `google-services.json` is git-ignored (public repo); it lives in `app/` locally. Signing files are git-ignored too.
- Domain code reads `Pack.current`; unit tests call `TestPack.install()` first.

## Build and test

```bash
./gradlew :app:testDebugUnitTest      # includes DataPackTest, which validates the real pack files
./gradlew :app:assembleDebug          # then: cp app/build/outputs/apk/debug/app-debug.apk dist/LockedIn-debug.apk
./gradlew :app:bundleRelease          # Play upload (needs keystore.properties, see docs/PLAY_STORE.md)
```

Emulator: AVD `Pixel_8`; tools in `~/Library/Android/sdk/{platform-tools,emulator}` (not on PATH by default).

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$HOME/Library/Android/sdk/emulator:$PATH"
emulator -avd Pixel_8 -no-snapshot-save &
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The emulator holds the user's own test data: don't clear it and delete any test entries you create. To test first
launch, use a temporary Android user (`adb shell pm create-user Test`, `am switch-user`, `adb install --user N`),
then switch back to user 0 and `pm remove-user N`. Use `uiautomator dump` for element bounds before tapping.
