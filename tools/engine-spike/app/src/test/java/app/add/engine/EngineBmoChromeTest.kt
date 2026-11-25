package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
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
import androidx.test.espresso.Espresso
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
    fun pageChipAndSelectionRailKeep48dpTargets() {
        val doc = EngineDocument().apply { addText() }
        compose.setContent {
            EngineTheme {
                Column(Modifier.size(411.dp, 240.dp)) {
                    BmoPageChip(
                        doc = doc,
                        onBack = {},
                        onDocuments = {},
                        onHideChrome = {},
                        onInfo = {},
                        onCommands = {},
                        onToggleSafeArea = {},
                    )
                    BmoSelectionRail(
                        doc = doc,
                        onStyle = {},
                        onEffects = {},
                        onLayers = {},
                        onDone = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("bmo-page-chip")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        listOf(
            "object-style",
            "object-effects",
            "object-texture",
            "object-layers",
            "object-done",
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun zoomPillKeeps48dpTargets() {
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 100.dp)) {
                    ZoomPill(zoom = 1f, onZoom = {}, onReset = {})
                }
            }
        }

        listOf("zoom-out", "zoom-reset", "zoom-in").forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
    fun narrowDockStacksActionsBelowTheTools() {
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(360.dp, 140.dp)) {
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

        // At 360dp one row cannot hold five tools plus the three page actions
        // without the fifth tool sliding under the scroll edge, so the dock must
        // stack them instead of clipping a label.
        compose.onNodeWithTag("bmo-tool-sticker").assertIsDisplayed()
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
        val sticker = compose.onNodeWithTag("bmo-tool-sticker").getBoundsInRoot()
        val add = compose.onNodeWithTag("bmo-add").getBoundsInRoot()
        assertTrue("the page actions start below the tool row", add.top >= sticker.bottom)
    }

    @Test
    fun pageChipOpensDocumentsAndHide() {
        var documents = 0
        var hidden = 0
        var safeArea = 0
        val doc = EngineDocument()
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 120.dp)) {
                    BmoPageChip(
                        doc = doc,
                        onBack = {},
                        onDocuments = { documents++ },
                        onHideChrome = { hidden++ },
                        onInfo = {},
                        onCommands = {},
                        onToggleSafeArea = {
                            safeArea++
                            doc.safeArea = !doc.safeArea
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("bmo-page-chip").performClick()
        listOf(
            "bmo-menu-documents",
            "bmo-menu-actions",
            "bmo-menu-show-safe-area",
            "bmo-menu-hide-chrome",
            "bmo-menu-back",
            "bmo-menu-about",
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Documents").performClick()
        assertEquals(1, documents)

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Hide chrome").performClick()
        assertEquals(1, hidden)

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Show safe area").performClick()
        assertEquals(1, safeArea)

        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.onNodeWithText("Hide safe area").assertExists()
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
    fun pageChipMenuDismissesOnBackWithoutLeavingTheEditor() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-page-chip").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Show safe area").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Show safe area").assertDoesNotExist()
        compose.onNodeWithTag("bmo-tool-dock").assertExists()
    }

    @Test
    fun backClosesSurfaceBeforeLeavingTheEditor() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-add").performClick()
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
    fun editorChromeKeeps48dpHitTargets() {
        compose.waitForIdle()
        compose.onNodeWithTag("bmo-page-chip")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        listOf(
            "object-style",
            "object-effects",
            "object-texture",
            "object-layers",
            "object-done",
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }

        compose.onNodeWithTag("bmo-add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("create-dismiss")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        compose.onNodeWithTag("create-type").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notice-inspect")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("notice-done")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        compose.onNodeWithTag("notice-inspect").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sheet-history")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("sheet-done")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        listOf("tab-style", "tab-effects", "tab-texture", "tab-layers").forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }

        compose.onNodeWithTag("tab-effects").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fx-surprise")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("fx-master-switch")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("fx-switch-grade")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        compose.onNodeWithText("Grade").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fx-fine-tune")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun selectionRailOpensTheInspectorWithoutLeavingTheEditor() {
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
