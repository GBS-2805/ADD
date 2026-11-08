package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementTest {
    @Test
    fun typedBoundsAreClampedToAGrowablePage() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.setBounds(-4f, -4f, 40f, 40f)
        val layer = doc.selected!!
        assertEquals(-0.5f, layer.x, 0.001f)
        assertEquals(-0.5f, layer.y, 0.001f)
        assertEquals(1.6f, layer.w, 0.001f)
    }

    @Test
    fun nudgeMovesByArtboardPixels() {
        val doc = EngineDocument()
        doc.select("vector-star")
        val before = doc.selected!!.x
        doc.nudge(10.8f, 0f)
        assertEquals(before + 0.01f, doc.selected!!.x, 0.0001f)
    }

    @Test
    fun anyRowMovesNotJustTheSelection() {
        val doc = EngineDocument()
        val id = doc.layers.first().id
        val start = doc.layers.indexOfFirst { it.id == id }
        doc.moveLayer(id, 2)
        assertEquals(start + 2, doc.layers.indexOfFirst { it.id == id })
    }
}
