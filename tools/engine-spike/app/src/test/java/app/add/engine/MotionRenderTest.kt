package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playback displaces the layer inside the compositor's bitmap. The bitmap is sized
 * from the static frame, so without the motion pad the layer is cut off at its own
 * edge the moment the path leaves the frame. This is the pixel proof of that pad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotionRenderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val typefaces = Typefaces(context)
    private val blue = 0xFF2F6BFF.toInt()

    private fun photo() = ImageLayer(
        id = "p",
        name = "P",
        x = 0.1f,
        y = 0.1f,
        w = 0.2f,
        h = 0.2f,
        path = "blue.png",
    )

    private fun render(offsets: Map<String, Pair<Float, Float>>): Bitmap {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(blue) }
        val bitmap = Bitmap.createBitmap(
            ArtboardPainter.W.toInt(),
            ArtboardPainter.H.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        ArtboardPainter.draw(
            canvas = Canvas(bitmap),
            layers = listOf(photo()),
            typefaces = typefaces,
            images = mapOf("blue.png" to source),
            motionOffsets = offsets,
            scale = 1f,
        )
        return bitmap
    }

    private fun green(bitmap: Bitmap, x: Float, y: Float): Int {
        val pixel = bitmap.getPixel(
            (ArtboardPainter.W * x).toInt(),
            (ArtboardPainter.H * y).toInt(),
        )
        return (pixel ushr 8) and 0xFF
    }

    @Test
    fun aMotionOffsetDrawsOutsideTheStaticFrame() {
        val still = render(emptyMap())
        val moving = render(mapOf("p" to (0.35f to 0f)))

        // The frame sits at 0.1..0.3; the same blue now appears around 0.45..0.65.
        assertTrue("still frame should be blue at its centre", green(still, 0.2f, 0.2f) < 160)
        assertTrue("moved layer must leave its old position", green(moving, 0.2f, 0.2f) > 200)
        assertEquals(107.0, green(moving, 0.55f, 0.2f).toDouble(), 8.0)
    }
}
