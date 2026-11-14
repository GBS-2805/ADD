package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineBmoResponsiveTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactUsesOneBottomSheet() {
        assertEquals(EngineLayout.Compact, engineLayout(411.dp))
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 800.dp)) {
                    EnginePanelHost(
                        layout = EngineLayout.Compact,
                        title = "Style",
                        onDismiss = {},
                    ) { Text("panel body") }
                }
            }
        }

        compose.onNodeWithTag("bmo-panel-compact").assertExists()
        compose.onNodeWithTag("bmo-panel-expanded").assertDoesNotExist()
        compose.onNodeWithTag("bmo-panel-close").assertExists()
    }

    @Test
    fun expandedUsesAnEdgePanel() {
        assertEquals(EngineLayout.Expanded, engineLayout(840.dp))
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(840.dp, 800.dp)) {
                    EnginePanelHost(
                        layout = EngineLayout.Expanded,
                        title = "Layers",
                        onDismiss = {},
                    ) { Text("panel body") }
                }
            }
        }

        compose.onNodeWithTag("bmo-panel-expanded").assertExists()
        compose.onNodeWithTag("bmo-panel-compact").assertDoesNotExist()
        compose.onNodeWithTag("bmo-panel-close").assertExists()
    }

    @Test
    fun expandingDoesNotDuplicateTheOpenPanel() {
        var layout by mutableStateOf(EngineLayout.Compact)
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(840.dp, 800.dp)) {
                    EnginePanelHost(layout = layout, title = "Style", onDismiss = {}) {
                        Text("panel body")
                    }
                }
            }
        }

        compose.onAllNodesWithTag("bmo-panel-compact").assertCountEquals(1)
        compose.runOnUiThread { layout = EngineLayout.Expanded }
        compose.waitForIdle()
        compose.onAllNodesWithTag("bmo-panel-expanded").assertCountEquals(1)
        compose.onNodeWithTag("bmo-panel-compact").assertDoesNotExist()
    }

    @Test
    fun panelCloseReturnsControlToTheEditor() {
        var dismissed = false
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 800.dp)) {
                    EnginePanelHost(layout = EngineLayout.Compact, title = "Style",
                        onDismiss = { dismissed = true }) { Text("panel body") }
                }
            }
        }

        compose.onNodeWithTag("bmo-panel-close").performClick()
        assertTrue(dismissed)
    }
}
