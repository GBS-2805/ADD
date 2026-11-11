package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Material Ink is only real if the recipe survives the app's own save path and the
 * same recipe draws the same pixels. The performance assertion measures the added
 * render work, not the whole screen's Compose frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TextureInkTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        DocumentStore.clear(context)
        TextureRenderer.clear()
    }

    @Test
    fun materialInkSurvivesTheRepositoryRoundTrip() {
        val doc = EngineDocument()
        doc.toggleInkMode()
        doc.chooseInkFamily(TextureFamily.Marble)
        doc.beginInkStroke(0.11f, 0.22f)
        doc.extendInkStroke(0.15f, 0.26f)
        doc.extendInkStroke(0.22f, 0.31f)
        doc.endInkStroke()

        val before = doc.ink
        assertEquals(1, before.size)
        assertEquals(TextureFamily.Marble, before.single().recipe.family)
        assertTrue(before.single().points.size >= 3)

        assertTrue(DocumentStore.save(context, doc))
        val restored = assertNotNull(DocumentStore.restore(context))
        assertEquals(before, restored.ink)
        assertEquals(before.single().recipe, restored.ink.single().recipe)
        assertEquals(before.single().points, restored.ink.single().points)
    }

    @Test
    fun aMaterialStrokeIsOneUndoStepAndRedoReturnsIt() {
        val doc = EngineDocument()
        doc.toggleInkMode()
        doc.beginInkStroke(0.2f, 0.3f)
        doc.extendInkStroke(0.3f, 0.4f)
        doc.endInkStroke()
        val stroke = doc.ink.single()

        doc.undo()
        assertTrue(doc.ink.isEmpty())
        doc.redo()
        assertEquals(listOf(stroke), doc.ink)
    }

    @Test
    fun theSameStrokeDrawsByteIdenticalPixelsAndASeedChangesThem() {
        val doc = EngineDocument()
        doc.toggleInkMode()
        doc.chooseInkFamily(TextureFamily.WoodGrain)
        doc.beginInkStroke(0.12f, 0.18f)
        doc.extendInkStroke(0.56f, 0.42f)
        doc.endInkStroke()

        val first = render(doc.ink)
        val second = render(doc.ink)
        assertArrayEquals(pixels(first), pixels(second))

        val changed = doc.ink.single().copy(
            recipe = doc.ink.single().recipe.copy(seed = doc.ink.single().recipe.seed + 1),
        )
        val third = render(listOf(changed))
        val a = pixels(first)
        val b = pixels(third)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun undoDoesNotFillHistoryWithRepeatedUndoRows() {
        val doc = EngineDocument()
        doc.toggleInkMode()
        doc.beginInkStroke(0.2f, 0.3f)
        doc.extendInkStroke(0.3f, 0.4f)
        doc.endInkStroke()
        val before = doc.recentActions
        doc.undo()
        doc.redo()
        doc.undo()
        assertEquals(before, doc.recentActions)
    }

    @Test
    fun cachedMaterialInkStaysUnderTheSixteenMillisecondFrameBudget() {
        val doc = EngineDocument()
        doc.toggleInkMode()
        doc.chooseInkFamily(TextureFamily.Concrete)
        doc.beginInkStroke(0.05f, 0.12f)
        doc.extendInkStroke(0.9f, 0.78f)
        doc.endInkStroke()
        val ink = doc.ink
        TextureInkRenderer.prewarm(ink.single())

        val samples = mutableListOf<Double>()
        repeat(18) {
            val started = System.nanoTime()
            render(ink)
            samples += (System.nanoTime() - started) / 1_000_000.0
        }
        val median = samples.sorted()[samples.size / 2]
        println("materialInk medianMs=$median samples=${samples.joinToString { "%.2f".format(it) }}")
        assertTrue("material ink median ${median}ms exceeded 16ms", median < 16.0)
    }

    private fun render(strokes: List<TextureInk>): Bitmap {
        val bitmap = Bitmap.createBitmap(360, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF3EBDD.toInt())
        canvas.scale(360f / ArtboardPainter.W, 450f / ArtboardPainter.H)
        TextureInkRenderer.draw(canvas, strokes, ArtboardPainter.W.toInt(), ArtboardPainter.H.toInt())
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return requireNotNull(value)
    }
}
