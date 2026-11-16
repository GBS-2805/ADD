package app.add.engine

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Produces the review capture only. It is intentionally a separate test class so
 * `verify-chrome.sh`, which compares EngineFrameCaptureTest, does not treat the new
 * image as a golden that must already exist.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EnginePrismCaptureTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun freezeAutosaveClock() {
            EngineClock.nowMillis = { 1_700_000_000_000L }
        }

        @AfterClass
        @JvmStatic
        fun restoreAutosaveClock() {
            EngineClock.nowMillis = { System.currentTimeMillis() }
        }
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturePrismPluginPanel() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugins").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugin-prism").performClick()
        compose.waitForIdle()

        val dir = File(System.getenv("ENGINE_RENDER_OUT") ?: "build/prism-capture").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "bmo-plugin-prism.png").absolutePath)
    }
}
