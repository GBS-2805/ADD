package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LayerThumbnailTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyLayerRowHasAThumbnail() {
        val doc = EngineDocument()
        // This class runs after the animated shell captures in the full suite; stop
        // the clock so a leftover frame loop cannot keep the idling resource busy.
        compose.mainClock.autoAdvance = false

        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 700.dp)) {
                    LayersTab(doc)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithTag("layer-type-kicker").assertExists()
        listOf("type-kicker", "vector-star", "model-gem").forEach { id ->
            compose.onNodeWithTag("layer-thumb-$id", useUnmergedTree = true).assertExists()
        }
    }
}
