package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Task 6: a crop is a stored source-space rect, not a flattened bitmap. The same
 * document therefore previews, exports, survives undo and reopens from an older
 * schema; the decode path stays bounded and refreshes when the file changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageCropTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val typefaces = Typefaces(context)
    private val blue = 0xFF2F6BFF.toInt()
    private val clay = 0xFFE86A4A.toInt()

    private fun source(): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(blue)
        canvas.drawRect(50f, 0f, 100f, 100f, android.graphics.Paint().apply { color = clay })
        return bitmap
    }

    private fun photo(crop: ImageCrop, fill: Boolean = true, path: String = "split.png") = ImageLayer(
        id = "photo",
        name = "PHOTO",
        x = 0.1f,
        y = 0.1f,
        w = 0.4f,
        h = 0.32f,
        path = path,
        fill = fill,
        crop = crop,
    )

    private fun render(layers: List<Layer>, images: Map<String, Bitmap>): Bitmap {
        val bitmap = Bitmap.createBitmap(
            ArtboardPainter.W.toInt(),
            ArtboardPainter.H.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        ArtboardPainter.draw(
            canvas = Canvas(bitmap),
            layers = layers,
            typefaces = typefaces,
            images = images,
            scale = 1f,
        )
        return bitmap
    }

    private fun pixel(bitmap: Bitmap, x: Float, y: Float): Int =
        bitmap.getPixel(
            (ArtboardPainter.W * x).toInt(),
            (ArtboardPainter.H * y).toInt(),
        )

    @Test
    fun cropChangesThePixelsTheLayerDraws() {
        val source = source()
        val full = render(listOf(photo(ImageCrop())), mapOf("split.png" to source))
        val left = render(
            listOf(photo(ImageCrop(x = 0f, y = 0f, w = 0.5f, h = 1f))),
            mapOf("split.png" to source),
        )
        val right = render(
            listOf(photo(ImageCrop(x = 0.5f, y = 0f, w = 0.5f, h = 1f))),
            mapOf("split.png" to source),
        )

        assertEquals(blue, pixel(full, 0.2f, 0.25f))
        assertEquals(blue, pixel(left, 0.2f, 0.25f))
        assertEquals(clay, pixel(right, 0.2f, 0.25f))

        // Rendering a crop reads the source; it never rewrites its pixels.
        assertEquals(blue, source.getPixel(25, 50))
        assertEquals(clay, source.getPixel(75, 50))
    }

    @Test
    fun fitShowsTheWholeCroppedAreaAndNothingOutsideIt() {
        val source = source()
        val backdrop = render(emptyList(), emptyMap())
        val fitted = render(
            listOf(photo(ImageCrop(x = 0.5f, y = 0f, w = 0.5f, h = 1f), fill = false)),
            mapOf("split.png" to source),
        )

        // A half-width crop fitted into a square frame is letterboxed at the sides.
        assertEquals(clay, pixel(fitted, 0.3f, 0.25f))
        assertEquals(pixel(backdrop, 0.115f, 0.25f), pixel(fitted, 0.115f, 0.25f))
    }

    @Test
    fun aMissingPhotoLeavesAVisiblePlaceholderAndDoesNotCrash() {
        val backdrop = render(emptyList(), emptyMap())
        val missing = render(listOf(photo(ImageCrop(), path = "gone.jpg")), emptyMap())

        assertNotEquals(pixel(backdrop, 0.2f, 0.25f), pixel(missing, 0.2f, 0.25f))
        assertEquals(Color.alpha(pixel(missing, 0.2f, 0.25f)), 255)
    }

    @Test
    fun cropKeepsTheFrameHitAndUndoRestoresTheFullPhoto() {
        val doc = EngineDocument()
        doc.addImage("photo.jpg")
        val image = doc.selected as ImageLayer
        val id = image.id
        assertEquals(id, doc.layerAt(image.centerX, image.centerY)?.id)

        val crop = ImageCrop(x = 0.2f, y = 0.15f, w = 0.5f, h = 0.6f)
        doc.beginGesture()
        doc.setImageCrop(id, crop)
        doc.endGesture()

        assertEquals(crop, (doc.selected as ImageLayer).crop)
        assertEquals(id, doc.layerAt(image.centerX, image.centerY)?.id)

        doc.undo()
        assertEquals(ImageCrop(), (doc.layers.findLayer(id) as ImageLayer).crop)
        doc.redo()
        assertEquals(crop, (doc.layers.findLayer(id) as ImageLayer).crop)
    }

    @Test
    fun cropSurvivesGroupingAndUndoKeepsTheSelection() {
        val doc = EngineDocument()
        doc.addImage("photo.jpg")
        val id = (doc.selected as ImageLayer).id
        val crop = ImageCrop(x = 0.25f, y = 0.1f, w = 0.4f, h = 0.5f)
        doc.beginGesture()
        doc.setImageCrop(id, crop)
        doc.endGesture()

        doc.select("vector-star", additive = true)
        assertEquals(setOf("vector-star", id), doc.selectedIds)
        assertNotNull(doc.groupSelection())
        assertEquals(crop, (doc.layers.findLayer(id) as ImageLayer).crop)

        // Undo the grouping: the cropped child returns with its dials and selection.
        doc.undo()
        assertEquals(crop, (doc.layers.findLayer(id) as ImageLayer).crop)
        assertEquals(setOf("vector-star", id), doc.selectedIds)
    }

    @Test
    fun cropRoundTripsAndOlderDocumentsDefaultToTheFullPhoto() {
        val doc = EngineDocument()
        doc.addImage("photo.jpg")
        val id = (doc.selected as ImageLayer).id
        doc.setImageCrop(id, ImageCrop(x = 0.12f, y = 0.2f, w = 0.55f, h = 0.6f))

        val restored = DocumentCodec.decode(DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom))
        assertNotNull(restored)
        val saved = restored!!.layers.findLayer(id) as ImageLayer
        assertEquals(0.12f, saved.crop.x, 0.0005f)
        assertEquals(0.2f, saved.crop.y, 0.0005f)
        assertEquals(0.55f, saved.crop.w, 0.0005f)
        assertEquals(0.6f, saved.crop.h, 0.0005f)

        val old = DocumentCodec.decode(
            """{"schema":3,"layers":[{"kind":"image","id":"p","name":"P","x":0.1,"y":0.1,
            "w":0.2,"h":0.2,"path":"old.jpg","fill":true}]}""".trimIndent(),
        )
        assertNotNull(old)
        assertEquals(ImageCrop(), (old!!.layers.single() as ImageLayer).crop)
    }

    @Test
    fun aFourKSourceIsCappedForPreviewAndFullForExport() {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "four-k.jpg")
        val large = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888).apply { eraseColor(blue) }
        file.outputStream().use { large.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        large.recycle()

        val preview = ImageStore.load(context, "four-k.jpg", ImageStore.PREVIEW_MAX_PX)
        assertNotNull(preview)
        assertTrue(maxOf(preview!!.width, preview.height) <= ImageStore.PREVIEW_MAX_PX)

        val full = ImageStore.load(context, "four-k.jpg", ImageStore.EXPORT_MAX_PX)
        assertNotNull(full)
        assertEquals(4096, full!!.width)
        assertEquals(2048, full.height)
    }

    @Test
    fun replacingAPhotoInvalidatesTheMtimeKeyedCache() {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "swap.png")
        fun write(color: Int) {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
                file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                recycle()
            }
        }
        write(blue)
        val first = ImageStore.load(context, "swap.png")!!
        assertEquals(blue, first.getPixel(4, 4))
        val oldModified = file.lastModified()

        write(clay)
        file.setLastModified(oldModified + 5_000L)
        val second = ImageStore.load(context, "swap.png")!!
        assertEquals(clay, second.getPixel(4, 4))
    }
}
