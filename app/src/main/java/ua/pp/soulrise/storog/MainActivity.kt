package ua.pp.soulrise.storog

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.nio.ByteBuffer
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
    private val differenceThreshold = 15.0

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (hasCameraPermission) {
                            CameraScreen(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                mainViewModel = mainViewModel,
                                imageCapture = imageCapture,
                                cameraExecutor = cameraExecutor
                            )
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

                                Button(
                                    onClick = {
                                        ua.pp.soulrise.storog.takePhoto(
                                            applicationContext,
                                            imageCapture,
                                            onImageCaptured = { photoBytes ->
                                                if (photoBytes != null) {
                                                    mainViewModel.onSendPhotoButtonClicked(photoBytes) { success, message ->
                                                        runOnUiThread {
                                                            if (success) {
                                                                Toast.makeText(applicationContext, "Фото отправлено!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(applicationContext, "Ошибка отправки фото: ${message ?: "Неизвестная ошибка"}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    runOnUiThread {
                                                        Toast.makeText(applicationContext, "Ошибка при создании фото", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    Text("Отправить фото")
                                }
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
    } // Closes onCreate

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
                                    val difference = ImageComparator.calculateDifferencePercentage(initialBitmap!!, currentBitmap)
                                    val message = "Разница: ${String.format("%.2f", difference)}%"
                                    runOnUiThread {
                                        comparisonMessage = message
                                    }
                                    Log.d("MainActivity", message)

                                    if (difference > differenceThreshold) {
                                        val alertMessage = "Обнаружено значительное изменение: ${String.format("%.2f", difference)}%"
                                        runOnUiThread {
                                            comparisonMessage = alertMessage
                                        }
                                        Log.w("MainActivity", alertMessage)
                                        mainViewModel.onSendAlertButtonClicked(alertMessage) { success ->
                                            if (success) Log.d("MainActivity", "Alert sent for significant change.")
                                            else Log.e("MainActivity", "Failed to send alert for significant change.")
                                        }
                                    }
                                    currentBitmap.recycle() // Moved inside the launch block
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

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != android.graphics.ImageFormat.JPEG) {
            Log.e("MainActivity", "Unexpected image format: ${imageProxy.format}. Expected JPEG.")
            imageProxy.close()
            return null
        }
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        imageProxy.close()
        return bitmap
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