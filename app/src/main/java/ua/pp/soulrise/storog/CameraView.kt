package ua.pp.soulrise.storog

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraScreen(modifier: Modifier = Modifier, mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView: PreviewView? by remember { mutableStateOf(null) } // Для доступа к PreviewView
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize()) { // Изменено на fillMaxSize для кнопки
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
                        preview,
                        imageCapture // Добавляем ImageCapture
                    )
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case binding failed", exc)
                }
            }
        )

        Button(
            onClick = {
                takePhoto(context, imageCapture, cameraExecutor, mainViewModel)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Отправить фото")
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    mainViewModel: MainViewModel
) {
    val photoFile = File(
        context.externalMediaDirs.firstOrNull(),
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                Toast.makeText(context, "Ошибка съемки: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val msg = "Фото сохранено: ${output.savedUri}"
                Log.d("CameraScreen", msg)
                // Конвертируем Uri в ByteArray и отправляем
                output.savedUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val photoBytes = inputStream.readBytes()
                        mainViewModel.onSendPhotoButtonClicked(photoBytes) { success, geminiResponse ->
                            val resultMsg = if (success) {
                                geminiResponse ?: "Фото успешно отправлено и проанализировано"
                            } else {
                                geminiResponse ?: "Ошибка отправки или анализа фото"
                            }
                            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show() // Используем LENGTH_LONG для более длинных сообщений
                            Log.d("CameraScreen", "Callback: success=$success, response='$geminiResponse', displayedMsg='$resultMsg'")
                            // Опционально: удалить файл после отправки
                            // photoFile.delete()
                        }
                    }
                } ?: run {
                    Toast.makeText(context, "Ошибка: URI фото не найден", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}