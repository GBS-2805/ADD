package app.add.engine

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineCommandSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun command(
        id: String,
        label: String,
        group: String = "File",
        keywords: String = "",
        enabled: Boolean = true,
        run: () -> Unit = {},
    ) = EngineCommand(id, label, group, "•", keywords, enabled, run)

    private val commands = listOf(
        command("undo", "Undo", keywords = "history back"),
        command("add.vector", "Add vector", group = "Create", keywords = "shape pen"),
        command("delete", "Delete layer", group = "Edit", keywords = "remove erase", enabled = false),
    )

    @Test
    fun filtersByLabelAndKeyword() {
        compose.setContent {
            EngineTheme {
                EngineCommandSheet(commands = commands, onRun = {}, onClose = {})
            }
        }

        compose.onNodeWithTag("bmo-command-undo").assertExists()
        compose.onNodeWithTag("bmo-command-add.vector").assertExists()

        compose.onNodeWithTag("bmo-command-search").performTextInput("vector")
        compose.waitForIdle()

        compose.onNodeWithTag("bmo-command-add.vector").assertExists()
        compose.onNodeWithTag("bmo-command-undo").assertDoesNotExist()

        compose.onNodeWithTag("bmo-command-search").performTextClearance()
        compose.onNodeWithTag("bmo-command-search").performTextInput(" remove ")
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-command-delete").assertExists()
        compose.onNodeWithTag("bmo-command-add.vector").assertDoesNotExist()
    }

    @Test
    fun groupsCommandsAndRunsTheSelectedCommand() {
        var ran: String? = null
        compose.setContent {
            EngineTheme {
                EngineCommandSheet(commands = commands, onRun = { ran = it.id }, onClose = {})
            }
        }

        compose.onNodeWithText("FILE").assertExists()
        compose.onNodeWithText("CREATE").assertExists()
        compose.onNodeWithTag("bmo-command-add.vector").performClick()
        assertEquals("add.vector", ran)
    }

    @Test
    fun showsAnEmptyStateForNoMatches() {
        compose.setContent {
            EngineTheme {
                EngineCommandSheet(commands = commands, onRun = {}, onClose = {})
            }
        }

        compose.onNodeWithTag("bmo-command-search").performTextInput("zzzz")
        compose.waitForIdle()

        compose.onNodeWithText("No actions match.").assertExists()
        compose.onNodeWithTag("bmo-command-undo").assertDoesNotExist()
    }

    @Test
    fun pluginCommandOpensItsPlugin() {
        var opened: String? = null
        val pluginCommands = enginePluginCommands(EnginePlugins.builtIn) { opened = it }
        compose.setContent {
            EngineTheme {
                EngineCommandSheet(
                    commands = pluginCommands,
                    onRun = { it.run() },
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("bmo-command-prism.open").performClick()

        assertEquals("prism", opened)
    }

    @Test
    fun disabledCommandsCannotRun() {
        var ran = 0
        compose.setContent {
            EngineTheme {
                EngineCommandSheet(
                    commands = listOf(command("delete", "Delete layer", enabled = false) { ran++ }),
                    onRun = { it.run() },
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("bmo-command-delete").assertIsNotEnabled()
        compose.onNodeWithTag("bmo-command-delete").performClick()
        assertEquals(0, ran)
    }
}
