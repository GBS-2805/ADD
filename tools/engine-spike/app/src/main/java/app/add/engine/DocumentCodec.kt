package app.add.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The document on disk, as versioned JSON.
 *
 * A screen's worth of layers is small, so this is one file rewritten after each edit
 * rather than a database: the whole document is the record, and a partial write can
 * only ever produce something the decoder rejects, which falls back to the seed
 * instead of a half-edited page.
 *
 * The version is checked, not assumed. A file written by a newer build is refused
 * rather than decoded into a document missing whatever that build added.
 */
object DocumentCodec {
    const val VERSION = 1

    data class Document(
        val layers: List<Layer>,
        val selectedId: String?,
        val zoom: Float,
    )

    fun encode(layers: List<Layer>, selectedId: String?, zoom: Float): String {
        val root = JSONObject()
        root.put("schema", VERSION)
        root.put("zoom", zoom.toDouble())
        root.put("selected", selectedId ?: "")
        val array = JSONArray()
        layers.forEach { array.put(encodeLayer(it)) }
        root.put("layers", array)
        return root.toString()
    }

    fun decode(text: String): Document? = runCatching {
        val root = JSONObject(text)
        if (root.optInt("schema", 0) != VERSION) return null
        val array = root.optJSONArray("layers") ?: return null
        val layers = (0 until array.length()).mapNotNull { index ->
            runCatching { decodeLayer(array.getJSONObject(index)) }.getOrNull()
        }
        if (layers.isEmpty()) return null
        Document(
            layers = layers,
            selectedId = root.optString("selected").ifBlank { null },
            zoom = root.optDouble("zoom", 1.0).toFloat().takeIf { it.isFinite() } ?: 1f,
        )
    }.getOrNull()

    private fun encodeLayer(layer: Layer): JSONObject {
        val o = JSONObject()
        o.put("kind", layer.kind)
        o.put("id", layer.id)
        o.put("name", layer.name)
        o.put("visible", layer.visible)
        o.put("x", layer.x.toDouble())
        o.put("y", layer.y.toDouble())
        o.put("w", layer.w.toDouble())
        o.put("h", layer.h.toDouble())
        o.put("opacity", layer.opacity.toDouble())
        o.put("rotation", layer.rotation.toDouble())
        when (layer) {
            is VectorLayer -> {
                o.put("network", encodeNetwork(layer.network))
                o.put("fill", layer.fill)
                o.put("stroke", layer.stroke)
                o.put("strokeWidth", layer.strokeWidth.toDouble())
            }
            is TextLayer -> {
                o.put("text", layer.text)
                o.put("fontSize", layer.fontSize.toDouble())
                o.put("lineHeight", layer.lineHeight.toDouble())
                o.put("color", layer.color)
                o.put("align", layer.align.name)
                o.put("weight", layer.weight)
            }
            is IconLayer -> {
                o.put("glyph", encodeGlyph(layer.glyph))
                o.put("tint", layer.tint)
            }
            is ModelLayer -> {
                o.put("asset", layer.asset.name)
                o.put("scaleToUnits", layer.scaleToUnits.toDouble())
            }
        }
        return o
    }

    private fun decodeLayer(o: JSONObject): Layer? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        val name = o.optString("name", id)
        val visible = o.optBoolean("visible", true)
        val x = o.optDouble("x", 0.0).toFloat()
        val y = o.optDouble("y", 0.0).toFloat()
        val w = o.optDouble("w", 0.1).toFloat()
        val h = o.optDouble("h", 0.1).toFloat()
        val opacity = o.optDouble("opacity", 1.0).toFloat()
        val rotation = o.optDouble("rotation", 0.0).toFloat()
        return when (o.optString("kind")) {
            "vector" -> VectorLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                network = o.optJSONObject("network")?.let { decodeNetwork(it) } ?: starNetwork(),
                fill = o.optInt("fill", 0xFF0FBF92.toInt()),
                stroke = o.optInt("stroke", 0xFF11231C.toInt()),
                strokeWidth = o.optDouble("strokeWidth", 4.0).toFloat(),
            )
            "text" -> TextLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                text = o.optString("text", "TYPE"),
                fontSize = o.optDouble("fontSize", 48.0).toFloat(),
                lineHeight = o.optDouble("lineHeight", 1.0).toFloat(),
                color = o.optInt("color", 0xFF11231C.toInt()),
                align = runCatching { TextAlign.valueOf(o.optString("align", "Left")) }
                    .getOrDefault(TextAlign.Left),
                weight = o.optInt("weight", 600),
            )
            "icon" -> IconLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                glyph = o.optJSONObject("glyph")?.let { decodeGlyph(it) } ?: return null,
                tint = o.optInt("tint", 0xFF11231C.toInt()),
            )
            "model" -> ModelLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                scaleToUnits = o.optDouble("scaleToUnits", 1.0).toFloat(),
                asset = runCatching { ModelAsset.valueOf(o.optString("asset", "Gem")) }
                    .getOrDefault(ModelAsset.Gem),
            )
            else -> null
        }
    }

    private fun encodeNetwork(network: VectorNetwork): JSONObject = JSONObject().apply {
        put("points", JSONArray().also { array ->
            network.points.forEach { point ->
                array.put(
                    JSONObject()
                        .put("id", point.id)
                        .put("x", point.x.toDouble())
                        .put("y", point.y.toDouble())
                )
            }
        })
        put("segments", JSONArray().also { array ->
            network.segments.forEach { segment ->
                array.put(
                    JSONObject()
                        .put("id", segment.id)
                        .put("a", segment.a)
                        .put("b", segment.b)
                )
            }
        })
        put("regions", JSONArray().also { array ->
            network.regions.forEach { region ->
                array.put(
                    JSONObject()
                        .put("id", region.id)
                        .put("fill", region.fill)
                        .put("segments", JSONArray(region.segments))
                )
            }
        })
    }

    private fun decodeNetwork(o: JSONObject): VectorNetwork {
        val points = o.optJSONArray("points").orEmpty().map { point ->
            VecPoint(
                id = point.optString("id"),
                x = point.optDouble("x", 0.0).toFloat(),
                y = point.optDouble("y", 0.0).toFloat(),
            )
        }
        val segments = o.optJSONArray("segments").orEmpty().map { segment ->
            VecSegment(
                id = segment.optString("id"),
                a = segment.optString("a"),
                b = segment.optString("b"),
            )
        }
        val regions = o.optJSONArray("regions").orEmpty().map { region ->
            VecRegion(
                id = region.optString("id"),
                segments = region.optJSONArray("segments").orEmpty().map { it.toString() },
                fill = region.optInt("fill", 0),
            )
        }
        return VectorNetwork(points, segments, regions)
    }

    private fun encodeGlyph(glyph: IconGlyph): JSONObject = JSONObject().apply {
        put("id", glyph.id)
        put("name", glyph.name)
        put("brandSlug", glyph.brandSlug)
        put("brandLabel", glyph.brandLabel)
        put("style", glyph.style)
        put("category", glyph.category)
        put("viewBox", JSONArray().also { array -> glyph.viewBox.forEach { array.put(it.toDouble()) } })
        put("paths", JSONArray().also { array ->
            glyph.paths.forEach { spec ->
                array.put(
                    JSONObject().apply {
                        put("d", spec.d)
                        put("fill", spec.fill ?: JSONObject.NULL)
                        put("stroke", spec.stroke ?: JSONObject.NULL)
                        put("strokeWidth", spec.strokeWidth.toDouble())
                        put("opacity", spec.opacity.toDouble())
                        put("rule", spec.rule ?: JSONObject.NULL)
                        spec.matrix?.let { matrix ->
                            put("matrix", JSONArray().also { values -> matrix.forEach { values.put(it.toDouble()) } })
                        }
                    }
                )
            }
        })
    }

    private fun decodeGlyph(o: JSONObject): IconGlyph = IconGlyph(
        id = o.optString("id"),
        name = o.optString("name"),
        brandSlug = o.optString("brandSlug"),
        brandLabel = o.optString("brandLabel"),
        style = o.optString("style"),
        category = o.optString("category"),
        viewBox = o.optJSONArray("viewBox").orEmpty().map { it.toString().toFloat() },
        paths = o.optJSONArray("paths").orEmpty().map { spec ->
            IconPathSpec(
                d = spec.optString("d"),
                fill = if (spec.isNull("fill")) null else spec.optString("fill"),
                stroke = if (spec.isNull("stroke")) null else spec.optString("stroke"),
                strokeWidth = spec.optDouble("strokeWidth", 1.0).toFloat(),
                opacity = spec.optDouble("opacity", 1.0).toFloat(),
                rule = if (spec.isNull("rule")) null else spec.optString("rule"),
                matrix = spec.optJSONArray("matrix")?.let { values ->
                    FloatArray(values.length()) { values.getDouble(it).toFloat() }
                },
            )
        },
    )

    private val Layer.kind: String
        get() = when (this) {
            is VectorLayer -> "vector"
            is TextLayer -> "text"
            is IconLayer -> "icon"
            is ModelLayer -> "model"
        }

    private fun JSONArray?.orEmpty(): List<JSONObject> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull {
            runCatching { array.getJSONObject(it) }.getOrNull()
        }
    }
}

/**
 * The one file the engine keeps. Saving is best-effort: a filled disk must not take
 * the screen down with it, and a document that failed to save is still on screen.
 */
object DocumentStore {
    private const val FILE = "document.json"

    fun save(context: Context, doc: EngineDocument) {
        runCatching {
            File(context.filesDir, FILE).writeText(
                DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom)
            )
        }
    }

    fun restore(context: Context): DocumentCodec.Document? = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) null else DocumentCodec.decode(file.readText())
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
