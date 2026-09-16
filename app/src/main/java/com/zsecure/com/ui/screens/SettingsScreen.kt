package com.zsecure.com.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.zsecure.com.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zsecure.com.data.DatabaseProvider
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
            .background(Color(0xFF0A0E17)) // World class deep-dark space theme background
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Branding Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cropped app launcher icon
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Z+ Secure Logo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Z+ Secure",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Version 1.0.0 (Premium Vault)",
                    fontSize = 12.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Section Title: Security Settings
        Text(
            text = "Security Configuration",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            ),
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // Security Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lock on App Close",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Instantly lock vault when minimizing the app",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
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
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Black.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Biometrics Display / Config Info
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
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Biometric Lock Cache",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (biometricEnabled) "Biometric Unlock is configured and ready" else "Setup key first to cache biometrics",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
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
                    } else {
                        Text(
                            text = "Auto-on",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section Title: Vault Actions
        Text(
            text = "Vault Protection",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            ),
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        DatabaseProvider.lock()
                        onLock()
                    },
                    enabled = lockOnClose, // Only manual lock if lock option is on!
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Lock Database Instantly", color = Color.White, fontWeight = FontWeight.Bold)
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
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Z+ Secure utilizes AES-256 local encryption.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
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
            title = { Text("Disable App Protection", color = Color.White) },
            containerColor = Color(0xFF131A26),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Verify your Master Password to decrypt your vault and disable auto-lock.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = verifyPassword,
                        onValueChange = { verifyPassword = it; verifyError = "" },
                        label = { Text("Master Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                        ),
                        visualTransformation = if (verifyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (verifyPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { verifyPasswordVisible = !verifyPasswordVisible }) {
                                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (verifyError.isNotEmpty()) {
                        Text(text = verifyError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                            val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            val verSaltHex = sharedPrefs.getString("ver_salt_hex", null)!!
                            val verSalt = verSaltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            
                            val derivedKey = CryptoUtils.deriveKey(verifyPassword.toCharArray(), salt)
                            val verificationHash = CryptoUtils.deriveKey(verifyPassword.toCharArray(), verSalt)
                            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }
                            
                            if (verificationHashHexStr == keyHashCheckHex) {
                                // Rekey vault from current user master key to default static key
                                DatabaseProvider.rekey(context, derivedKey, DatabaseProvider.DEFAULT_KEY)
                                
                                // Re-initialize db with default key
                                DatabaseProvider.initialize(context, DatabaseProvider.DEFAULT_KEY)
                                
                                // Delete secure settings in preferences
                                sharedPrefs.edit()
                                    .putBoolean("lock_on_close", false)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Disable Lock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // 2. Password Setup Dialog (To Turn ON Security)
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text("Enable App Protection", color = Color.White) },
            containerColor = Color(0xFF131A26),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set up a new Master Password. This password will encrypt your local database.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = setupPassword,
                        onValueChange = { setupPassword = it; setupError = "" },
                        label = { Text("New Master Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                        ),
                        visualTransformation = if (setupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (setupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { setupPasswordVisible = !setupPasswordVisible }) {
                                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmSetupPassword,
                        onValueChange = { confirmSetupPassword = it; setupError = "" },
                        label = { Text("Confirm Master Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                        ),
                        visualTransformation = if (setupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (setupError.isNotEmpty()) {
                        Text(text = setupError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                            
                            // Rekey vault from current default static key to user's new custom key
                            DatabaseProvider.rekey(context, DatabaseProvider.DEFAULT_KEY, derivedKey)
                            
                            // Re-initialize Room with the user's custom key
                            DatabaseProvider.initialize(context, derivedKey)
                            
                            // Save secure configs to preferences
                            sharedPrefs.edit()
                                .putBoolean("lock_on_close", true)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD))
                ) {
                    Text("Enable protection", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}
