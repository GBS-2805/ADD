package app.add.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * Renders the real engine activity on the JVM. The seed 3D layer is invisible, and the
 * sheet filters 3D surfaces, so these frames do not need a Filament compositor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineFrameCaptureTest {
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

    private fun capture(name: String) {
        val dir = File(System.getenv("ENGINE_RENDER_OUT") ?: "build/engine-render")
        dir.mkdirs()
        compose.onRoot().captureRoboImage(File(dir, name).absolutePath)
    }

    @Test
    fun captureStudioShellDeckAndInspector() {
        compose.waitForIdle()
        capture("engine-studio-stage.png")

        // Bare stage is the way back to the Create dock. The scrim-free top pill
        // appears after the tap; reopen it so the dock is shown in its real frame.
        compose.onNodeWithTag("engine-artboard").performTouchInput {
            click(Offset(width * 0.9f, height * 0.7f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("studio-peek").performClick()
        compose.waitForIdle()
        capture("engine-studio-dock.png")

        compose.onNodeWithTag("engine-add").performClick()
        compose.waitForIdle()
        capture("engine-studio-create.png")

        compose.onNodeWithTag("create-type").performClick()
        compose.waitForIdle()
        capture("engine-studio-landed.png")

        compose.onNodeWithTag("notice-inspect").performClick()
        compose.waitForIdle()
        capture("engine-studio-style.png")

        compose.onNodeWithTag("tab-effects").performClick()
        compose.waitForIdle()
        capture("engine-studio-fx.png")

        compose.onNodeWithText("Grade").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("FINE TUNE").performClick()
        compose.waitForIdle()
        capture("engine-studio-fx-grade.png")

        compose.onNodeWithTag("tab-layers").performClick()
        compose.waitForIdle()
        capture("engine-studio-layers.png")

        // A visible 3D layer in the document, with the sheet still up. It must stay
        // off the panel; no Filament surface is composed while the sheet is open.
        compose.onNodeWithTag("layer-vis-model-gem").performClick()
        compose.waitForIdle()
        capture("engine-studio-layers-3d.png")
        compose.onNodeWithTag("layer-model-gem").performClick()
        compose.onNodeWithTag("tab-style").performClick()
        compose.waitForIdle()
        capture("engine-studio-3d-style.png")

        compose.onNodeWithTag("tab-layers").performClick()
        compose.onNodeWithTag("layer-vis-model-gem").performScrollTo().performClick()
        compose.onNodeWithTag("layer-type-statement").performScrollTo().performClick()
        compose.onNodeWithTag("tab-style").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("mask-entry").performScrollTo().performClick()
        compose.waitForIdle()
        capture("engine-studio-mask.png")
    }
}
