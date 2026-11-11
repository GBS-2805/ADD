package app.add.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The document stores a texture recipe, not pixels. These tests prove that the
 * recipe survives the real save/load path and that every bad reference has a
 * clean paper fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextureDocumentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        DocumentStore.clear(context)
        TextureRenderer.clear()
    }

    @Test
    fun textureSurvivesSaveLoadAndRendersTheSamePixels() {
        val doc = EngineDocument()
        doc.applyTexture(TextureFamily.Marble, seed = 73_331)
        val before = requireNotNull(doc.texture)
        assertTrue(DocumentStore.save(context, doc))

        val restored = DocumentStore.restore(context)
        assertNotNull(restored)
        assertEquals(before, restored!!.texture)

        val after = requireNotNull(restored.texture)
        val a = TexturePresets.render(before.family, before.seed, 120, 150, before.scale)
        val b = TexturePresets.render(after.family, after.seed, 120, 150, after.scale)
        assertArrayEquals(a.argb, b.argb)
    }

    @Test
    fun unknownFamilyLoadsThePageWithCleanPaperAndAWarning() {
        val bad = documentWithTexture(
            JSONObject()
                .put("family", "not-a-real-material")
                .put("seed", 12)
                .put("opacity", 0.3)
                .put("scale", 1.0),
        )
        val decoded = DocumentCodec.decode(bad)
        assertNotNull(decoded)
        assertNull(decoded!!.texture)
        assertNotNull(decoded.textureWarning)
        val doc = EngineDocument()
        doc.replaceAll(decoded)
        assertNull(doc.texture)
        assertTrue(doc.status.contains("TEXTURE DROPPED"))
    }

    @Test
    fun invalidOpacityAndMissingSeedBothFallBackWithoutThrowing() {
        val invalidOpacity = documentWithTexture(
            JSONObject()
                .put("family", "brick")
                .put("seed", 2)
                .put("opacity", 9.0)
                .put("scale", 1.0),
        )
        val missingSeed = documentWithTexture(
            JSONObject()
                .put("family", "brick")
                .put("opacity", 0.3)
                .put("scale", 1.0),
        )
        listOf(invalidOpacity, missingSeed).forEach { json ->
            val decoded = DocumentCodec.decode(json)
            assertNotNull(decoded)
            assertNull(decoded!!.texture)
            assertNotNull(decoded.textureWarning)
        }
    }

    @Test
    fun aCorruptTextureReferenceIsDroppedNotFatal() {
        val corrupt = documentWithTexture("marble")
        val decoded = DocumentCodec.decode(corrupt)
        assertNotNull(decoded)
        assertEquals(seedLayers().size, decoded!!.layers.size)
        assertNull(decoded.texture)
        assertNotNull(decoded.textureWarning)
    }

    @Test
    fun applyingAndUndoingATextureRestoresThePreviousRecipe() {
        val doc = EngineDocument()
        doc.applyTexture(TextureFamily.Brick)
        val brick = requireNotNull(doc.texture)
        doc.setTextureSeed(brick.seed + 5)
        assertEquals(brick.seed + 5, doc.texture?.seed)
        doc.undo()
        assertEquals(brick, doc.texture)
        doc.removeTexture()
        assertNull(doc.texture)
        doc.undo()
        assertEquals(brick, doc.texture)
    }

    private fun documentWithTexture(texture: Any): String {
        val root = JSONObject(
            DocumentCodec.encode(
                layers = seedLayers(),
                selectedId = "vector-star",
                zoom = 1f,
            )
        )
        root.put("texture", texture)
        return root.toString()
    }
}
