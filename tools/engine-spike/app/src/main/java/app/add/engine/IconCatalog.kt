package app.add.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

data class IconPathSpec(
    val d: String,
    val fill: String?,
    val stroke: String?,
    val strokeWidth: Float,
    val opacity: Float,
    val rule: String?,
    val matrix: FloatArray?,
)

data class IconGlyph(
    val id: String,
    val name: String,
    val brandSlug: String,
    val brandLabel: String,
    val style: String,
    val category: String,
    val viewBox: List<Float>,
    val paths: List<IconPathSpec>,
)

data class IconCategory(val name: String, val count: Int)

data class IconBrand(
    val slug: String,
    val label: String,
    val style: String,
    val licence: String,
    val source: String,
    val count: Int,
    val categories: List<IconCategory>,
) {
    val displayName: String get() = if (style.isBlank()) label else "$label $style"
}

data class IconEntry(
    val id: String,
    val name: String,
    val brandSlug: String,
    val brandLabel: String,
    val category: String,
)

sealed class IconCatalogState {
    data object Idle : IconCatalogState()
    data object Loading : IconCatalogState()
    data class Ready(
        val brands: List<IconBrand>,
        val entries: List<IconEntry>,
        val glyphs: Map<String, IconGlyph>,
        val total: Int,
    ) : IconCatalogState()

    data class Failed(val message: String) : IconCatalogState()
}

/**
 * Metadata and the shared 240-glyph path cache. Brand files are parsed in full
 * once, off the main thread; the sheet then filters an in-memory list rather
 * than reopening assets.
 */
object IconCatalog {

    var state by mutableStateOf<IconCatalogState>(IconCatalogState.Idle)
        private set

    suspend fun load(context: Context) {
        if (state is IconCatalogState.Ready || state is IconCatalogState.Loading) return
        state = IconCatalogState.Loading
        val loaded = runCatching {
            withContext(Dispatchers.IO) { parse(context.applicationContext) }
        }
        state = loaded.fold(
            onSuccess = { it },
            onFailure = { IconCatalogState.Failed(it.message ?: it.javaClass.simpleName) },
        )
    }

    fun reset() {
        state = IconCatalogState.Idle
    }

    private fun parse(context: Context): IconCatalogState.Ready {
        val index = readJson(context, "icons/index.json")
        val brandArray = index.getJSONArray("brands")
        val brands = ArrayList<IconBrand>(brandArray.length())
        val glyphs = LinkedHashMap<String, IconGlyph>()
        val entries = ArrayList<IconEntry>(index.optInt("count", 0))

        for (i in 0 until brandArray.length()) {
            val meta = brandArray.getJSONObject(i)
            val slug = meta.getString("slug")
            val label = meta.optString("label", slug)
            val style = meta.optString("style", "")
            val cats = ArrayList<IconCategory>()
            meta.optJSONArray("cats")?.let { array ->
                for (c in 0 until array.length()) {
                    val item = array.getJSONObject(c)
                    cats.add(IconCategory(item.getString("name"), item.optInt("count", 0)))
                }
            }
            val body = readJson(context, "icons/$slug.json")
            val icons = body.getJSONArray("icons")
            for (g in 0 until icons.length()) {
                val glyph = parseGlyph(icons.getJSONObject(g))
                if (glyph != null && !glyphs.containsKey(glyph.id)) {
                    glyphs[glyph.id] = glyph
                }
            }
            brands.add(
                IconBrand(
                    slug = slug,
                    label = label,
                    style = style,
                    licence = meta.optString("licence", ""),
                    source = meta.optString("source", ""),
                    count = meta.optInt("count", 0),
                    categories = cats,
                )
            )
        }

        val indexIcons = index.getJSONArray("icons")
        for (i in 0 until indexIcons.length()) {
            val item = indexIcons.getJSONObject(i)
            val id = item.getString("id")
            val glyph = glyphs[id] ?: continue
            entries.add(
                IconEntry(
                    id = id,
                    name = item.optString("name", glyph.name),
                    brandSlug = item.optString("b", glyph.brandSlug),
                    brandLabel = glyph.brandLabel,
                    category = item.optString("c", glyph.category),
                )
            )
        }
        entries.sortWith(compareBy({ it.brandSlug }, { it.name.lowercase() }))
        require(glyphs.isNotEmpty()) { "icon catalog is empty" }
        return IconCatalogState.Ready(brands, entries, glyphs, glyphs.size)
    }

    private fun parseGlyph(source: JSONObject): IconGlyph? {
        val id = source.optString("id", "")
        val name = source.optString("name", "")
        val view = source.optJSONArray("viewBox") ?: return null
        if (view.length() < 4 || id.isBlank()) return null
        val viewBox = List(4) { view.getDouble(it).toFloat() }
        if (viewBox[2] <= 0f || viewBox[3] <= 0f) return null
        val pathsJson = source.optJSONArray("paths") ?: return null
        val paths = ArrayList<IconPathSpec>(pathsJson.length())
        for (p in 0 until pathsJson.length()) {
            val path = pathsJson.getJSONObject(p)
            val d = path.optString("d", "")
            if (d.isBlank()) continue
            paths.add(
                IconPathSpec(
                    d = d,
                    fill = path.optString("f", "none"),
                    stroke = path.optString("s", "none"),
                    strokeWidth = path.optDouble("w", 1.0).toFloat(),
                    opacity = path.optDouble("o", 1.0).toFloat().coerceIn(0f, 1f),
                    rule = path.optString("r", "nonzero"),
                    matrix = path.optJSONArray("m")?.let { array ->
                        FloatArray(array.length()) { array.getDouble(it).toFloat() }
                    },
                )
            )
        }
        if (paths.isEmpty()) return null
        return IconGlyph(
            id = id,
            name = name,
            brandSlug = source.optString("brand", ""),
            brandLabel = source.optString("brandLabel", ""),
            style = source.optString("style", ""),
            category = source.optString("category", ""),
            viewBox = viewBox,
            paths = paths,
        )
    }

    private fun readJson(context: Context, path: String): JSONObject {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    fun search(
        ready: IconCatalogState.Ready,
        query: String,
        brand: String?,
        category: String?,
    ): List<IconEntry> {
        val needle = query.trim().lowercase()
        return ready.entries.filter { entry ->
            (brand == null || entry.brandSlug == brand) &&
                (category == null || entry.category == category) &&
                (needle.isEmpty() || entry.name.lowercase().contains(needle) ||
                    entry.category.lowercase().contains(needle))
        }
    }
}

private data class PreparedPath(val path: Path, val spec: IconPathSpec)

/** Shared path cache: a 240-glyph window is more than any one sheet screen. */
private object IconPathCache {
    private const val LIMIT = 240
    private val cache = LinkedHashMap<String, List<PreparedPath>>(64, 0.75f, true)

    fun paths(glyph: IconGlyph): List<PreparedPath> = synchronized(cache) {
        cache[glyph.id]?.let { return it }
        val built = glyph.paths.mapNotNull { spec ->
            val path = runCatching { PathParser.createPathFromPathData(spec.d) }.getOrNull()
                ?: return@mapNotNull null
            path.fillType = if (spec.rule == "evenodd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
            if (spec.matrix != null) {
                val m = spec.matrix
                path.transform(
                    Matrix().apply {
                        setValues(
                            floatArrayOf(
                                m[0], m[2], m[4],
                                m[1], m[3], m[5],
                                0f, 0f, 1f,
                            )
                        )
                    }
                )
            }
            PreparedPath(path, spec)
        }
        if (cache.size >= LIMIT) {
            cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[glyph.id] = built
        built
    }
}

object IconRenderer {

    fun draw(
        canvas: Canvas,
        glyph: IconGlyph,
        tint: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        // A document saved before viewBox was serialised decodes to an empty list, and
        // indexing it aborted the whole engine tab (IndexOutOfBounds at draw). Skip the
        // glyph rather than bring the screen down; the decoder repairs old documents.
        val view = glyph.viewBox
        if (view.size < 4) return
        val viewW = view[2].coerceAtLeast(1f)
        val viewH = view[3].coerceAtLeast(1f)
        val fit = minOf(width / viewW, height / viewH)
        if (fit <= 0f) return
        val offsetX = (width - viewW * fit) / 2f - view[0] * fit
        val offsetY = (height - viewH * fit) / 2f - view[1] * fit
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.save()
        canvas.translate(left, top)
        canvas.translate(offsetX, offsetY)
        canvas.scale(fit, fit)
        for (prepared in IconPathCache.paths(glyph)) {
            val spec = prepared.spec
            val fill = parseColor(spec.fill, tint)
            if (fill != null) {
                fillPaint.color = fill
                fillPaint.alpha = (Color.alpha(fill) * spec.opacity).toInt().coerceIn(0, 255)
                canvas.drawPath(prepared.path, fillPaint)
            }
            val stroke = parseColor(spec.stroke, tint)
            if (stroke != null && spec.strokeWidth > 0f) {
                strokePaint.color = stroke
                strokePaint.strokeWidth = spec.strokeWidth
                strokePaint.alpha = (Color.alpha(stroke) * spec.opacity).toInt().coerceIn(0, 255)
                canvas.drawPath(prepared.path, strokePaint)
            }
        }
        canvas.restore()
    }

    private fun parseColor(value: String?, tint: Int): Int? {
        if (value.isNullOrBlank() || value == "none" || value == "transparent") return null
        if (value == "currentColor") return tint
        val text = value.trim()
        if (text.startsWith("url(")) return tint
        val expanded = when {
            text.length == 4 && text.startsWith("#") ->
                "#FF${text[1]}${text[1]}${text[2]}${text[2]}${text[3]}${text[3]}"
            text.length == 5 && text.startsWith("#") ->
                "#${text[1]}${text[1]}${text[2]}${text[2]}${text[3]}${text[3]}${text[4]}${text[4]}"
            else -> text
        }
        return runCatching { Color.parseColor(expanded) }.getOrNull()
    }
}
