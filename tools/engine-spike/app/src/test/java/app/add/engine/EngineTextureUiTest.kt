package app.add.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The texture shelf is part of the real segmented inspector, not a detached demo.
 * These tests exercise the actual composables with the document model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineTextureUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inspectorOffersTextureAsASegmentedSection() {
        val doc = EngineDocument()
        compose.setContent {
            EngineTheme {
                Box(Modifier.size(411.dp, 760.dp)) {
                    InspectorSheet(
                        doc = doc,
                        handle = SceneHandle(),
                        tab = StudioTab.Texture,
                        onTab = {},
                        onIcons = {},
                        onHistory = {},
                        onClose = {},
                        onMask = {},
                        onTurn3D = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("tab-texture").assertExists()
        compose.onNodeWithTag("texture-family-brick").assertExists()
        compose.onNodeWithContentDescription("Apply BRICK texture").assertExists()
    }

    @Test
    fun pickingAFamilyAppliesItAtTheDefaultSeedWith48dpTargets() {
        val doc = EngineDocument()
        compose.setContent { EngineTheme { TextureTab(doc) } }

        compose.onNodeWithTag("texture-family-brick")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(TextureFamily.Brick, doc.texture?.family)
        assertEquals(TexturePresets.seedFor(TextureFamily.Brick), doc.texture?.seed)
        compose.onNodeWithTag("texture-family-brick").assertIsSelected()
    }

    @Test
    fun seedOpacityAndRemoveControlsMutateTheLiveDocument() {
        val doc = EngineDocument()
        doc.applyTexture(TextureFamily.WoodGrain)
        val seed = requireNotNull(doc.texture).seed
        compose.setContent { EngineTheme { TextureTab(doc) } }

        compose.onNodeWithTag("texture-seed-next").performClick()
        assertEquals(seed + 1, doc.texture?.seed)

        compose.onNodeWithContentDescription("Texture opacity").assertExists()
        compose.onNodeWithContentDescription("Texture pattern scale").assertExists()

        compose.onNodeWithTag("texture-remove").performScrollTo().performClick()
        assertNull(doc.texture)
    }
}
