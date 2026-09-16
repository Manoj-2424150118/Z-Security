package com.zsecure.com.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.zsecure.com.data.Credential
import com.zsecure.com.data.DatabaseProvider
import com.zsecure.com.data.TwoFactorAccount
import com.zsecure.com.utils.GoogleAuthDecoder
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.SecureRandom

@Composable
fun ManagerScreen(
    activity: FragmentActivity,
    onRedirectToAuthenticator: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = DatabaseProvider.getInstance()
    val credentialDao = db.credentialDao()
    val twoFactorDao = db.twoFactorDao()

    val credentials by credentialDao.getAllCredentials().collectAsState(initial = emptyList())
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCredential by remember { mutableStateOf<Credential?>(null) }
    var viewingQrCode by remember { mutableStateOf<String?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }
    
    // Redirect dialogue state
    var redirectAccountToImport by remember { mutableStateOf<TwoFactorAccount?>(null) }

    val filteredList = credentials.filter {
        it.website.contains(searchQuery, ignoreCase = true) ||
        it.username.contains(searchQuery, ignoreCase = true) ||
        it.notes.contains(searchQuery, ignoreCase = true)
    }

    // Copy helper
    fun copyToClipboard(label: String, value: String) {
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
        
        coroutineScope.launch {
            delay(45000)
            try {
                val primaryClip = clipboard.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    val currentText = primaryClip.getItemAt(0).text?.toString()
                    if (currentText == value) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        Toast.makeText(context, "Clipboard cleared for security", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Silent catch
            }
        }
    }

    // QR Code Bitmap generation helper
    fun generateQrBitmap(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    // Random password generator button (Quick generator)
    fun generateQuickPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
        val random = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until 16) {
            if (chars.isNotEmpty()) {
                sb.append(chars[random.nextInt(chars.length)])
            }
        }
        return sb.toString()
    }

    // Scan result parser
    fun handleScanResult(content: String) {
        if (content.startsWith("passvault://import")) {
            // passvault://import?website=...&username=...&password=...&notes=...
            try {
                val uri = Uri.parse(content)
                val rawWebsite = uri.getQueryParameter("website") ?: "Imported"
                val website = cleanAndroidUri(rawWebsite)
                val username = uri.getQueryParameter("username") ?: ""
                val password = uri.getQueryParameter("password") ?: ""
                val notes = uri.getQueryParameter("notes") ?: ""

                coroutineScope.launch {
                    credentialDao.insert(
                        Credential(website = website, username = username, password = password, notes = notes)
                    )
                    Toast.makeText(context, "Credential Imported: $website", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to parse import QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (content.startsWith("otpauth://") || content.startsWith("otpauth-migration://")) {
            // Prompt smart redirection
            try {
                val uri = Uri.parse(content)
                val secret = uri.getQueryParameter("secret")
                var issuer = uri.getQueryParameter("issuer") ?: ""
                var name = uri.path?.removePrefix("/") ?: ""
                if (name.contains(":")) {
                    val parts = name.split(":")
                    if (issuer.isEmpty()) issuer = parts[0]
                    name = parts[1]
                }
                
                if (content.startsWith("otpauth-migration://") || !secret.isNullOrEmpty()) {
                    redirectAccountToImport = TwoFactorAccount(
                        issuer = issuer.ifEmpty { "Service" },
                        name = name.ifEmpty { "User" },
                        secret = secret ?: content // For migration URLs, save whole url temporarily
                    )
                } else {
                    Toast.makeText(context, "Invalid 2FA QR content", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Unrecognized QR code format", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val content = barcodes[0].rawValue ?: ""
                            handleScanResult(content)
                        } else {
                            Toast.makeText(context, "No QR Code found in selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CSV Import Launcher with dynamic header mapping
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line = reader.readLine()
                if (line == null) {
                    reader.close()
                    return@rememberLauncherForActivityResult
                }

                // Handle UTF-8 Byte Order Mark (BOM)
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1)
                }

                // Dynamic Header mapping logic (using fuzzy matching)
                val headers = parseCsvLine(line).map { it.trim().lowercase() }
                
                var websiteIdx = headers.indexOfFirst { 
                    it.contains("website") || it.contains("url") || it.contains("app") || 
                    it.contains("domain") || it.contains("site") || it.contains("title") || 
                    it.contains("name") || it.contains("host") || it.contains("link")
                }
                var usernameIdx = headers.indexOfFirst { 
                    it.contains("username") || it.contains("email") || 
                    it.contains("login") || it.contains("user")
                }
                var passwordIdx = headers.indexOfFirst { 
                    it.contains("password") || it.contains("pass") || it.contains("secret")
                }
                var notesIdx = headers.indexOfFirst { 
                    it.contains("note") || it.contains("comment") || it.contains("desc") || it.contains("remark")
                }

                var hasHeader = true
                if (websiteIdx == -1 && usernameIdx == -1 && passwordIdx == -1) {
                    // Fallback to standard indices if no headers matched
                    hasHeader = false
                    websiteIdx = 0
                    usernameIdx = 1
                    passwordIdx = 2
                    notesIdx = 3
                }

                val imported = mutableListOf<Credential>()

                // If first line was data (not headers), parse it as a credential
                if (!hasHeader) {
                    val parts = parseCsvLine(line!!)
                    if (parts.isNotEmpty()) {
                        val rawWebsite = if (websiteIdx >= 0 && websiteIdx < parts.size) parts[websiteIdx].trim() else "Imported Site"
                        val website = cleanAndroidUri(rawWebsite)
                        val username = if (usernameIdx >= 0 && usernameIdx < parts.size) parts[usernameIdx].trim() else ""
                        val password = if (passwordIdx >= 0 && passwordIdx < parts.size) parts[passwordIdx].trim() else ""
                        val notes = if (notesIdx >= 0 && notesIdx < parts.size) parts[notesIdx].trim() else ""

                        imported.add(Credential(website = website.ifEmpty { "Imported Site" }, username = username, password = password, notes = notes))
                    }
                }

                // Parse the rest of the lines
                while (reader.readLine().also { line = it } != null) {
                    val parts = parseCsvLine(line!!)
                    if (parts.isNotEmpty()) {
                        val rawWebsite = if (websiteIdx >= 0 && websiteIdx < parts.size) parts[websiteIdx].trim() else "Imported Site"
                        val website = cleanAndroidUri(rawWebsite)
                        val username = if (usernameIdx >= 0 && usernameIdx < parts.size) parts[usernameIdx].trim() else ""
                        val password = if (passwordIdx >= 0 && passwordIdx < parts.size) parts[passwordIdx].trim() else ""
                        val notes = if (notesIdx >= 0 && notesIdx < parts.size) parts[notesIdx].trim() else ""

                        imported.add(Credential(website = website.ifEmpty { "Imported Site" }, username = username, password = password, notes = notes))
                    }
                }
                reader.close()
                inputStream?.close()

                if (imported.isNotEmpty()) {
                    coroutineScope.launch {
                        imported.forEach { credentialDao.insert(it) }
                        Toast.makeText(context, "Successfully imported ${imported.size} credentials!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No credentials found in CSV", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // CSV Export
    fun exportCsv() {
        if (credentials.isEmpty()) {
            Toast.makeText(context, "No credentials to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("website,username,password,notes\n")
            for (cred in credentials) {
                csvBuilder.append("\"${cred.website.replace("\"", "\"\"")}\",")
                csvBuilder.append("\"${cred.username.replace("\"", "\"\"")}\",")
                csvBuilder.append("\"${cred.password.replace("\"", "\"\"")}\",")
                csvBuilder.append("\"${cred.notes.replace("\"", "\"\"")}\"\n")
            }

            val filename = "AegisPass_Vault_Backup.csv"
            context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write(csvBuilder.toString().toByteArray())
            }

            val file = File(context.filesDir, filename)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Vault Backup"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar & Import/Export Row
            // Search Bar & Import/Export Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Vault...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { 
                    DatabaseProvider.isLaunchingIntent = true
                    csvImportLauncher.launch("text/*") 
                }) {
                    Icon(Icons.Default.Input, contentDescription = "Import CSV", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { exportCsv() }) {
                    Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { showCameraScanner = true }) {
                    Icon(Icons.Default.CropFree, contentDescription = "Scan QR", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            // Credentials list
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "Your Vault is Empty\nTap '+' to add a password credential." else "No matches found.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList) { credential ->
                        var passwordVisible by remember { mutableStateOf(false) }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cleanAndroidUri(credential.website),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row {
                                        IconButton(onClick = {
                                            val uriStr = "passvault://import?" +
                                                     "website=${Uri.encode(cleanAndroidUri(credential.website))}&" +
                                                     "username=${Uri.encode(credential.username)}&" +
                                                     "password=${Uri.encode(credential.password)}&" +
                                                     "notes=${Uri.encode(credential.notes)}"
                                            viewingQrCode = uriStr
                                        }) {
                                            Icon(Icons.Default.QrCode, contentDescription = "Export to QR", tint = Color.White.copy(alpha = 0.7f))
                                        }
                                        IconButton(onClick = { editingCredential = credential }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White.copy(alpha = 0.7f))
                                        }
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                credentialDao.delete(credential)
                                                Toast.makeText(context, "Deleted ${cleanAndroidUri(credential.website)}", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Username: ${credential.username}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Password: ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = if (passwordVisible) credential.password else "••••••••",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Visibility",
                                                tint = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                        IconButton(onClick = { copyToClipboard("Password", credential.password) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                if (credential.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Notes: ${credential.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = Color(0xFF0D6EFD),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Credential")
        }

        // Add / Edit Dialog Builder
        if (showAddDialog || editingCredential != null) {
            val cred = editingCredential
            var website by remember { mutableStateOf(cleanAndroidUri(cred?.website ?: "")) }
            var username by remember { mutableStateOf(cred?.username ?: "") }
            var password by remember { mutableStateOf(cred?.password ?: "") }
            var notes by remember { mutableStateOf(cred?.notes ?: "") }
            
            var passwordVisible by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    editingCredential = null
                },
                title = { Text(if (cred != null) "Edit Credential" else "Add Credential") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Website / App Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username / Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Quick password generator button inside the details panel!
                            IconButton(
                                onClick = { password = generateQuickPassword() },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate Password")
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (website.isEmpty()) return@Button
                            coroutineScope.launch {
                                val entry = Credential(
                                    id = cred?.id ?: 0,
                                    website = cleanAndroidUri(website),
                                    username = username,
                                    password = password,
                                    notes = notes
                                )
                                if (cred != null) {
                                    credentialDao.update(entry)
                                    editingCredential = null
                                } else {
                                    credentialDao.insert(entry)
                                    showAddDialog = false
                                }
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddDialog = false
                        editingCredential = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // QR Code Sharing Display Dialog
        if (viewingQrCode != null) {
            val bitmap = generateQrBitmap(viewingQrCode!!)
            AlertDialog(
                onDismissRequest = { viewingQrCode = null },
                title = { Text("Scan to Share Login") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Login QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .padding(8.dp)
                            )
                        } else {
                            Text("Failed to generate QR Code")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan this QR code inside AegisPass Password Manager on another device to instantly import this credential.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { viewingQrCode = null }) {
                        Text("Done")
                    }
                }
            )
        }

        // Dialog for CameraX QR Scanner overlay
        if (showCameraScanner) {
            CameraScannerDialog(
                onQrCodeScanned = { qrText ->
                    showCameraScanner = false
                    handleScanResult(qrText)
                },
                onDismiss = { showCameraScanner = false }
            )
        }

        // Smart Redirection Confirmation Dialog
        if (redirectAccountToImport != null) {
            val target = redirectAccountToImport!!
            AlertDialog(
                onDismissRequest = { redirectAccountToImport = null },
                title = { Text("2FA QR Code Detected") },
                text = {
                    Text("This is a 2FA profile for ${target.issuer} (${target.name}). Do you want to import it into the Authenticator instead?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (target.secret.startsWith("otpauth-migration://")) {
                                    // Parse bulk migration
                                    val dataParam = Uri.parse(target.secret).getQueryParameter("data")
                                    if (dataParam != null) {
                                        val payload = android.util.Base64.decode(dataParam, android.util.Base64.DEFAULT)
                                        val list = GoogleAuthDecoder.decodeMigration(payload)
                                        val dbAccounts = list.map {
                                            TwoFactorAccount(
                                                issuer = it["issuer"] ?: "Imported",
                                                name = it["name"] ?: "Account",
                                                secret = it["secret"] ?: ""
                                            )
                                        }.filter { it.secret.isNotEmpty() }
                                        twoFactorDao.insertAll(dbAccounts)
                                        Toast.makeText(context, "Imported ${dbAccounts.size} accounts into Authenticator!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    twoFactorDao.insert(target)
                                    Toast.makeText(context, "Imported into Authenticator!", Toast.LENGTH_SHORT).show()
                                }
                                redirectAccountToImport = null
                                onRedirectToAuthenticator() // Switch tab to Authenticator
                            }
                        }
                    ) {
                        Text("Yes, Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { redirectAccountToImport = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// Helper function to parse a CSV line, respecting quotes and double-quotes
private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val curField = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                curField.append('\"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (c == ',' && !inQuotes) {
            result.add(curField.toString())
            curField.setLength(0)
        } else {
            curField.append(c)
        }
        i++
    }
    result.add(curField.toString())
    return result
}

// Cleans Android-style app package URIs (e.g., from Chrome exports) to standard web domains
private fun cleanAndroidUri(uriStr: String): String {
    if (!uriStr.startsWith("android://")) {
        return uriStr
    }
    try {
        val atIdx = uriStr.indexOf('@')
        if (atIdx == -1) return uriStr
        var pkg = uriStr.substring(atIdx + 1)
        if (pkg.endsWith("/")) {
            pkg = pkg.substring(0, pkg.length - 1)
        }

        val packageDomainMap = mapOf(
            "com.twitter.android" to "https://www.twitter.com/",
            "com.twitter" to "https://www.twitter.com/",
            "com.facebook.katana" to "https://www.facebook.com/",
            "com.facebook.orca" to "https://www.messenger.com/",
            "com.instagram.android" to "https://www.instagram.com/",
            "com.whatsapp" to "https://www.whatsapp.com/",
            "com.google.android.youtube" to "https://www.youtube.com/",
            "com.google.android.gm" to "https://mail.google.com/",
            "com.netflix.mediaclient" to "https://www.netflix.com/",
            "com.spotify.music" to "https://www.spotify.com/",
            "com.linkedin.android" to "https://www.linkedin.com/",
            "com.pinterest" to "https://www.pinterest.com/",
            "com.reddit.frontpage" to "https://www.reddit.com/",
            "com.snapchat.android" to "https://www.snapchat.com/",
            "com.amazon.mShop.android.shopping" to "https://www.amazon.com/",
            "com.ebay.mobile" to "https://www.ebay.com/",
            "org.mozilla.firefox" to "https://www.mozilla.org/",
            "com.android.chrome" to "https://www.google.com/chrome/"
        )

        if (packageDomainMap.containsKey(pkg)) {
            return packageDomainMap[pkg]!!
        }

        val parts = pkg.split(".")
        if (parts.size >= 2) {
            val tld = if (parts[0] in listOf("com", "org", "net", "co", "io", "in", "edu", "gov", "app")) parts[0] else "com"
            var mainName = parts[1]
            if (mainName == "google" || mainName == "android" || mainName == "amazon") {
                if (parts.size >= 3) {
                    val candidate = parts.last()
                    if (candidate != "android" && candidate != "app" && candidate != "mobile") {
                        mainName = candidate
                    } else if (parts.size >= 4) {
                        mainName = parts[parts.size - 2]
                    }
                }
            }
            mainName = mainName.replace("android", "").replace("mobile", "").trim('_', '-')
            return "https://www.$mainName.$tld/"
        }
    } catch (e: Exception) {
        // Fallback
    }
    return uriStr
}
