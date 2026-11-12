package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry Task 2 depends on: one matrix order, inverse hit testing and an
 * ungroup that keeps a rotated child's corners under a non-uniform group scale.
 */
class LayerAffineTest {

    private val tolerance = 0.001f

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, tolerance)
        assertEquals(expected.second, actual.second, tolerance)
    }

    @Test
    fun skewMovesTheEdgeButNotTheCentre() {
        val frame = LayerFrame(0.5f, 0.5f, 0.4f, 0.2f, skewX = 30f)
        assertPoint(0.5f to 0.5f, frame.matrix.map(0f, 0f))
        // tan(30) = 0.57735; the top-right corner (y = -0.1) shifts left by that much.
        assertPoint(
            0.5f + 0.2f - 0.1f * 0.57735f to 0.4f,
            frame.matrix.map(0.2f, -0.1f),
        )
    }

    @Test
    fun inverseMapsAWorldPointBackIntoFrameSpace() {
        val frame = LayerFrame(
            centerX = 0.4f,
            centerY = 0.6f,
            width = 0.3f,
            height = 0.2f,
            rotation = 35f,
            skewX = 12f,
            scaleX = 1.3f,
            scaleY = 0.8f,
        )
        val world = frame.matrix.map(0.05f, -0.02f)
        val inverse = frame.matrix.inverse()!!
        assertPoint(0.05f to -0.02f, inverse.map(world.first, world.second))
    }

    @Test
    fun hitTestingASkewedFrameIgnoresItsBoundingBoxCorner() {
        val frame = LayerFrame(0.5f, 0.5f, 0.2f, 0.2f, rotation = 45f)
        val corner = 0.5f + 0.13f to 0.5f + 0.13f
        assertTrue("the AABB reaches the empty corner", frame.boundsWorld().contains(corner.first, corner.second))
        assertFalse("the frame itself does not", frame.containsWorld(corner.first, corner.second))
        assertTrue(frame.containsWorld(0.5f, 0.5f))
    }

    @Test
    fun decomposeRecoversRotationSkewAndBothScales() {
        val frame = LayerFrame(
            centerX = 0.3f,
            centerY = 0.7f,
            width = 0.5f,
            height = 0.2f,
            rotation = 25f,
            skewX = 12f,
            scaleX = 1.4f,
            scaleY = 0.6f,
        )
        val parts = LayerAffine.decompose(frame.matrix)!!
        assertEquals(0.3f, parts.centerX, tolerance)
        assertEquals(0.7f, parts.centerY, tolerance)
        assertEquals(1.4f, parts.scaleX, tolerance)
        assertEquals(0.6f, parts.scaleY, tolerance)
        assertEquals(25f, parts.rotation, tolerance)
        assertEquals(12f, parts.skewX, tolerance)
    }

    @Test
    fun flatteningAChildOutOfAScaledGroupKeepsItsCorners() {
        val group = LayerFrame(
            centerX = 0.5f,
            centerY = 0.5f,
            width = 0.6f,
            height = 0.4f,
            rotation = 20f,
            scaleX = 1.6f,
            scaleY = 0.7f,
        )
        // A child is stored in its group's local space, not in artboard space.
        val child = LayerFrame(0.12f, 0.05f, 0.2f, 0.1f, rotation = 15f)
        val flattened = LayerAffine.flatten(child, group)!!
        val combined = group.matrix * child.matrix

        listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).forEach { (sx, sy) ->
            val expected = combined.map(sx * child.width / 2f, sy * child.height / 2f)
            val actual = flattened.matrix.map(sx * flattened.width / 2f, sy * flattened.height / 2f)
            assertPoint(expected, actual)
        }
    }

    @Test
    fun groupingAPlacedChildAndFlatteningItReturnsTheSameFrame() {
        val group = LayerFrame(0.5f, 0.5f, 0.6f, 0.4f, rotation = 20f)
        val world = LayerFrame(0.62f, 0.55f, 0.2f, 0.1f, rotation = 15f)

        val local = LayerAffine.grouped(world, group)!!
        val back = LayerAffine.flatten(local, group)!!

        listOf(-1f to -1f, 1f to 1f).forEach { (sx, sy) ->
            assertPoint(
                world.matrix.map(sx * world.width / 2f, sy * world.height / 2f),
                back.matrix.map(sx * back.width / 2f, sy * back.height / 2f),
            )
        }
    }

    @Test
    fun aCollapsedGroupRefusesToInvertAndDoesNotHit() {
        val collapsed = Affine.scale(0f, 1f)
        assertNull(collapsed.inverse())

        val frame = LayerFrame(0.5f, 0.5f, 0.2f, 0.2f, scaleX = 0f)
        assertFalse(frame.containsWorld(0.5f, 0.5f))
        assertNull(LayerAffine.flatten(LayerFrame(0.5f, 0.5f, 0.1f, 0.1f), frame))
    }
}
