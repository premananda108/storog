package ua.pp.soulrise.storog

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.pp.soulrise.storog.ui.theme.StorogTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

class MainActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mainViewModel: MainViewModel
    private var hasCameraPermission by mutableStateOf(false)

    private var initialBitmap: Bitmap? by mutableStateOf(null)
    private var monitoringJob: Job? by mutableStateOf(null)
    private var isMonitoringActive by mutableStateOf(false)
    private var comparisonMessage by mutableStateOf("")
    private val comparisonIntervalMillis = 5000L
    private var differenceThreshold by mutableStateOf(5.0f)
    private var aiPrompt by mutableStateOf("есть ли на изображении кошка?")
    private var showHelpDialog by mutableStateOf(false)

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
        aiPrompt = sharedPreferences.getString("aiPrompt", "есть ли на изображении кошка?") ?: "есть ли на изображении кошка?"
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

        mainViewModel.onSendAlertButtonClicked("Приложение запущено") { success ->
            if (success) {
                Log.i("MainActivity", "Сообщение о запуске отправлено в Telegram.")
            } else {
                Log.e("MainActivity", "Ошибка отправки сообщения о запуске в Telegram.")
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        checkCameraPermission()

        // Замените содержимое setContent в onCreate на следующий код:

        setContent {
            StorogTheme {
                val currentContext = LocalContext.current

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    topBar = {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(onClick = {
                                    val intent = Intent(currentContext, SettingsActivity::class.java)
                                    currentContext.startActivity(intent)
                                }) {
                                    Text("Настройки")
                                }
                                Button(onClick = { showHelpDialog = true }) {
                                    Text("Справка")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (showHelpDialog) {
                        HelpDialog(onDismiss = { showHelpDialog = false })
                    }

                    if (hasCameraPermission) {
                        // Используем Column для вертикального размещения элементов
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            // Камера занимает часть экрана (не весь)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.4f) // 40% экрана для камеры
                            ) {
                                CameraScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    imageCapture = imageCapture
                                )
                            }

                            // UI элементы занимают оставшуюся часть экрана
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.6f) // 60% экрана для UI
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Кнопка старт/стоп
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
                                        }
                                    ) {
                                        Text(if (isMonitoringActive) "Стоп" else "Старт")
                                    }
                                }

                                // Настройка порога срабатывания
                                Text(
                                    text = "Порог срабатывания",
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

                                // Поле для промпта ИИ
                                TextField(
                                    value = aiPrompt,
                                    onValueChange = { aiPrompt = it },
                                    label = { Text("Промпт для ИИ") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3 // Ограничиваем высоту поля
                                )

                                // Сообщение о сравнении - используем скроллируемый текст
                                val scrollState = rememberScrollState()
                                Text(
                                    text = comparisonMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f) // Занимает оставшееся место
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
                            Text("Разрешение на использование камеры не предоставлено.")
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
        comparisonMessage = "Начинаем мониторинг..."
        Log.d("MainActivity", "Attempting to start image monitoring.")

        captureCurrentFrameAsBitmap { capturedBitmap ->
            if (capturedBitmap != null) {
                initialBitmap?.recycle()
                initialBitmap = capturedBitmap
                comparisonMessage = "Начальное изображение сохранено. Мониторинг активен."
                Log.d("MainActivity", "Initial bitmap captured. Monitoring active.")

                monitoringJob?.cancel()
                monitoringJob = lifecycleScope.launch {
                    while (isActive && isMonitoringActive) {
                        delay(comparisonIntervalMillis)
                        if (!isMonitoringActive) break

                        Log.d("MainActivity", "Capturing frame for comparison...")
                        captureCurrentFrameAsBitmap { currentBitmap ->
                            if (currentBitmap != null && initialBitmap != null && isMonitoringActive) {
                                lifecycleScope.launch {
                                    // Вызов вашего ImageComparator.calculateDifferencePercentage, определенного в другом файле
                                    val difference = ImageComparator.calculateDifferencePercentage(initialBitmap!!, currentBitmap)
                                    val message = "Разница: ${String.format("%.2f", difference)}%"
                                    runOnUiThread {
                                        comparisonMessage = message
                                    }
                                    Log.d("MainActivity", message)

                                    if (differenceThreshold < 0.01f || difference > differenceThreshold.toDouble()) {
                                        val originalAlertMessage = "Обнаружено значительное изменение: ${String.format("%.2f", difference)}%"
                                        val alertMessageWithPhotoSendAttempt = "$originalAlertMessage. Попытка отправки фото."
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
                                                        val stopMessage = "Достигнут лимит сообщений. Ответ ИИ: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "Нет ответа"}"
                                                        Toast.makeText(applicationContext, stopMessage, Toast.LENGTH_LONG).show()
                                                        Log.i("MainActivity", "Monitoring stopped due to message limit. AI: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}")
                                                        comparisonMessage = stopMessage
                                                        stopImageMonitoring()
                                                    }
                                                    responseMsg?.startsWith("SKIPPED_NO:") == true -> {
                                                        val actualAiResponse = responseMsg.substringAfter("SKIPPED_NO:")
                                                        val skippedMessage = "Отправка пропущена. Ответ ИИ: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "Нет ответа"}"
                                                        Toast.makeText(applicationContext, skippedMessage, Toast.LENGTH_LONG).show()
                                                        Log.i("MainActivity", "Telegram send skipped due to AI response. AI: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}")
                                                        comparisonMessage = skippedMessage
                                                    }
                                                    analysisSuccess -> {
                                                        val successMessage = "Фото отправлено! Ответ ИИ: ${responseMsg ?: "Нет ответа"}"
                                                        Toast.makeText(applicationContext, successMessage, Toast.LENGTH_LONG).show()
                                                        Log.d("MainActivity", "Photo sent successfully. AI response: ${responseMsg ?: "No response"}")
                                                        comparisonMessage = successMessage
                                                    }
                                                    else -> {
                                                        val errorText = "Ошибка отправки фото или анализа ИИ: ${responseMsg ?: "Неизвестная ошибка"}"
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
                                Log.e("MainActivity", "Ошибка получения текущего изображения для сравнения или initialBitmap is null.")
                                runOnUiThread {
                                    comparisonMessage = "Ошибка сравнения изображений."
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
                comparisonMessage = "Ошибка: не удалось получить начальное изображение."
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
        comparisonMessage = "Мониторинг остановлен."
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