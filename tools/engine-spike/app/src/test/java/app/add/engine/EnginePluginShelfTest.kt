package app.add.engine

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
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
    override val summary = "First plugin with swatch tools."
    override val commands = listOf(EnginePluginCommand("alpha.open", "Open Alpha", "swatch fill"))
    @Composable override fun Panel(scope: EnginePluginScope) { Text("ALPHA") }
}

private object ShelfBeta : EnginePlugin {
    override val id = "beta"
    override val name = "Beta"
    override val glyph = "B"
    override val summary = "Second plugin with vector tools."
    override val commands = listOf(EnginePluginCommand("beta.open", "Open Beta", "vector shape"))
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
    fun listsPrismAndRunsItFromTheShelf() {
        var ran: String? = null
        compose.setContent {
            EngineTheme {
                EnginePluginShelf(
                    plugins = EnginePlugins.builtIn,
                    activeId = null,
                    recentIds = emptyList(),
                    onRun = { ran = it },
                    onOpen = {},
                    onHide = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("bmo-plugin-prism").performClick()
        assertEquals("prism", ran)
    }

    @Test
    fun searchFiltersByCommandKeywordAndClears() {
        compose.setContent {
            EngineTheme {
                EnginePluginShelf(
                    plugins = listOf(ShelfAlpha, ShelfBeta),
                    activeId = null,
                    recentIds = emptyList(),
                    onRun = {},
                    onOpen = {},
                    onHide = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search plugin catalogue").assertExists()
        compose.onNodeWithTag("bmo-plugin-catalogue-search").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("bmo-plugin-catalogue-search").performTextInput("vector")

        compose.onNodeWithTag("bmo-plugin-alpha").assertDoesNotExist()
        compose.onNodeWithTag("bmo-plugin-beta").assertExists()
        compose.onNodeWithTag("bmo-plugin-catalogue-clear")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        compose.onNodeWithTag("bmo-plugin-alpha").assertExists()
        compose.onNodeWithTag("bmo-plugin-beta").assertExists()
        compose.onNodeWithTag("bmo-plugin-catalogue-clear").assertDoesNotExist()
    }

    @Test
    fun searchMatchesSummaryAndShowsAnEmptyState() {
        compose.setContent {
            EngineTheme {
                EnginePluginShelf(
                    plugins = listOf(ShelfAlpha, ShelfBeta),
                    activeId = null,
                    recentIds = emptyList(),
                    onRun = {},
                    onOpen = {},
                    onHide = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("bmo-plugin-catalogue-search").performTextInput("second")
        compose.onNodeWithTag("bmo-plugin-alpha").assertDoesNotExist()
        compose.onNodeWithTag("bmo-plugin-beta").assertExists()

        compose.onNodeWithTag("bmo-plugin-catalogue-search").performTextReplacement("zzz")
        compose.onNodeWithTag("bmo-plugin-catalogue-empty").assertExists()
        compose.onNodeWithTag("bmo-plugin-alpha").assertDoesNotExist()
        compose.onNodeWithTag("bmo-plugin-beta").assertDoesNotExist()
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
