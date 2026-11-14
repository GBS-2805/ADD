package app.add.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
        capture("bmo-full.png")

        // Bare stage is the way back to the Create dock. The scrim-free top pill
        // appears after the tap; reopen it so the dock is shown in its real frame.
        compose.onNodeWithTag("engine-artboard").performTouchInput {
            click(Offset(width * 0.9f, height * 0.7f))
        }
        compose.waitForIdle()
        capture("bmo-peek.png")
        // Peek is the page chip only: the zoom pill belongs to Full chrome.
        compose.onAllNodesWithTag("stage-zoom").assertCountEquals(0)
        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.waitForIdle()
        capture("engine-studio-dock.png")

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Hide chrome").performClick()
        compose.waitForIdle()
        capture("bmo-canvas.png")
        compose.onNodeWithTag("bmo-restore").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bmo-add").performClick()
        compose.waitForIdle()
        capture("engine-studio-create.png")

        compose.onNodeWithTag("create-type").performClick()
        compose.waitForIdle()
        capture("engine-studio-landed.png")
        capture("bmo-selection.png")
        capture("bmo-compact-full.png")

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

        // The Material Ink path: arm from Texture, draw on the stage, and keep the
        // compact HUD visible. This is the artifact that proves the gesture path is
        // wired through the real activity, not just the renderer unit test.
        compose.onNodeWithTag("mask-done").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("object-style").assertHasClickAction()
        compose.onNodeWithTag("object-style").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("inspector-sheet").assertExists()
        compose.onNodeWithTag("tab-texture").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tab-texture").assertIsSelected()
        compose.onNodeWithTag("texture-ink-arm").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("engine-artboard").performTouchInput {
            down(Offset(width * 0.18f, height * 0.28f))
            moveTo(Offset(width * 0.35f, height * 0.34f))
            moveTo(Offset(width * 0.52f, height * 0.46f))
            moveTo(Offset(width * 0.7f, height * 0.58f))
            up()
        }
        compose.waitForIdle()
        capture("engine-studio-ink.png")

        // The recording's blank interval was a Marble material painted over the
        // layers. Keep the applied recipe and the still-readable page in the set.
        compose.onNodeWithTag("ink-done").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("object-texture").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("texture-family-marble").performClick()
        compose.waitForIdle()
        capture("engine-studio-texture-panel.png")
        compose.onNodeWithTag("sheet-done").performClick()
        compose.waitForIdle()
        capture("engine-studio-texture.png")

        // A first-party plugin on the same panel surface.
        compose.onNodeWithTag("bmo-plugins").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugin-tone").performClick()
        compose.waitForIdle()
        capture("bmo-plugin.png")
        capture("bmo-compact-plugin.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h1000dp-xxhdpi")
    fun captureExpandedShell() {
        compose.waitForIdle()
        capture("bmo-expanded.png")

        compose.onNodeWithTag("bmo-add").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("create-type").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notice-inspect").performClick()
        compose.waitForIdle()
        capture("bmo-expanded-inspector.png")
    }
}
