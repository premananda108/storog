package ua.pp.soulrise.storog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
    return stream.toByteArray()
}

fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    if (imageProxy.format != android.graphics.ImageFormat.JPEG) {
        Log.e("ImageUtils", "Unexpected image format: ${imageProxy.format}. Expected JPEG.")
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