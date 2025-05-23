package ua.pp.soulrise.storog

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture // ImageCapture передается, но не используется напрямую в этой версии CameraScreen для Preview
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            this.scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // LaunchedEffect для асинхронной инициализации ProcessCameraProvider и привязки use-кейсов камеры.
    // Эффект перезапустится, если lifecycleOwner или imageCapture изменятся (хотя imageCapture обычно стабилен).
    LaunchedEffect(lifecycleOwner, imageCapture, previewView) {
        try {
            Log.d("CameraScreen", "LaunchedEffect: Attempting to get CameraProvider.")
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            Log.d("CameraScreen", "LaunchedEffect: CameraProvider obtained. Unbinding all.")
            cameraProvider.unbindAll() // Отвязываем все предыдущие use-кейсы перед новой привязкой

            val previewUseCase = Preview.Builder().build().also {
                Log.d("CameraScreen", "LaunchedEffect: Setting SurfaceProvider for Preview.")
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            Log.d("CameraScreen", "LaunchedEffect: Attempting to bind to lifecycle.")
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase, // Use-кейс для превью
                imageCapture    // Переданный ImageCapture (для фото или анализа в сервисе)
            )
            Log.d("CameraScreen", "LaunchedEffect: Camera bound successfully to lifecycle.")
        } catch (e: Exception) {
            Log.e("CameraScreen", "LaunchedEffect: Use case binding failed.", e)
        }
    }

    // DisposableEffect для отвязки камеры при выходе из композиции (когда Composable удаляется с экрана).
    // Ключ Unit означает, что onDispose выполнится только один раз при уничтожении эффекта.
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CameraScreen", "DisposableEffect: Unbinding camera onDispose.")
            // Получаем инстанс провайдера снова, чтобы отвязать.
            // Это распространенный паттерн, если сам cameraProvider не сохранен в remember {}.
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    Log.d("CameraScreen", "DisposableEffect: Camera unbound successfully.")
                } catch (e: Exception) {
                    Log.e("CameraScreen", "DisposableEffect: Error unbinding camera.", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
        )
    }
}

/**
 * Вспомогательная suspend-функция для преобразования ListenableFuture в результат корутины.
 * Позволяет использовать .await() на ListenableFuture в suspend функциях.
 */
suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    if (continuation.isActive) { // Проверяем, активна ли корутина
                        continuation.resume(get())
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            },
            // Лучше использовать executor, который не является основным потоком для get(),
            // но для addListener и resume/resumeWithException основной поток безопасен.
            // ContextCompat.getMainExecutor(context) здесь может быть не доступен напрямую.
            // Можно передать executor или использовать Dispatchers.IO для get() если он блокирующий.
            // Однако, ProcessCameraProvider.getInstance() обычно выполняется быстро.
            // Для простоты оставим так, но в сложных случаях можно использовать кастомный executor.
            Runnable::run // Простой прямой вызов слушателя, или ContextCompat.getMainExecutor(applicationContext)
        )

        continuation.invokeOnCancellation {
            cancel(false) // Отменяем ListenableFuture, если корутина отменена
        }
    }