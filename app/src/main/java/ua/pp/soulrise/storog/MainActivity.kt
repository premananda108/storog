package ua.pp.soulrise.storog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import ua.pp.soulrise.storog.ui.theme.StorogTheme // Ваша тема

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel

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

        mainViewModel = ViewModelProvider(this, AndroidViewModelFactory.getInstance(application)).get(MainViewModel::class.java)

        // Отправляем сообщение при старте приложения
        mainViewModel.onSendAlertButtonClicked("Приложение запущено") { success ->
            if (success) {
                // Можно добавить Toast или лог, если нужно
                // Toast.makeText(applicationContext, "Сообщение о запуске отправлено!", Toast.LENGTH_SHORT).show()
                android.util.Log.i("MainActivity", "Сообщение о запуске отправлено в Telegram.")
            } else {
                // Toast.makeText(applicationContext, "Ошибка отправки сообщения о запуске.", Toast.LENGTH_SHORT).show()
                android.util.Log.e("MainActivity", "Ошибка отправки сообщения о запуске в Telegram.")
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false) // Для edge-to-edge
        // Этот блок был перемещен и изменен выше, удаляем дубликат если он был создан по ошибке предыдущего шага

        // Проверяем разрешение при старте
        checkCameraPermission()

        setContent {
            StorogTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasCameraPermission) {
                        CameraScreen(modifier = Modifier.padding(innerPadding), mainViewModel = mainViewModel)
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