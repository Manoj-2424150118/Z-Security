package com.zsecure.com.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zsecure.com.data.DatabaseProvider
import com.zsecure.com.data.TwoFactorAccount
import com.zsecure.com.utils.GoogleAuthDecoder
import com.zsecure.com.utils.TotpUtils
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder

@Composable
fun AuthenticatorScreen(
    activity: FragmentActivity,
    onRedirectToAuthenticator: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = DatabaseProvider.getInstance()
    val twoFactorDao = db.twoFactorDao()
    
    val accounts by twoFactorDao.getAllAccounts().collectAsState(initial = emptyList())
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showMigrationDialog by remember { mutableStateOf(false) }

    // Run active loop for 2FA timer
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(200)
        }
    }

    val remainingSecs = (30 - ((currentTimeMs / 1000) % 30)).toInt()
    val timerProgress = remainingSecs / 30f

    // Copy to clipboard helper
    fun copyCode(account: TwoFactorAccount) {
        val codeSpaced = TotpUtils.generateTotp(account.secret, currentTimeMs)
        val rawCode = codeSpaced.replace(" ", "")
        val clip = ClipData.newPlainText("2FA Code", rawCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Code copied: $codeSpaced", Toast.LENGTH_SHORT).show()
        
        coroutineScope.launch {
            delay(45000)
            try {
                val primaryClip = clipboard.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    val currentText = primaryClip.getItemAt(0).text?.toString()
                    if (currentText == rawCode) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        Toast.makeText(context, "Clipboard cleared for security", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Silent catch
            }
        }
    }

    // Google Authenticator Migration URI Parser
    fun handleMigrationUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            val dataParam = uri.getQueryParameter("data")
            if (dataParam == null) {
                Toast.makeText(context, "Invalid Migration Link: data parameter missing", Toast.LENGTH_LONG).show()
                return
            }
            // Base64 decode
            val payload = android.util.Base64.decode(dataParam, android.util.Base64.DEFAULT)
            val parsed = GoogleAuthDecoder.decodeMigration(payload)
            if (parsed.isEmpty()) {
                Toast.makeText(context, "No 2FA accounts found in migration data", Toast.LENGTH_SHORT).show()
                return
            }

            coroutineScope.launch {
                val dbAccounts = parsed.map { map ->
                    TwoFactorAccount(
                        issuer = map["issuer"] ?: "Imported",
                        name = map["name"] ?: "Account",
                        secret = map["secret"] ?: ""
                    )
                }.filter { it.secret.isNotEmpty() }
                
                twoFactorDao.insertAll(dbAccounts)
                Toast.makeText(context, "Successfully imported ${dbAccounts.size} accounts!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to parse migration data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Generic QR Content Handler (standard otpauth or migration otpauth-migration)
    fun handleQrContent(content: String) {
        if (content.startsWith("otpauth-migration://")) {
            handleMigrationUrl(content)
        } else if (content.startsWith("otpauth://totp/")) {
            // standard otpauth://totp/Issuer:Account?secret=XXX&issuer=Issuer
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

                if (secret.isNullOrEmpty()) {
                    Toast.makeText(context, "Secret key missing in QR code", Toast.LENGTH_SHORT).show()
                    return
                }

                coroutineScope.launch {
                    twoFactorDao.insert(
                        TwoFactorAccount(
                            issuer = issuer.ifEmpty { "Service" },
                            name = name.ifEmpty { "User" },
                            secret = secret
                        )
                    )
                    Toast.makeText(context, "Imported: ${issuer.ifEmpty { "Service" }} ($name)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to parse standard QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (content.startsWith("passvault://")) {
            Toast.makeText(context, "This is a password credential. Please scan it inside the Password Manager tab instead.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Invalid or unrecognized QR Code format", Toast.LENGTH_SHORT).show()
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
                            handleQrContent(content)
                        } else {
                            Toast.makeText(context, "No QR Code found in selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header timer circular card
            // Header timer circular card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time-Based OTP Codes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Codes regenerate automatically every 30 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Countdown Ring Animation
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Canvas(modifier = Modifier.size(54.dp)) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                style = Stroke(width = 4.dp.toPx())
                            )
                            drawArc(
                                color = if (remainingSecs <= 5) Color(0xFFE57373) else Color(0xFF00E5FF),
                                startAngle = -90f,
                                sweepAngle = timerProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                        Text(
                            text = remainingSecs.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (remainingSecs <= 5) Color(0xFFE57373) else Color.White
                        )
                    }
                }
            }

            // List of Accounts
            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No 2FA Accounts\nTap '+' to scan a QR code or add a secret key.",
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
                    items(accounts) { account ->
                        val code = TotpUtils.generateTotp(account.secret, currentTimeMs)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyCode(account) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.issuer,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                
                                Row {
                                    IconButton(onClick = { copyCode(account) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color.White.copy(alpha = 0.7f))
                                    }
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            twoFactorDao.delete(account)
                                            Toast.makeText(context, "Deleted: ${account.issuer}", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = Color(0xFFD32F2F))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddMenu = true },
            containerColor = Color(0xFF0D6EFD),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Account")
        }

        // Bottom sheet options menu
        if (showAddMenu) {
            Dialog(onDismissRequest = { showAddMenu = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Add Authenticator Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ListItem(
                            headlineContent = { Text("Scan QR Code") },
                            leadingContent = { Icon(Icons.Default.CropFree, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAddMenu = false
                                showCameraScanner = true
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Choose from Gallery") },
                            leadingContent = { Icon(Icons.Default.Photo, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAddMenu = false
                                DatabaseProvider.isLaunchingIntent = true
                                galleryLauncher.launch("image/*")
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Enter Key Manually") },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAddMenu = false
                                showManualDialog = true
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Import Google Authenticator Migration") },
                            leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAddMenu = false
                                showMigrationDialog = true
                            }
                        )

                        TextButton(
                            onClick = { showAddMenu = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Dialog for Manual Entry
        if (showManualDialog) {
            var manualIssuer by remember { mutableStateOf("") }
            var manualName by remember { mutableStateOf("") }
            var manualKey by remember { mutableStateOf("") }
            var keyError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                title = { Text("Enter account details") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualIssuer,
                            onValueChange = { manualIssuer = it },
                            label = { Text("Issuer (e.g. Google)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text("Account Name / Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = manualKey,
                            onValueChange = { manualKey = it; keyError = false },
                            label = { Text("Secret Key") },
                            singleLine = true,
                            isError = keyError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = "User Tip: The Setup Key is the 16-character backup text code (e.g. JBSWY3DPEHPK3PXP) provided by websites during 2FA setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanKey = manualKey.uppercase().replace(" ", "")
                            if (cleanKey.isEmpty()) {
                                keyError = true
                                return@Button
                            }
                            try {
                                // Try to decode base32 to verify key correctness
                                TotpUtils.decodeBase32(cleanKey)
                                coroutineScope.launch {
                                    twoFactorDao.insert(
                                        TwoFactorAccount(
                                            issuer = manualIssuer.ifEmpty { "Service" },
                                            name = manualName.ifEmpty { "User" },
                                            secret = cleanKey
                                        )
                                    )
                                    showManualDialog = false
                                }
                            } catch (e: Exception) {
                                keyError = true
                                Toast.makeText(context, "Invalid Base32 secret key format", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for migration sync URL input (as a fallback or paste option)
        if (showMigrationDialog) {
            var migrationUrl by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showMigrationDialog = false },
                title = { Text("Import Google Authenticator Link") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paste your otpauth-migration://offline?data=... URL exported from Google Authenticator to bulk import accounts.")
                        OutlinedTextField(
                            value = migrationUrl,
                            onValueChange = { migrationUrl = it },
                            label = { Text("Migration URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (migrationUrl.startsWith("otpauth-migration://")) {
                            handleMigrationUrl(migrationUrl)
                            showMigrationDialog = false
                        } else {
                            Toast.makeText(context, "Invalid format. URL must start with otpauth-migration://", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMigrationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for CameraX QR Scanner overlay
        if (showCameraScanner) {
            CameraScannerDialog(
                onQrCodeScanned = { qrText ->
                    showCameraScanner = false
                    handleQrContent(qrText)
                },
                onDismiss = { showCameraScanner = false }
            )
        }
    }
}

// Camera Scanner Dialog using CameraX and ML Kit
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScannerDialog(
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    // Control scanning debounce
    var isScanned by remember { mutableStateOf(false) }

    fun rebindCamera(provider: ProcessCameraProvider, view: PreviewView) {
        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !isScanned) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val raw = barcode.rawValue
                                if (!raw.isNullOrEmpty()) {
                                    isScanned = true
                                    onQrCodeScanned(raw)
                                    break
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Camera bind failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (hasCameraPermission) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                previewView = pv
                                cameraProviderFuture.addListener({
                                    val provider = cameraProviderFuture.get()
                                    rebindCamera(provider, pv)
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay UI controls
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(
                                text = "Scan 2FA QR Code",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            IconButton(onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                                previewView?.let { pv ->
                                    if (cameraProviderFuture.isDone) {
                                        rebindCamera(cameraProviderFuture.get(), pv)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Camera", tint = Color.White)
                            }
                        }

                        // Circular target scanner area
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .align(Alignment.CenterHorizontally)
                        )

                        Text(
                            text = "Align the QR code inside the box to scan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}
