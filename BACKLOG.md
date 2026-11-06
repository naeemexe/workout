# Backlog

Add anything here. One item per session; delete it when it's committed. Higher in a section means sooner.
**(you)** means it needs your accounts or a decision, so a session can't finish it alone.

For a bug, write what you did, what happened, what you expected, and the date if it's about logged data.

## Bugs

- **Home chart repeats its axis labels.** With one logged day the chart shows 100 three times. Hide or round the
  labels when the values are flat.

## Before launch: code

- **(you) Test Android backup restore on a real phone.** Rules in `res/xml` back up only the database and settings.
  The emulator has no backup transport, so this needs a phone: reinstall and check the data returns.
- **(you) Debug and Play builds share one package id.** A Play build won't install over the debug build on the same
  phone until you uninstall it, which erases its local data. Back up first, or decide whether debug should get its
  own id (needs registering in Firebase).
- **(you) No-plan Log page** now lists only exercises logged that day (same as Custom). Confirm that's wanted.

## Before launch: accounts and Play (see docs/PLAY_STORE.md)

- **(you)** Send a test email to support@lockedinlabs.work from another account. It forwards to
  hello.lockedinlabs@gmail.com through Porkbun.
- **(you)** Play Console: declare the rest timer foreground service (`specialUse`, see docs/PLAY_STORE.md).
- **(you)** Play Console: listing, data safety, content rating, closed test (12 testers for 14 days).
- **(you)** Test end to end on a real Google account: sign-in, backup restore from the welcome screen, delete account
  (with and without erasing the phone). Include a backup of holds and cardio.

## Improvements

- **(you) Crash reporting.** Today only Play Console vitals. Crashlytics means a new dependency and updates to the
  privacy policy and data safety form. Decide before the closed test.
- **Migration tests.** There are no instrumented tests. Before the first schema change after launch, test the
  migration on a real v15 database (needs `room-testing`; ask before adding).
- Dependency updates are available. Do them after launch, one at a time, not right before a release.
