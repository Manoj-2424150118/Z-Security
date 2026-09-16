package com.zsecure.com.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zsecure.com.R
import com.zsecure.com.utils.CryptoUtils
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

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

            sharedPrefs.edit()
                .putString("salt_hex", saltHexStr)
                .putString("ver_salt_hex", verificationSaltHexStr)
                .putString("key_hash_check", verificationHashHexStr)
                .putInt("failed_attempts", 0)
                .putLong("cooldown_unlock_timestamp", 0L)
                .apply()

            failedAttempts = 0
            onUnlocked(derivedKey)
        } else {
            // Decrypt & Verify vault
            val salt = saltHex!!.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val verSaltHex = sharedPrefs.getString("ver_salt_hex", null)!!
            val verSalt = verSaltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            
            val passwordChars = password.toCharArray()
            val derivedKey = CryptoUtils.deriveKey(passwordChars, salt)
            val verificationHash = CryptoUtils.deriveKey(passwordChars, verSalt)
            CryptoUtils.clearArray(passwordChars) // Memory Safety: wipe password CharArray!
            
            val verificationHashHexStr = verificationHash.joinToString("") { String.format("%02x", it) }

            if (verificationHashHexStr == keyHashCheckHex) {
                sharedPrefs.edit()
                    .putInt("failed_attempts", 0)
                    .putLong("cooldown_unlock_timestamp", 0L)
                    .apply()
                failedAttempts = 0
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
                .setSubtitle("Unlock AegisPass Vault")
                .setNegativeButtonText("Use Password")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    val localDensity = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Get screen dimensions in Dp from configuration
    val screenHeightDp = activity.resources.configuration.screenHeightDp
    val screenWidthDp = activity.resources.configuration.screenWidthDp

    val heightPx = with(localDensity) { screenHeightDp.dp.toPx() }
    val widthPx = with(localDensity) { screenWidthDp.dp.toPx() }

    // Water level at 58% of the screen height
    val waterLevelY = heightPx * 0.58f
    val waterLevelYDp = screenHeightDp * 0.58f
    val iconSizeDp = 88.dp
    val halfIconSizeDp = 44f

    // Animation states
    val iconY = remember { Animatable(-180f) } // starts 180dp above screen
    val rotationXAnim = remember { Animatable(60f) }
    val rotationYAnim = remember { Animatable(-90f) }
    val rotationZAnim = remember { Animatable(45f) }
    val scaleAnim = remember { Animatable(0.7f) }

    val rippleAnim = remember { Animatable(0f) }
    val splashAnim = remember { Animatable(0f) }

    var animationFinished by remember { mutableStateOf(false) }
    val contentAlpha = remember { Animatable(0f) }
    val bobbingAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(400) // Initial delay to build anticipation

        // Step 1: Drop to water level
        iconY.animateTo(
            targetValue = waterLevelYDp,
            animationSpec = tween(durationMillis = 1800, easing = FastOutLinearInEasing)
        )

        // Step 2: Impact! Trigger ripples and splash in parallel
        launch {
            rippleAnim.animateTo(1f, tween(3200, easing = LinearOutSlowInEasing))
        }
        launch {
            splashAnim.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing))
        }

        // Wobble/Settle the 3D rotation in parallel
        launch {
            rotationXAnim.animateTo(30f, tween(2000, easing = LinearOutSlowInEasing))
        }
        launch {
            rotationYAnim.animateTo(-35f, tween(2000, easing = LinearOutSlowInEasing))
        }
        launch {
            rotationZAnim.animateTo(0f, tween(2000, easing = LinearOutSlowInEasing))
        }
        launch {
            scaleAnim.animateTo(1f, tween(2000, easing = LinearOutSlowInEasing))
        }

        // Submerge (overshoot down)
        iconY.animateTo(
            targetValue = waterLevelYDp + 30f,
            animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
        )

        // Float back up above water line
        iconY.animateTo(
            targetValue = waterLevelYDp - 12f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )

        // Settle at water line
        iconY.animateTo(
            targetValue = waterLevelYDp,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )

        // Mark animation as finished and fade in password card
        animationFinished = true
        contentAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    // Bobbing effect after settling
    LaunchedEffect(animationFinished) {
        if (animationFinished) {
            bobbingAnim.animateTo(
                targetValue = 6f, // bob down 6dp
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    // Auto trigger biometric if enabled and cached key exists, only AFTER the opening animation has finished
    LaunchedEffect(isFirstTime, animationFinished) {
        if (!isFirstTime && animationFinished) {
            val cachedKeyHex = sharedPrefs.getString("cached_key_hex", null)
            if (cachedKeyHex != null) {
                triggerBiometrics()
            }
        }
    }

    val currentY = if (animationFinished) waterLevelYDp + bobbingAnim.value else iconY.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
    ) {
        // Draw the water surface, wave ripple, concentric ripples and splash particles on a Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val waterLevel = canvasHeight * 0.58f

            // Draw water depth (vertical gradient below water level)
            val waterGradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0x9900E5FF), // Semi-transparent bright cyan/blue at the surface
                    Color(0xDD0D6EFD), // Deeper blue
                    Color(0xFF080C14)  // Blends into the dark background at the bottom
                ),
                startY = waterLevel,
                endY = canvasHeight
            )

            // Draw surface with wave deformation
            val path = Path()
            path.moveTo(0f, waterLevel)

            val impactX = canvasWidth / 2f
            val rVal = rippleAnim.value

            if (rVal > 0f && rVal < 1f) {
                // Wavy surface deformation
                val waveAmplitude = with(localDensity) { 24.dp.toPx() } * (1f - rVal) * sin(rVal * 5f * Math.PI.toFloat())
                val waveHalfWidth = with(localDensity) { 150.dp.toPx() }

                path.lineTo(impactX - waveHalfWidth, waterLevel)
                path.cubicTo(
                    impactX - waveHalfWidth / 2f, waterLevel + waveAmplitude,
                    impactX - waveHalfWidth / 4f, waterLevel - waveAmplitude,
                    impactX, waterLevel + waveAmplitude / 2f
                )
                path.cubicTo(
                    impactX + waveHalfWidth / 4f, waterLevel + waveAmplitude,
                    impactX + waveHalfWidth / 2f, waterLevel - waveAmplitude,
                    impactX + waveHalfWidth, waterLevel
                )
                path.lineTo(canvasWidth, waterLevel)
            } else {
                path.lineTo(canvasWidth, waterLevel)
            }

            path.lineTo(canvasWidth, canvasHeight)
            path.lineTo(0f, canvasHeight)
            path.close()

            drawPath(path = path, brush = waterGradient)

            // Draw concentric ripple circles
            if (rVal > 0f) {
                val maxRippleRadius = with(localDensity) { 130.dp.toPx() }
                val rippleRadius1 = rVal * maxRippleRadius
                val alpha1 = 1f - rVal

                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha1 * 0.7f),
                    radius = rippleRadius1,
                    center = Offset(impactX, waterLevel),
                    style = Stroke(width = with(localDensity) { 3.dp.toPx() })
                )

                if (rVal > 0.25f) {
                    val rVal2 = (rVal - 0.25f) / 0.75f
                    drawCircle(
                        color = Color(0xFF0D6EFD).copy(alpha = (1f - rVal2) * 0.5f),
                        radius = rVal2 * maxRippleRadius * 0.8f,
                        center = Offset(impactX, waterLevel),
                        style = Stroke(width = with(localDensity) { 2.dp.toPx() })
                    )
                }
            }

            // Draw splash particles
            val sVal = splashAnim.value
            if (sVal > 0f && sVal < 1f) {
                val maxSplashDistance = with(localDensity) { 90.dp.toPx() }
                val particleCount = 8
                for (i in 0 until particleCount) {
                    val angle = Math.toRadians((180 + i * (180 / (particleCount - 1))).toDouble())
                    val speed = 1.0f + (i % 3).toFloat() * 0.4f
                    val t = sVal

                    val x = impactX + cos(angle).toFloat() * t * maxSplashDistance * speed
                    val y = waterLevel + sin(angle).toFloat() * t * maxSplashDistance * speed + (t * t * with(localDensity) { 50.dp.toPx() })

                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 1f - t),
                        radius = with(localDensity) { (4f - t * 2f).dp.toPx() },
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Draw the 3D angled Icon
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (currentY - halfIconSizeDp).dp)
                    .size(iconSizeDp)
                    .graphicsLayer {
                        rotationX = rotationXAnim.value
                        rotationY = rotationYAnim.value
                        rotationZ = rotationZAnim.value
                        cameraDistance = 12f * density
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                    }
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF0D6EFD), Color(0xFF00E5FF))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0D6EFD))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Z+ Secure Logo",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }

        // Fade in the lock screen card once the animation finishes
        if (animationFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Z+ Secure Logo",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isFirstTime) "Setup Z+ Secure" else "Vault Locked",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (isFirstTime)
                                "Choose a strong master password. Your credentials and 2FA keys will be fully encrypted on this device."
                                else "Enter your master password to unlock your credentials.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorText = "" },
                            label = { Text("Master Password") },
                            singleLine = true,
                            enabled = (cooldownTimeLeft <= 0L),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF00E5FF),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                            ),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = (cooldownTimeLeft <= 0L)
                                ) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = Color.White.copy(alpha = 0.5f))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isFirstTime) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorText = "" },
                                label = { Text("Confirm Master Password") },
                                singleLine = true,
                                enabled = (cooldownTimeLeft <= 0L),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = Color(0xFF00E5FF),
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                                ),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (errorText.isNotEmpty()) {
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Start
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { handleUnlock() },
                            enabled = (cooldownTimeLeft <= 0L),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isFirstTime) "Create Vault" else "Unlock Z+ Secure",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (!isFirstTime && sharedPrefs.contains("cached_key_hex")) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = { triggerBiometrics() },
                                enabled = (cooldownTimeLeft <= 0L)
                            ) {
                                Text("Unlock with Biometrics", color = Color(0xFF00E5FF), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
