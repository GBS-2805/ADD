package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineBmoChromeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toolDockHasLabelled48dpTargets() {
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 120.dp)) {
                    BmoToolDock(
                        active = Tool.Select,
                        onTool = {},
                        onAdd = {},
                        onCommands = {},
                        onPlugins = {},
                        onPluginLongPress = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("bmo-tool-dock").assertExists()
        listOf(
            "bmo-tool-select",
            "bmo-tool-vector",
            "bmo-tool-type",
            "bmo-tool-icon",
            "bmo-tool-sticker",
            "bmo-tool-model",
            "bmo-add",
            "bmo-actions",
            "bmo-plugins",
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Select").assertExists()
        compose.onNodeWithContentDescription("Add layer").assertExists()
        compose.onNodeWithContentDescription("Actions").assertExists()
        compose.onNodeWithContentDescription("Plugins").assertExists()
    }

    @Test
    fun pageChipOpensDocumentsAndHide() {
        var documents = 0
        var hidden = 0
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 120.dp)) {
                    BmoPageChip(
                        doc = EngineDocument(),
                        onBack = {},
                        onDocuments = { documents++ },
                        onHideChrome = { hidden++ },
                        onInfo = {},
                        onCommands = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Documents").performClick()
        assertEquals(1, documents)

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Hide chrome").performClick()
        assertEquals(1, hidden)
    }

    @Test
    fun canvasModeLeavesOnlyTheRestoreHandle() {
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 800.dp)) {
                    BmoCanvasRestore(onRestore = {})
                }
            }
        }

        compose.onNodeWithTag("bmo-restore")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Show editor controls").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineBmoActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun canvasTapEntersPeekAndPeekTapRestoresFull() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-tool-dock").assertExists()

        compose.onNodeWithTag("engine-artboard").performTouchInput {
            click(Offset(width * 0.9f, height * 0.72f))
        }
        compose.waitForIdle()

        compose.onNodeWithTag("bmo-page-chip").assertExists()
        compose.onNodeWithTag("bmo-tool-dock").assertDoesNotExist()

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-tool-dock").assertExists()
    }

    @Test
    fun canvasModeLeavesOnlyTheRestoreHandle() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Hide chrome").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bmo-restore").assertExists()
        compose.onNodeWithTag("bmo-tool-dock").assertDoesNotExist()
        compose.onNodeWithTag("bmo-page-chip").assertDoesNotExist()

        compose.onNodeWithTag("bmo-restore").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-tool-dock").assertExists()
    }

    @Test
    fun backClosesSurfaceBeforeLeavingTheEditor() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-add").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("create-type").assertExists()

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("create-type").assertDoesNotExist()
        compose.onNodeWithTag("bmo-tool-dock").assertExists()
    }

    @Test
    fun selectionRailOpensTheInspectorWithoutLeavingTheEditor() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-add").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("create-type").assertExists().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bmo-selection-rail").assertExists()
        compose.onNodeWithTag("object-style").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("inspector-sheet").assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("bmo-tool-dock")).fetchSemanticsNodes().size == 1)
    }
}

private fun hasTestTag(tag: String) =
    androidx.compose.ui.test.hasTestTag(tag)
