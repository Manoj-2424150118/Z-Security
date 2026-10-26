package com.zsecure.com.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zsecure.com.ui.theme.Spacing
import com.zsecure.com.ui.theme.StrengthMedium
import com.zsecure.com.ui.theme.StrengthStrong
import com.zsecure.com.ui.theme.StrengthVeryStrong
import com.zsecure.com.ui.theme.StrengthVeryWeak
import com.zsecure.com.ui.theme.StrengthWeak
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.security.SecureRandom

@Composable
fun GeneratorScreen() {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var length by remember { mutableFloatStateOf(16f) }
    var includeUpper by remember { mutableStateOf(true) }
    var includeLower by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    var generatedPassword by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    fun generate() {
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val digitChars = "0123456789"
        val symbolChars = "!@#$%^&*()-_=+[]{}|;:',.<>/?~"

        val activeCategories = mutableListOf<String>()
        val pool = StringBuilder()

        if (includeUpper) {
            activeCategories.add(upperChars)
            pool.append(upperChars)
        }
        if (includeLower) {
            activeCategories.add(lowerChars)
            pool.append(lowerChars)
        }
        if (includeNumbers) {
            activeCategories.add(digitChars)
            pool.append(digitChars)
        }
        if (includeSymbols) {
            activeCategories.add(symbolChars)
            pool.append(symbolChars)
        }

        if (pool.isEmpty() || activeCategories.isEmpty()) {
            generatedPassword = ""
            return
        }

        val random = SecureRandom()
        val chosenChars = mutableListOf<Char>()
        val len = length.toInt()

        // Force at least one character from each active category first
        for (category in activeCategories) {
            if (chosenChars.size < len) {
                val index = random.nextInt(category.length)
                chosenChars.add(category[index])
            }
        }

        // Fill the remaining length from the general pool
        val poolStr = pool.toString()
        while (chosenChars.size < len) {
            if (poolStr.isNotEmpty()) {
                val index = random.nextInt(poolStr.length)
                chosenChars.add(poolStr[index])
            }
        }

        // Cryptographically secure shuffle of the selected characters
        for (i in chosenChars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = chosenChars[i]
            chosenChars[i] = chosenChars[j]
            chosenChars[j] = temp
        }

        generatedPassword = chosenChars.joinToString("")

        // Maintain history (max 10)
        if (generatedPassword.isNotEmpty()) {
            if (history.contains(generatedPassword)) {
                history.remove(generatedPassword)
            }
            history.add(0, generatedPassword)
            if (history.size > 10) {
                history.removeLast()
            }
        }
    }

    // Generate once initially
    LaunchedEffect(Unit) {
        generate()
    }

    // Calculate Entropy
    var poolSize = 0
    if (includeUpper) poolSize += 26
    if (includeLower) poolSize += 26
    if (includeNumbers) poolSize += 10
    if (includeSymbols) poolSize += 29 // "!@#$%^&*()-_=+[]{}|;:',.<>/?~" length is 29

    val entropy = if (poolSize > 0) length.toInt() * (Math.log(poolSize.toDouble()) / Math.log(2.0)) else 0.0

    // Strength rating + semantic colour (thresholds unchanged)
    val (strengthLabel, barColor) = when {
        entropy < 28.0 -> "Very Weak" to StrengthVeryWeak
        entropy < 36.0 -> "Weak" to StrengthWeak
        entropy < 60.0 -> "Medium" to StrengthMedium
        entropy < 128.0 -> "Strong" to StrengthStrong
        else -> "Very Strong" to StrengthVeryStrong
    }

    fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        val clip = ClipData.newPlainText("Generated Password", text)
        clipboard.setPrimaryClip(clip)
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Password copied — clipboard clears in 45 seconds")
        }

        coroutineScope.launch {
            kotlinx.coroutines.delay(45000)
            try {
                val primaryClip = clipboard.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    val currentText = primaryClip.getItemAt(0).text?.toString()
                    if (currentText == text) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        Toast.makeText(context, "Clipboard cleared for security", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Silent catch
            }
        }
    }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Password Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = generatedPassword.ifEmpty { "Select options to generate" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (generatedPassword.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { generate() },
                        enabled = poolSize > 0
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Generate")
                    }

                    OutlinedButton(
                        onClick = { copyToClipboard(generatedPassword) },
                        enabled = generatedPassword.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Copy")
                    }
                }
            }
        }

        // Strength meter — animated progress with label + entropy
        if (generatedPassword.isNotEmpty()) {
            val progress = (entropy / 150.0).coerceIn(0.0, 1.0).toFloat()
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                label = "strengthProgress"
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strengthLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = barColor
                    )
                    Text(
                        text = "${String.format("%.1f", entropy)} bits of entropy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Customization Controls Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Length",
                        style = MaterialTheme.typography.titleMedium
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text("${length.toInt()} characters") }
                    )
                }
                Slider(
                    value = length,
                    onValueChange = { newValue ->
                        val oldVal = length.toInt()
                        length = newValue
                        val newVal = length.toInt()
                        if (newVal != oldVal) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        generate()
                    },
                    valueRange = 4f..64f,
                    steps = 60
                )

                HorizontalDivider()

                // Character-set switches (whole row is the touch target)
                CharacterSetRow(
                    label = "Uppercase",
                    example = "ABC",
                    checked = includeUpper,
                    onCheckedChange = { includeUpper = it; generate() }
                )
                CharacterSetRow(
                    label = "Lowercase",
                    example = "abc",
                    checked = includeLower,
                    onCheckedChange = { includeLower = it; generate() }
                )
                CharacterSetRow(
                    label = "Numbers",
                    example = "123",
                    checked = includeNumbers,
                    onCheckedChange = { includeNumbers = it; generate() }
                )
                CharacterSetRow(
                    label = "Symbols",
                    example = "!@#",
                    checked = includeSymbols,
                    onCheckedChange = { includeSymbols = it; generate() }
                )
            }
        }

        // History Card
        if (history.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "History",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "This session only — tap to copy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showClearHistoryConfirm = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear history",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(history) { pastPassword ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { copyToClipboard(pastPassword) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pastPassword,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy this password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(Spacing.screen)
    )
  }

  // Clear-history confirmation
  if (showClearHistoryConfirm) {
      AlertDialog(
          onDismissRequest = { showClearHistoryConfirm = false },
          icon = { Icon(Icons.Default.Delete, contentDescription = null) },
          title = { Text("Clear generator history?") },
          text = { Text("The ${history.size} passwords generated this session will be removed. Anything you still need should be copied or saved in your vault first.") },
          confirmButton = {
              Button(
                  onClick = {
                      history.clear()
                      showClearHistoryConfirm = false
                  },
                  colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.error,
                      contentColor = MaterialTheme.colorScheme.onError
                  )
              ) {
                  Text("Clear")
              }
          },
          dismissButton = {
              TextButton(onClick = { showClearHistoryConfirm = false }) {
                  Text("Cancel")
              }
          }
      )
  }
}

/** One character-set row: label + example, switch on the right, whole row toggles. */
@Composable
private fun CharacterSetRow(
    label: String,
    example: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = example,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
