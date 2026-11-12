package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldPaintTest {

    @Test
    fun backgroundAndLayerFieldsSurviveSchemaSix() {
        val doc = EngineDocument()
        val preset = FieldPresets.all.first().field
        doc.setFieldPreset(FieldTarget.Background, preset)
        doc.setFieldDistortion(FieldTarget.Background, 0.72f)
        doc.setFieldSpot(FieldTarget.Background, 1, 0.63f, 0.41f)

        doc.select("vector-star")
        doc.setFieldPreset(FieldTarget.Layer, preset)
        doc.setFieldBand(FieldTarget.Layer, 0, 0.48f)

        val restored = DocumentCodec.decode(
            DocumentCodec.encode(
                doc.layers,
                doc.selectedId,
                doc.zoom,
                backgroundField = doc.backgroundField,
            )
        )
        assertNotNull(restored)
        val background = restored!!.backgroundField
        assertNotNull(background)
        assertEquals(4, background!!.colors.size)
        assertEquals(0.72f, background.distortion, 0.0005f)
        assertEquals(0.63f, background.colors[1].x, 0.0005f)

        val vector = restored.layers.first { it.id == "vector-star" } as VectorLayer
        val layerField = (vector.paint as PaintSpec.Field).field
        assertEquals(0.48f, layerField.colors.first().band, 0.0005f)
    }

    @Test
    fun onePresetIsOneUndoStepAndUndoRemovesTheField() {
        val doc = EngineDocument()
        doc.setFieldPreset(FieldTarget.Background, FieldPresets.all[1].field)
        assertNotNull(doc.backgroundField)
        assertTrue(doc.undoDepth >= 1)

        doc.undo()
        assertNull(doc.backgroundField)
    }

    @Test
    fun aFieldKeepsTwoColoursAndEightAtMost() {
        val field = FieldSpec(listOf(
            FieldColor(0xFFFF0000.toInt(), 0.2f, 0.2f),
            FieldColor(0xFF0000FF.toInt(), 0.8f, 0.8f),
        ))
        assertEquals(2, field.removeColor(0).colors.size)
        var eight = field
        repeat(10) { eight = eight.addColor(0xFF00FF00.toInt()) }
        assertEquals(8, eight.colors.size)
    }

    @Test
    fun theSoftwareFieldIsOpaqueDeterministicAndHasColourVariation() {
        val field = FieldPresets.all[3].field
        val first = FieldRenderer.bitmap(field, 96, 54, phase = 1.5f)
        val second = FieldRenderer.bitmap(field, 96, 54, phase = 1.5f)
        assertSame("an unchanged field must reuse its bitmap", first, second)

        val pixels = IntArray(first.width * first.height)
        first.getPixels(pixels, 0, first.width, 0, 0, first.width, first.height)
        assertTrue("field must not contain transparent pixels", pixels.all { (it ushr 24) == 0xFF })
        assertTrue("field has no colour variation", pixels.toSet().size > 24)

        val other = FieldRenderer.bitmap(field, 96, 54, phase = 12f)
        val otherPixels = IntArray(other.width * other.height)
        other.getPixels(otherPixels, 0, other.width, 0, 0, other.width, other.height)
        assertFalse("phase must change the animated field", pixels.contentEquals(otherPixels))
    }
}
