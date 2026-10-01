# Data pack

Everything the app *knows* about training lives in `app/src/main/assets/pack/`, apart from the code that *uses* it.
Change the files, rebuild, and the app picks it up. No Kotlin changes needed.

| File | What it holds | Used by |
| --- | --- | --- |
| `exercises.json` | Built-in exercises: main and assisting muscles, aliases, group | Exercise search and library, muscle map, Strength Index detraining |
| `session-plans.json` | Ready-made session plans (days, exercises, sets, levels) | More → Session plans, first-launch "Pick a plan" |
| `progression-plans.json` | Ready-made progression plans; the `default` one is created on first launch | More → Progression plans |
| `training-science.json` | Detraining, projection limits, muscle volume targets, streak rest days, with sources | `StrengthIndex`, `MuscleStats`, `Streak` |

Each file has an `about` field that explains its fields.

## How it loads

`LockedInApp.onCreate` calls `PackParser.fromAssets(...)` and `Pack.install(...)`. Logic reads `Pack.current`
(for example `ExerciseCatalog` reads `Pack.current.exercises`, `StrengthIndex` reads `Pack.science.strength`).

`PackParser` checks the files fit together and fails loudly if not:
- muscle ids exist (`CHEST`, `SHOULDERS`, `BICEPS`, `TRICEPS`, `FOREARMS`, `ABS`, `OBLIQUES`, `TRAPS`, `UPPER_BACK`,
  `LATS`, `LOWER_BACK`, `GLUTES`, `QUADS`, `HAMSTRINGS`, `CALVES`),
- exercise names and aliases don't clash,
- every exercise in a ready-made plan is in `exercises.json`,
- plans have 1 to 7 days, unique workout day names, a level, and 1 to 10 sets per exercise,
- exactly one progression plan is the default.

The unit tests (`DataPackTest`) parse the real files, so `./gradlew :app:testDebugUnitTest` catches a broken pack
before it ships.

## Common edits

- **Add an exercise**: add a line to `exercises.json` with `name`, `group`, `primary` and optional `secondary` and
  `aliases`.
- **Add a ready-made plan**: copy a plan in `session-plans.json`, give it a new `id`, and list its days in order.
  Use `{"rest": true}` for a rest day. Days repeat in order.
- **Tune the science**: change a number in `training-science.json` (for example `detrainingGraceDays`). Keep the
  `about` text and `sources` in step with the change.

Adding a new muscle is the one change that needs code: the `Muscle` enum and the body drawing in `MuscleMap.kt`.
