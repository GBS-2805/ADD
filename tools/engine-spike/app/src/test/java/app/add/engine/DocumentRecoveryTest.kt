package app.add.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Task 4: history must carry the whole document, selection must not be an edit,
 * autosave must be atomic, and a newer schema must refuse a layer it cannot read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentRecoveryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun document(): EngineDocument = EngineDocument()

    @Test
    fun changingSelectionAddsNoUndoEntry() {
        val doc = document()
        doc.addText()
        val depth = doc.undoDepth

        doc.select("vector-star")
        doc.select(doc.layers.first { it is TextLayer }.id)

        assertEquals(depth, doc.undoDepth)
        assertTrue(doc.undoDepth > 0)
    }

    @Test
    fun changingSelectionKeepsTheRedoStack() {
        val doc = document()
        val before = doc.layers.size
        doc.addText()
        val after = doc.layers.size
        doc.undo()
        assertTrue(doc.redoDepth > 0)

        doc.select("vector-star")

        assertTrue(doc.redoDepth > 0)
        doc.redo()
        assertEquals(after, doc.layers.size)
        assertEquals(before + 1, doc.layers.size)
    }

    @Test
    fun groupingAlreadyGroupedLayersIsANoOp() {
        val doc = document()
        doc.addText()
        val textId = doc.layers.last().id
        doc.select("vector-star")
        doc.select(textId, additive = true)
        val groupId = doc.groupSelection()!!
        val depth = doc.undoDepth

        doc.select(groupId)

        assertNull(doc.groupSelection())
        assertEquals(depth, doc.undoDepth)
        assertTrue(doc.layers.findLayer(groupId) is GroupLayer)
    }

    @Test
    fun ungroupingWithNothingSelectedIsANoOp() {
        val doc = document()
        doc.clearSelection()
        val depth = doc.undoDepth

        assertFalse(doc.ungroupSelection())
        assertEquals(depth, doc.undoDepth)
    }

    @Test
    fun undoRestoresTheEditingGroupAndSelection() {
        val doc = document()
        doc.addText()
        val textId = doc.layers.last().id
        doc.select("vector-star")
        doc.select(textId, additive = true)
        val groupId = doc.groupSelection()!!
        doc.enterGroup()
        val childId = doc.selectedId

        doc.beginGesture()
        doc.moveSelected(0.02f, 0.01f)
        doc.endGesture()
        doc.undo()

        assertEquals(groupId, doc.editingGroupId)
        assertEquals(childId, doc.selectedId)
        assertTrue(childId in doc.selectedIds)
    }

    @Test
    fun undoRestoresTheGroupedTreeAndSelection() {
        val doc = document()
        doc.addText()
        val textId = doc.layers.last().id
        doc.select("vector-star")
        doc.select(textId, additive = true)
        val groupId = doc.groupSelection()!!

        doc.undo()
        assertNull(doc.layers.findLayer(groupId))
        assertEquals(setOf("vector-star", textId), doc.selectedIds)
        assertNull(doc.editingGroupId)

        doc.redo()
        assertNotNull(doc.layers.findLayer(groupId) as? GroupLayer)
        assertEquals(setOf(groupId), doc.selectedIds)
    }

    @Test
    fun aBurstOfTypingIsOneUndoStep() {
        val doc = document()
        doc.addText()
        val textId = doc.layers.last { it is TextLayer }.id
        doc.select(textId)
        val before = (doc.selected as TextLayer).text
        val depth = doc.undoDepth
        EngineClock.nowMillis = { 1_700_000_000_000L }
        try {
            doc.setText("ONE")
            doc.setText("ONE MORE")
            doc.setText("ONE MORE LINE")
            assertEquals(depth + 1, doc.undoDepth)

            doc.undo()
            assertEquals(before, (doc.selected as TextLayer).text)
        } finally {
            EngineClock.nowMillis = { System.currentTimeMillis() }
        }
    }

    @Test
    fun historyIsCappedAtOneHundredSteps() {
        val doc = document()
        repeat(105) { doc.addText() }
        assertTrue(doc.undoDepth <= 100)
    }

    @Test
    fun aCrashBetweenTempWriteAndRenameKeepsTheLastGoodFile() {
        val doc = document()
        doc.rename("vector-star", "BASE")
        assertTrue(DocumentStore.save(context, doc))

        // A process killed before the rename leaves only a half temp file.
        File(context.filesDir, "document.json.tmp").writeText("{\"schema\":4,\"layers\":[")

        val restored = DocumentStore.restore(context)
        assertNotNull(restored)
        assertEquals("BASE", restored!!.layers.first { it.id == "vector-star" }.name)
    }

    @Test
    fun aCorruptMainFileFallsBackToTheLastGoodBackup() {
        val doc = document()
        doc.rename("vector-star", "FIRST")
        assertTrue(DocumentStore.save(context, doc))
        doc.rename("vector-star", "SECOND")
        assertTrue(DocumentStore.save(context, doc))
        File(context.filesDir, "document.json").writeText("not json at all")

        val restored = DocumentStore.restore(context)
        assertNotNull(restored)
        assertEquals("FIRST", restored!!.layers.first { it.id == "vector-star" }.name)
    }

    @Test
    fun aVersionThreeDocumentStillOpens() {
        val old = """
            {"schema":3,"layers":[{"kind":"text","id":"t","name":"T","x":0.1,"y":0.1,
            "w":0.3,"h":0.1,"text":"HI","fontSize":40,"lineHeight":1.0,"color":-1}]}
        """.trimIndent()
        val restored = DocumentCodec.decode(old)
        assertNotNull(restored)
        assertEquals("HI", (restored!!.layers.single() as TextLayer).text)
    }

    @Test
    fun aVersionFourFileWithAnUnknownLayerIsRefused() {
        val unknownTopLevel = """
            {"schema":4,"layers":[{"kind":"alien","id":"x","name":"X"}]}
        """.trimIndent()
        val unknownChild = """
            {"schema":4,"layers":[{"kind":"group","id":"g","name":"G","x":0,"y":0,
            "w":1,"h":1,"children":[{"kind":"alien","id":"x","name":"X"}]}]}
        """.trimIndent()

        assertNull(DocumentCodec.decode(unknownTopLevel))
        assertNull(DocumentCodec.decode(unknownChild))
    }
}
