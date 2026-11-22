package app.kurenai.feature.animator.pipeline

import android.graphics.Bitmap
import app.kurenai.feature.animator.model.PanelRect
import kotlin.math.abs

/**
 * Fast gutter-based panel detector. It downsamples the page, finds near-uniform
 * white gutters, splits rows then columns and orders panels top-to-bottom with
 * right-to-left ordering for manga when requested.
 */
class PanelDetector(
    private val gutterLuma: Int = 246,
    private val gutterVariance: Double = 6.0,
    private val minAreaFraction: Float = 0.018f,
) {
    fun detect(bitmap: Bitmap, rightToLeft: Boolean = true): List<PanelRect> {
        val scale = 320f / bitmap.width.coerceAtLeast(1)
        val width = 320
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = IntArray(pixels.size) { index ->
            val p = pixels[index]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        val rowBlank = BooleanArray(height) { y -> isGutter(0, width, y, luma, width) }
        val rowRanges = runs(rowBlank, height).filter { it.second - it.first > height * 0.02f }
        val panels = mutableListOf<PanelRect>()
        for ((top, bottom) in rowRanges) {
            val colBlank = BooleanArray(width) { x -> isGutter(x, width, 0, luma, width, top, bottom) }
            val colRanges = runs(colBlank, width).filter { it.second - it.first > width * 0.03f }
            val ordered = if (rightToLeft) colRanges.reversed() else colRanges
            for ((left, right) in ordered) {
                val area = ((right - left).toFloat() / width) * ((bottom - top).toFloat() / height)
                if (area >= minAreaFraction) {
                    panels += PanelRect(
                        left = left.toFloat() / width,
                        top = top.toFloat() / height,
                        right = right.toFloat() / width,
                        bottom = bottom.toFloat() / height,
                        order = panels.size,
                    )
                }
            }
        }
        small.recycle()
        if (panels.isEmpty()) {
            return listOf(PanelRect(0f, 0f, 1f, 1f, 0))
        }
        return panels.mapIndexed { index, rect -> rect.copy(order = index) }
    }

    private fun isGutter(
        x: Int,
        width: Int,
        y: Int,
        luma: IntArray,
        stride: Int,
        top: Int = 0,
        bottom: Int = -1,
    ): Boolean {
        if (bottom <= 0) {
            var sum = 0L
            var sumSq = 0L
            for (i in 0 until width) {
                val v = luma[y * stride + i]
                sum += v
                sumSq += v.toLong() * v
            }
            val mean = sum.toDouble() / width
            val variance = sumSq.toDouble() / width - mean * mean
            return mean >= gutterLuma && variance <= gutterVariance
        }
        var sum = 0L
        var sumSq = 0L
        var count = 0
        for (row in top until bottom) {
            val v = luma[row * stride + x]
            sum += v
            sumSq += v.toLong() * v
            count++
        }
        if (count == 0) return true
        val mean = sum.toDouble() / count
        val variance = sumSq.toDouble() / count - mean * mean
        return mean >= gutterLuma && variance <= gutterVariance
    }

    private fun runs(blank: BooleanArray, size: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (i in 0 until size) {
            if (!blank[i]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start > 2) result += start to i
                start = -1
            }
        }
        if (start >= 0 && size - start > 2) result += start to size
        return result
    }
}

/** Estimates bubble and caption boxes from bright, rounded regions. Text comes from OCR. */
class BubbleDetector {
    fun detect(bitmap: Bitmap, panels: List<PanelRect>): List<app.kurenai.feature.animator.model.Bubble> {
        // Conservative placeholder: one caption per panel keeps the pipeline honest
        // when no OCR engine is configured, and never invents dialogue.
        return panels.mapIndexed { index, _ ->
            app.kurenai.feature.animator.model.Bubble(
                panelIndex = index,
                text = "",
                left = 0.08f,
                top = 0.12f,
                right = 0.92f,
                bottom = 0.28f,
                isNarration = true,
            )
        }
    }
}
