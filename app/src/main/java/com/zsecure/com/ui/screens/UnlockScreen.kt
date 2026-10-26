package com.zsecure.com.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zsecure.com.R
import com.zsecure.com.data.DatabaseProvider
import com.zsecure.com.ui.theme.Spacing
import com.zsecure.com.utils.CryptoUtils
import kotlinx.coroutines.delay

@Composable
fun UnlockScreen(
    activity: FragmentActivity,
    onUnlocked: (ByteArray) -> Unit
) {
    val sharedPrefs = remember { CryptoUtils.getEncryptedSharedPreferences(activity) }
    val saltHex = sharedPrefs.getString("salt_hex", null)
    val isFirstTime = saltHex == null

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    // State for temporary password decryption check
    var keyHashCheckHex = sharedPrefs.getString("key_hash_check", null)

    var failedAttempts by remember { mutableStateOf(sharedPrefs.getInt("failed_attempts", 0)) }
    var cooldownUnlockTimestamp by remember { mutableStateOf(sharedPrefs.getLong("cooldown_unlock_timestamp", 0L)) }
    var cooldownTimeLeft by remember { mutableStateOf(0L) }

    LaunchedEffect(cooldownUnlockTimestamp) {
        while (System.currentTimeMillis() < cooldownUnlockTimestamp) {
            cooldownTimeLeft = ((cooldownUnlockTimestamp - System.currentTimeMillis()) / 1000) + 1
            delay(1000)
        }
        cooldownTimeLeft = 0L
    }

    fun handleUnlock() {
        if (cooldownTimeLeft > 0L) {
            errorText = "Too many failed attempts. Try again in $cooldownTimeLeft seconds."
            return
        }

        if (password.isEmpty()) {
            errorText = "Password cannot be empty"
            return
        }

        if (isFirstTime) {
            if (password != confirmPassword) {
                errorText = "Passwords do not match"
                return
            }
            if (password.length < 8) {
                errorText = "Password must be at least 8 characters"
                return
            }

            // Setup new vault
            val salt = CryptoUtils.generateSalt()
            val passwordChars = password.toCharArray()
            val derivedKey = CryptoUtils.deriveKey(passwordChars, salt)

            // Generate a verification hash check to confirm correct password in future
            val verificationSalt = CryptoUtils.generateSalt()
            val verificationHash = CryptoUtils.deriveKey(passwordChars, verificationSalt)
            CryptoUtils.clearArray(passwordChars) // Memory Safety: wipe password CharArray!

            val saltHexStr = salt.joinToString("") { String.format("%02x", it) }
            val verificationSaltHexStr = verificationSalt.joinToString("") { String.format("%02x", it) }
            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }

            // commit() (not apply()): if the process dies before an async apply()
            // flushes, the salt/verifier are lost while the DB file survives,
            // leaving a vault that can never be unlocked again.
            // Creating the master password is also the explicit opt-in that
            // enables biometric unlock (the derived key gets cached on unlock).
            sharedPrefs.edit()
                .putString("salt_hex", saltHexStr)
                .putString("ver_salt_hex", verificationSaltHexStr)
                .putString("key_hash_check", verificationHashHexStr)
                .putInt("pbkdf2_iterations", CryptoUtils.ITERATIONS)
                .putBoolean("biometric_unlock_enabled", true)
                .putInt("failed_attempts", 0)
                .putLong("cooldown_unlock_timestamp", 0L)
                .commit()

            failedAttempts = 0
            onUnlocked(derivedKey)
        } else {
            // Decrypt & Verify vault
            val verSaltHex = sharedPrefs.getString("ver_salt_hex", null)
            if (saltHex == null || verSaltHex == null) {
                // Previously this crashed with an NPE (ver_salt_hex!!) whenever
                // part of the stored setup data was missing.
                errorText = "Stored vault data is incomplete. The app's secure storage may have been cleared."
                return
            }
            val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val verSalt = verSaltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            // Vaults created before the PBKDF2 cost upgrade store their
            // iteration count (absent = legacy 65,536); derive with the STORED
            // count so existing users can still unlock.
            val storedIterations = sharedPrefs.getInt("pbkdf2_iterations", CryptoUtils.LEGACY_ITERATIONS)
            val passwordChars = password.toCharArray()
            val derivedKey = CryptoUtils.deriveKey(passwordChars, salt, storedIterations)
            val verificationHash = CryptoUtils.deriveKey(passwordChars, verSalt, storedIterations)
            CryptoUtils.clearArray(passwordChars) // Memory Safety: wipe password CharArray!

            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }

            if (verificationHashHexStr == keyHashCheckHex) {
                sharedPrefs.edit()
                    .putInt("failed_attempts", 0)
                    .putLong("cooldown_unlock_timestamp", 0L)
                    .apply()
                failedAttempts = 0

                if (storedIterations < CryptoUtils.ITERATIONS) {
                    // Transparent cost upgrade: re-derive at the current
                    // iteration count, rekey the vault, and store the new
                    // verifier. If anything fails, fall back to unlocking with
                    // the old key — migration is retried on the next unlock.
                    val migrationChars = password.toCharArray()
                    val upgradedKey = CryptoUtils.deriveKey(migrationChars, salt, CryptoUtils.ITERATIONS)
                    val upgradedVerifier = CryptoUtils.deriveKey(migrationChars, verSalt, CryptoUtils.ITERATIONS)
                    CryptoUtils.clearArray(migrationChars)

                    fun healPrefs() {
                        sharedPrefs.edit()
                            .putString(
                                "key_hash_check",
                                upgradedVerifier.joinToString("") { String.format("%02x", it) }
                            )
                            .putInt("pbkdf2_iterations", CryptoUtils.ITERATIONS)
                            .commit()
                    }

                    try {
                        DatabaseProvider.initialize(activity, derivedKey) // open with the old key
                        DatabaseProvider.rekey(activity, upgradedKey)
                        healPrefs()
                        onUnlocked(upgradedKey)
                        return
                    } catch (e: Exception) {
                        // The vault may ALREADY be on the upgraded key: if the
                        // process died after a previous attempt's rekey but
                        // before its prefs commit, the stored count/verifier
                        // still describe the old key while the DB does not.
                        // Heal by opening with the upgraded key and rewriting
                        // the prefs (verification above used the same
                        // password, so this cannot be triggered by a wrong one).
                        try {
                            DatabaseProvider.initialize(activity, upgradedKey)
                            healPrefs()
                            onUnlocked(upgradedKey)
                            return
                        } catch (e2: Exception) {
                            // fall through to a normal unlock with the old key
                        }
                    }
                }
                onUnlocked(derivedKey)
            } else {
                failedAttempts++
                var cooldownSeconds = 0L
                if (failedAttempts >= 5) {
                    val multiplier = Math.pow(2.0, (failedAttempts - 5).toDouble()).toLong()
                    cooldownSeconds = multiplier * 30L
                    if (cooldownSeconds > 3600) cooldownSeconds = 3600L // Limit lockout to 1 hour max
                }

                val unlockTimestamp = if (cooldownSeconds > 0) System.currentTimeMillis() + cooldownSeconds * 1000L else 0L
                sharedPrefs.edit()
                    .putInt("failed_attempts", failedAttempts)
                    .putLong("cooldown_unlock_timestamp", unlockTimestamp)
                    .apply()

                cooldownUnlockTimestamp = unlockTimestamp

                if (cooldownSeconds > 0) {
                    errorText = "Too many failed attempts. Locking vault for $cooldownSeconds seconds."
                } else {
                    errorText = "Incorrect password (${5 - failedAttempts} attempts remaining before lockout)"
                }
            }
        }
    }

    fun triggerBiometrics() {
        if (cooldownTimeLeft > 0L) {
            errorText = "Too many failed attempts. Try again in $cooldownTimeLeft seconds."
            return
        }
        val biometricManager = BiometricManager.from(activity)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val cachedKeyHex = sharedPrefs.getString("cached_key_hex", null)
                        if (cachedKeyHex != null) {
                            val cachedKey = cachedKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            onUnlocked(cachedKey)
                        } else {
                            errorText = "Please enter password once first to enable biometric unlock"
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Unlock")
                .setSubtitle("Unlock Z+ Secure vault")
                .setNegativeButtonText("Use Password")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    val biometricAvailable = !isFirstTime && sharedPrefs.contains("cached_key_hex")
    val inCooldown = cooldownTimeLeft > 0L

    // Biometric-first: offer the prompt immediately on arrival (previously it
    // waited ~6 s for a decorative splash animation to finish). The same
    // gating applies as before: only with a cached key, never during cooldown
    // (triggerBiometrics re-checks the cooldown itself).
    LaunchedEffect(isFirstTime) {
        if (biometricAvailable) {
            triggerBiometrics()
        }
    }

    // Quick, purposeful entrance — content is usable immediately.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.xl))

            // Brand mark
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Z+ Secure logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "Z+ Secure",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = if (isFirstTime) "Create your vault" else "Welcome back",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = if (isFirstTime)
                    "Choose a strong master password. Your passwords and 2FA keys are encrypted on this device — this password cannot be recovered if forgotten."
                else
                    "Unlock with biometrics, or enter your master password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            if (biometricAvailable) {
                Button(
                    onClick = { triggerBiometrics() },
                    enabled = !inCooldown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text("Unlock with biometrics")
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "or use your master password",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorText = "" },
                label = { Text("Master password") },
                singleLine = true,
                enabled = !inCooldown,
                isError = errorText.isNotEmpty(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isFirstTime) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { handleUnlock() }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !inCooldown
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (isFirstTime) {
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorText = "" },
                    label = { Text("Confirm master password") },
                    singleLine = true,
                    enabled = !inCooldown,
                    isError = errorText.isNotEmpty(),
                    supportingText = { Text("At least 8 characters. There is no recovery if you forget it.") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleUnlock() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error / lockout state
            AnimatedVisibility(visible = errorText.isNotEmpty() || inCooldown) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = if (inCooldown) Icons.Default.Lock else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (inCooldown)
                                "Too many failed attempts. Try again in $cooldownTimeLeft seconds."
                            else errorText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = { handleUnlock() },
                enabled = !inCooldown,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (isFirstTime) "Create vault" else "Unlock vault")
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Encrypted on this device only — nothing leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
