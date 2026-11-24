package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 1's additive-selection contract, which the layers panel now reaches through
 * its MULTI toggle. The model already had the set; these tests pin the primary
 * hand-off that the panel depends on.
 */
class SelectionTest {

    private fun text(id: String, x: Float) = TextLayer(
        id = id,
        name = id.uppercase(),
        x = x,
        y = 0.1f,
        w = 0.3f,
        h = 0.2f,
        text = id.uppercase(),
        fontSize = 40f,
        lineHeight = 1f,
        color = 0xFF11231C.toInt(),
    )

    private fun document() = EngineDocument(
        listOf(text("a", 0.1f), text("b", 0.3f), text("c", 0.6f))
    )

    @Test
    fun additiveSelectionTogglesAndHandsThePrimaryToARemainingLayer() {
        val doc = document()
        doc.select("a")
        doc.select("b", additive = true)
        assertEquals(setOf("a", "b"), doc.selectedIds)
        assertEquals("b", doc.selectedId)

        doc.select("b", additive = true)
        assertEquals(setOf("a"), doc.selectedIds)
        assertEquals("a", doc.selectedId)
    }

    @Test
    fun additiveSelectionNeverLeavesNothingSelected() {
        val doc = document()
        doc.select("a")
        doc.select("a", additive = true)
        assertEquals(setOf("a"), doc.selectedIds)
        assertEquals("a", doc.selectedId)
    }

    @Test
    fun aPlainTapReplacesTheSelection() {
        val doc = document()
        doc.select("a")
        doc.select("b", additive = true)
        doc.select("c")
        assertEquals(setOf("c"), doc.selectedIds)
        assertEquals("c", doc.selectedId)
    }
}
