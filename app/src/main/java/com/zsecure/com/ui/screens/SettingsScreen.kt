package com.zsecure.com.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import com.zsecure.com.BuildConfig
import com.zsecure.com.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zsecure.com.data.DatabaseProvider
import com.zsecure.com.ui.theme.Spacing
import com.zsecure.com.utils.CryptoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("KotlinConstantConditions")
fun SettingsScreen(
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { CryptoUtils.getEncryptedSharedPreferences(context) }
    
    var lockOnClose by remember { mutableStateOf(sharedPrefs.getBoolean("lock_on_close", true)) }
    var biometricEnabled by remember { mutableStateOf(sharedPrefs.contains("cached_key_hex")) }

    // Dialog trigger states
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    // Verification Dialog fields
    var verifyPassword by remember { mutableStateOf("") }
    var verifyPasswordVisible by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf("") }

    // Setup Dialog fields
    var setupPassword by remember { mutableStateOf("") }
    var confirmSetupPassword by remember { mutableStateOf("") }
    var setupPasswordVisible by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // App Header Branding Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Z+ Secure logo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Z+ Secure",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Encrypted vault & authenticator",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Section: Security
        Text(
            text = "Security",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = Spacing.sm)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Lock on Close Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lock on app close",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Lock the vault as soon as you leave the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = lockOnClose,
                        onCheckedChange = { isChecked ->
                            if (!isChecked) {
                                // Request password verification to toggle OFF security
                                verifyPassword = ""
                                verifyError = ""
                                verifyPasswordVisible = false
                                showVerifyDialog = true
                            } else {
                                // Request password creation to toggle ON security
                                setupPassword = ""
                                confirmSetupPassword = ""
                                setupError = ""
                                setupPasswordVisible = false
                                showSetupDialog = true
                            }
                        }
                    )
                }

                HorizontalDivider()

                // Biometrics status / config
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Biometric unlock",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (biometricEnabled) "Ready — your key is cached for fingerprint unlock" else "Turns on when you set a master password",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (biometricEnabled) {
                        TextButton(
                            onClick = {
                                sharedPrefs.edit().remove("cached_key_hex").apply()
                                biometricEnabled = false
                                Toast.makeText(context, "Biometric cache cleared!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Section: Vault
        Text(
            text = "Vault",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = Spacing.sm)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        DatabaseProvider.lock()
                        onLock()
                    },
                    enabled = lockOnClose, // Only manual lock if lock option is on!
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Lock vault now")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vault encrypted at rest with SQLCipher (AES-256); keys derived with PBKDF2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // 1. Password Verification Dialog (To Turn OFF Security)
    if (showVerifyDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("Disable app protection?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Verify your master password to disable auto-lock. Your vault will be re-encrypted with a device-local key so it stays encrypted at rest, but anyone holding your phone will be able to open it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = verifyPassword,
                        onValueChange = { verifyPassword = it; verifyError = "" },
                        label = { Text("Master password") },
                        singleLine = true,
                        isError = verifyError.isNotEmpty(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (verifyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (verifyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { verifyPasswordVisible = !verifyPasswordVisible }) {
                                Icon(icon, contentDescription = if (verifyPasswordVisible) "Hide password" else "Show password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (verifyError.isNotEmpty()) {
                        Text(text = verifyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val saltHex = sharedPrefs.getString("salt_hex", null)
                        val keyHashCheckHex = sharedPrefs.getString("key_hash_check", null)
                        
                        if (saltHex == null || keyHashCheckHex == null) {
                            verifyError = "No password setup found."
                            return@Button
                        }
                        
                        try {
                            val verSaltHex = sharedPrefs.getString("ver_salt_hex", null)
                            if (verSaltHex == null) {
                                verifyError = "Stored verification data is missing. Vault settings may be corrupted."
                                return@Button
                            }
                            val verSalt = verSaltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

                            // Verify with the vault's STORED iteration count (older
                            // vaults predate the PBKDF2 cost upgrade)
                            val storedIterations = sharedPrefs.getInt("pbkdf2_iterations", CryptoUtils.LEGACY_ITERATIONS)
                            val verificationHash = CryptoUtils.deriveKey(verifyPassword.toCharArray(), verSalt, storedIterations)
                            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }
                            
                            if (verificationHashHexStr == keyHashCheckHex) {
                                // Rekey the vault from the user's master key to a fresh RANDOM
                                // device-local key, stored in EncryptedSharedPreferences as
                                // auto_unlock_key_hex (the same mechanism MainActivity uses when
                                // lock_on_close is off). NEVER rekey to a constant: the previous
                                // code used a published static key, leaving the vault readable by
                                // anyone with the app source.
                                val autoKey = ByteArray(32)
                                java.security.SecureRandom().nextBytes(autoKey)
                                DatabaseProvider.rekey(context, autoKey)
                                DatabaseProvider.initialize(context, autoKey)
                                val autoKeyHex = autoKey.joinToString("") { String.format("%02x", it) }
                                CryptoUtils.clearArray(autoKey)

                                // Delete secure settings in preferences
                                sharedPrefs.edit()
                                    .putBoolean("lock_on_close", false)
                                    .putString("auto_unlock_key_hex", autoKeyHex)
                                    .putBoolean("biometric_unlock_enabled", false)
                                    .remove("salt_hex")
                                    .remove("ver_salt_hex")
                                    .remove("key_hash_check")
                                    .remove("cached_key_hex")
                                    .apply()
                                
                                lockOnClose = false
                                biometricEnabled = false
                                showVerifyDialog = false
                                Toast.makeText(context, "Protection disabled. Master Key reset.", Toast.LENGTH_LONG).show()
                            } else {
                                verifyError = "Invalid master password."
                            }
                        } catch (e: Exception) {
                            verifyError = "Verification failed: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Disable lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Password Setup Dialog (To Turn ON Security)
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("Enable app protection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set a master password. It encrypts your vault on this device and cannot be recovered if forgotten.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = setupPassword,
                        onValueChange = { setupPassword = it; setupError = "" },
                        label = { Text("New master password") },
                        singleLine = true,
                        isError = setupError.isNotEmpty(),
                        supportingText = { Text("At least 8 characters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (setupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (setupPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { setupPasswordVisible = !setupPasswordVisible }) {
                                Icon(icon, contentDescription = if (setupPasswordVisible) "Hide password" else "Show password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmSetupPassword,
                        onValueChange = { confirmSetupPassword = it; setupError = "" },
                        label = { Text("Confirm master password") },
                        singleLine = true,
                        isError = setupError.isNotEmpty(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (setupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (setupError.isNotEmpty()) {
                        Text(text = setupError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (setupPassword != confirmSetupPassword) {
                            setupError = "Passwords do not match."
                            return@Button
                        }
                        if (setupPassword.length < 8) {
                            setupError = "Password must be at least 8 characters."
                            return@Button
                        }
                        
                        try {
                            // Setup new vault
                            val salt = CryptoUtils.generateSalt()
                            val derivedKey = CryptoUtils.deriveKey(setupPassword.toCharArray(), salt)
                            
                            val verificationSalt = CryptoUtils.generateSalt()
                            val verificationHash = CryptoUtils.deriveKey(setupPassword.toCharArray(), verificationSalt)
                            
                            val saltHexStr = salt.joinToString("") { String.format("%02x", it) }
                            val verificationSaltHexStr = verificationSalt.joinToString("") { String.format("%02x", it) }
                            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }
                            
                            // Rekey vault from the current (random auto-unlock) key to user's new custom key
                            DatabaseProvider.rekey(context, derivedKey)
                            
                            // Re-initialize Room with the user's custom key
                            DatabaseProvider.initialize(context, derivedKey)
                            
                            // Save secure configs to preferences. Setting up protection is
                            // the explicit opt-in that enables biometric unlock + key cache.
                            sharedPrefs.edit()
                                .putBoolean("lock_on_close", true)
                                .putBoolean("biometric_unlock_enabled", true)
                                .putInt("pbkdf2_iterations", CryptoUtils.ITERATIONS)
                                .remove("auto_unlock_key_hex")
                                .putString("salt_hex", saltHexStr)
                                .putString("ver_salt_hex", verificationSaltHexStr)
                                .putString("key_hash_check", verificationHashHexStr)
                                .putString("cached_key_hex", derivedKey.joinToString("") { String.format("%02x", it) })
                                .apply()
                            
                            lockOnClose = true
                            biometricEnabled = true
                            showSetupDialog = false
                            Toast.makeText(context, "Protection enabled. Vault encrypted.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            setupError = "Configuration failed: ${e.message}"
                        }
                    },
                ) {
                    Text("Enable protection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
