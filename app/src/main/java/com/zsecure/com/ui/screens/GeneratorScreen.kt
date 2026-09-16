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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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

    // Strength ratings & color configurations
    val (strengthLabel, barColor, textColor) = when {
        entropy < 28.0 -> Triple("Very Weak", Color(0xFFE57373), Color.White) // Red
        entropy < 36.0 -> Triple("Weak", Color(0xFFFFB74D), Color.Black)      // Orange
        entropy < 60.0 -> Triple("Medium", Color(0xFFFFF176), Color.Black)    // Yellow
        entropy < 128.0 -> Triple("Strong", Color(0xFF81C784), Color.Black)   // Green
        else -> Triple("Very Strong", Color(0xFF2E7D32), Color.White)         // Dark Green
    }

    fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        val clip = ClipData.newPlainText("Generated Password", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Password copied to clipboard!", Toast.LENGTH_SHORT).show()
        
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Password Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        color = if (generatedPassword.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { generate() },
                        enabled = poolSize > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D6EFD),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate", fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedButton(
                        onClick = { copyToClipboard(generatedPassword) },
                        enabled = generatedPassword.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00E5FF)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.sweepGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFF0D6EFD)))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Strength Indicator Bar with dynamic high-contrast text color
        if (generatedPassword.isNotEmpty()) {
            val progress = (entropy / 150.0).coerceIn(0.0, 1.0).toFloat()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Entropy: ${String.format("%.1f", entropy)} bits",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202B3C)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                    
                    Text(
                        text = "$strengthLabel (${String.format("%.1f", entropy)} bits)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Customization Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Length: ${length.toInt()}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
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
                    steps = 60,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Checkbox Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeUpper = !includeUpper; generate() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeUpper,
                        onCheckedChange = { includeUpper = it; generate() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Text("Uppercase (A-Z)", color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeLower = !includeLower; generate() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeLower,
                        onCheckedChange = { includeLower = it; generate() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Text("Lowercase (a-z)", color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeNumbers = !includeNumbers; generate() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeNumbers,
                        onCheckedChange = { includeNumbers = it; generate() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Text("Numbers (0-9)", color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeSymbols = !includeSymbols; generate() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSymbols,
                        onCheckedChange = { includeSymbols = it; generate() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Text("Symbols (!@#$)", color = Color.White)
                }
            }
        }

        // History Card
        if (history.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "History (Tap to copy)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        IconButton(
                            onClick = { history.clear() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear History",
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(history) { pastPassword ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B2433))
                                    .clickable { copyToClipboard(pastPassword) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pastPassword,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Icon",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
