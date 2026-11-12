package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The anchoring rules from Task 3. The important case is the group one: a child's
 * path is stored in group-local space, so a group transform carries it without
 * rewriting a single point.
 */
class MotionPathTest {

    private val tolerance = 0.001f

    private fun pathAt(centerX: Float, centerY: Float): MotionPath = MotionAnchoring.anchoredTo(
        centerX,
        centerY,
        MotionPath(
            start = MotionPoint(0f, 0f),
            c1 = MotionPoint(0.3f, -0.2f),
            c2 = MotionPoint(0.7f, 0.4f),
            end = MotionPoint(1f, 0f),
        ),
    )

    @Test
    fun aNewPathStartsAtTheLayerCentre() {
        val path = pathAt(0.42f, 0.63f)
        assertEquals(0.42f, path.start.x, tolerance)
        assertEquals(0.63f, path.start.y, tolerance)
        assertEquals(1.42f, path.end.x, tolerance)
    }

    @Test
    fun draggingTheLayerTranslatesEveryPointByTheSameDelta() {
        val path = pathAt(0.4f, 0.5f)
        val (dx, dy) = MotionAnchoring.deltaForCentreChange(0.4f, 0.5f, 0.55f, 0.42f)
        val moved = path.translated(dx, dy)

        listOf(
            path.start to moved.start,
            path.c1 to moved.c1,
            path.c2 to moved.c2,
            path.end to moved.end,
        ).forEach { (before, after) ->
            assertEquals(before.x + dx, after.x, tolerance)
            assertEquals(before.y + dy, after.y, tolerance)
        }
        assertEquals(moved.start.x, moved.pointAt(0f).x, tolerance)
    }

    @Test
    fun aPathInsideAGroupMovesWithTheGroupMatrix() {
        val groupBefore = Affine.translate(0.1f, 0.2f)
        val groupAfter = Affine.translate(0.3f, 0.15f)
        val world = pathAt(0.5f, 0.5f)

        val local = MotionAnchoring.intoGroup(world, groupBefore)!!
        val worldAfter = MotionAnchoring.outOfGroup(local, groupAfter)

        assertEquals(world.start.x + 0.2f, worldAfter.start.x, tolerance)
        assertEquals(world.start.y - 0.05f, worldAfter.start.y, tolerance)

        val localAgain = MotionAnchoring.intoGroup(worldAfter, groupAfter)!!
        assertEquals(local.start.x, localAgain.start.x, tolerance)
        assertEquals(local.start.y, localAgain.start.y, tolerance)
    }

    @Test
    fun clampingMovesTheWholePathByOneRigidOffset() {
        val path = MotionPath(
            start = MotionPoint(0.95f, 0.5f),
            c1 = MotionPoint(1.0f, 0.5f),
            c2 = MotionPoint(1.05f, 0.6f),
            end = MotionPoint(1.1f, 0.6f),
        )
        val before = path.bounds()
        val clamped = MotionAnchoring.clampedToArtboard(path)
        val after = clamped.bounds()

        assertTrue(after.right <= 1f + tolerance)
        assertEquals(before.width, after.width, tolerance)
        assertEquals(before.height, after.height, tolerance)
        assertEquals(
            clamped.start.x - path.start.x,
            clamped.end.x - path.end.x,
            tolerance,
        )
    }

    @Test
    fun aPathWiderThanTheArtboardIsCentredRatherThanSquashed() {
        val path = MotionPath(
            start = MotionPoint(-0.4f, 0.2f),
            c1 = MotionPoint(0f, 0.2f),
            c2 = MotionPoint(1.2f, 0.8f),
            end = MotionPoint(1.6f, 0.8f),
        )
        val before = path.bounds()
        val after = MotionAnchoring.clampedToArtboard(path).bounds()

        assertEquals(before.width, after.width, 0.02f)
        assertEquals(0.5f, after.left + after.width / 2f, 0.02f)
    }

    @Test
    fun samplingIncludesBothEnds() {
        val path = pathAt(0.5f, 0.5f)
        val samples = path.sample(8)
        assertEquals(9, samples.size)
        assertEquals(path.start.x, samples.first().x, tolerance)
        assertEquals(path.end.x, samples.last().x, tolerance)
    }

    @Test
    fun theCurveStaysInsideItsControlHull() {
        val path = pathAt(0.5f, 0.5f)
        val bounds = path.bounds()
        val xs = listOf(path.start, path.c1, path.c2, path.end).map { it.x }
        assertTrue(bounds.left >= xs.min() - tolerance)
        assertTrue(bounds.right <= xs.max() + tolerance)
    }

    @Test
    fun reducedMotionJumpsStraightToTheEnd() {
        assertEquals(1f, EngineMotion.progress(0, 1400, 0f), tolerance)
    }

    @Test
    fun durationScaleStretchesPlayback() {
        assertEquals(0.25f, EngineMotion.progress(700, 1400, 2f), tolerance)
        assertEquals(0.5f, EngineMotion.progress(700, 1400, 1f), tolerance)
    }

    @Test
    fun theEndHandleIsHitAtTheEndPoint() {
        val path = pathAt(0.5f, 0.5f)
        assertEquals(3, EngineMotion.nearestHandle(path, path.end.x, path.end.y, 0.02f, 0.02f))
        assertNull(EngineMotion.nearestHandle(path, 0.05f, 0.05f, 0.01f, 0.01f))
    }

    @Test
    fun creatingAPathAnchorsItToTheLayerCentre() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.createMotionPath()
        val centre = doc.worldCentre("vector-star")!!
        val path = doc.motionPathInWorld("vector-star")!!
        assertEquals(centre.first, path.start.x, tolerance)
        assertEquals(centre.second, path.start.y, tolerance)
        assertNotNull(doc.motion["vector-star"])
    }

    @Test
    fun aMoveInterruptsPlaybackAndThePathStaysOnTheLayer() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.createMotionPath()
        doc.playMotion()
        assertEquals("vector-star", doc.playingLayerId)

        doc.moveSelected(0.05f, 0.03f)

        assertNull(doc.playingLayerId)
        assertNotNull(doc.motionPathInWorld("vector-star"))
        val centre = doc.worldCentre("vector-star")!!
        val path = doc.motionPathInWorld("vector-star")!!
        assertEquals(centre.first, path.start.x, tolerance)
        assertEquals(centre.second, path.start.y, tolerance)
    }

    @Test
    fun rotatingANonSquareLayerKeepsThePathAnchored() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.createMotionPath()
        doc.setRotation(37f)

        val centre = doc.worldCentre("vector-star")!!
        val path = doc.motionPathInWorld("vector-star")!!
        assertEquals(centre.first, path.start.x, tolerance)
        assertEquals(centre.second, path.start.y, tolerance)
        assertTrue(path.sample(8).all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun undoRestoresThePathAnchorWithTheLayer() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.createMotionPath()
        val before = doc.motionPathInWorld("vector-star")!!

        doc.beginGesture()
        doc.moveSelected(0.1f, 0.05f)
        doc.endGesture()
        doc.undo()

        val after = doc.motionPathInWorld("vector-star")!!
        assertEquals(before.start.x, after.start.x, tolerance)
        assertEquals(before.start.y, after.start.y, tolerance)
    }

    @Test
    fun motionSurvivesTheCodecAndDropsPathsForMissingLayers() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.createMotionPath()
        val before = doc.motion["vector-star"]!!
        val text = DocumentCodec.encode(
            doc.layers,
            doc.selectedId,
            doc.zoom,
            motion = doc.motion + ("ghost" to before),
        )
        val back = DocumentCodec.decode(text)!!
        val after = back.motion["vector-star"]!!
        assertEquals(before.start.x, after.start.x, tolerance)
        assertEquals(before.end.y, after.end.y, tolerance)
        assertEquals(before.durationMs, after.durationMs)
        assertNull(back.motion["ghost"])
    }
}
