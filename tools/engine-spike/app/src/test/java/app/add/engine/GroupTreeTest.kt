package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Task 2 acceptance cases. These run the real document, not just the matrix
 * helpers: grouping is a tree mutation in [EngineDocument], undo is the existing
 * snapshot stack, and every assertion is on artboard-space geometry.
 */
class GroupTreeTest {

    private fun text(id: String, x: Float, y: Float) = TextLayer(
        id = id,
        name = id.uppercase(),
        x = x,
        y = y,
        w = 0.3f,
        h = 0.2f,
        text = id.uppercase(),
        fontSize = 40f,
        lineHeight = 1f,
        color = 0xFF11231C.toInt(),
    )

    private fun document() = EngineDocument(
        listOf(
            text("a", 0.10f, 0.10f),
            text("b", 0.30f, 0.25f),
            text("c", 0.90f, 0.90f),
        )
    )

    private fun group(doc: EngineDocument): String {
        doc.select("a")
        doc.select("b", additive = true)
        return doc.groupSelection() ?: error("group failed")
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.0005f) {
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun groupingTwoLayersKeepsTheirAbsoluteCentres() {
        val doc = document()
        val beforeA = doc.worldCentre("a")!!
        val beforeB = doc.worldCentre("b")!!
        val groupId = group(doc)

        assertTrue(doc.layers.findLayer(groupId) is GroupLayer)
        assertNear(beforeA.first, doc.worldCentre("a")!!.first)
        assertNear(beforeA.second, doc.worldCentre("a")!!.second)
        assertNear(beforeB.first, doc.worldCentre("b")!!.first)
        assertNear(beforeB.second, doc.worldCentre("b")!!.second)
        assertEquals(2, doc.layers.size)
        assertEquals(4, doc.layers.flattenTree().size)
    }

    @Test
    fun movingAGroupCarriesItsChildren() {
        val doc = document()
        val groupId = group(doc)
        val before = doc.worldCentre("a")!!

        doc.updateLayer(groupId) { it.withBounds(it.x + 0.1f, it.y - 0.05f, it.w, it.h) }

        val after = doc.worldCentre("a")!!
        assertNear(before.first + 0.1f, after.first)
        assertNear(before.second - 0.05f, after.second)
    }

    @Test
    fun scalingAGroupCarriesItsChildrenWithoutRewritingThem() {
        val doc = document()
        val groupId = group(doc)
        val childBefore = doc.layers.findLayer("a") as TextLayer
        val centreBefore = doc.worldCentre("a")!!

        doc.updateLayer(groupId) { layer ->
            (layer as GroupLayer).copy(scaleX = 1.5f, scaleY = 0.8f)
        }

        val childAfter = doc.layers.findLayer("a") as TextLayer
        assertEquals(childBefore.x, childAfter.x, 0.0001f)
        assertEquals(childBefore.y, childAfter.y, 0.0001f)
        // The group centre is the scale anchor, so the child moves away from it.
        val centreAfter = doc.worldCentre("a")!!
        assertFalse(kotlin.math.abs(centreAfter.first - centreBefore.first) < 0.0001f &&
            kotlin.math.abs(centreAfter.second - centreBefore.second) < 0.0001f)
    }

    @Test
    fun ungroupingARotatedScaledGroupKeepsEveryChildCorner() {
        val doc = document()
        val groupId = group(doc)
        doc.updateLayer(groupId) { it.withRotation(23f) }
        doc.updateLayer(groupId) { layer ->
            (layer as GroupLayer).copy(scaleX = 1.4f, scaleY = 0.7f, skewX = 9f)
        }

        val before = doc.worldCorners("a")!!
        doc.select(groupId)
        assertTrue(doc.ungroupSelection())
        val after = doc.worldCorners("a")!!

        assertEquals(4, after.size)
        before.zip(after).forEach { (expected, actual) ->
            assertNear(expected.first, actual.first, 0.002f)
            assertNear(expected.second, actual.second, 0.002f)
        }
        // The skew that a non-uniform scale produced is not silently dropped.
        val released = doc.layers.findLayer("a") as TextLayer
        assertTrue(kotlin.math.abs(released.skewX) > 0.01f)
    }

    @Test
    fun tappingInsideAGroupSelectsTheGroupAndEnteringSelectsAChild() {
        val doc = document()
        val groupId = group(doc)
        val centre = doc.worldCentre("a")!!

        doc.clearSelection()
        assertEquals(groupId, doc.layerAt(centre.first, centre.second)?.id)

        doc.select(groupId)
        assertTrue(doc.enterGroup())
        assertEquals("a", doc.layerAt(centre.first, centre.second)?.id)
        assertTrue(doc.exitGroup())
        assertEquals(groupId, doc.selectedId)
    }

    @Test
    fun aRotatedAndSkewedGroupHitTestsIntoTheRightChild() {
        val doc = document()
        val groupId = group(doc)
        doc.updateLayer(groupId) { it.withRotation(35f) }
        doc.updateLayer(groupId) { layer ->
            if (layer is GroupLayer) layer.copy(skewX = 18f) else layer
        }
        doc.select(groupId)

        // Outside the group the pointer lands on the group node itself...
        val (gx, gy) = doc.worldCentre("b")!!
        assertEquals(groupId, doc.layerAt(gx, gy)?.id)

        // ...and inside it the inverse map picks the child under the pointer.
        doc.enterGroup()
        doc.select("b")
        val (bx, by) = doc.worldCentre("b")!!
        assertEquals("b", doc.layerAt(bx, by)?.id)
    }

    @Test
    fun movingAGroupCarriesItsChildrensPaths() {
        val doc = document()
        val groupId = group(doc)
        val centre = doc.worldCentre("a")!!
        doc.setMotionPath(
            "a",
            MotionPath(
                start = MotionPoint(centre.first, centre.second),
                c1 = MotionPoint(centre.first + 0.1f, centre.second - 0.05f),
                c2 = MotionPoint(centre.first + 0.2f, centre.second + 0.05f),
                end = MotionPoint(centre.first + 0.3f, centre.second),
            ),
        )

        val before = doc.motionPathInWorld("a")!!
        doc.updateLayer(groupId) { it.withBounds(it.x + 0.12f, it.y + 0.03f, it.w, it.h) }
        val after = doc.motionPathInWorld("a")!!

        assertNear(before.start.x + 0.12f, after.start.x)
        assertNear(before.start.y + 0.03f, after.start.y)
        assertNear(before.end.x + 0.12f, after.end.x)
        assertNear(before.end.y + 0.03f, after.end.y)
    }

    @Test
    fun draggingAChildInsideARotatedGroupFollowsThePointer() {
        val doc = document()
        val groupId = group(doc)
        doc.updateLayer(groupId) { it.withRotation(90f) }
        doc.select(groupId)
        doc.enterGroup()
        doc.select("a")
        val before = doc.worldCentre("a")!!

        doc.moveSelected(0.05f, 0f)

        val after = doc.worldCentre("a")!!
        assertNear(before.first + 0.05f, after.first, 0.003f)
        assertNear(before.second, after.second, 0.003f)
    }

    @Test
    fun editingTextInsideAGroupKeepsItsPathAnchored() {
        val doc = document()
        val groupId = group(doc)
        doc.select(groupId)
        doc.enterGroup()
        doc.select("a")
        val centre = doc.worldCentre("a")!!
        doc.setMotionPath(
            "a",
            MotionPath(
                start = MotionPoint(centre.first, centre.second),
                c1 = MotionPoint(centre.first + 0.05f, centre.second),
                c2 = MotionPoint(centre.first + 0.1f, centre.second),
                end = MotionPoint(centre.first + 0.15f, centre.second),
            ),
        )

        doc.updateLayer("a") { layer ->
            (layer as TextLayer).copy(fontSize = 64f, y = layer.y + 0.03f)
        }

        val path = doc.motionPathInWorld("a")!!
        val moved = doc.worldCentre("a")!!
        assertNear(moved.first, path.start.x)
        assertNear(moved.second, path.start.y)
    }

    @Test
    fun dropLayerMovesALayerIntoAGroupAndBackOut() {
        val doc = document()
        val groupId = group(doc)

        doc.select("c")
        assertTrue(doc.dropLayer("c", "a"))
        assertEquals(groupId, doc.layers.parentGroup("c")?.id)

        assertTrue(doc.dropLayer("c", "b"))
        assertEquals(groupId, doc.layers.parentGroup("c")?.id)

        doc.dropLayer("a", "c")
        assertEquals(groupId, doc.layers.parentGroup("c")?.id)
    }

    @Test
    fun undoRestoresTheTreeAfterGroupingAndUngrouping() {
        val doc = document()
        val groupId = group(doc)
        assertEquals(2, doc.layers.size)

        assertTrue(doc.ungroupSelection())
        assertEquals(3, doc.layers.size)

        doc.undo()
        assertNotNull(doc.layers.findLayer(groupId))
        assertEquals(2, doc.layers.size)

        doc.undo()
        assertNull(doc.layers.findLayer(groupId))
        assertEquals(3, doc.layers.size)
    }

    @Test
    fun aGroupRoundTripsThroughTheCodec() {
        val doc = document()
        val groupId = group(doc)
        doc.updateLayer(groupId) { it.withRotation(31f) }
        doc.updateLayer(groupId) { layer ->
            (layer as GroupLayer).copy(scaleX = 1.3f, scaleY = 0.6f, skewX = 12f)
        }
        doc.select(groupId)
        doc.enterGroup()

        val text = DocumentCodec.encode(
            doc.layers,
            doc.selectedId,
            doc.zoom,
            doc.lockedIds,
            doc.selectedIds,
            doc.editingGroupId,
        )
        val back = DocumentCodec.decode(text)!!

        val restored = back.layers.findLayer(groupId) as? GroupLayer
        assertNotNull(restored)
        assertEquals(31f, restored!!.rotation, 0.001f)
        assertEquals(1.3f, restored.scaleX, 0.001f)
        assertEquals(0.6f, restored.scaleY, 0.001f)
        assertEquals(12f, restored.skewX, 0.001f)
        assertEquals(2, restored.children.size)
        assertEquals(groupId, back.editingGroupId)
    }
}
