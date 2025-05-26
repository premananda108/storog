package ua.pp.soulrise.storog // Ваше имя пакета

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

object ImageComparator {


    /**
     * Сравнивает два изображения и возвращает процент различия между ними.
     *
     * @param bitmap1 Первое изображение.
     * @param bitmap2 Второе изображение.
     * @param resizeWidth Ширина, до которой будут уменьшены изображения перед сравнением (для скорости и устойчивости).
     *                    Если null, используется оригинальный размер (не рекомендуется для больших изображений).
     * @param convertToGrayscale Преобразовывать ли изображения в оттенки серого перед сравнением.
     * @return Процент различия (от 0.0 до 100.0).
     *         Возвращает -1.0 при ошибке (например, разные размеры после ресайза, если он не применялся).
     */
    suspend fun calculateDifferencePercentage(
        bitmap1: Bitmap,
        bitmap2: Bitmap,
        resizeWidth: Int? = 100,
        convertToGrayscale: Boolean = true
    ): Double = withContext(Dispatchers.Default) {
        try {
            val processedBitmap1 = preprocessBitmap(bitmap1, resizeWidth, convertToGrayscale)
            val processedBitmap2 = preprocessBitmap(bitmap2, resizeWidth, convertToGrayscale)

            var totalPixels = 0
            var differentPixels = 0

            for (x in 0 until processedBitmap1.width) {
                for (y in 0 until processedBitmap1.height) {
                    val pixel1 = processedBitmap1[x, y]
                    val pixel2 = processedBitmap2[x, y]

                    totalPixels++
                    if (pixel1 != pixel2) {
                        differentPixels++
                    }
                }
            }

            (differentPixels.toDouble() / totalPixels.toDouble()) * 100.0
        } catch (e: Exception) {
            Log.e("ImageComparator", "Error calculating difference", e)
            0.0
        }
    }

    private fun preprocessBitmap(
        bitmap: Bitmap,
        resizeWidth: Int?,
        convertToGrayscale: Boolean
    ): Bitmap {
        var processedBitmap = bitmap

        // Уменьшение размера для ускорения обработки
        if (resizeWidth != null) {
            val aspectRatio = bitmap.height.toDouble() / bitmap.width.toDouble()
            val resizeHeight = (resizeWidth * aspectRatio).toInt()
            processedBitmap = bitmap.scale(resizeWidth, resizeHeight)
        }

        // Конвертация в градации серого для более точного сравнения
        if (convertToGrayscale) {
            processedBitmap = convertToGrayscale(processedBitmap)
        }

        return processedBitmap
    }


    /**
     * Преобразует Bitmap в оттенки серого.
     */
    private fun convertToGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmpGrayscale =
            createBitmap(width, height) // Используем ARGB_8888 для совместимости с getPixels

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Стандартный коэффициент для преобразования в оттенки серого (Luminosity method)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] =
                Color.rgb(gray, gray, gray) // Alpha остается прежним (из оригинального пикселя)
        }
        bmpGrayscale.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmpGrayscale
    }
}