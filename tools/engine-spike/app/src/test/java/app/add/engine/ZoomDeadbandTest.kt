package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A recorded session showed the zoom drifting a point at a time while nothing was
 * being pinched (145%→144%, 200%→200%), and at 192%→191% it also cleared the pan.
 * These pin down the band that stops it without swallowing a slow pinch.
 */
class ZoomDeadbandTest {

    @Test
    fun jitterThatReversesNeverReachesTheArtboard() {
        var pending = 1f
        var applied = 0
        repeat(60) { index ->
            val (factor, carry) = foldZoom(pending, if (index % 2 == 0) 1.004f else 0.996f)
            pending = carry
            if (factor != 1f) applied++
        }
        assertEquals("a tremor that cancels itself must not move the zoom", 0, applied)
    }

    @Test
    fun aDeliberatePinchStillCrossesTheBand() {
        var pending = 1f
        var zoom = 1f
        repeat(10) {
            val (factor, carry) = foldZoom(pending, 1.01f)
            if (factor != 1f) zoom *= factor
            pending = carry
        }
        // Ten one-percent steps are a 10% pinch: the band may delay them, never lose them.
        assertTrue("zoom should have grown, got $zoom", zoom > 1.09f)
    }

    @Test
    fun theCarriedRemainderIsAppliedOnceAndKeptForLater() {
        var pending = 1f
        var zoom = 1f
        repeat(4) {
            val (factor, carry) = foldZoom(pending, 1.006f)
            if (factor != 1f) zoom *= factor
            pending = carry
        }
        // Four 0.6% steps make 2.4%: one crossing to 1.8%, the last step still carried.
        assertTrue("expected exactly one crossing, got $zoom", zoom > 1.015f && zoom < 1.02f)
        assertEquals("the uncrossed step is carried, not lost", 1.006f, pending, 0.0001f)
    }
}
