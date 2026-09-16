# 📱 Android Studio Specification & Prompt: Secure Password Vault & 2FA Authenticator

This document serves as a complete development specification and prompts to build a Play Store-ready Android application using Android Studio. 

---

## 🚀 App Overview & Monetization Goal
* **App Name Ideas**: *AegisPass*, *TitanVault*, *Nova Authenticator & Password Manager*
* **Core Objective**: A secure, offline-first personal utility app combining (1) a customizable Password Generator, (2) a Google/Microsoft-compatible 2FA TOTP Authenticator, and (3) a secured Login Vault with CSV & QR code sharing capabilities.
* **Passive Income Monetization Strategy**:
  1. **Google AdMob Integration**: 
     - Banner ads at the bottom of the Password Generator tab.
     - Interstitial ads triggered occasionally when exporting backups or generating multiple passwords.
  2. **Premium In-App Purchase (IAP)**: 
     - Lifetime or subscription upgrade to remove ads, unlock biometric auto-fill, and enable encrypted backup sync.

---

## 🛠 Android Tech Stack (Recommended)
* **Development IDE**: Android Studio (Koala/Ladybug or newer)
* **Programming Language**: Kotlin (modern standard, fully interoperable with Java APIs)
* **UI Toolkit**: **Jetpack Compose** (Material 3) for clean, responsive, and dynamic styles.
* **Local Database**: **Room Database** integrated with **SQLCipher** for AES-256 file encryption of the vault database.
* **Camera API**: **CameraX** with **ML Kit Barcode Scanning** (Google's optimized library for instant QR code detection).
* **Biometric Auth**: Android **BiometricPrompt** (Fingerprint, Face Unlock) to unlock the app securely.

---

## 🎨 Design System & Aesthetics
* **Theme**: Modern Dark Mode by default, with dynamic Material 3 color themes.
* **Typography**: Outfit, Inter, or Roboto.
* **Layout**: Bottom Navigation Bar with 3 tabs:
  1. **Generator** (Key Icon)
  2. **Authenticator** (Shield Icon)
  3. **Manager** (Folder / Locker Icon)
* **High Contrast Rule**: The password strength indicator bar must dynamically change text color depending on its background (e.g., black text on a green/yellow bar; white text on a red/dark bar) to maintain perfect accessibility.

---

## 🔑 Tab 1: Password Generator & Strength Calculator
### Functional Requirements
1. **Interactive Controls**:
   - A length slider (range: 4 to 64 characters, default 16).
   - Toggles: Include Uppercase, Lowercase, Numbers, and Symbols.
   - Action Buttons: `Generate` and `Copy to Clipboard` (with visual toast confirmation).
2. **Smart Password Strength Meter**:
   - Calculate strength based on entropy: $E = L \times \log_2(R)$ where $L$ is length and $R$ is character pool size.
   - Show a multi-colored progress bar:
     - **Red (Very Weak)**: White text
     - **Orange (Weak)**: Black text
     - **Yellow (Medium)**: Black text
     - **Green (Strong)**: Black text
     - **Dark Green (Very Strong)**: White text
3. **Local Generation History**:
   - A scrollable history list showing the last 10 generated passwords (stored temporarily in RAM or Room database).

---

## 🛡 Tab 2: 2FA Authenticator (Google & MS Compatible)
### Functional Requirements
1. **TOTP Generation Engine**:
   - Pure Kotlin implementation of RFC 6238.
   - Base32 secret key decoding to raw bytes.
   - HMAC-SHA1 cryptographic function using a 30-second epoch time step.
   - Formats the output as spaced 6-digit codes (e.g. `123 456`).
2. **Countdown Circle Animation**:
   - A dynamic visual ring indicating the remaining seconds (0 to 30) of the current epoch.
   - Refreshes all 2FA codes automatically when the timer hits 0.
3. **Flexible Account Import Methods**:
   - **Manual Key Input**: Input fields for Issuer, Account name, and the Secret Key.
     - *User Tip Label*: Clearly explain that the "Setup Key" is the 16-character backup text code (e.g., `JBSWY3DPEHPK3PXP`) provided by sites during 2FA setup.
   - **Webcam / Camera Scanner**: Use **CameraX** to scan QR Codes. Provide a button to flip between the front and rear cameras.
   - **Image Scan**: Let the user choose an image from their gallery, scan it via ML Kit, and decode the TOTP URI.
4. **Google Authenticator Migration Sync**:
   - Support scanning Google's export QR codes (`otpauth-migration://offline?data=...`).
   - Parse the URL-decoded, Base64-decoded protobuf payload containing:
     - `secret`: The raw secret key bytes (encode to Base32 for saving).
     - `name`: The account username/email.
     - `issuer`: The service provider.
   - Bulk-import all parsed accounts with a single scan.
5. **Backup CSV Import/Export**:
   - Parse and generate CSV templates containing: `issuer,name,secret`.

---

## 📁 Tab 3: Encrypted Password Manager
### Functional Requirements
1. **AES-256 Encrypted Local Storage**:
   - Use Room + SQLCipher. The database is encrypted using a key derived from the user's Master Password using PBKDF2 (minimum 65,536 iterations).
   - Integrate BiometricPrompt (fingerprint) as a fast-unlock bridge.
2. **Credential Detail Editor**:
   - Fields: Website/App Name, Username/Email, Password (with eye-toggle visibility), and Notes.
   - Quick Random Password Generator button inside the detail panel.
3. **Dynamically Filtered List**:
   - Instant search bar filtering entries by website, username, or notes.
4. **Import & Export Features**:
   - **CSV File Import/Export**: Let the user import from or export to standard CSV. Support dynamic header mapping (`website`, `url`, `username`, `login_username`, `password`, `notes`).
   - **QR Code Sharing**:
     - **Export to QR**: Convert a credentials entry to a custom URI: `passvault://import?website=...&username=...&password=...&notes=...` and display it as a QR code.
     - **Import from QR**: Scan a `passvault://` QR code via Camera/Webcam/Image to instantly import the credential.
   - **Smart Redirection**:
     - If the user scans a 2FA TOTP QR code inside the Password Manager, prompt them: *"This is a 2FA profile. Do you want to import it into the Authenticator instead?"*

---

## ⚡ Developer Hot Reload & Debug Bypass

To achieve the equivalent of our F5 recompile-and-reload bypass on Android during testing, configure the following:

1. **Android Studio Live Edit for Compose**:
   - Enable **Live Edit** in Android Studio settings (*Settings > Editor > Live Edit*).
   - This lets you edit Composable UI layouts and immediately see changes on the emulator/device in real-time without recompiling or relaunching the application, completely bypassing the lock screen!
2. **Debug Auto-Unlock Bypass (Build Config)**:
   - To avoid entering your Master Key/PIN on every debug build relaunch:
   - Configure a mock auto-login for the `debug` build variant inside your `MainActivity`:
     ```kotlin
     if (BuildConfig.DEBUG) {
         // Auto-login to the database during local emulator testing
         val debugKey = "debug_master_password_123"
         try {
             val loadedVault = CryptoUtils.loadVault(vaultFile, debugKey.toCharArray())
             // Bypasses PIN screen and enters Main Screen directly
             navigateToMainScreen(loadedVault)
         } catch (e: Exception) {
             // Fallback to normal dialog if key mismatch
             showUnlockScreen()
         }
     } else {
         showUnlockScreen()
     }
     ```
3. **Vector Icon Rendering**:
   - Do not use emoji characters (like 🔑, 🛡, 📁) in the UI text or tab bars. Emojis render differently across Android OS versions and often display as empty boxes or look generic.
   - Use Android Studio's built-in **Vector Assets** (`xml` paths) or Jetpack Compose's **Icons.Default** collection (e.g. `Icons.Default.VpnKey` for Key, `Icons.Default.Security` for Shield, and `Icons.Default.Folder` for Folder) to render crisp, high-DPI, theme-adaptive native vector graphics.

---

## 🛠 Implementation Blueprint: Kotlin Protobuf Decoder
If you are developing in Kotlin, here is the clean translation of the custom Google Authenticator Protobuf parser we implemented:

```kotlin
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.Base64

object GoogleAuthDecoder {
    fun decodeMigration(payload: ByteArray): List<Map<String, String>> {
        val accounts = mutableListOf<Map<String, String>>()
        val bis = ByteArrayInputStream(payload)
        
        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()
            
            if (tag == 1 && wireType == 2) {
                val len = readVarint(bis)
                val subBytes = ByteArray(len.toInt())
                bis.read(subBytes)
                val acc = parseOtpParameters(subBytes)
                if (acc != null) {
                    accounts.add(acc)
                }
            } else {
                skipField(bis, wireType)
            }
        }
        return accounts
    }
    
    private fun parseOtpParameters(bytes: ByteArray): Map<String, String>? {
        val bis = ByteArrayInputStream(bytes)
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        
        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()
            
            when {
                tag == 1 && wireType == 2 -> {
                    val len = readVarint(bis)
                    secret = ByteArray(len.toInt())
                    bis.read(secret)
                }
                tag == 2 && wireType == 2 -> {
                    val len = readVarint(bis)
                    val strBytes = ByteArray(len.toInt())
                    bis.read(strBytes)
                    name = String(strBytes, Charsets.UTF_8)
                }
                tag == 3 && wireType == 2 -> {
                    val len = readVarint(bis)
                    val strBytes = ByteArray(len.toInt())
                    bis.read(strBytes)
                    issuer = String(strBytes, Charsets.UTF_8)
                }
                else -> skipField(bis, wireType)
            }
        }
        
        if (secret == null) return null
        
        return mapOf(
            "secret" to encodeBase32(secret),
            "name" to name,
            "issuer" to if (issuer.isEmpty()) "Service" else issuer
        )
    }
    
    private fun readVarint(input: InputStream): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw java.io.EOFException()
            value = value or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }
    
    private fun skipField(input: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(input)
            1 -> input.skip(8)
            2 -> {
                val len = readVarint(input)
                input.skip(len)
            }
            5 -> input.skip(4)
        }
    }
    
    private fun encodeBase32(bytes: ByteArray): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var i = 0
        var index = 0
        var digit: Int
        var currByte: Int
        var nextByte: Int
        
        while (i < bytes.size) {
            currByte = if (bytes[i] >= 0) bytes[i].toInt() else bytes[i].toInt() + 256
            if (index > 3) {
                nextByte = if (i + 1 < bytes.size) {
                    if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else bytes[i + 1].toInt() + 256
                } else 0
                digit = currByte and (0xFF ushr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte ushr (8 - index))
                i++
            } else {
                digit = (currByte ushr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            sb.append(base32Chars[digit])
        }
        return sb.toString()
    }
}
```
