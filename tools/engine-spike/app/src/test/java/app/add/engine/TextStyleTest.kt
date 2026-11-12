package app.add.engine

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Task 5: tracking is real measurement, case is a display transform, a missing
 * family falls back visibly, and type fields survive the codec without changing
 * older documents.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TextStyleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun trackingChangesMeasuredWidth() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 120f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val plain = paint.measureText("TYPE")
        paint.letterSpacing = 0.25f
        val tracked = paint.measureText("TYPE")
        assertTrue("tracking must widen the run: $plain -> $tracked", tracked > plain + 2f)
    }

    @Test
    fun caseIsDisplayOnlyAndKeepsTheSourceText() {
        val doc = EngineDocument()
        doc.addText()
        doc.setText("Mixed Case")
        doc.setTextCase(TextCase.Upper)

        val stored = (doc.selected as TextLayer).text
        assertEquals("Mixed Case", stored)
        assertEquals("MIXED CASE", TextCase.Upper.applyTo(stored))
        assertEquals("mixed case", TextCase.Lower.applyTo(stored))
        assertEquals(stored, TextCase.None.applyTo(stored))
    }

    @Test
    fun aMissingFamilyFallsBackToPoppinsAndReportsIt() {
        val registry = TypefaceRegistry(context)
        val resolved = registry.resolve("Definitely Not Installed", 500)

        assertNotNull(resolved.typeface)
        assertTrue(resolved.substituted)
        assertEquals("Poppins", resolved.family)

        val bundled = registry.resolve("Poppins", 600)
        assertFalse(bundled.substituted)
        assertEquals("Poppins", bundled.family)
    }

    @Test
    fun anInstalledPackFamilyResolvesByItsFolderName() {
        val dir = File(context.filesDir, "font-packs/demo-serif/1").apply { mkdirs() }
        context.assets.open("fonts/poppins_regular.ttf").use { input ->
            File(dir, "DemoSerif-Regular.ttf").outputStream().use { output -> input.copyTo(output) }
        }

        val registry = TypefaceRegistry(context)
        assertTrue("Demo Serif" in registry.families())
        val resolved = registry.resolve("Demo Serif", 500)
        assertFalse(resolved.substituted)
        assertEquals("Demo Serif", resolved.family)
        assertNotNull(resolved.typeface)
    }

    @Test
    fun aVersionThreeDocumentOpensInPoppinsWithoutTracking() {
        val old = """
            {"schema":3,"layers":[{"kind":"text","id":"t","name":"T","x":0.1,"y":0.1,
            "w":0.5,"h":0.2,"text":"OLD","fontSize":48,"lineHeight":1.0,"color":-1}]}
        """.trimIndent()
        val restored = DocumentCodec.decode(old)
        assertNotNull(restored)
        val text = restored!!.layers.single() as TextLayer
        assertEquals("Poppins", text.fontFamily)
        assertEquals(0f, text.tracking, 0.0001f)
        assertEquals(TextCase.None, text.textCase)
    }

    @Test
    fun familyTrackingAndCaseRoundTripThroughV4() {
        val doc = EngineDocument()
        doc.addText()
        doc.setFontFamily("Serif")
        doc.setTracking(0.12f)
        doc.setTextCase(TextCase.Lower)

        val text = DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom)
        val restored = DocumentCodec.decode(text)
        assertNotNull(restored)
        val layer = restored!!.layers.filterIsInstance<TextLayer>().last()

        assertEquals("Serif", layer.fontFamily)
        assertEquals(0.12f, layer.tracking, 0.0001f)
        assertEquals(TextCase.Lower, layer.textCase)
        assertEquals(DocumentCodec.VERSION, text.substringAfter("\"schema\":").substringBefore(",").toInt())
    }
}
