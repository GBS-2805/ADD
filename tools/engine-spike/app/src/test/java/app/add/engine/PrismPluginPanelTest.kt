package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PrismPluginPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private fun scope(doc: EngineDocument, onToast: (String) -> Unit = {}): EnginePluginScope =
        EnginePluginScope(
            doc = doc,
            effects = EngineDocumentEffects(doc),
            close = {},
            hide = {},
            toast = onToast,
        )

    private fun showPanel(doc: EngineDocument, onToast: (String) -> Unit = {}) {
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    PrismPlugin.Panel(scope(doc, onToast))
                }
            }
        }
    }

    @Test
    fun exposesTargetsPresetsAnd48dpActions() {
        val doc = EngineDocument().apply { addText() }
        showPanel(doc)

        compose.onNodeWithTag("prism-target-artboard").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("prism-target-layer").assertHeightIsAtLeast(48.dp)
        FieldPresets.all.forEach { preset ->
            compose.onNodeWithTag(prismPresetTag(preset.name))
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("prism-clear").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun layerTargetIsDisabledWithoutAPaintableSelection() {
        val doc = EngineDocument().apply { addImage("/tmp/prism-panel-disabled.png") }
        showPanel(doc)

        compose.onNodeWithTag("prism-target-layer").assertIsNotEnabled()
        compose.onNodeWithTag("prism-target-artboard").assertIsSelected()
        assertFalse(EngineDocumentEffects(doc).canTarget(EngineEffectTarget.Layer))
    }

    @Test
    fun tappingAPresetAppliesItAndShowsTheSelectedState() {
        val doc = EngineDocument().apply { addText() }
        var toast = ""
        showPanel(doc) { toast = it }
        val preset = FieldPresets.all.first()

        compose.onNodeWithTag("prism-target-layer").performClick()
        compose.onNodeWithTag(prismPresetTag(preset.name)).performClick()
        compose.waitForIdle()

        assertEquals(preset.field.normalized(), doc.fieldFor(FieldTarget.Layer))
        compose.onNodeWithTag(prismPresetTag(preset.name)).assertIsSelected()
        assertTrue(toast.contains("PRISM"))
    }

    @Test
    fun targetSelectorRoutesThePresetToTheChosenSurface() {
        val doc = EngineDocument().apply { addText() }
        showPanel(doc)
        val layerPreset = FieldPresets.all[1]
        val artboardPreset = FieldPresets.all[2]

        compose.onNodeWithTag("prism-target-layer").performClick()
        compose.onNodeWithTag(prismPresetTag(layerPreset.name)).performClick()
        compose.waitForIdle()
        assertEquals(layerPreset.field.normalized(), doc.fieldFor(FieldTarget.Layer))

        compose.onNodeWithTag("prism-target-artboard").performClick()
        compose.onNodeWithTag(prismPresetTag(artboardPreset.name)).performClick()
        compose.waitForIdle()
        assertEquals(artboardPreset.field.normalized(), doc.fieldFor(FieldTarget.Background))
    }
}
