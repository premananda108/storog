package ua.pp.soulrise.storog // Your package name

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

object ImageComparator {

    private const val TAG = "ImageComparator"

    /**
     * Compares two images and returns the percentage difference between them.
     *
     * @param bitmap1 First image.
     * @param bitmap2 Second image.
     * @param resizeWidth Width to which images will be reduced before comparison (for speed and stability).
     *                    If null, original size is used (not recommended for large images).
     * @param convertToGrayscale Whether to convert images to grayscale before comparison.
     * @return Percentage difference (from 0.0 to 100.0).
     *         Returns -1.0 on error (e.g., different sizes after resize, if it was not applied).
     */
    suspend fun calculateDifferencePercentage(
        bitmap1: Bitmap,
        bitmap2: Bitmap,
        resizeWidth: Int? = 100, // Reduce for speed, e.g., to a width of 100px
        convertToGrayscale: Boolean = true
    ): Double = withContext(Dispatchers.Default) { // Execute on a computation thread
        try {
            val processedBitmap1: Bitmap
            val processedBitmap2: Bitmap

            if (resizeWidth != null) {
                val aspectRatio1 = bitmap1.height.toDouble() / bitmap1.width.toDouble()
                val resizeHeight1 = (resizeWidth * aspectRatio1).toInt()
                processedBitmap1 = bitmap1.scale(resizeWidth, resizeHeight1)

                val aspectRatio2 = bitmap2.height.toDouble() / bitmap2.width.toDouble()
                val resizeHeight2 = (resizeWidth * aspectRatio2).toInt()
                processedBitmap2 = bitmap2.scale(resizeWidth, resizeHeight2)
            } else {
                processedBitmap1 = bitmap1
                processedBitmap2 = bitmap2
            }

            if (processedBitmap1.width != processedBitmap2.width || processedBitmap1.height != processedBitmap2.height) {
                Log.e(TAG, "Processed bitmaps have different dimensions after potential resize. " +
                        "B1: ${processedBitmap1.width}x${processedBitmap1.height}, " +
                        "B2: ${processedBitmap2.width}x${processedBitmap2.height}")
                return@withContext -1.0 // Error: different sizes
            }

            val finalBitmap1 = if (convertToGrayscale) convertToGrayscale(processedBitmap1) else processedBitmap1
            val finalBitmap2 = if (convertToGrayscale) convertToGrayscale(processedBitmap2) else processedBitmap2

            var diffSum: Long = 0
            val width = finalBitmap1.width
            val height = finalBitmap1.height
            val totalPixels = width * height

            if (totalPixels == 0) return@withContext 0.0

            val pixels1 = IntArray(totalPixels)
            val pixels2 = IntArray(totalPixels)
            finalBitmap1.getPixels(pixels1, 0, width, 0, 0, width, height)
            finalBitmap2.getPixels(pixels2, 0, width, 0, 0, width, height)

            for (i in 0 until totalPixels) {
                val pixel1 = pixels1[i]
                val pixel2 = pixels2[i]

                if (convertToGrayscale) {
                    // In grayscale, red, green, and blue components are equal
                    val gray1 = Color.red(pixel1) // or green, or blue
                    val gray2 = Color.red(pixel2)
                    diffSum += abs(gray1 - gray2)
                } else {
                    // Comparison by RGB components
                    val r1 = Color.red(pixel1)
                    val g1 = Color.green(pixel1)
                    val b1 = Color.blue(pixel1)

                    val r2 = Color.red(pixel2)
                    val g2 = Color.green(pixel2)
                    val b2 = Color.blue(pixel2)

                    diffSum += abs(r1 - r2)
                    diffSum += abs(g1 - g2)
                    diffSum += abs(b1 - b2)
                }
            }

            // Normalize the difference
            // For grayscale, the maximum difference per pixel is 255
            // For RGB, the maximum difference per pixel is 255 * 3
            val maxDiffPerPixel = if (convertToGrayscale) 255.0 else (255.0 * 3.0)
            val maxTotalDiff = maxDiffPerPixel * totalPixels
            if (maxTotalDiff == 0.0) return@withContext 0.0

            val differencePercentage = (diffSum.toDouble() / maxTotalDiff) * 100.0

            // Free memory if new bitmaps were created
            if (resizeWidth != null) {
                if (!processedBitmap1.isRecycled) processedBitmap1.recycle()
                if (!processedBitmap2.isRecycled) processedBitmap2.recycle()
            }
            if (convertToGrayscale) {
                if (finalBitmap1 != processedBitmap1 && !finalBitmap1.isRecycled) finalBitmap1.recycle()
                if (finalBitmap2 != processedBitmap2 && !finalBitmap2.isRecycled) finalBitmap2.recycle()
            }

            return@withContext differencePercentage

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating image difference", e)
            return@withContext -1.0 // Error
        }
    }

    /**
     * Converts Bitmap to grayscale.
     */
    private fun convertToGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmpGrayscale = createBitmap(width, height) // Use ARGB_8888 for compatibility with getPixels

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Standard coefficient for grayscale conversion (Luminosity method)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.rgb(gray, gray, gray) // Alpha remains the same (from the original pixel)
        }
        bmpGrayscale.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmpGrayscale
    }

}