package ua.pp.soulrise.storog

import android.Manifest
import android.content.Context
import android.content.Intent // Убедимся, что импорт есть
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
// import androidx.compose.foundation.lazy.LazyRow // Если не используется, можно удалить
// import androidx.compose.foundation.lazy.items // Если не используется, можно удалить
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// import androidx.compose.material3.Slider // Если не используется, можно удалить
import androidx.compose.material3.TextField
// import androidx.compose.material3.AlertDialog // Если HelpDialog - это AlertDialog, он может быть нужен
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // Убедимся, что импорт есть
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
import ua.pp.soulrise.storog.bitmapToByteArray // Добавленный импорт
import ua.pp.soulrise.storog.imageProxyToBitmap // Добавленный импорт
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
        checkCameraPermission()

        setContent {
            StorogTheme {
                val currentContext = LocalContext.current // Получаем контекст для использования в topBar

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // Ваша верхняя панель теперь здесь
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp), // Отступы для topBar
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
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding) // Применяем innerPadding от Scaffold
                            .fillMaxSize()
                    ) {
                        // Верхняя панель УДАЛЕНА отсюда

                        if (showHelpDialog) {
                            // Вызов вашей Composable функции HelpDialog, определенной в другом файле
                            HelpDialog(onDismiss = { showHelpDialog = false })
                        }

                        if (hasCameraPermission) {
                            Box(modifier = Modifier.weight(1f)) { // Этот Box для CameraScreen
                                // Вызов вашей Composable функции CameraScreen, определенной в другом файле
                                CameraScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    mainViewModel = mainViewModel,
                                    imageCapture = imageCapture,
                                    cameraExecutor = cameraExecutor
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
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
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (differenceThreshold > 0f) {
                                            differenceThreshold = (differenceThreshold - 5f).coerceIn(0f, 100f)
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
                                            differenceThreshold = (differenceThreshold + 5f).coerceIn(0f, 100f)
                                        }
                                    }
                                ) {
                                    Text("+")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = aiPrompt,
                                    onValueChange = { aiPrompt = it },
                                    label = { Text("Промпт для ИИ") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false
                                )
                            }

                            Text(
                                text = comparisonMessage,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(), // innerPadding уже применен к родительскому Column
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Разрешение на использование камеры не предоставлено.")
                            }
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

                                    if (difference > differenceThreshold.toDouble()) {
                                        val originalAlertMessage = "Обнаружено значительное изменение: ${String.format("%.2f", difference)}%"
                                        val alertMessageWithPhotoSendAttempt = "$originalAlertMessage. Попытка отправки фото."
                                        runOnUiThread {
                                            comparisonMessage = alertMessageWithPhotoSendAttempt
                                        }
                                        Log.w("MainActivity", alertMessageWithPhotoSendAttempt)

                                        val photoBytes = bitmapToByteArray(currentBitmap)

                                        mainViewModel.processAndSendImageWithPrompt(photoBytes, aiPrompt) { analysisSuccess, responseMsg ->
                                            runOnUiThread {
                                                if (responseMsg?.startsWith("SKIPPED_NO:") == true) {
                                                    val actualAiResponse = responseMsg.substringAfter("SKIPPED_NO:")
                                                    val skippedMessage = "Отправка пропущена. Ответ ИИ: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "Нет ответа"}"
                                                    Toast.makeText(applicationContext, skippedMessage, Toast.LENGTH_LONG).show()
                                                    Log.i("MainActivity", "Telegram send skipped due to AI response. AI: ${actualAiResponse.takeIf { it.isNotBlank() } ?: "No response"}")
                                                    comparisonMessage = skippedMessage
                                                } else if (analysisSuccess) {
                                                    val successMessage = "Фото отправлено! Ответ ИИ: ${responseMsg ?: "Нет ответа"}"
                                                    Toast.makeText(applicationContext, successMessage, Toast.LENGTH_LONG).show()
                                                    Log.d("MainActivity", "Photo sent successfully. AI response: ${responseMsg ?: "No response"}")
                                                    comparisonMessage = successMessage
                                                } else {
                                                    val errorText = "Ошибка отправки фото или анализа ИИ: ${responseMsg ?: "Неизвестная ошибка"}"
                                                    Toast.makeText(applicationContext, errorText, Toast.LENGTH_LONG).show()
                                                    Log.e("MainActivity", errorText)
                                                    comparisonMessage = errorText
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