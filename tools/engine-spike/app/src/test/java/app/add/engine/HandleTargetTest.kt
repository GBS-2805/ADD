package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A grip is a touch target, not the whole layer. At 22 dp it used to cover most of a
 * 22% icon, so the drag that was meant to move the icon took a corner instead and
 * scaled it to three quarters of the artboard in one gesture.
 */
class HandleTargetTest {
    private val gripX = 0.061f // 22 dp over a 1080-wide artboard at 1x zoom
    private val gripY = 0.052f
    private val insetX = 0.03f
    private val insetY = 0.024f

    private fun icon(w: Float = 0.22f) = VectorLayer(
        id = "icon",
        name = "ICON / TEST",
        x = 0.3f,
        y = 0.3f,
        w = w,
        h = squareFrameHeight(w),
        network = triangleNetwork(),
        fill = 0xFF11231C.toInt(),
        stroke = 0xFFFFFFFF.toInt(),
        strokeWidth = 2f,
    )

    @Test
    fun aGripDoesNotReachTheBodyOfASmallIcon() {
        val layer = icon()
        // 0.055 right of the top-left corner: inside 22 dp, outside a quarter of the icon.
        val handle = Geometry.nearestHandle(
            layer,
            layer.x + insetX + 0.055f,
            layer.y + insetY,
            gripX,
            gripY,
            insetX,
            insetY,
        )
        assertNull(handle)
    }

    @Test
    fun theCornerItselfIsStillAGrip() {
        val layer = icon()
        val handle = Geometry.nearestHandle(
            layer,
            layer.x + insetX,
            layer.y + insetY,
            gripX,
            gripY,
            insetX,
            insetY,
        )
        assertEquals(LayerHandle.TopLeft, handle)
    }

    @Test
    fun aLargeLayerKeepsTheFullTarget() {
        val layer = icon(w = 0.58f)
        val handle = Geometry.nearestHandle(
            layer,
            layer.x + insetX + 0.055f,
            layer.y + insetY,
            gripX,
            gripY,
            insetX,
            insetY,
        )
        assertEquals(LayerHandle.TopLeft, handle)
    }
}
