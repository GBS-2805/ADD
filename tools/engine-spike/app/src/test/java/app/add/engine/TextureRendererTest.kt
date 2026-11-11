package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The cache and the real artboard draw path, not just the pure generator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextureRendererTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        TextureRenderer.clear()
    }

    @Test
    fun overlayDownsamplesToTheBoundedFieldAndIsCached() {
        val spec = TextureSpec.create(TextureFamily.Marble, seed = 4_004)
        val first = TextureRenderer.overlay(spec, ArtboardPainter.W.toInt(), ArtboardPainter.H.toInt())
        val pixels = first.width.toLong() * first.height
        assertTrue("field was not bounded: ${first.width}x${first.height}", pixels <= 240_000L)
        assertTrue("field is not proportional", first.width > 0 && first.height > 0)

        val second = TextureRenderer.overlay(spec, ArtboardPainter.W.toInt(), ArtboardPainter.H.toInt())
        assertSame("the same recipe and field must reuse the bitmap", first, second)
    }

    @Test
    fun anOpaqueArtboardMaterialCannotEraseTheLayersAboveIt() {
        val layer = VectorLayer(
            id = "texture-order-vector",
            name = "TEXTURE ORDER",
            x = 0.1f,
            y = 0.1f,
            w = 0.6f,
            h = 0.75f,
            network = diamondNetwork(),
            fill = 0xFF0FBF92.toInt(),
            stroke = 0xFF11231C.toInt(),
            strokeWidth = 4f,
        )
        val typefaces = Typefaces(context)
        val material = TextureSpec(
            family = TextureFamily.Concrete,
            seed = 71,
            opacity = 1f,
            blend = BlendKind.Normal,
        )
        val withLayer = render(listOf(layer), typefaces, material)
        val withoutLayer = render(emptyList(), typefaces, material)
        val layerPixels = pixels(withLayer)
        val backgroundPixels = pixels(withoutLayer)
        val visible = layerPixels.indices.count { layerPixels[it] != backgroundPixels[it] }
        assertTrue(
            "artboard material covered every layer pixel; the layer must render above its material ($visible px)",
            visible > 20_000,
        )
        // The material is under the page now. A fully opaque layer pixel must be
        // exactly the layer fill, not the material blended over it.
        val centerX = (ArtboardPainter.W * 0.4f).toInt()
        val centerY = (ArtboardPainter.H * 0.475f).toInt()
        val center = layerPixels[centerY * ArtboardPainter.W.toInt() + centerX]
        assertEquals(
            "the artboard material is still painting over the selected layer at its center",
            0xFF0FBF92.toInt(),
            center,
        )
        withLayer.recycle()
        withoutLayer.recycle()
    }

    @Test
    fun artboardPainterActuallyAppliesTheTextureOverlay() {
        val layer = VectorLayer(
            id = "texture-test-vector",
            name = "TEXTURE TEST",
            x = 0.1f,
            y = 0.1f,
            w = 0.6f,
            h = 0.75f,
            network = diamondNetwork(),
            fill = 0xFF0FBF92.toInt(),
            stroke = 0xFF11231C.toInt(),
            strokeWidth = 4f,
        )
        val typefaces = Typefaces(context)
        val plain = render(layer, typefaces, texture = null)
        val textured = render(
            layer,
            typefaces,
            texture = TextureSpec.create(TextureFamily.Scanline, seed = 17),
        )
        val plainPixels = pixels(plain)
        val texturedPixels = pixels(textured)
        val changed = plainPixels.indices.count { plainPixels[it] != texturedPixels[it] }
        assertTrue("texture did not reach the artboard ($changed px)", changed > plainPixels.size / 20)
        plain.recycle()
        textured.recycle()
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun render(layer: Layer, typefaces: Typefaces, texture: TextureSpec?): Bitmap =
        render(listOf(layer), typefaces, texture)

    private fun render(layers: List<Layer>, typefaces: Typefaces, texture: TextureSpec?): Bitmap {
        val bitmap = Bitmap.createBitmap(
            ArtboardPainter.W.toInt(),
            ArtboardPainter.H.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        ArtboardPainter.draw(
            canvas = Canvas(bitmap),
            layers = layers,
            typefaces = typefaces,
            texture = texture,
        )
        return bitmap
    }
}
