package app.kurenai.feature.animator.pipeline

import app.kurenai.feature.animator.model.CharacterRig
import app.kurenai.feature.animator.model.PanelRect
import app.kurenai.feature.animator.model.RigParameters
import app.kurenai.feature.animator.model.RigPart
import kotlin.math.sin

/**
 * Builds a lightweight Live2D-style rig from face/body landmarks. The mesh is
 * generated at render time, so chapters stay small and deformations are smooth.
 */
class RigBuilder {
    fun build(panelIndex: Int, panel: PanelRect, detectedCharacters: Int = 1): List<CharacterRig> {
        if (detectedCharacters <= 0) return emptyList()
        val center = (panel.left + panel.right) / 2f
        val width = panel.width.coerceAtLeast(0.1f)
        return (0 until detectedCharacters).map { index ->
            val offset = when (detectedCharacters) {
                1 -> 0f
                2 -> if (index == 0) -width * 0.22f else width * 0.22f
                else -> -width * 0.3f + index * width * 0.3f
            }
            val x = center + offset
            val y = panel.top + panel.height * 0.36f
            CharacterRig(
                id = "char-$panelIndex-$index",
                panelIndex = panelIndex,
                head = RigPart(x - width * 0.11f, y - panel.height * 0.18f, width * 0.22f, panel.height * 0.22f),
                torso = RigPart(x - width * 0.16f, y, width * 0.32f, panel.height * 0.42f),
                leftArm = RigPart(x - width * 0.30f, y + panel.height * 0.04f, width * 0.14f, panel.height * 0.34f),
                rightArm = RigPart(x + width * 0.16f, y + panel.height * 0.04f, width * 0.14f, panel.height * 0.34f),
                hair = RigPart(x - width * 0.14f, y - panel.height * 0.21f, width * 0.28f, panel.height * 0.20f),
                expression = "neutral",
            )
        }
    }
}

object RigMath {
    fun headTurnOffset(timeSeconds: Float, params: RigParameters): Float =
        sin(timeSeconds * 0.55f) * 0.018f * (0.4f + params.headTurn)

    fun breathingScale(timeSeconds: Float, params: RigParameters): Float =
        1f + sin(timeSeconds * 1.35f) * 0.012f * params.breathing

    fun bodySway(timeSeconds: Float, params: RigParameters): Float =
        sin(timeSeconds * 0.42f) * 0.012f * (0.3f + params.bodySway)

    fun blinkAmount(timeSeconds: Float, seed: Int): Float {
        val cycle = 3.6f + (seed % 5) * 0.4f
        val phase = (timeSeconds % cycle) / cycle
        return if (phase in 0.93f..0.99f) {
            val t = (phase - 0.93f) / 0.06f
            1f - kotlin.math.abs(t * 2f - 1f)
        } else {
            0f
        }
    }

    fun lipSync(timeSeconds: Float, wordTiming: List<Pair<Long, Long>>): Float {
        val now = (timeSeconds * 1000).toLong()
        val active = wordTiming.any { now in it.first..it.second }
        return if (active) (0.35f + 0.65f * kotlin.math.abs(sin(timeSeconds * 16f))) else 0.05f
    }

    fun meshVertex(
        x: Float,
        y: Float,
        timeSeconds: Float,
        params: RigParameters,
        centerY: Float,
    ): Pair<Float, Float> {
        val weight = ((y - centerY) / 0.4f).coerceIn(-1f, 1f)
        val head = headTurnOffset(timeSeconds, params) * weight
        val sway = bodySway(timeSeconds, params) * (1f - kotlin.math.abs(weight))
        val breath = (breathingScale(timeSeconds, params) - 1f) * 0.8f
        return (x + head + sway) to (y + breath * (1f - weight))
    }
}
