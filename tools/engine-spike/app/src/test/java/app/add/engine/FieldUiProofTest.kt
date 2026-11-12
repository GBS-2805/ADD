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
 * Fallback UI evidence when no hardware-accelerated Android runtime is available.
 * This drives the real MainActivity and captures the real Compose frames.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FieldUiProofTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val dir = File(System.getenv("ENGINE_RENDER_OUT") ?: "build/field-proof").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, name).absolutePath)
    }

    @Test
    fun fieldSurvivesTheRealUi() {
        compose.waitForIdle()
        capture("ui-01-stage.png")

        compose.onNodeWithTag("object-style").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithTag("field-artboard-card").performClick()
        compose.mainClock.advanceTimeBy(200)

        // The field is a live animation; pause the test clock so idling does not wait
        // forever on the next animation frame. The frame captured is still real.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("ADD FIELD BACKGROUND").performClick()
        compose.mainClock.advanceTimeBy(64)
        capture("ui-02-field-applied.png")
        compose.mainClock.advanceTimeBy(900)
        capture("ui-03-artboard-field.png")

        compose.onNodeWithTag("sheet-done").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithTag("object-style").performClick()
        compose.mainClock.advanceTimeBy(900)
        capture("ui-04-reopened.png")
    }
}
