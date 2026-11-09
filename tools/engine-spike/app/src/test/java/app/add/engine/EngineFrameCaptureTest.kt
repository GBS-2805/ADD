package app.add.engine

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
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
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val dir = File(System.getenv("ENGINE_RENDER_OUT") ?: "build/engine-render")
        dir.mkdirs()
        compose.onRoot().captureRoboImage(File(dir, name).absolutePath)
    }

    @Test
    fun captureRailSettledAddAndInspector() {
        compose.waitForIdle()
        capture("engine-rail.png")

        compose.onNodeWithTag("engine-add").performClick()
        compose.onNodeWithTag("add-type").performClick()
        compose.waitForIdle()
        capture("engine-added.png")

        compose.onNodeWithText("INSPECT").performClick()
        compose.waitForIdle()
        capture("engine-inspector.png")

        compose.onNodeWithTag("tab-layers").performClick()
        compose.waitForIdle()
        capture("engine-layers.png")

        // A visible 3D layer in the document, with the sheet still up. It must stay
        // off the panel; no Filament surface is composed while the sheet is open.
        compose.onNodeWithTag("layer-vis-model-gem").performClick()
        compose.waitForIdle()
        capture("engine-layers-3d-visible.png")
        compose.onNodeWithTag("layer-model-gem").performClick()
        compose.onNodeWithTag("tab-inspect").performClick()
        compose.waitForIdle()
        capture("engine-inspector-3d-visible.png")
    }
}
