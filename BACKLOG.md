# Backlog

Add anything here: features, bugs, ideas. Work one item per session, then delete it when it's committed.

## Bugs

- **Past date shows the wrong session.** On Sep 29 the Log page picked "Hands" even though Squat and Hamstring
  Extension (Legs exercises) were logged that day. The day's recorded session may be filtered out by the active
  plan's `activeSince` (`PlanRepository.observeChoices`) after the plan was edited or re-activated. Check
  `Rotation.dayFor` for past dates with logs.

## Release (see docs/PLAY_STORE.md)

- Create the upload key and `keystore.properties`.
- Add upload and Play app-signing SHA-1 fingerprints in Firebase, then refresh `app/google-services.json`.
  Google sign-in won't work on the Play build without this.
- Set Firestore security rules (users can only read, write and delete their own `users/{uid}` tree).
- Host docs/privacy-policy.md at lockedinlabs.work/privacy and docs/delete-account.md at
  lockedinlabs.work/delete-account.
- Confirm support@lockedinlabs.work is a real inbox (it's in the app, policy and listing).
- Play Console: listing, data safety, content rating, closed test (12 testers for 14 days).
- Test end to end on a real Google account: sign-in, backup restore from the welcome screen, delete account
  (with and without erasing the phone).

## Improvements

- Lint: `ValueField` in `ui/log/LogScreen.kt` has the `modifier` parameter in the wrong position; merge
  `res/mipmap-anydpi-v26` into `mipmap-anydpi` (minSdk is 26); dependency updates are available.
- No-plan Log page now lists only exercises logged that day (same as Custom). Confirm with the user that's wanted.
