package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCodecTest {
    private fun glyph() = IconGlyph(
        id = "test/box",
        name = "box",
        brandSlug = "test",
        brandLabel = "Test",
        style = "Line",
        category = "Symbols",
        viewBox = listOf(0f, 0f, 24f, 24f),
        paths = listOf(
            IconPathSpec(
                d = "M0 0h24v24H0z",
                fill = "currentColor",
                stroke = null,
                strokeWidth = 1f,
                opacity = 1f,
                rule = "nonzero",
                matrix = floatArrayOf(1f, 0f, 0f, 1f, 2f, 3f),
            )
        ),
    )

    @Test
    fun everyLayerKindSurvivesTheRoundTrip() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.setRotation(42f)
        doc.setScaleToWidth(0.5f)
        doc.addText()
        doc.setText("HELLO\nWORLD")
        doc.addIcon(glyph())
        doc.select("model-gem")
        doc.setModelScale(1.3f)

        val encoded = DocumentCodec.encode(doc.layers, doc.selectedId, 1.25f)
        val restored = DocumentCodec.decode(encoded)

        assertNotNull(restored)
        val layers = restored!!.layers
        assertEquals(doc.layers.size, layers.size)
        assertEquals(doc.layers.map { it.id }, layers.map { it.id })
        assertEquals(1.25f, restored.zoom, 0.0001f)

        val vector = layers.first { it.id == "vector-star" } as VectorLayer
        assertEquals(0.5f, vector.w, 0.0005f)
        assertEquals(42f, vector.rotation, 0.0005f)
        assertEquals(10, vector.network.points.size)
        assertEquals(10, vector.network.segments.size)
        assertEquals(1, vector.network.regions.size)

        val text = layers.filterIsInstance<TextLayer>().first { it.text.startsWith("HELLO") }
        assertEquals("HELLO\nWORLD", text.text)
        assertEquals(58f, text.fontSize, 0.0005f)

        val icon = layers.filterIsInstance<IconLayer>().first()
        assertEquals("test/box", icon.glyph.id)
        assertEquals(1, icon.glyph.paths.size)
        assertEquals(6, icon.glyph.paths.first().matrix!!.size)

        val model = layers.filterIsInstance<ModelLayer>().first { it.id == "model-gem" }
        assertEquals(1.3f, model.scaleToUnits, 0.0005f)
        assertEquals(ModelAsset.Gem, model.asset)
    }

    @Test
    fun aFutureSchemaIsRefusedRatherThanGuessed() {
        assertNull(DocumentCodec.decode("""{"schema":2,"layers":[]}"""))
    }

    @Test
    fun corruptTextIsRefused() {
        assertNull(DocumentCodec.decode("this is not json"))
    }

    @Test
    fun aDocumentWithNoUsableLayerIsRefused() {
        assertNull(DocumentCodec.decode("""{"schema":1,"layers":[{"kind":"text","name":"X"}]}"""))
    }

    @Test
    fun theDocumentNamesTheControlThatChangedIt() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.duplicateSelected()
        assertEquals("DUP", doc.action)
        assertTrue(doc.actionId > 0)
        assertTrue(doc.undoDepth > 0)

        doc.undo()
        assertEquals("UNDO", doc.action)
        assertEquals(0, doc.undoDepth)
        assertEquals(1, doc.redoDepth)
    }
}
