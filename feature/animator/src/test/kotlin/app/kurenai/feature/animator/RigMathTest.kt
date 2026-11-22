package app.kurenai.feature.animator

import app.kurenai.feature.animator.model.RigParameters
import app.kurenai.feature.animator.pipeline.RigMath
import org.junit.Assert.assertTrue
import org.junit.Test

class RigMathTest {
    private val params = RigParameters(headTurn = 1f, bodySway = 1f, breathing = 1f)

    @Test
    fun `blink stays in normalised range`() {
        (0..200).forEach { step ->
            val blink = RigMath.blinkAmount(step * 0.05f, seed = 3)
            assertTrue("blink=$blink", blink in 0f..1f)
        }
    }

    @Test
    fun `head turn and breathing are bounded`() {
        (0..100).forEach { step ->
            val time = step * 0.1f
            assertTrue(RigMath.headTurnOffset(time, params) in -0.03f..0.03f)
            assertTrue(RigMath.breathingScale(time, params) in 0.98f..1.02f)
        }
    }

    @Test
    fun `lip sync opens only during active word ranges`() {
        val idle = RigMath.lipSync(0.5f, listOf(1_000L to 2_000L))
        val active = RigMath.lipSync(1.25f, listOf(1_000L to 2_000L))
        assertTrue(idle < 0.1f)
        assertTrue(active >= 0.35f)
    }

    @Test
    fun `mesh deformation remains finite`() {
        val (x, y) = RigMath.meshVertex(0.4f, 0.6f, 4.2f, params, centerY = 0.5f)
        assertTrue(x.isFinite() && y.isFinite())
    }
}
