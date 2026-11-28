# TeleNest: working notes for Claude

TeleNest is an Android app (Kotlin, Jetpack Compose, Hilt, Room, TDLib) that turns a Telegram account into a cloud drive.
Package/applicationId: `com.awd.teledrive` (never change it, it would become a different app).

## How the owner wants to work
- The owner is not technical and writes Bengali. **Reply in Bengali**, short and step by step.
- The owner wants Claude to do everything: write code, push, open and merge the PR, and get the release out.
- When the owner must do something in GitHub, give the **direct link** to the exact page and say what to type there.
- Never ask the owner to paste passwords, tokens or keystores in chat.

## Release flow (fully automatic)
1. Work on a branch, push. `Build & Test Check` (`.github/workflows/build-check.yml`) compiles and runs unit tests. Wait for green.
2. Open a PR to `main` and merge it (the owner authorised Claude to do this).
3. To publish an update, bump `versionName` AND `versionCode` in `app/build.gradle.kts` in that PR. A change to that file on `main` triggers `Build & Release APK` (`release.yml`), which builds the signed APK and creates the GitHub Release `vX.Y.Z` with `TeleNest-vX.Y.Z.apk`.
4. Installed apps read `releases/latest` and offer "Update Now" when the tag is newer. Android only accepts the update if `versionCode` is higher and the APK is signed with the same key.
- Current version: 3.5.0 (versionCode 47). Next release must use a higher versionCode.
- Signing secrets live in GitHub Actions Secrets: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` (alias `upload`). Claude cannot read or set Secrets (the proxy blocks that API).
- **Never commit a keystore, password or token.** `*.jks`, `debug.keystore*` are git-ignored.

## Environment limits (Claude's cloud sandbox)
- No Android SDK and `dl.google.com` is blocked, so the app cannot be compiled locally. Use the GitHub Actions run as the compiler: push, then read the result with the actions tools and fix errors from the job logs.
- Pure-JVM code (e.g. `EncryptionManager`) can be tested locally in a scratch Gradle project.
- Claude cannot run the app on a device. After UI or biometric changes, tell the owner what to check on the phone, or publish a `-beta` first.

## Security notes
- The old release keystore was briefly committed to this public repo on 2026-11-03 (commits still fetchable by SHA) and its password is the default `android`. The owner knowingly chose to keep using this key. Treat it as exposed; do not make it worse, and re-offer rotating to a new key when convenient (existing users would have to reinstall once).
- Vault files: AES-256-GCM, PBKDF2-HMAC-SHA256 250k iterations, authenticated header `TNESTENC`. Legacy files (10k iterations, no header) must stay readable. Covered by `EncryptionManagerTest`.
- Biometric unlock is bound to an Android KeyStore key (`BiometricCrypto`). Keep it requiring a CryptoObject.

## Known gaps (do not claim these exist)
- Room cache is not encrypted (SQLCipher is a dependency but unused); Lazysodium is unused.
- `SecureSettings` silently falls back to plain SharedPreferences if EncryptedSharedPreferences fails.
- A default master password string is hard-coded in `MasterPasswordService`.
- Not implemented: FLAG_SECURE, PiP, video gestures/speed, background audio, syntax highlighting, EXIF/APK inspectors. See README Roadmap.
- No `gradlew` in the repo (CI uses Gradle 9.3.1 via setup-gradle).
