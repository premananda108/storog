package ua.pp.soulrise.storog // Ваше имя пакета

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object ImageComparator {

    private const val TAG = "ImageComparator"

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
        resizeWidth: Int? = 100, // Уменьшаем для скорости, например, до ширины 100px
        convertToGrayscale: Boolean = true
    ): Double = withContext(Dispatchers.Default) { // Выполняем на вычислительном потоке
        try {
            val processedBitmap1: Bitmap
            val processedBitmap2: Bitmap

            if (resizeWidth != null) {
                val aspectRatio1 = bitmap1.height.toDouble() / bitmap1.width.toDouble()
                val resizeHeight1 = (resizeWidth * aspectRatio1).toInt()
                processedBitmap1 = Bitmap.createScaledBitmap(bitmap1, resizeWidth, resizeHeight1, true)

                val aspectRatio2 = bitmap2.height.toDouble() / bitmap2.width.toDouble()
                val resizeHeight2 = (resizeWidth * aspectRatio2).toInt()
                processedBitmap2 = Bitmap.createScaledBitmap(bitmap2, resizeWidth, resizeHeight2, true)
            } else {
                processedBitmap1 = bitmap1
                processedBitmap2 = bitmap2
            }

            if (processedBitmap1.width != processedBitmap2.width || processedBitmap1.height != processedBitmap2.height) {
                Log.e(TAG, "Processed bitmaps have different dimensions after potential resize. " +
                        "B1: ${processedBitmap1.width}x${processedBitmap1.height}, " +
                        "B2: ${processedBitmap2.width}x${processedBitmap2.height}")
                return@withContext -1.0 // Ошибка: разные размеры
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
                    // В оттенках серого, красный, зеленый и синий компоненты равны
                    val gray1 = Color.red(pixel1) // или green, или blue
                    val gray2 = Color.red(pixel2)
                    diffSum += abs(gray1 - gray2)
                } else {
                    // Сравнение по RGB компонентам
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

            // Нормализуем разницу
            // Для оттенков серого максимальная разница на пиксель - 255
            // Для RGB максимальная разница на пиксель - 255 * 3
            val maxDiffPerPixel = if (convertToGrayscale) 255.0 else (255.0 * 3.0)
            val maxTotalDiff = maxDiffPerPixel * totalPixels
            if (maxTotalDiff == 0.0) return@withContext 0.0

            val differencePercentage = (diffSum.toDouble() / maxTotalDiff) * 100.0

            // Освобождаем память, если создавали новые битмапы
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
            return@withContext -1.0 // Ошибка
        }
    }

    /**
     * Преобразует Bitmap в оттенки серого.
     */
    private fun convertToGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // Используем ARGB_8888 для совместимости с getPixels

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Стандартный коэффициент для преобразования в оттенки серого (Luminosity method)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.rgb(gray, gray, gray) // Alpha остается прежним (из оригинального пикселя)
        }
        bmpGrayscale.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmpGrayscale
    }

    /**
     * Функция-обертка для проверки, превышает ли различие заданный порог.
     *
     * @param bitmap1 Первое изображение.
     * @param bitmap2 Второе изображение.
     * @param differenceThresholdPercentage Порог различия в процентах (например, 10.0 для 10%).
     * @param resizeWidth Ширина для ресайза.
     * @param convertToGrayscale Преобразовывать ли в оттенки серого.
     * @return true, если различие превышает порог, false в противном случае или при ошибке.
     */
    suspend fun doImagesDifferSignificantly(
        bitmap1: Bitmap,
        bitmap2: Bitmap,
        differenceThresholdPercentage: Double,
        resizeWidth: Int? = 100,
        convertToGrayscale: Boolean = true
    ): Boolean {
        if (differenceThresholdPercentage < 0.0 || differenceThresholdPercentage > 100.0) {
            Log.w(TAG, "Difference threshold percentage is out of range (0-100): $differenceThresholdPercentage")
            return false // Некорректный порог
        }

        val difference = calculateDifferencePercentage(
            bitmap1,
            bitmap2,
            resizeWidth,
            convertToGrayscale
        )

        Log.d(TAG, "Calculated image difference: $difference%")

        return if (difference >= 0) {
            difference > differenceThresholdPercentage
        } else {
            Log.e(TAG, "Failed to calculate image difference, assuming no significant change.")
            false // Ошибка при вычислении, считаем, что нет значительных изменений
        }
    }
}