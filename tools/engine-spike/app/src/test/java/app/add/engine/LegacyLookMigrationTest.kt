package app.add.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyLookMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStorage() {
        context.getSharedPreferences("compress_settings", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "legacy-looks.json").delete()
    }

    private fun store(vararg lines: String) {
        context.getSharedPreferences("compress_settings", Context.MODE_PRIVATE)
            .edit().putString("editor_looks", lines.joinToString("\n")).commit()
    }

    @Test
    fun importsEveryValidLookAndCountsTheMalformedOne() {
        store(
            "Warm\t10,20,-30,35,55",
            "Duo\t0,0,0,0,0\t16711680,0,5,95,200,0;255,2,-2,110,120,1",
            "broken line without a tab",
        )

        val result = LegacyLookMigration.migrate(context)

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(2, result.looks.size)
        assertTrue(result.looks[1].types[0].shadow.not())
        assertTrue(result.looks[1].types[1].shadow)
        assertTrue(File(context.filesDir, "legacy-looks.json").isFile)
    }

    @Test
    fun migrationIsIdempotent() {
        store("Warm\t10,20,-30,35,55")
        val first = LegacyLookMigration.migrate(context)
        val second = LegacyLookMigration.migrate(context)

        assertEquals(first.imported, second.imported)
        assertEquals(first.looks.map { it.name }, second.looks.map { it.name })
    }

    @Test
    fun applyingALookMapsTheGradeAndTypeStyles() {
        store("Duo\t0,20,-30,0,0\t16711680,2,5,95,200,0")
        val look = LegacyLookMigration.migrate(context).looks.single()
        val doc = EngineDocument()
        doc.addText()

        assertTrue(LegacyLookMigration.apply(doc, look))

        val text = doc.selected as TextLayer
        assertEquals(0xFFFF0000.toInt(), text.color)
        assertEquals(TextAlign.Right, text.align)
        assertEquals(0.05f, text.tracking, 0.0005f)
        assertEquals(0.95f, text.lineHeight, 0.0005f)
        assertEquals(200f / 255f, text.opacity, 0.0005f)
    }

    @Test
    fun unsupportedFieldsAreReportedNotDropped() {
        store("Vintage\t0,0,0,35,55\t16711680,0,0,90,255,1")
        val result = LegacyLookMigration.migrate(context)

        assertTrue(result.warnings.any { it.contains("warmth") })
        assertTrue(result.warnings.any { it.contains("shadow") })
        assertEquals(1, result.imported)
    }

    @Test
    fun originalStyleStoreDataRemainsUntilTheMigrationPasses() {
        store("Keep\t1,2,3,4,5")
        val before = context.getSharedPreferences("compress_settings", Context.MODE_PRIVATE)
            .getString("editor_looks", "")

        LegacyLookMigration.migrate(context)

        val after = context.getSharedPreferences("compress_settings", Context.MODE_PRIVATE)
            .getString("editor_looks", "")
        assertEquals(before, after)
    }
}
