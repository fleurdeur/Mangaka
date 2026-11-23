package app.kurenai

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts the real Hilt application and the real MainActivity on the JVM. This
 * catches missing bindings, bad resources, Room/FTS setup errors and Compose
 * composition failures without needing a physical device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupSmokeTest {
    @Test
    fun `application and main activity start`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse(activity.isFinishing)
    }
}
