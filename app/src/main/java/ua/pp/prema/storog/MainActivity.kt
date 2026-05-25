package ua.pp.prema.storog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.pp.prema.storog.ui.theme.StorogTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import ua.pp.prema.storog.engine.ModelInfo

class MainActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mainViewModel: MainViewModel
    private var hasCameraPermission by mutableStateOf(false)
    private var showModelSelectionDialog by mutableStateOf(false)
    private var availableModels by mutableStateOf<List<ModelInfo>>(emptyList())

    private var initialBitmap: Bitmap? by mutableStateOf(null)
    private var monitoringJob: Job? by mutableStateOf(null)
    private var isMonitoringActive by mutableStateOf(false)
    private var comparisonMessage by mutableStateOf("")
    private val comparisonIntervalMillis = 5000L
    private var differenceThreshold by mutableStateOf(5.0f)
    private var aiPrompt by mutableStateOf("is there a cat in the picture?")
    private var showHelpDialog by mutableStateOf(false)
    private var showPreflightDialog by mutableStateOf(false)
    private var preflightWarnings by mutableStateOf("")

    private fun saveSettings() {
        val sharedPreferences = getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat("differenceThreshold", differenceThreshold)
            putString("aiPrompt", aiPrompt)
            apply()
        }
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
        differenceThreshold = sharedPreferences.getFloat("differenceThreshold", 5.0f)
        aiPrompt = sharedPreferences.getString("aiPrompt", "is there a cat in the picture?") ?: "is there a cat in the picture?"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
        if (isGranted) {
            Log.d("MainActivity", "Camera permission granted.")
        } else {
            Log.d("MainActivity", "Camera permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()

        mainViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)).get(MainViewModel::class.java)
        imageCapture = ImageCapture.Builder().build()
        cameraExecutor = Executors.newSingleThreadExecutor()

        mainViewModel.onSendAlertButtonClicked("Application started") { success ->
            if (success) {
                Log.i("MainActivity", "Startup message sent to Telegram.")
            } else {
                Log.e("MainActivity", "Error sending startup message to Telegram.")
            }
        }

        // Observe ViewModel events for UI feedback (loading, errors, etc.)
        lifecycleScope.launch {
            mainViewModel.events.collect { event ->
                when (event) {
                    is UiEvent.ShowError -> {
                        Log.e("MainActivity", "UI Event: ${event.message}")
                        // Show error toast or snackbar
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    is UiEvent.ShowPreflight -> {
                        Log.w("MainActivity", "Preflight check: errors=${event.result.errors}, warnings=${event.result.warnings}")
                        preflightWarnings = event.result.warnings.joinToString("\n")
                        showPreflightDialog = true
                    }
                    is UiEvent.ShowGpuFallback -> {
                        Log.w("MainActivity", "GPU fallback: ${event.reason}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "GPU unavailable: ${event.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                    is UiEvent.ShowModelSelection -> {
                        Log.i("MainActivity", "No models found, show model selection")
                        val models = mainViewModel.availableModels()
                        if (models.isNotEmpty()) {
                            availableModels = models
                            showModelSelectionDialog = true
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "No available models are configured.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        checkCameraPermission()

        // Replace the content of setContent in onCreate with the following code:

        setContent {
            StorogTheme {
                val currentContext = LocalContext.current
                val uiState by mainViewModel.uiState.collectAsState()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    topBar = {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(onClick = {
                                        val intent = Intent(currentContext, SettingsActivity::class.java)
                                        currentContext.startActivity(intent)
                                    }) {
                                        Text("Settings")
                                    }
                                    Button(onClick = { showHelpDialog = true }) {
                                        Text("Help")
                                    }
                                }
                                
                                // Model status display
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    color = when (uiState.modelStatus) {
                                        is ModelStatus.Ready -> MaterialTheme.colorScheme.tertiaryContainer
                                        is ModelStatus.Error -> MaterialTheme.colorScheme.errorContainer
                                        is ModelStatus.Loading, is ModelStatus.Downloading -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = uiState.statusText,
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (showHelpDialog) {
                        HelpDialog(onDismiss = { showHelpDialog = false })
                    }

                    if (showPreflightDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Warning") },
                            text = { Text(preflightWarnings) },
                            confirmButton = {
                                Button(onClick = {
                                    showPreflightDialog = false
                                    mainViewModel.onPreflightAccepted()
                                }) {
                                    Text("Continue")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { showPreflightDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showModelSelectionDialog) {
                        AlertDialog(
                            onDismissRequest = { showModelSelectionDialog = false },
                            title = { Text("Select model to download") },
                            text = {
                                Column {
                                    Text("Choose a model to download and initialize.")
                                    availableModels.forEach { model ->
                                        Button(
                                            onClick = {
                                                mainViewModel.downloadModel(model)
                                                showModelSelectionDialog = false
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(model.name)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showModelSelectionDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (hasCameraPermission) {
                        // Use Column for vertical placement of elements
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            // Camera takes up part of the screen (not all)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.4f) // 40% of the screen for the camera
                            ) {
                                CameraScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    imageCapture = imageCapture
                                )
                            }

                            // UI elements take up the remaining part of the screen
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.6f) // 60% of the screen for UI
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Start/stop button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            if (isMonitoringActive) {
                                                stopImageMonitoring()
                                            } else {
                                                startImageMonitoring()
                                            }
                                        },
                                        enabled = uiState.isMonitoringEnabled
                                    ) {
                                        Text(if (isMonitoringActive) "Stop" else "Start")
                                    }
                                }

                                // Setting the trigger threshold
                                Text(
                                    text = "Trigger threshold",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (differenceThreshold > 0f) {
                                                differenceThreshold = (differenceThreshold - 1f).coerceIn(0f, 100f)
                                            }
                                        }
                                    ) {
                                        Text("-")
                                    }

                                    Text(
                                        text = "${differenceThreshold.toInt()}%",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    Button(
                                        onClick = {
                                            if (differenceThreshold < 100f) {
                                                differenceThreshold = (differenceThreshold + 1f).coerceIn(0f, 100f)
                                            }
                                        }
                                    ) {
                                        Text("+")
                                    }
                                }

                                // AI prompt field
                                TextField(
                                    value = aiPrompt,
                                    onValueChange = { aiPrompt = it },
                                    label = { Text("Prompt for AI") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3 // Limit the height of the field
                                )

                                // Comparison message - use scrollable text
                                val scrollState = rememberScrollState()
                                Text(
                                    text = comparisonMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f) // Takes up the remaining space
                                        .verticalScroll(scrollState)
                                        .padding(8.dp),
                                    textAlign = TextAlign.Start,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Camera permission not granted.")
                        }
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity", "Camera permission already granted.")
                hasCameraPermission = true
            }
            else -> {
                Log.d("MainActivity", "Requesting camera permission.")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
        cameraExecutor.shutdown()
        stopImageMonitoring()
    }

    private fun startImageMonitoring() {
        if (isMonitoringActive) return

        isMonitoringActive = true
        comparisonMessage = "Starting monitoring..."
        Log.d("MainActivity", "Attempting to start image monitoring.")

        captureCurrentFrameAsBitmap { capturedBitmap ->
            if (capturedBitmap != null) {
                initialBitmap?.recycle()
                initialBitmap = capturedBitmap
                comparisonMessage = "Initial image saved. Monitoring active."
                Log.d("MainActivity", "Initial bitmap captured. Monitoring active.")

                monitoringJob?.cancel()
                monitoringJob = lifecycleScope.launch {
                    while (isActive && isMonitoringActive) {
                        delay(comparisonIntervalMillis)
                        if (!isMonitoringActive) break

                        if (mainViewModel.isAnalysisInProgress.value) {
                            Log.d("MainActivity", "Analysis in progress, skipping this iteration.")
                            continue
                        }

                        Log.d("MainActivity", "Capturing frame for comparison...")
                        captureCurrentFrameAsBitmap { currentBitmap ->
                            if (currentBitmap != null && initialBitmap != null && isMonitoringActive) {
                                lifecycleScope.launch {
                                    // Calling your ImageComparator.calculateDifferencePercentage, defined in another file
                                    val difference = ImageComparator.calculateDifferencePercentage(initialBitmap!!, currentBitmap)
                                    val message = "Difference: ${String.format("%.2f", difference)}%"
                                    runOnUiThread {
                                        comparisonMessage = message
                                    }
                                    Log.d("MainActivity", message)

                                    if (differenceThreshold < 0.01f || difference > differenceThreshold.toDouble()) {
                                        val originalAlertMessage = "Significant change detected: ${String.format("%.2f", difference)}%"
                                        val alertMessageWithPhotoSendAttempt = "$originalAlertMessage. Attempting to send photo."
                                        runOnUiThread {
                                            comparisonMessage = alertMessageWithPhotoSendAttempt
                                        }
                                        Log.w("MainActivity", alertMessageWithPhotoSendAttempt)

                                        val photoBytes = bitmapToByteArray(currentBitmap)

                                        mainViewModel.processAndSendImageWithPrompt(photoBytes, aiPrompt) { analysisSuccess, responseMsg ->
                                            runOnUiThread {
                                                when {
                                                    responseMsg?.startsWith("STOP_MONITORING:") == true -> {
                                                        val actualAiResponse = responseMsg.substringAfter("STOP_MONITORING:")
                                                        val stopMessage = "Message limit reached. AI response: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}"
                                                        Toast.makeText(applicationContext, stopMessage, Toast.LENGTH_LONG).show()
                                                        Log.i("MainActivity", "Monitoring stopped due to message limit. AI: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}")
                                                        comparisonMessage = stopMessage
                                                        stopImageMonitoring()
                                                    }
                                                    responseMsg?.startsWith("SKIPPED_NO:") == true -> {
                                                        val actualAiResponse = responseMsg.substringAfter("SKIPPED_NO:")
                                                        val skippedMessage = "Sending skipped. AI response: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}"
                                                        Toast.makeText(applicationContext, skippedMessage, Toast.LENGTH_LONG).show()
                                                        Log.i("MainActivity", "Telegram send skipped due to AI response. AI: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}")
                                                        comparisonMessage = skippedMessage
                                                    }
                                                    analysisSuccess -> {
                                                        val successMessage = "Photo sent! AI response: ${responseMsg ?: "No response"}"
                                                        Toast.makeText(applicationContext, successMessage, Toast.LENGTH_LONG).show()
                                                        Log.d("MainActivity", "Photo sent successfully. AI response: ${responseMsg ?: "No response"}")
                                                        comparisonMessage = successMessage
                                                    }
                                                    else -> {
                                                        val errorText = "Error sending photo or AI analysis: ${responseMsg ?: "Unknown error"}"
                                                        Toast.makeText(applicationContext, errorText, Toast.LENGTH_LONG).show()
                                                        Log.e("MainActivity", errorText)
                                                        comparisonMessage = errorText
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    currentBitmap.recycle()
                                }
                            } else if (isMonitoringActive) {
                                Log.e("MainActivity", "Error getting current image for comparison or initialBitmap is null.")
                                runOnUiThread {
                                    comparisonMessage = "Image comparison error."
                                }
                                currentBitmap?.recycle()
                            } else {
                                currentBitmap?.recycle()
                            }
                        }
                    }
                }
            } else {
                isMonitoringActive = false
                comparisonMessage = "Error: failed to get initial image."
                Log.e("MainActivity", "Failed to capture initial bitmap.")
            }
        }
    }

    private fun stopImageMonitoring() {
        if (!isMonitoringActive && monitoringJob == null && initialBitmap == null) return

        isMonitoringActive = false
        monitoringJob?.cancel()
        monitoringJob = null
        initialBitmap?.recycle()
        initialBitmap = null
        comparisonMessage = "Monitoring stopped."
        Log.d("MainActivity", "Image monitoring stopped.")
    }



    private fun captureCurrentFrameAsBitmap(callback: (Bitmap?) -> Unit) {
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                Log.d("MainActivity", "Frame captured successfully for comparison.")
                val bitmap = imageProxyToBitmap(imageProxy)
                callback(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("MainActivity", "Frame capture failed for comparison: ${exception.message}", exception)
                callback(null)
            }
        })
    }
} // Closes MainActivity