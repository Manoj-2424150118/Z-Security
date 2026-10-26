# Z+ Secure — Encrypted Vault & Authenticator

A fully offline Android password vault and two-factor authenticator. Everything is stored locally in a SQLCipher-encrypted database — no account, no cloud, no tracking.

## 📲 Download & Install

1. Go to the [**Releases**](https://github.com/Manoj-2424150118/Z-Security/releases) page.
2. Download `Z-Security-v1.0.1.apk` from the latest release.
3. Open the APK on your Android phone and allow "Install from this source" when prompted (Android blocks sideloading by default).
4. Requires Android 8.0 (API 26) or newer. No account and no internet needed — everything is stored locally and encrypted.

## 🚀 How to Use

1. **Set a master password** on first launch — it encrypts your vault (SQLCipher + PBKDF2-HMAC-SHA256, 310,000 iterations). Don't lose it: by design there is no recovery.
2. **Passwords tab** — tap **+** to store a credential (website, username, password). Use the built-in generator for strong passwords; imported QR codes (`passvault://`) land here too.
3. **Authenticator tab** — tap **+** to scan an `otpauth://` QR code, pick one from a photo, or import a Google Authenticator **migration** QR. SHA-1/SHA-256, 6- or 8-digit codes, 30 s/60 s steps and counter-based **HOTP** accounts are all supported — the issuer's own settings in the QR are honoured.
4. **Generator tab** — generate passwords with guaranteed character classes and an entropy readout.
5. **Settings** — enable biometric unlock (opt-in; the vault key is cached encrypted only if you allow it), or turn the app lock off — the vault then uses a fresh random device-local key, never a fixed one.
6. **Export** (Passwords tab) produces a plaintext CSV via the system share sheet. Treat it like cash: it is written to transient cache storage, but anyone you share it with can read it.

Your data never leaves the device, screenshots are blocked while the vault is open, and the app's backups are disabled by design.

## 🔒 Security notes

- Vault: SQLCipher (AES-256) with a PBKDF2-HMAC-SHA256-derived key; older vaults are upgraded to the current iteration count transparently at unlock.
- If the vault file cannot be opened, the app fails closed and leaves the file untouched — it will never silently delete and recreate your vault.
- Biometric unlock is a convenience gate backed by Android Keystore-encrypted preferences.

## 🛠️ Build from source

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # JVM unit tests (RFC 6238/4648 vectors included)
```

Requires JDK 17 and Android SDK platform 35. CI (GitHub Actions) runs the unit tests and builds the debug APK on every push.

## 📦 Tech

Kotlin • Jetpack Compose • Room + SQLCipher • CameraX + ML Kit barcode scanning • AndroidX Security (Keystore)

---

Built by [Manoj Kumar Sharma](https://github.com/Manoj-2424150118) — cybersecurity & software developer, Jaipur, India.
