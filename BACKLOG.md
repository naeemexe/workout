# Backlog

Add anything here. One item per session; delete it when it's committed. Higher in a section means sooner.
**(you)** means it needs your accounts or a decision, so a session can't finish it alone.

For a bug, write what you did, what happened, what you expected, and the date if it's about logged data.

## Bugs

- **Past date shows the wrong session.** On Sep 29 the Log page picked "Hands" even though Squat and Hamstring
  Extension (Legs exercises) were logged that day. The day's recorded session may be filtered out by the active
  plan's `activeSince` (`PlanRepository.observeChoices`) after the plan was edited or re-activated. Check
  `Rotation.dayFor` for past dates with logs.


## Before launch: code

- **Run the shrunk release build.** It builds with R8 but has never been run, and only the debug build has been
  tested. Install a locally signed copy on a temporary emulator user and go through onboarding, logging, Home,
  sign-in and backup. Look for anything R8 removed.
- **Test Android Auto Backup restore.** `allowBackup` is on with no backup rules, so `SyncStore` and settings can be
  restored onto a new install. Check a restore with `adb shell bmgr`: onboarding gate, signed-out state and the next
  sync must all behave. Add backup rules if not.
- **(you) Debug and Play builds share one package id.** A Play build won't install over the debug build on the same
  phone until you uninstall it, which erases its local data. Back up first, or decide whether debug should get its
  own id (needs registering in Firebase).
- **(you) No-plan Log page** now lists only exercises logged that day (same as Custom). Confirm that's wanted.

## Before launch: accounts and Play (see docs/PLAY_STORE.md)

- **(you)** Create the upload key and `keystore.properties`.
- **(you)** Add upload and Play app-signing SHA-1 fingerprints in Firebase, then refresh `app/google-services.json`.
  Google sign-in won't work on the Play build without this.
- **(you)** Set Firestore security rules (users can only read, write and delete their own `users/{uid}` tree).
- **(you)** Host docs/privacy-policy.md at lockedinlabs.work/privacy and docs/delete-account.md at
  lockedinlabs.work/delete-account.
- **(you)** Confirm support@lockedinlabs.work is a real inbox (it's in the app, policy and listing).
- **(you)** Play Console: declare the rest timer foreground service (`specialUse`, see docs/PLAY_STORE.md).
- **(you)** Play Console: listing, data safety, content rating, closed test (12 testers for 14 days).
- **(you)** Test end to end on a real Google account: sign-in, backup restore from the welcome screen, delete account
  (with and without erasing the phone).

## Improvements

- **(you) Crash reporting.** Today only Play Console vitals. Crashlytics means a new dependency and updates to the
  privacy policy and data safety form. Decide before the closed test.
- **Migration tests.** There are no instrumented tests. Before the first schema change after launch, test the
  migration on a real v15 database (needs `room-testing`; ask before adding).
- Lint: `ValueField` in `ui/log/LogScreen.kt` has the `modifier` parameter in the wrong position; merge
  `res/mipmap-anydpi-v26` into `mipmap-anydpi` (minSdk is 26); dependency updates are available.
