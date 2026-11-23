package app.kurenai.feature.animator.pipeline

import app.kurenai.core.model.MotionIntensity
import app.kurenai.feature.animator.model.CameraShot
import app.kurenai.feature.animator.model.PanelRect
import app.kurenai.feature.animator.model.TransitionKind
import kotlin.math.min

/**
 * Builds a camera plan from panels and narration timing. Reading order is already
 * RTL-aware; the director adds gentle push-ins, pans and parallax, and chooses
 * transitions from panel aspect and neighbouring energy.
 */
class Director {
    fun compose(
        panels: List<PanelRect>,
        narrationMs: Long,
        intensity: MotionIntensity,
        transitionSeed: Int = 0,
        panelDurations: List<Long>? = null,
    ): List<CameraShot> {
        if (panels.isEmpty()) return emptyList()
        val total = narrationMs.coerceAtLeast(panels.size * 1_200L)
        val perPanel = (total / panels.size).coerceAtLeast(1_200L)
        val durations = panelDurations
            ?.takeIf { it.size == panels.size }
            ?.map { it.coerceAtLeast(900L) }
            ?: List(panels.size) { perPanel }
        var start = 0L
        return panels.mapIndexed { index, panel ->
            val duration = durations[index]
            val shotStart = start
            start += duration
            val strength = when (intensity) {
                MotionIntensity.STILL -> 0f
                MotionIntensity.SUBTLE -> 0.04f
                MotionIntensity.CINEMATIC -> 0.10f
                MotionIntensity.FULL -> 0.17f
            }
            val push = if (strength == 0f) 0f else strength * (0.7f + (index % 3) * 0.15f)
            val panX = if (intensity == MotionIntensity.STILL) 0f else strength * if (index % 2 == 0) 1f else -1f
            val transition = when {
                index == 0 -> TransitionKind.CUT
                panel.aspect > 1.4f -> TransitionKind.SLIDE
                transitionSeed % 5 == 0 -> TransitionKind.DISSOLVE
                else -> TransitionKind.PARALLAX
            }
            CameraShot(
                panelIndex = index,
                startMs = shotStart,
                durationMs = duration,
                fromLeft = panel.left,
                fromTop = panel.top,
                fromRight = panel.right,
                fromBottom = panel.bottom,
                toLeft = panel.left + push * 0.5f,
                toTop = panel.top + push * 0.4f,
                toRight = panel.right - push * 0.5f,
                toBottom = panel.bottom - push * 0.4f,
                pushIn = push,
                parallax = min(0.2f, strength),
                shake = if (intensity == MotionIntensity.FULL && index % 4 == 3) 0.008f else 0f,
                transition = transition,
            )
        }
    }
}
