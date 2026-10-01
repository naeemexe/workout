# Play Store launch checklist

The app side is ready: targets Android 16 (API 36), release builds are shrunk with R8, accounts can be deleted in
the app, and help links to a privacy policy. These steps need you (accounts, keys and the Play Console).

## 1. Create your upload key (once, keep it safe)

```bash
keytool -genkeypair -v -keystore lockedin-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Put `lockedin-upload.jks` in the project root and create `keystore.properties` next to it:

```properties
storeFile=lockedin-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```

Both files are git-ignored. Back them up somewhere safe (a password manager). With Play App Signing, a lost upload
key can be reset through Play support, but it takes days.

## 2. Build the bundle

```bash
./gradlew :app:bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Raise `versionCode` in `app/build.gradle.kts` by 1 for
every upload. Keep `app/build/outputs/mapping/release/mapping.txt` for each release (Play Console can take it, which
makes crash reports readable).

## 3. Google sign-in on release builds

Google sign-in only works for signing keys registered in Firebase. Add **both** SHA-1 fingerprints to Firebase
console → Project settings → your Android app → Add fingerprint:
- your upload key: `keytool -list -v -keystore lockedin-upload.jks -alias upload`
- Play's app signing key: Play Console → your app → Test and release → App integrity → App signing.

Then download the new `google-services.json` into `app/` and rebuild.

## 4. Firestore security rules

Make sure each user can only reach their own data, and can delete it (account deletion needs this):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

## 5. Host two pages on lockedinlabs.work

- `https://lockedinlabs.work/privacy`: from `docs/privacy-policy.md` (the app's Help & support links here).
- `https://lockedinlabs.work/delete-account`: from `docs/delete-account.md` (Play asks for an account deletion URL).

Check that `support@lockedinlabs.work` exists and is read; it's in the app, the policy and the listing.

## 6. Play Console

- **Create app**: name "Locked In", free, app. Category: Health & Fitness.
- **Privacy policy**: `https://lockedinlabs.work/privacy`.
- **App access**: all features work without signing in (sign-in is only for backup).
- **Ads**: no ads.
- **Content rating**: fill in the questionnaire (no violence, no user-generated content shared with others).
- **Target audience**: 13 and over (18+ is simplest).
- **Data safety** (matches the app today):
  - Data collected: Personal info → Name, Email address, User IDs (only with Google sign-in, for account and backup);
    Health and fitness → Fitness info (workouts, body weight).
  - Purpose: App functionality, Account management. Not shared with third parties. Not used for ads or analytics.
  - Encrypted in transit: yes. Users can request deletion: yes (in app and at the delete-account URL).
- **Account deletion**: yes, in app (Profile → Delete account) and at `https://lockedinlabs.work/delete-account`.
- **Store listing**: short description (80 chars), full description, 512×512 icon, 1024×500 feature graphic, at
  least 2 phone screenshots (Home, Log, Session plans and the welcome screen show it well).

## 7. Testing before production

New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before they can
publish to production. Create a closed testing track, add testers by email, and share the opt-in link.

## Release checks

- [ ] `./gradlew :app:testDebugUnitTest` passes (includes the data pack checks)
- [ ] Release build installed from Play's internal test: first launch shows the welcome, sign-in works, a workout
      saves, Home updates, account deletion works
- [ ] `versionCode` raised, `versionName` updated
