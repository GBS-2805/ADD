package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonePluginTest {

    @Test
    fun applyingToneChangesTheSelectedFill() {
        val doc = EngineDocument()
        doc.addText()
        val before = (doc.selected as TextLayer).color

        assertTrue(applyTone(doc, "#5B2A4B"))

        val after = (doc.selected as TextLayer).color
        assertEquals(0xFF5B2A4B.toInt(), after)
        assertTrue(before != after)
    }

    @Test
    fun applyingToneIsUndoable() {
        val doc = EngineDocument()
        doc.addText()
        val before = (doc.selected as TextLayer).color

        assertTrue(applyTone(doc, "#F5ECD8"))
        assertTrue((doc.selected as TextLayer).color != before)

        doc.undo()

        assertEquals(before, (doc.selected as TextLayer).color)
    }

    @Test
    fun toneIsDisabledWithoutATextOrVectorSelection() {
        val doc = EngineDocument()
        doc.addImage("/tmp/tone-disabled.png")
        assertFalse(toneEnabled(doc))
        assertFalse(applyTone(doc, "#5B2A4B"))
    }
}
