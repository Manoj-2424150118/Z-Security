package com.zsecure.com

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.zsecure.com.data.DatabaseProvider
import com.zsecure.com.ui.screens.MainContainer
import com.zsecure.com.ui.screens.UnlockScreen
import com.zsecure.com.utils.CryptoUtils

class MainActivity : FragmentActivity() {

    private val isUnlockedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots, screen-recordings, and leaks in recent apps list
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // Perform preference migration and get secure preferences
        CryptoUtils.migrateLegacyPreferences(this)
        val sharedPrefs = CryptoUtils.getEncryptedSharedPreferences(this)
        val lockOnClose = sharedPrefs.getBoolean("lock_on_close", true)

        if (!lockOnClose) {
            try {
                // Get or create secure hardware/random auto-unlock key
                var autoKeyHex = sharedPrefs.getString("auto_unlock_key_hex", null)
                val autoKey = if (autoKeyHex != null) {
                    autoKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    val key = ByteArray(32)
                    java.security.SecureRandom().nextBytes(key)
                    autoKeyHex = key.joinToString("") { String.format("%02x", it) }
                    sharedPrefs.edit().putString("auto_unlock_key_hex", autoKeyHex).commit()
                    key
                }
                DatabaseProvider.initialize(this, autoKey)
                CryptoUtils.clearArray(autoKey) // Wipe key from memory
                isUnlockedState.value = true
            } catch (e: Exception) {
                isUnlockedState.value = false
            }
        } else {
            isUnlockedState.value = DatabaseProvider.isUnlocked()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isUnlocked by isUnlockedState
                    
                    // Root/Integrity Warning dialog
                    val isRooted = remember { isDeviceRooted() }
                    var showRootWarning by remember { mutableStateOf(isRooted) }
                    
                    if (showRootWarning) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showRootWarning = false },
                            title = { androidx.compose.material3.Text("Security Warning") },
                            text = { androidx.compose.material3.Text("A rooted or compromised operating system environment has been detected. Running Z+ Secure in a compromised environment may expose your sensitive credentials to malicious software.") },
                            confirmButton = {
                                androidx.compose.material3.Button(onClick = { showRootWarning = false }) {
                                    androidx.compose.material3.Text("I Understand")
                                }
                            }
                        )
                    }

                    if (isUnlocked) {
                        MainContainer(
                            activity = this@MainActivity,
                            onLock = { isUnlocked = false }
                        )
                    } else {
                        UnlockScreen(
                            activity = this@MainActivity,
                            onUnlocked = { derivedKey ->
                                // Cache the derived key as hex inside EncryptedSharedPreferences for biometric convenience
                                val securePrefs = CryptoUtils.getEncryptedSharedPreferences(this@MainActivity)
                                val keyHex = derivedKey.joinToString("") { String.format("%02x", it) }
                                securePrefs.edit()
                                    .putString("cached_key_hex", keyHex)
                                    .apply()

                                DatabaseProvider.initialize(this@MainActivity, derivedKey)
                                CryptoUtils.clearArray(derivedKey) // Wipe derived key from memory
                                isUnlocked = true
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isDeviceRooted(): Boolean {
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inStream = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            inStream.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    override fun onResume() {
        super.onResume()
        DatabaseProvider.isLaunchingIntent = false
    }

    override fun onStop() {
        super.onStop()
        val sharedPrefs = CryptoUtils.getEncryptedSharedPreferences(this)
        val lockOnClose = sharedPrefs.getBoolean("lock_on_close", true)
        if (lockOnClose && !DatabaseProvider.isLaunchingIntent) {
            DatabaseProvider.lock()
            isUnlockedState.value = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DatabaseProvider.lock()
    }
}
