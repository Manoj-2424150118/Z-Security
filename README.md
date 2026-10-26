# Z+ Secure — Encrypted Vault & Authenticator

A fully offline Android password vault and two-factor authenticator. Everything is stored locally in a SQLCipher-encrypted database — no account, no cloud, no tracking. The app doesn't even request the Internet permission; the only permissions it asks for are Camera (for scanning QR codes) and Biometrics.

## ✨ What's new in v1.1.0

- **Full Material 3 redesign** — a new design system with branded light *and* dark themes that follow your system setting, and rebuilt Unlock, Vault, Authenticator, Generator, and Settings screens.
- **Biometric-first unlock** — the fingerprint prompt appears as soon as the app opens, with the master password as fallback and a live countdown during lockouts.
- **Permanent release signing** — releases are now signed with a permanent key, so from v1.1.0 onward future updates install in place over the previous release. (Upgrading from v1.0.x needs one extra step — see below.)
- **Valid no-backup rules** — the Android backup-exclusion rules were fixed, so the app's no-backup policy is correctly defined and enforced.
- Carried in from **v1.0.1**: unlock now self-heals if the app was killed in the middle of the transparent PBKDF2 cost upgrade, instead of leaving the stored settings and the vault key out of sync.

## 📲 Download & Install

1. Go to the [**Releases**](https://github.com/Manoj-2424150118/Z-Security/releases) page.
2. Download `Z-Security-v1.1.0.apk` from the v1.1.0 release.
3. Open the APK on your Android phone and allow "Install from this source" when prompted (Android blocks sideloading by default).
4. Requires Android 8.0 (API 26) or newer. No account and no internet needed — everything is stored locally and encrypted.

### ⚠️ Upgrading from v1.0.x

The v1.0.x builds were test builds signed with one-off debug keys. Android only accepts an update if it carries the *same* signature as the installed app, so it will **refuse to install v1.1.0 over v1.0.x** ("App not installed"). The old app has to be uninstalled first — and uninstalling deletes its data, so back up first:

1. Open the old app, go to the **Vault** tab, and tap the **share/export icon** to export a CSV of your passwords. Keep the file somewhere safe and private — **it is plaintext**, readable by anyone who gets it.
2. Note the limit: the CSV covers saved passwords only. **The app has no export for 2FA accounts**, so before uninstalling, make sure you still have each account's setup key or recovery codes — or plan to disable and re-enable 2FA on those services after reinstalling.
3. Uninstall the old app.
4. Install v1.1.0, create your master password, and re-import the CSV from the **Vault** tab (import icon).
5. From v1.1.0 onward this won't happen again: releases use a permanent signing key, so future updates install in place.

## 🚀 How to Use

### Unlocking

1. **First launch — create your vault.** Choose a master password (at least 8 characters) and confirm it. It derives the key that encrypts your vault (PBKDF2-HMAC-SHA256, 310,000 iterations). Don't lose it: by design there is no recovery.
2. **Biometric-first unlock.** Creating the master password also turns on biometric unlock — the vault key is cached in Android Keystore-encrypted preferences. On later launches the fingerprint prompt appears immediately; you can also tap **Unlock with biometrics**, or just type the master password instead.
3. **Lockout protection.** After 5 wrong passwords the vault locks for 30 seconds, and the wait doubles after each further failure (up to 1 hour). A live countdown is shown on the unlock screen, and the attempt counter survives app restarts — closing the app doesn't reset it.

### Vault tab

- **Search** at the top filters your logins by website, username, or notes as you type.
- Tap **+** to add a credential (website/app, username, password, notes). The dice button in the form generates a 16-character password on the spot; the pencil icon on a card edits it.
- Each card shows the password masked — tap the eye to reveal it. Tap the **copy** icon to copy it: a confirmation appears, and the clipboard is **automatically cleared after 45 seconds** (if you haven't copied something else in the meantime).
- **Deleting asks for confirmation** and is permanent — there is no undo.
- The **QR icon** on a card shares that login as a QR code. As the app itself warns: *anyone who scans it gets the password* — only share it in person, on a device you trust.
- The icons next to search: **import CSV** (column headers are detected automatically, so exports from browsers and other password managers import cleanly), **export CSV**, and **scan QR**.
- Scanning a `passvault://` QR here imports a credential. If you scan a 2FA code by mistake, the app offers to send it to the Authenticator tab instead.
- **Export** produces a plaintext CSV via the system share sheet. Treat it like cash: it is written to transient cache storage, but anyone you share it with can read it.

### Authenticator tab

- Each account is a card with its current code in large type and a **countdown ring** showing the seconds left in that account's own time step (the header ring tracks the standard 30-second cycle). Rings turn red in the last 5 seconds.
- **Tap a card to copy its code** — the clipboard auto-clears after 45 seconds, same as in the Vault.
- Tap **+** to open the add sheet:
  - **Scan QR code** — point the camera at a setup code (Google Authenticator *migration* QRs work here too and import in bulk).
  - **Choose from gallery** — pick a screenshot or photo of a QR code.
  - **Enter key manually** — type the issuer, account name, and the Base32 setup key; the key is validated before it's accepted.
  - **Import from Google Authenticator** — paste an `otpauth-migration://` link to bulk-import accounts.
- The issuer's own settings in the QR are honoured: SHA-1/SHA-256/SHA-512/MD5, 6–8 digit codes, and custom time steps. Counter-based **HOTP** accounts show their counter and a **Next code** button instead of a ring.
- Deleting an account asks for confirmation — and warns that removing it without disabling 2FA on that service first could lock you out of it.

### Generator tab

- A password is generated as soon as you open the tab, and regenerated whenever you change a setting.
- **Strength meter** — an animated bar with a rating (Very Weak → Very Strong) and the exact **entropy readout in bits**.
- **Length slider** — 4 to 64 characters.
- **Character sets are switch rows** — Uppercase, Lowercase, Numbers, Symbols. Tap a row to toggle it; at least one character from every enabled set is guaranteed in each password. Generation uses `SecureRandom`.
- **Copy** puts the password on the clipboard (45-second auto-clear applies). The last 10 generated passwords are kept in a **history list for this session only** — tap one to copy it again, or clear the list with the trash icon.

### Settings

The header card shows the version you have installed. Options are grouped:

**Security**
- **Lock on app close** — on by default; the vault locks as soon as you leave the app. Turning it *off* requires your master password, then re-encrypts the vault with a fresh random device-local key (never a fixed one): the vault stays encrypted at rest, but anyone holding your unlocked phone can open the app. Turning it back *on* sets a new master password.
- **Biometric unlock** — shows whether a vault key is currently cached for fingerprint unlock. **Clear** wipes the cached key immediately; your master password keeps working.

**Vault**
- **Lock vault now** — locks the app immediately (available while lock-on-close is on). There's also a lock button in the top bar of every tab.
- As the note in this group states: the vault is encrypted at rest with SQLCipher (AES-256), and keys are derived with PBKDF2.

Your data never leaves the device, screenshots are blocked while the app is open, and Android backups of the app are disabled by design.

## 🔒 Security notes

- Vault: SQLCipher (AES-256) with a PBKDF2-HMAC-SHA256-derived key (310,000 iterations); older vaults are upgraded to the current iteration count transparently at unlock.
- If the vault file cannot be opened, the app fails closed and leaves the file untouched — it will never silently delete and recreate your vault.
- Biometric unlock is opt-in (enabled when you set a master password) and backed by Android Keystore-encrypted preferences; the cached key can be wiped any time in Settings.
- Copied passwords and 2FA codes are cleared from the clipboard after 45 seconds.
- Screenshots are blocked (FLAG_SECURE) and `allowBackup` is off, with backup-exclusion rules as defence in depth.

## 🛠️ Build from source

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # JVM unit tests (RFC 6238/4648 vectors included)
```

Requires JDK 17 and Android SDK platform 35. CI (GitHub Actions) runs the unit tests and builds the APK on every push. The APKs attached to GitHub Releases are built and signed in CI with the permanent release key — the key itself is not in this repo.

## 📦 Tech

Kotlin • Jetpack Compose (Material 3) • Room + SQLCipher • CameraX + ML Kit barcode scanning • AndroidX Security (Keystore)

---

Built by [Manoj Kumar Sharma](https://github.com/Manoj-2424150118) — cybersecurity & software developer, Jaipur, India.
