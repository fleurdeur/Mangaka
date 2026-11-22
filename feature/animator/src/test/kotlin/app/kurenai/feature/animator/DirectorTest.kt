package app.kurenai.feature.animator

import app.kurenai.core.model.MotionIntensity
import app.kurenai.feature.animator.model.PanelRect
import app.kurenai.feature.animator.pipeline.Director
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorTest {
    private val panels = listOf(
        PanelRect(0.05f, 0.05f, 0.95f, 0.42f, order = 0),
        PanelRect(0.05f, 0.48f, 0.47f, 0.95f, order = 1),
        PanelRect(0.53f, 0.48f, 0.95f, 0.95f, order = 2),
    )

    @Test
    fun `one shot per panel with contiguous timing`() {
        val shots = Director().compose(panels, narrationMs = 9_000, intensity = MotionIntensity.CINEMATIC)
        assertEquals(3, shots.size)
        assertEquals(0L, shots.first().startMs)
        shots.zipWithNext().forEach { (a, b) ->
            assertEquals(a.startMs + a.durationMs, b.startMs)
        }
    }

    @Test
    fun `still intensity removes all camera motion`() {
        val shots = Director().compose(panels, narrationMs = 6_000, intensity = MotionIntensity.STILL)
        shots.forEach { shot ->
            assertEquals(0f, shot.pushIn, 0.0001f)
            assertEquals(0f, shot.parallax, 0.0001f)
            assertEquals(0f, shot.shake, 0.0001f)
            assertEquals(shot.fromLeft, shot.toLeft, 0.0001f)
            assertEquals(shot.fromTop, shot.toTop, 0.0001f)
        }
    }

    @Test
    fun `full intensity adds bounded push in`() {
        val shots = Director().compose(panels, narrationMs = 6_000, intensity = MotionIntensity.FULL)
        shots.forEach { shot ->
            assertTrue(shot.pushIn in 0f..0.25f)
            assertTrue(shot.toLeft >= shot.fromLeft)
            assertTrue(shot.toRight <= shot.fromRight)
        }
    }

    @Test
    fun `empty input is safe`() {
        assertTrue(Director().compose(emptyList(), 1_000, MotionIntensity.CINEMATIC).isEmpty())
    }
}
