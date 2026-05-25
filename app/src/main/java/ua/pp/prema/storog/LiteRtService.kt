package ua.pp.prema.storog

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ua.pp.prema.storog.engine.LiteRtManager
import java.io.File

class LiteRtService(
    private val context: Context,
    private val liteRtManager: LiteRtManager
) {

    /**
     * Generates a response from the LiteRT model with an optional image.
     * Unlike the cloud Gemini API, this uses a local model and does not support chat history.
     *
     * @param userPrompt Text query from the user.
     * @param imageBitmap Optional image for visual analysis.
     * @return Flow<String> Stream of text tokens from the model response.
     */
    suspend fun generateChatResponseStreaming(
        userPrompt: String,
        imageBitmap: Bitmap? = null
    ): Flow<String> = flow {
        Log.d(TAG, "Starting LiteRT inference with prompt: $userPrompt, Image: ${imageBitmap != null}")

        // Convert Bitmap to file if provided
        val imageFile: File? = if (imageBitmap != null) {
            try {
                val tempImageFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                val success = saveBitmapToFile(imageBitmap, tempImageFile)

                if (success && tempImageFile.exists()) {
                    Log.d(TAG, "Image saved to: ${tempImageFile.absolutePath}")
                    tempImageFile
                } else {
                    Log.e(TAG, "Failed to save image to file")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image to file", e)
                null
            }
        } else {
            null
        }

        try {
            // Call LiteRT model through LiteRtManager
            liteRtManager.generateResponse(
                prompt = userPrompt,
                imageFile = imageFile,
                audioBytes = null
            ).collect { token ->
                emit(token)
            }
            Log.d(TAG, "LiteRT inference completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LiteRT inference", e)
            emit("Error: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            // Clean up temporary image file
            imageFile?.let { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Cleaned up temp image file")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete temp image file", e)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Saves a Bitmap to a JPEG file.
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing bitmap to file", e)
            false
        }
    }

    companion object {
        private const val TAG = "LiteRtService"
    }
}