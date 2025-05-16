package ua.pp.soulrise.storog // Ваше имя пакета

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import ua.pp.soulrise.storog.ui.theme.StorogTheme // Ваша тема
import ua.pp.soulrise.storog.CameraScreen // Импорт CameraScreen

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
        if (isGranted) {
            Log.d("MainActivity", "Camera permission granted.")
            // UI обновится автоматически из-за изменения hasCameraPermission
        } else {
            Log.d("MainActivity", "Camera permission denied.")
            // Можно показать сообщение пользователю, что разрешение необходимо
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // Для edge-to-edge

        // Проверяем разрешение при старте
        checkCameraPermission()

        setContent {
            StorogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasCameraPermission) {
                        CameraScreen(modifier = Modifier.padding(innerPadding))
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Разрешение на использование камеры не предоставлено.")
                            // Можно добавить кнопку для повторного запроса, если нужно
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
            // Опционально: объяснить, зачем нужно разрешение
            // shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> { ... }
            else -> {
                Log.d("MainActivity", "Requesting camera permission.")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}