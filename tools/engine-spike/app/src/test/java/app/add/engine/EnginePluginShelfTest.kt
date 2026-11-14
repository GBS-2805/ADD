package app.add.engine

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object ShelfAlpha : EnginePlugin {
    override val id = "alpha"
    override val name = "Alpha"
    override val glyph = "A"
    override val summary = "First plugin."
    override val commands = emptyList<EnginePluginCommand>()
    @Composable override fun Panel(scope: EnginePluginScope) { Text("ALPHA") }
}

private object ShelfBeta : EnginePlugin {
    override val id = "beta"
    override val name = "Beta"
    override val glyph = "B"
    override val summary = "Second plugin."
    override val commands = emptyList<EnginePluginCommand>()
    @Composable override fun Panel(scope: EnginePluginScope) { Text("BETA") }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EnginePluginShelfTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listsPluginsAndOpensTheRunningOne() {
        var opened: String? = null
        var ran: String? = null
        compose.setContent {
            EngineTheme {
                EnginePluginShelf(
                    plugins = listOf(ShelfAlpha, ShelfBeta),
                    activeId = "alpha",
                    recentIds = listOf("beta"),
                    onRun = { ran = it },
                    onOpen = { opened = it },
                    onHide = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Alpha").assertExists()
        compose.onNodeWithText("RUNNING").assertExists()
        compose.onNodeWithTag("bmo-plugin-alpha").performClick()
        assertEquals("alpha", opened)

        compose.onNodeWithTag("bmo-plugin-beta").performClick()
        assertEquals("beta", ran)
    }

    @Test
    fun hideAndCloseAreAvailableForTheActiveSession() {
        var hidden = false
        var closed = false
        compose.setContent {
            EngineTheme {
                EnginePluginShelf(
                    plugins = listOf(ShelfAlpha),
                    activeId = "alpha",
                    recentIds = emptyList(),
                    onRun = {},
                    onOpen = {},
                    onHide = { hidden = true },
                    onClose = { closed = true },
                )
            }
        }

        compose.onNodeWithTag("bmo-plugin-shelf-hide").performClick()
        assertTrue(hidden)
        compose.onNodeWithTag("bmo-plugin-shelf-close").performClick()
        assertTrue(closed)
    }
}
