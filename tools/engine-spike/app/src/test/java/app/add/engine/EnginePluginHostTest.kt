package app.add.engine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object CounterPlugin : EnginePlugin {
    override val id = "counter"
    override val name = "Counter"
    override val glyph = "+"
    override val summary = "Counts taps."
    override val commands = emptyList<EnginePluginCommand>()

    @Composable
    override fun Panel(scope: EnginePluginScope) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        Column {
            Text("COUNT $count")
            TactileBox(
                onClick = { count++ },
                modifier = Modifier.size(48.dp).testTag("counter-inc"),
            ) { Text("+") }
        }
    }
}

private object OtherPlugin : EnginePlugin {
    override val id = "other"
    override val name = "Other"
    override val glyph = "□"
    override val summary = "Another panel."
    override val commands = emptyList<EnginePluginCommand>()

    @Composable
    override fun Panel(scope: EnginePluginScope) {
        Text("OTHER PANEL")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EnginePluginHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hidingAndReopeningPreservesPluginState() {
        lateinit var holder: SaveableStateHolder
        var visible by mutableStateOf(true)
        compose.setContent {
            EngineTheme {
                val stateHolder = rememberSaveableStateHolder()
                SideEffect { holder = stateHolder }
                if (visible) {
                    EnginePluginHost(
                        plugin = CounterPlugin,
                        doc = EngineDocument(),
                        onHide = {},
                        onClose = {},
                        stateHolder = stateHolder,
                    )
                }
            }
        }

        compose.onNodeWithTag("counter-inc").performClick()
        compose.onNodeWithText("COUNT 1").assertExists()

        compose.runOnUiThread { visible = false }
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-plugin-host").assertDoesNotExist()

        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("COUNT 1").assertExists()
    }

    @Test
    fun closingAndReopeningStartsFresh() {
        lateinit var holder: SaveableStateHolder
        var visible by mutableStateOf(true)
        compose.setContent {
            EngineTheme {
                val stateHolder = rememberSaveableStateHolder()
                SideEffect { holder = stateHolder }
                if (visible) {
                    EnginePluginHost(
                        plugin = CounterPlugin,
                        doc = EngineDocument(),
                        onHide = {},
                        onClose = {},
                        stateHolder = stateHolder,
                    )
                }
            }
        }

        compose.onNodeWithTag("counter-inc").performClick()
        compose.onNodeWithText("COUNT 1").assertExists()

        compose.runOnUiThread {
            holder.removeState("counter")
            visible = false
        }
        compose.waitForIdle()
        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("COUNT 0").assertExists()
    }

    @Test
    fun onlyOnePluginCanBeActive() {
        var active by mutableStateOf("counter")
        compose.setContent {
            EngineTheme {
                val stateHolder = rememberSaveableStateHolder()
                val plugin = if (active == "counter") CounterPlugin else OtherPlugin
                EnginePluginHost(
                    plugin = plugin,
                    doc = EngineDocument(),
                    onHide = {},
                    onClose = {},
                    stateHolder = stateHolder,
                )
            }
        }

        compose.onNodeWithText("COUNT 0").assertExists()
        compose.runOnUiThread { active = "other" }
        compose.waitForIdle()

        compose.onNodeWithText("OTHER PANEL").assertExists()
        compose.onNodeWithTag("counter-inc").assertDoesNotExist()
    }

    @Test
    fun pluginPanelTakesTheInspectorSurface() {
        compose.setContent {
            EngineTheme {
                EnginePluginHost(
                    plugin = CounterPlugin,
                    doc = EngineDocument(),
                    onHide = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("bmo-plugin-host").assertExists()
        compose.onNodeWithTag("inspector-sheet").assertExists()
        compose.onNodeWithText("RUNNING").assertExists()
        compose.onNodeWithTag("bmo-plugin-hide").assertExists()
        compose.onNodeWithTag("bmo-plugin-close").assertExists()
    }
}
