package app.add.engine

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/** Review capture for the plugin catalogue. It is not part of the chrome golden set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineCatalogueCaptureTest {
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
    fun captureSearchablePluginCatalogue() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugins").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugin-catalogue-search").performTextInput("prism")
        compose.waitForIdle()

        val dir = File(System.getenv("ENGINE_RENDER_OUT") ?: "build/catalogue-capture").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "bmo-plugin-catalogue.png").absolutePath)
    }
}
