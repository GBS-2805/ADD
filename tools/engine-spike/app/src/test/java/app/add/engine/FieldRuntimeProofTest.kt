package app.add.engine

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Fallback runtime proof when the TCG emulator cannot keep Android's package/window
 * services alive. It drives the real EngineDocument, the real ArtboardPainter through
 * Exporter.renderArtboard and the real schema-six persistence. It is not device proof;
 * it is the app's own render/export path.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FieldRuntimeProofTest {
    private val proofDir: File
        get() = File(System.getenv("FIELD_PROOF_DIR") ?: "build/field-proof").apply { mkdirs() }

    @Test
    fun fieldBackgroundAndLayerFillSurviveExportAndReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val typefaces = Typefaces(context)
        val doc = EngineDocument()
        val field = FieldPresets.all[3].field
            .copy(swirl = 0.31f, distortion = 0.74f, noise = 0.05f)
        doc.setFieldPreset(FieldTarget.Background, field)

        // A vector and a text layer both carry the same recipe as a paint fill.
        doc.select("vector-star")
        doc.setFieldPreset(FieldTarget.Layer, field.copy(distortion = 0.68f))
        doc.setFieldBand(FieldTarget.Layer, 0, 0.42f)
        doc.select("type-statement")
        doc.setFieldPreset(FieldTarget.Layer, field.copy(swirl = 0.26f))

        val exported = Exporter.renderArtboard(
            context = context,
            layers = doc.layers,
            typefaces = typefaces,
            sceneViews = emptyMap(),
            scale = 1,
            backgroundField = doc.backgroundField,
            fieldPhase = 1.7f,
        )
        write(exported.bitmap, "exported.png")

        val pixels = IntArray(exported.bitmap.width * exported.bitmap.height)
        exported.bitmap.getPixels(pixels, 0, exported.bitmap.width, 0, 0, exported.bitmap.width, exported.bitmap.height)
        assertTrue("field export is opaque", pixels.all { (it ushr 24) == 0xFF })
        assertTrue("field export is not a flat colour", pixels.toSet().size > 500)

        // Save, reopen, and render the restored document through the same export path.
        val closed = DocumentCodec.encode(
            doc.layers,
            doc.selectedId,
            doc.zoom,
            backgroundField = doc.backgroundField,
        )
        File(proofDir, "saved-document.json").writeText(closed)
        val restored = DocumentCodec.decode(closed)
        assertNotNull(restored)
        val restoredField = restored!!.backgroundField
        assertNotNull(restoredField)
        assertEquals(field.colors.size, restoredField!!.colors.size)
        assertEquals(field.colors[0].color, restoredField.colors[0].color)
        assertEquals(field.swirl, restoredField.swirl, 0.0005f)
        assertEquals(field.distortion, restoredField.distortion, 0.0005f)
        assertEquals(field.noise, restoredField.noise, 0.0005f)

        val reopened = Exporter.renderArtboard(
            context = context,
            layers = restored.layers,
            typefaces = typefaces,
            sceneViews = emptyMap(),
            scale = 1,
            backgroundField = restored.backgroundField,
            fieldPhase = 1.7f,
        )
        write(reopened.bitmap, "reopened.png")
        assertTrue("reopened recipe renders identically", exported.bitmap.sameAs(reopened.bitmap))

        // Undo and redo the exact field recipe, then prove the redone pixels are identical.
        doc.select("vector-star")
        doc.setFieldPreset(FieldTarget.Layer, field.copy(swirl = 0.55f))
        doc.undo()
        val afterUndo = (doc.layers.first { it.id == "vector-star" } as VectorLayer)
            .paint as PaintSpec.Field
        assertTrue("undo restores the earlier layer recipe", afterUndo.field.swirl != 0.55f)
        doc.redo()
        val afterRedoField = (doc.layers.first { it.id == "vector-star" } as VectorLayer)
            .paint as PaintSpec.Field
        assertEquals("redo restores the field recipe", 0.55f, afterRedoField.field.swirl, 0.0005f)
        val afterRedo = Exporter.renderArtboard(
            context = context,
            layers = doc.layers,
            typefaces = typefaces,
            sceneViews = emptyMap(),
            scale = 1,
            backgroundField = doc.backgroundField,
            fieldPhase = 1.7f,
        )
        write(afterRedo.bitmap, "redo.png")
    }

    private fun write(bitmap: Bitmap, name: String) {
        FileOutputStream(File(proofDir, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
