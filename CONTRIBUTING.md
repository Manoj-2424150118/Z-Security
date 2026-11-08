# Contributing to Z-Security

Thanks for helping improve Z-Security. This guide covers the setup, the
test suite, and the ground rules that keep an offline vault and
authenticator safe to change. Everything here is grounded in `README.md`,
`SECURITY.md`, `android_studio_spec.md`, and the CI workflow
(`.github/workflows/android-ci.yml`, `Android CI`).

## Setup

- Install **Android Studio** (JDK 17 toolchain) and clone the repository.
- Open the project and let Gradle sync.
- Platform: Android — minSdk 26 (Android 8.0), target/compileSdk 35,
  applicationId `com.zsecure.com`, current app build `versionName 1.2.0`
  / `versionCode 4` (see `app/build.gradle.kts`).

## Build and test

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests (23 tests)
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:assembleRelease     # signed release APK (requires the release keystore — maintainer builds)
./gradlew :app:lintDebug           # Android Lint (advisory in CI)
```

- **23 JVM unit tests** live in `app/src/test/java/com/zsecure/com/utils/`:
  `TotpUtilsTest` (RFC 6238 vectors for SHA-1/SHA-256, 6- and 8-digit
  codes, period honoured, HOTP counter codes, invalid secret returns
  null instead of a plausible-looking fake code, Base32 RFC 4648
  vectors), `GoogleAuthDecoderTest` (protobuf parsing, `otpauth://`
  parameters, Google Authenticator migration-QR import, Base32
  round-trip, oversized length prefix throws instead of allocating),
  `CryptoUtilsTest` (PBKDF2 key derivation: deterministic, 32 bytes,
  salt- and password-dependent, iteration-count-dependent, 16-byte
  random salt), and `AccountFilterTest` (authenticator search:
  case-insensitive issuer/name match, query trimming, blank query
  returns the list unchanged, no match returns empty).
- **Run `./gradlew :app:testDebugUnitTest` before opening a PR, and keep
  it green.** CI (`Android CI`) runs the unit tests, builds the debug
  APK, builds the signed release APK, and runs Lint on every push to
  `main`, plus a weekly health check (Mondays 03:30 UTC) that catches
  toolchain/dependency rot.
- If you add or remove a test, update the test count above to match.

## Manual testing

The vault, authenticator, and generator run fully offline on a device or
emulator — no account, no server, no network. If your change touches
unlock, biometrics, or the lockout countdown, verify it on a real device
or an emulator with a lock screen set up: biometric-first unlock with
the master-password fallback, and the lockout after repeated failures,
cannot be exercised by the JVM unit tests. If your change touches QR
scanning, verify it with a real `otpauth://` QR code and, for migration
imports, a Google Authenticator migration QR.

## Ground rules

- **Never commit secrets.** No release keystore, keystore passwords,
  key aliases, or signing material of any kind. Release APKs are built
  by the maintainer; releases from v1.1.0 onward are signed with a
  permanent release key so updates install in place.
- **Do not roll your own crypto.** The vault database is encrypted with
  SQLCipher, its key derived from the master password with
  PBKDF2-HMAC-SHA256 at 310,000 iterations; preferences use
  EncryptedSharedPreferences with an Android Keystore master key; TOTP
  follows RFC 6238 / RFC 4226 with each account's own algorithm, digit
  count, and period. Changes touch parameters and plumbing, never
  primitives.
- **Keep it offline.** The app requests **no INTERNET permission** and
  has no network code path. Do not add one — no account, no sync, no
  analytics.
- **Report vulnerabilities privately.** Do not open a public issue for
  a security flaw — follow `SECURITY.md` (GitHub private vulnerability
  reporting). Security fixes go to `main` and the latest release only.
- **Keep the docs honest.** The README documents the architecture, the
  threat model, and the honest limitations (including that a forgotten
  master password is unrecoverable by design, and that the CSV vault
  export is plaintext). If a change alters behaviour — including the
  release/version table (`versionName` / `versionCode` in
  `app/build.gradle.kts` and the version badge / "Current release" row
  in the README) — update the README section that describes it in the
  same PR.

## Pull requests

- Keep PRs focused: one change, one purpose.
- Use conventional commit messages (`feat:`, `fix:`, `docs:`, `test:`,
  `refactor:`, `perf:` with the `z-security` scope where it helps, e.g.
  `docs(z-security): …`).
- Describe what you tested (`./gradlew :app:testDebugUnitTest` output,
  and any manual device/emulator checks from the section above) in the
  PR.
