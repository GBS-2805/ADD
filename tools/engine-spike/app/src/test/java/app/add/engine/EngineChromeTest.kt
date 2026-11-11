package app.add.engine

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 7 shell rules: labelled icon actions, a titled/dismissible sheet, selected
 * alignment semantics, a keyboard order of stage → toolbar → sheet, 48dp targets and
 * a reduced-motion path.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineChromeTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun toolbarIconsAreLabelledAndMeetTheMinimumTarget() {
        val doc = EngineDocument()
        compose.setContent {
            EngineTheme {
                Box(androidx.compose.ui.Modifier.size(411.dp, 120.dp)) {
                    StudioTopBar(
                        doc = doc,
                        onBack = {},
                        onDocs = {},
                        onExport = {},
                        onExportScale = {},
                        onInfo = {},
                        onLegend = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithContentDescription("Undo").assertExists()
        compose.onNodeWithContentDescription("Redo").assertExists()
        compose.onNodeWithContentDescription("More").assertExists()
        compose.onNodeWithContentDescription("EXPORT").assertExists()
        compose.onNodeWithContentDescription("EXPORT options").assertExists()

        listOf(
            "bar-back",
            "bar-undo",
            "bar-redo",
            "bar-more",
            "split-export-main",
            "split-export-options",
        ).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun sheetAnnouncesItsTitleAndCanBeDismissedFromAccessibilityActions() {
        var dismissed = false
        val doc = EngineDocument()
        val title = doc.selected?.name ?: "The page"
        compose.setContent {
            EngineTheme {
                Box(androidx.compose.ui.Modifier.size(411.dp, 700.dp)) {
                    InspectorSheet(
                        doc = doc,
                        handle = SceneHandle(),
                        tab = StudioTab.Style,
                        onTab = {},
                        onIcons = {},
                        onHistory = {},
                        onClose = { dismissed = true },
                        onMask = {},
                        onTurn3D = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("inspector-sheet")
            .assertExists()
        compose.onNodeWithContentDescription("$title sheet").assertExists()
        compose.onNodeWithTag("inspector-sheet")
            .performCustomAccessibilityActionWithLabel("Dismiss $title")
        assertTrue(dismissed)
    }

    @Test
    fun alignmentSegmentsExposeAndMoveTheSelectedState() {
        val doc = EngineDocument()
        doc.addText()
        compose.setContent {
            EngineTheme {
                Box(androidx.compose.ui.Modifier.size(411.dp, 900.dp)) {
                    Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                        val layer = doc.selected as TextLayer
                        TextInspector(doc, layer)
                    }
                }
            }
        }

        compose.onNodeWithTag("align-left").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("align-right").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(TextAlign.Right, (doc.selected as TextLayer).align)
        compose.onNodeWithTag("align-right").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("align-left").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun layerTreeExpandsGroupsAndKeepsSelectionSemantics() {
        val doc = EngineDocument()
        doc.addText()
        val text = doc.selected as TextLayer
        doc.addVector()
        val vector = doc.selected as VectorLayer
        doc.select(text.id, additive = true)
        val groupId = doc.groupSelection() ?: error("group was not created")

        compose.setContent {
            EngineTheme {
                Box(androidx.compose.ui.Modifier.size(411.dp, 700.dp)) {
                    LayersTab(doc)
                }
            }
        }

        compose.onNodeWithTag("layer-$groupId").assertIsSelected()
        compose.onNodeWithTag("layer-expand-$groupId").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("layer-${text.id}").assertExists()
        compose.onNodeWithTag("layer-${vector.id}").assertExists()

        compose.onNodeWithTag("layer-${text.id}").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("layer-${text.id}").assertIsSelected()
    }

    @Test
    fun aNewLayerIsTheSelectedTreeRow() {
        val doc = EngineDocument()
        doc.addText()
        val id = (doc.selected as TextLayer).id
        assertEquals(setOf(id), doc.selectedIds)

        compose.setContent {
            EngineTheme {
                Box(androidx.compose.ui.Modifier.size(411.dp, 700.dp)) {
                    LayersTab(doc)
                }
            }
        }

        compose.onNodeWithTag("layer-$id").assertExists().assertIsSelected()
    }

    @Test
    fun reducedMotionReadsTheSystemAnimationScale() {
        val resolver = context.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var reduced = false
            compose.setContent {
                EngineTheme {
                    reduced = prefersReducedMotion()
                }
            }
            compose.waitForIdle()
            assertTrue(reduced)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineChromeFocusTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun focusMovesFromTheStageToTheToolbarAndThenIntoTheOpenSheet() {
        compose.waitForIdle()
        compose.onNodeWithTag("engine-stage").requestFocus()
        compose.onNodeWithTag("engine-stage").assertIsFocused()

        compose.onNodeWithTag("engine-stage").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("engine-toolbar").assertIsFocused()

        // Open the real inspector from the Create flow, then continue the same
        // keyboard traversal. The toolbar's explicit next target is the sheet.
        compose.onNodeWithTag("engine-artboard").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.9f, height * 0.7f))
        }
        compose.onNodeWithTag("studio-peek").performClick()
        compose.onNodeWithTag("engine-add").performClick()
        compose.onNodeWithTag("create-type").performClick()
        compose.onNodeWithTag("notice-inspect").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("engine-toolbar").requestFocus()
        compose.onNodeWithTag("engine-toolbar").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("inspector-sheet").assertIsFocused()
    }
}
