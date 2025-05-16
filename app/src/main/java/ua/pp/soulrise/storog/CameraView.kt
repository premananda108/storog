package ua.pp.soulrise.storog

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView: PreviewView? by remember { mutableStateOf(null) } // Для доступа к PreviewView

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight(0.33f)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER // или другой тип масштабирования
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    previewView = this // Сохраняем ссылку
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> // 'view' здесь это PreviewView
                val cameraProvider = cameraProviderFuture.get() // Блокирующий вызов, но getInstance обычно быстрый
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll() // Отвязываем предыдущие юзкейсы
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case binding failed", exc)
                }
            }
        )
        // Здесь можно добавить другие элементы UI поверх превью камеры, если нужно
    }
}