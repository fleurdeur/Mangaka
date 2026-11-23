package app.kurenai.feature.reader

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.min

/**
 * Trims uniform scanner/website borders without touching interior art. Only a
 * full edge line that is almost entirely the page background is removed, so
 * black panels, full-bleed pages and transparent PNGs are left alone. The crop
 * is a lossless bitmap copy; no resampling is performed.
 */
class MarginCropTransformation(
    private val tolerance: Int = 20,
    private val maxTrimFraction: Float = 0.22f,
) : Transformation() {

    override val cacheKey: String = "kurenai-margin-crop-v1-$tolerance"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width < 16 || input.height < 16) return input
        val background = estimateBackground(input)
        val luma = (0.299f * ((background shr 16) and 0xFF) +
            0.587f * ((background shr 8) and 0xFF) +
            0.114f * (background and 0xFF))
        // Only paper-like borders are safe to trim. A dark full-bleed page must
        // never lose its outer art just because the edge happens to be uniform.
        if (luma < 180f) return input
        val maxX = (input.width * maxTrimFraction).toInt()
        val maxY = (input.height * maxTrimFraction).toInt()
        var left = 0
        var top = 0
        var right = input.width - 1
        var bottom = input.height - 1

        while (left < maxX && isUniformColumn(input, left, top, bottom, background)) left++
        while (right > input.width - 1 - maxX && isUniformColumn(input, right, top, bottom, background)) right--
        while (top < maxY && isUniformRow(input, top, left, right, background)) top++
        while (bottom > input.height - 1 - maxY && isUniformRow(input, bottom, left, right, background)) bottom--

        val width = right - left + 1
        val height = bottom - top + 1
        if (width < input.width * 0.5f || height < input.height * 0.5f) return input
        if (left == 0 && top == 0 && width == input.width && height == input.height) return input
        return Bitmap.createBitmap(input, left, top, width, height)
    }

    private fun estimateBackground(bitmap: Bitmap): Int {
        val xs = intArrayOf(0, bitmap.width - 1)
        val ys = intArrayOf(0, bitmap.height - 1)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in ys) for (x in xs) {
            val c = bitmap.getPixel(x, y)
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            count++
        }
        return android.graphics.Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun isUniformColumn(bitmap: Bitmap, x: Int, top: Int, bottom: Int, background: Int): Boolean {
        val step = ((bottom - top) / 96).coerceAtLeast(1)
        var checked = 0
        var matches = 0
        var y = top
        while (y <= bottom) {
            checked++
            if (similar(bitmap.getPixel(x, y), background)) matches++
            y += step
        }
        return checked > 0 && matches.toFloat() / checked > 0.985f
    }

    private fun isUniformRow(bitmap: Bitmap, y: Int, left: Int, right: Int, background: Int): Boolean {
        val step = ((right - left) / 96).coerceAtLeast(1)
        var checked = 0
        var matches = 0
        var x = left
        while (x <= right) {
            checked++
            if (similar(bitmap.getPixel(x, y), background)) matches++
            x += step
        }
        return checked > 0 && matches.toFloat() / checked > 0.985f
    }

    private fun similar(color: Int, background: Int): Boolean {
        val dr = abs(((color shr 16) and 0xFF) - ((background shr 16) and 0xFF))
        val dg = abs(((color shr 8) and 0xFF) - ((background shr 8) and 0xFF))
        val db = abs((color and 0xFF) - (background and 0xFF))
        return dr + dg + db <= tolerance * 3
    }
}
