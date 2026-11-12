package app.add.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Isolates the alpha path from the artboard blit so opacity cannot fail silently. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayerPaintTest {

    private val cream = 0xFFF3EBDD.toInt()
    private val red = 0xFFE86A4A.toInt()

    private fun redChannel(pixel: Int) = (pixel ushr 16) and 0xFF

    @Test
    fun drawingAHalfAlphaBitmapBlendsWithTheBackdrop() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(cream)
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(red) }
        canvas.drawBitmap(source, 0f, 0f, Paint().apply { alpha = 128 })
        assertEquals(237.0, redChannel(bitmap.getPixel(2, 2)).toDouble(), 3.0)
    }

    @Test
    fun theCompositorAppliesGroupOpacityOnce() {
        val group = GroupLayer(
            id = "g",
            name = "G",
            x = 0f,
            y = 0f,
            w = 1f,
            h = 0.8f,
            opacity = 0.5f,
            children = listOf(
                ImageLayer(id = "photo", name = "PHOTO", x = 0f, y = 0f, w = 1f, h = 0.8f, path = "red.png")
            ),
        )
        val dest = Bitmap.createBitmap(ArtboardPainter.W.toInt(), ArtboardPainter.H.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        canvas.drawColor(cream)
        LayerCompositor(1f) { layerCanvas, _ ->
            layerCanvas.drawRect(
                0f,
                0f,
                ArtboardPainter.W.toFloat(),
                ArtboardPainter.H.toFloat(),
                Paint().apply { color = red },
            )
        }.draw(canvas, dest, listOf(group))
        assertEquals(237.0, redChannel(dest.getPixel(540, 500)).toDouble(), 3.0)
    }

    @Test
    fun theCompositorAppliesLayerOpacityOnce() {
        val layer = ImageLayer(
            id = "photo",
            name = "PHOTO",
            x = 0f,
            y = 0f,
            w = 1f,
            h = 0.8f,
            path = "red.png",
            opacity = 0.5f,
        )
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(red) }
        val dest = Bitmap.createBitmap(ArtboardPainter.W.toInt(), ArtboardPainter.H.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        canvas.drawColor(cream)
        LayerCompositor(1f) { layerCanvas, target ->
            layerCanvas.drawRect(
                0f,
                0f,
                target.w * ArtboardPainter.W,
                target.h * ArtboardPainter.H,
                Paint().apply { color = red },
            )
        }.draw(canvas, dest, listOf(layer))
        assertEquals(237.0, redChannel(dest.getPixel(540, 500)).toDouble(), 3.0)
    }
}
