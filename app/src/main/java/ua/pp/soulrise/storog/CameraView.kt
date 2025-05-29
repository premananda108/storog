package ua.pp.soulrise.storog

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture // ImageCapture is passed but not used directly in this version of CameraScreen for Preview
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

    // LaunchedEffect for asynchronous initialization of ProcessCameraProvider and binding of camera use cases.
    // The effect will restart if lifecycleOwner or imageCapture changes (although imageCapture is usually stable).
    LaunchedEffect(lifecycleOwner, imageCapture, previewView) {
        try {
            Log.d("CameraScreen", "LaunchedEffect: Attempting to get CameraProvider.")
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            Log.d("CameraScreen", "LaunchedEffect: CameraProvider obtained. Unbinding all.")
            cameraProvider.unbindAll() // Unbind all previous use cases before new binding

            val previewUseCase = Preview.Builder().build().also {
                Log.d("CameraScreen", "LaunchedEffect: Setting SurfaceProvider for Preview.")
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            Log.d("CameraScreen", "LaunchedEffect: Attempting to bind to lifecycle.")
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase, // Use case for preview
                imageCapture    // Passed ImageCapture (for photo or analysis in the service)
            )
            Log.d("CameraScreen", "LaunchedEffect: Camera bound successfully to lifecycle.")
        } catch (e: Exception) {
            Log.e("CameraScreen", "LaunchedEffect: Use case binding failed.", e)
        }
    }

    // DisposableEffect to unbind the camera when exiting the composition (when the Composable is removed from the screen).
    // The Unit key means that onDispose will only be executed once when the effect is destroyed.
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CameraScreen", "DisposableEffect: Unbinding camera onDispose.")
            // Get the provider instance again to unbind.
            // This is a common pattern if cameraProvider itself is not saved in remember {}.
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
 * Helper suspend function to convert ListenableFuture to a coroutine result.
 * Allows using .await() on ListenableFuture in suspend functions.
 */
suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    if (continuation.isActive) { // Check if the coroutine is active
                        continuation.resume(get())
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            },
            // It is better to use an executor that is not the main thread for get(),
            // but for addListener and resume/resumeWithException the main thread is safe.
            // ContextCompat.getMainExecutor(context) may not be directly available here.
            // You can pass an executor or use Dispatchers.IO for get() if it is blocking.
            // However, ProcessCameraProvider.getInstance() usually executes quickly.
            // For simplicity, we'll leave it like this, but in complex cases, a custom executor can be used.
            Runnable::run // Simple direct call to the listener, or ContextCompat.getMainExecutor(applicationContext)
        )

        continuation.invokeOnCancellation {
            cancel(false) // Cancel ListenableFuture if the coroutine is cancelled
        }
    }