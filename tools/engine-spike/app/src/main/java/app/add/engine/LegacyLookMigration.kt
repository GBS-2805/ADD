package app.add.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LegacyTypeStyle(
    val colour: Int,
    val align: Int,
    val tracking: Float,
    val leading: Float,
    val opacity: Int,
    val shadow: Boolean,
)

data class LegacyLook(
    val name: String,
    val adjustments: IntArray,
    val types: List<LegacyTypeStyle>,
    val raw: String,
)

data class MigrationResult(
    val imported: Int,
    val skipped: Int,
    val warnings: List<String>,
    val looks: List<LegacyLook>,
)

/**
 * Reads the old `StyleStore` preference line format without deleting or rewriting it.
 * The sidecar is versioned and keeps both the original line and the parsed fields.
 */
object LegacyLookMigration {
    private const val PREFS = "compress_settings"
    private const val KEY = "editor_looks"
    private const val SIDECAR = "legacy-looks.json"

    fun migrate(context: Context): MigrationResult {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()
        val looks = mutableListOf<LegacyLook>()
        val warnings = mutableListOf<String>()
        var skipped = 0
        stored.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parsed = parse(line)
            if (parsed == null) {
                skipped++
                warnings += "Malformed look skipped: ${line.take(40)}"
            } else {
                looks += parsed
                if (parsed.adjustments[3] != 0) {
                    warnings += "${parsed.name}: warmth is not represented by the engine grade"
                }
                if (parsed.types.any { it.shadow }) {
                    warnings += "${parsed.name}: type shadow is not represented by the engine"
                }
            }
        }
        writeSidecar(context, looks, warnings, skipped)
        return MigrationResult(looks.size, skipped, warnings, looks)
    }

    /** Applies on request only; no document is changed during migration. */
    fun apply(doc: EngineDocument, look: LegacyLook): Boolean {
        val a = look.adjustments
        val effects = LayerEffects(
            exposure = (a.getOrElse(0) { 0 } / 100f).coerceIn(-1f, 1f),
            contrast = (a.getOrElse(1) { 0 } / 100f).coerceIn(-1f, 1f),
            saturation = (a.getOrElse(2) { 0 } / 100f).coerceIn(-1f, 1f),
            // Fade lifts the shadow end in the old matrix.
            shadows = (a.getOrElse(4) { 0 } / 100f).coerceIn(-1f, 1f),
        )
        doc.applyEffectsPreset("LEGACY ${look.name}", effects)
        val textLayers = doc.layers.filterIsInstance<TextLayer>()
        look.types.forEachIndexed { index, style ->
            val layer = textLayers.getOrNull(index) ?: return@forEachIndexed
            doc.select(layer.id)
            doc.setFill(style.colour or 0xFF000000.toInt())
            doc.setAlign(TextAlign.entries.getOrElse(style.align) { TextAlign.Left })
            doc.setTracking(style.tracking)
            doc.setLineHeight(style.leading)
            doc.setOpacity(style.opacity / 255f)
        }
        return textLayers.isNotEmpty() || doc.selected != null
    }

    private fun parse(line: String): LegacyLook? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val name = line.substring(0, tab).trim()
        if (name.isEmpty()) return null
        val chunks = line.substring(tab + 1).split('\t')
        val values = chunks.getOrNull(0)?.split(',')?.map { it.trim().toIntOrNull() }
            ?: return null
        if (values.size != 5 || values.any { it == null }) return null
        val types = if (chunks.size > 1) {
            val parsed = chunks[1].split(';').filter { it.isNotBlank() }.map { parseType(it) }
            if (parsed.any { it == null }) emptyList() else parsed.filterNotNull()
        } else emptyList()
        return LegacyLook(name, values.map { it!! }.toIntArray(), types, line)
    }

    private fun parseType(raw: String): LegacyTypeStyle? {
        val p = raw.split(',').map { it.trim() }
        if (p.size != 6) return null
        return try {
            LegacyTypeStyle(
                colour = p[0].toInt(),
                align = p[1].toInt(),
                tracking = p[2].toInt() / 100f,
                leading = p[3].toInt() / 100f,
                opacity = p[4].toInt(),
                shadow = p[5] == "1",
            )
        } catch (bad: NumberFormatException) {
            null
        }
    }

    private fun writeSidecar(
        context: Context,
        looks: List<LegacyLook>,
        warnings: List<String>,
        skipped: Int,
    ) {
        val root = JSONObject()
            .put("version", 1)
            .put("imported", looks.size)
            .put("skipped", skipped)
            .put("warnings", JSONArray(warnings))
        val items = JSONArray()
        looks.forEach { look ->
            val types = JSONArray()
            look.types.forEach { style ->
                types.put(
                    JSONObject()
                        .put("colour", style.colour)
                        .put("align", style.align)
                        .put("tracking", style.tracking)
                        .put("leading", style.leading)
                        .put("opacity", style.opacity)
                        .put("shadow", style.shadow),
                )
            }
            items.put(
                JSONObject()
                    .put("name", look.name)
                    .put("raw", look.raw)
                    .put("adjustments", JSONArray(look.adjustments.toList()))
                    .put("types", types),
            )
        }
        root.put("looks", items)
        File(context.filesDir, SIDECAR).writeText(root.toString())
    }
}
