package app.add.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
    // 3 adds the optional paint and style blocks. Schema 1 and 2 documents carry
    // neither and decode to solid paint, normal blend and bypassed effects.
    // 4 adds groups, per-layer skew and the entered group; older documents decode
    // with no groups, skew 0 and the artboard as the editing level. Type family,
    // tracking and case are optional v4 fields: a v3 or early-v4 file opens in
    // Poppins with no tracking and no case transform.
    // 5 adds material ink strokes: seeded recipes and normalized points, never pixels.
    const val VERSION = 5

    data class Document(
        val layers: List<Layer>,
        val selectedId: String?,
        val zoom: Float,
        val locked: Set<String> = emptySet(),
        val selectedIds: Set<String> = emptySet(),
        val editingGroupId: String? = null,
        val motion: Map<String, MotionPath> = emptyMap(),
        val texture: TextureSpec? = null,
        /** Non-null when a saved texture reference could not be regenerated. */
        val textureWarning: String? = null,
        val ink: List<TextureInk> = emptyList(),
    )

    fun encode(
        layers: List<Layer>,
        selectedId: String?,
        zoom: Float,
        locked: Set<String> = emptySet(),
        selectedIds: Set<String> = emptySet(),
        editingGroupId: String? = null,
        motion: Map<String, MotionPath> = emptyMap(),
        texture: TextureSpec? = null,
        ink: List<TextureInk> = emptyList(),
    ): String {
        val root = JSONObject()
        root.put("schema", VERSION)
        root.put("locked", JSONArray(locked.toList()))
        root.put("zoom", zoom.toDouble())
        root.put("selected", selectedId ?: "")
        root.put("selectedIds", JSONArray(selectedIds.toList()))
        root.put("editingGroup", editingGroupId ?: "")
        val array = JSONArray()
        layers.forEach { array.put(encodeLayer(it)) }
        root.put("layers", array)
        val paths = JSONArray()
        motion.forEach { (id, path) ->
            paths.put(
                JSONObject().apply {
                    put("id", id)
                    put("startX", path.start.x.toDouble())
                    put("startY", path.start.y.toDouble())
                    put("c1X", path.c1.x.toDouble())
                    put("c1Y", path.c1.y.toDouble())
                    put("c2X", path.c2.x.toDouble())
                    put("c2Y", path.c2.y.toDouble())
                    put("endX", path.end.x.toDouble())
                    put("endY", path.end.y.toDouble())
                    put("duration", path.durationMs)
                }
            )
        }
        root.put("motion", paths)
        texture?.let {
            root.put(
                "texture",
                JSONObject()
                    .put("family", it.family.id)
                    .put("seed", it.seed)
                    .put("opacity", it.opacity.toDouble())
                    .put("scale", it.scale.toDouble())
                    .put("blend", it.blend.name),
            )
        }
        if (ink.isNotEmpty()) {
            root.put("ink", JSONArray().also { array -> ink.forEach { array.put(encodeInk(it)) } })
        }
        return root.toString()
    }

    fun decode(text: String): Document? = runCatching {
        val root = JSONObject(text)
        val schema = root.optInt("schema", 0)
        // 1 is the same document without locks or photographs; it still opens.
        if (schema !in 1..VERSION) return null
        val array = root.optJSONArray("layers") ?: return null
        val decoded = (0 until array.length()).map { index ->
            runCatching { decodeLayer(array.getJSONObject(index)) }.getOrNull()
        }
        // From 4 on, a layer this build does not understand refuses the whole file
        // rather than opening a document with a silent hole in it.
        if (schema >= 4 && decoded.any { it == null }) return null
        val layers = decoded.filterNotNull()
        if (layers.isEmpty()) return null
        val ids = layers.flattenTree().map { it.id }.toSet()
        val motion = root.optJSONArray("motion").let { paths ->
            if (paths == null) emptyMap() else buildMap {
                for (index in 0 until paths.length()) {
                    val o = paths.optJSONObject(index) ?: continue
                    val id = o.optString("id")
                    // A path for a layer this document does not carry is dropped
                    // rather than left to dangle against a later id.
                    if (id !in ids) continue
                    put(
                        id,
                        MotionPath(
                            start = MotionPoint(
                                o.optDouble("startX", 0.0).toFloat(),
                                o.optDouble("startY", 0.0).toFloat(),
                            ),
                            c1 = MotionPoint(
                                o.optDouble("c1X", 0.0).toFloat(),
                                o.optDouble("c1Y", 0.0).toFloat(),
                            ),
                            c2 = MotionPoint(
                                o.optDouble("c2X", 0.0).toFloat(),
                                o.optDouble("c2Y", 0.0).toFloat(),
                            ),
                            end = MotionPoint(
                                o.optDouble("endX", 0.0).toFloat(),
                                o.optDouble("endY", 0.0).toFloat(),
                            ),
                            durationMs = o.optInt("duration", EngineMotion.DefaultDurationMs),
                        ),
                    )
                }
            }
        }
        val textureRaw = root.opt("texture")
        val texture = decodeTexture(textureRaw as? JSONObject)
        val ink = decodeInk(root.optJSONArray("ink"))
        Document(
            layers = layers,
            selectedId = root.optString("selected").ifBlank { null },
            zoom = root.optDouble("zoom", 1.0).toFloat().takeIf { it.isFinite() } ?: 1f,
            locked = root.optJSONArray("locked").let { array ->
                if (array == null) emptySet() else (0 until array.length())
                    .mapNotNull { index -> array.optString(index).ifBlank { null } }
                    .toSet()
            },
            selectedIds = root.optJSONArray("selectedIds").let { array ->
                if (array == null) emptySet() else (0 until array.length())
                    .mapNotNull { index -> array.optString(index).ifBlank { null } }
                    .toSet()
            },
            editingGroupId = root.optString("editingGroup").ifBlank { null },
            motion = motion,
            texture = texture,
            textureWarning = if (textureRaw != null && texture == null) {
                "TEXTURE DROPPED · unknown family or invalid recipe; paper kept clean"
            } else null,
            ink = ink,
        )
    }.getOrNull()

    /**
     * A texture is a recipe, not a bitmap. An unknown family, missing seed or
     * out-of-range opacity/scale is a clean fallback: the page opens with paper and a
     * warning, never a crash and never a half-rendered field.
     */
    private fun decodeTexture(o: JSONObject?): TextureSpec? {
        if (o == null) return null
        val family = TextureFamily.fromId(o.optString("family")) ?: return null
        val seedValue = o.opt("seed") as? Number ?: return null
        val opacity = o.optDouble("opacity", family.defaultOpacity.toDouble()).toFloat()
        val scale = o.optDouble("scale", 1.0).toFloat()
        if (!opacity.isFinite() || opacity !in 0f..1f) return null
        if (!scale.isFinite() || scale !in 0.25f..4f) return null
        return TextureSpec(
            family = family,
            seed = seedValue.toInt(),
            opacity = opacity,
            scale = scale,
            blend = BlendKind.fromName(o.optString("blend", family.defaultBlend.name)),
        )
    }

    private fun encodeInk(ink: TextureInk): JSONObject = JSONObject().apply {
        put("id", ink.id)
        put("width", ink.width.toDouble())
        put(
            "recipe",
            JSONObject()
                .put("family", ink.recipe.family.id)
                .put("seed", ink.recipe.seed)
                .put("opacity", ink.recipe.opacity.toDouble())
                .put("scale", ink.recipe.scale.toDouble())
                .put("blend", ink.recipe.blend.name),
        )
        put("points", JSONArray().also { points ->
            ink.points.forEach { point ->
                points.put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
            }
        })
    }

    private fun decodeInk(array: JSONArray?): List<TextureInk> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val recipe = decodeTexture(o.optJSONObject("recipe")) ?: return@mapNotNull null
            val points = o.optJSONArray("points").orEmpty().mapNotNull { point ->
                val x = point.optDouble("x", Double.NaN)
                val y = point.optDouble("y", Double.NaN)
                if (!x.isFinite() || !y.isFinite()) null else InkPoint(x.toFloat(), y.toFloat())
            }
            if (points.isEmpty()) return@mapNotNull null
            TextureInk(
                id = o.optString("id").ifBlank { "ink-$index" },
                recipe = recipe,
                width = o.optDouble("width", 0.075).toFloat(),
                points = points,
            ).clean()
        }
    }

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
        o.put("skewX", layer.skewX.toDouble())
        when (layer) {
            is GroupLayer -> {
                o.put("scaleX", layer.scaleX.toDouble())
                o.put("scaleY", layer.scaleY.toDouble())
                val children = JSONArray()
                layer.children.forEach { children.put(encodeLayer(it)) }
                o.put("children", children)
            }
            is VectorLayer -> {
                o.put("network", encodeNetwork(layer.network))
                o.put("fill", layer.fill)
                o.put("stroke", layer.stroke)
                o.put("strokeWidth", layer.strokeWidth.toDouble())
                layer.paint?.let { o.put("paint", encodePaint(it)) }
            }
            is TextLayer -> {
                o.put("text", layer.text)
                o.put("fontSize", layer.fontSize.toDouble())
                o.put("lineHeight", layer.lineHeight.toDouble())
                o.put("color", layer.color)
                o.put("align", layer.align.name)
                o.put("weight", layer.weight)
                o.put("fontFamily", layer.fontFamily)
                o.put("tracking", layer.tracking.toDouble())
                o.put("textCase", layer.textCase.name)
                layer.paint?.let { o.put("paint", encodePaint(it)) }
            }
            is IconLayer -> {
                o.put("glyph", encodeGlyph(layer.glyph))
                o.put("tint", layer.tint)
            }
            is ModelLayer -> {
                o.put("asset", layer.asset.name)
                o.put("scaleToUnits", layer.scaleToUnits.toDouble())
            }
            is ImageLayer -> {
                o.put("path", layer.path)
                o.put("fill", layer.fill)
                o.put("crop", JSONObject()
                    .put("x", layer.crop.x.toDouble())
                    .put("y", layer.crop.y.toDouble())
                    .put("w", layer.crop.w.toDouble())
                    .put("h", layer.crop.h.toDouble()))
            }
        }
        if (layer.style != LayerStyle()) o.put("style", encodeStyle(layer.style))
        return o
    }

    /** A group with one unreadable child is refused, never opened half-decoded. */
    private fun decodeChildren(o: JSONObject): List<Layer>? {
        val array = o.optJSONArray("children") ?: return emptyList()
        val children = (0 until array.length()).map { index ->
            runCatching { decodeLayer(array.getJSONObject(index)) }.getOrNull()
        }
        return if (children.any { it == null }) null else children.filterNotNull()
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
        val skew = o.optDouble("skewX", 0.0).toFloat()
        return when (o.optString("kind")) {
            "group" -> GroupLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                skewX = skew,
                scaleX = o.optDouble("scaleX", 1.0).toFloat(),
                scaleY = o.optDouble("scaleY", 1.0).toFloat(),
                children = decodeChildren(o) ?: return null,
                style = decodeStyle(o.optJSONObject("style")),
            )
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
                skewX = skew,
                network = o.optJSONObject("network")?.let { decodeNetwork(it) } ?: starNetwork(),
                fill = o.optInt("fill", 0xFF0FBF92.toInt()),
                stroke = o.optInt("stroke", 0xFF11231C.toInt()),
                strokeWidth = o.optDouble("strokeWidth", 4.0).toFloat(),
                paint = decodePaint(o.optJSONObject("paint")),
                style = decodeStyle(o.optJSONObject("style")),
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
                skewX = skew,
                text = o.optString("text", "TYPE"),
                fontSize = o.optDouble("fontSize", 48.0).toFloat(),
                lineHeight = o.optDouble("lineHeight", 1.0).toFloat(),
                color = o.optInt("color", 0xFF11231C.toInt()),
                align = runCatching { TextAlign.valueOf(o.optString("align", "Left")) }
                    .getOrDefault(TextAlign.Left),
                weight = o.optInt("weight", 600),
                fontFamily = o.optString("fontFamily", "Poppins").ifBlank { "Poppins" },
                tracking = o.optDouble("tracking", 0.0).toFloat(),
                textCase = runCatching { TextCase.valueOf(o.optString("textCase", "None")) }
                    .getOrDefault(TextCase.None),
                paint = decodePaint(o.optJSONObject("paint")),
                style = decodeStyle(o.optJSONObject("style")),
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
                skewX = skew,
                glyph = o.optJSONObject("glyph")?.let { decodeGlyph(it) } ?: return null,
                tint = o.optInt("tint", 0xFF11231C.toInt()),
                style = decodeStyle(o.optJSONObject("style")),
            )
            "image" -> ImageLayer(
                id = id,
                name = name,
                visible = visible,
                x = x,
                y = y,
                w = w,
                h = h,
                opacity = opacity,
                rotation = rotation,
                skewX = skew,
                path = o.optString("path"),
                fill = o.optBoolean("fill", true),
                crop = o.optJSONObject("crop")?.let { crop ->
                    ImageCrop(
                        x = crop.optDouble("x", 0.0).toFloat(),
                        y = crop.optDouble("y", 0.0).toFloat(),
                        w = crop.optDouble("w", 1.0).toFloat(),
                        h = crop.optDouble("h", 1.0).toFloat(),
                    ).normalized()
                } ?: ImageCrop(),
                style = decodeStyle(o.optJSONObject("style")),
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
                skewX = skew,
                scaleToUnits = o.optDouble("scaleToUnits", 1.0).toFloat(),
                asset = runCatching { ModelAsset.valueOf(o.optString("asset", "Gem")) }
                    .getOrDefault(ModelAsset.Gem),
                style = decodeStyle(o.optJSONObject("style")),
            )
            else -> null
        }
    }

    private fun encodePaint(paint: PaintSpec): JSONObject = JSONObject().apply {
        when (paint) {
            is PaintSpec.Solid -> {
                put("type", "solid")
                put("color", paint.color)
            }
            is PaintSpec.Gradient -> {
                put("type", "gradient")
                put("kind", paint.kind.name)
                put("stops", JSONArray().also { array ->
                    paint.stops.forEach { stop ->
                        array.put(
                            JSONObject()
                                .put("offset", stop.offset.toDouble())
                                .put("color", stop.color)
                        )
                    }
                })
            }
            is PaintSpec.Image -> {
                put("type", "image")
                put("path", paint.path)
                put("fill", paint.fill)
            }
            is PaintSpec.Pattern -> {
                put("type", "pattern")
                put("kind", paint.kind.name)
                put("foreground", paint.foreground)
                put("background", paint.background)
                put("scale", paint.scale.toDouble())
            }
        }
    }

    private fun decodePaint(o: JSONObject?): PaintSpec? {
        if (o == null) return null
        return when (o.optString("type")) {
            "solid" -> PaintSpec.Solid(o.optInt("color", 0xFF0FBF92.toInt()))
            "gradient" -> {
                val kind = runCatching { PaintKind.valueOf(o.optString("kind", "Linear")) }
                    .getOrDefault(PaintKind.Linear)
                val stops = o.optJSONArray("stops").orEmpty().map { stop ->
                    PaintStop(
                        offset = stop.optDouble("offset", 0.0).toFloat().coerceIn(0f, 1f),
                        color = stop.optInt("color", 0xFF000000.toInt()),
                    )
                }
                if (stops.size < 2) null else PaintSpec.Gradient(kind, stops)
            }
            "image" -> PaintSpec.Image(
                path = o.optString("path"),
                fill = o.optBoolean("fill", true),
            )
            "pattern" -> PaintSpec.Pattern(
                kind = runCatching { PatternKind.valueOf(o.optString("kind", "Checks")) }
                    .getOrDefault(PatternKind.Checks),
                foreground = o.optInt("foreground", 0xFF11231C.toInt()),
                background = o.optInt("background", 0xFFF3EBDD.toInt()),
                scale = o.optDouble("scale", 1.0).toFloat(),
            )
            else -> null
        }
    }

    private fun encodeStyle(style: LayerStyle): JSONObject = JSONObject().apply {
        put("blend", style.blend.name)
        put("clip", style.clipToBelow)
        put("effects", encodeEffects(style.effects))
        style.mask?.let { mask ->
            put("mask", JSONObject()
                .put("enabled", mask.enabled)
                .put("strokes", JSONArray().also { strokes ->
                    mask.strokes.forEach { stroke ->
                        strokes.put(
                            JSONObject()
                                .put("radius", stroke.radius.toDouble())
                                .put("erase", stroke.erase)
                                .put("points", JSONArray().also { points ->
                                    stroke.points.forEach { point ->
                                        points.put(
                                            JSONObject()
                                                .put("x", point.x.toDouble())
                                                .put("y", point.y.toDouble())
                                        )
                                    }
                                })
                        )
                    }
                }))
        }
    }

    private fun decodeStyle(o: JSONObject?): LayerStyle {
        if (o == null) return LayerStyle()
        val maskObject = o.optJSONObject("mask")
        return LayerStyle(
            blend = BlendKind.fromName(o.optString("blend", "Normal")),
            clipToBelow = o.optBoolean("clip", false),
            effects = decodeEffects(o.optJSONObject("effects")),
            mask = maskObject?.let { mask ->
                LayerMask(
                    enabled = mask.optBoolean("enabled", true),
                    strokes = mask.optJSONArray("strokes").orEmpty().map { stroke ->
                        MaskStroke(
                            points = stroke.optJSONArray("points").orEmpty().map { point ->
                                MaskPoint(
                                    x = point.optDouble("x", 0.0).toFloat(),
                                    y = point.optDouble("y", 0.0).toFloat(),
                                )
                            },
                            radius = stroke.optDouble("radius", 0.1).toFloat(),
                            erase = stroke.optBoolean("erase", true),
                        )
                    },
                )
            },
        )
    }

    private fun encodeEffects(effects: LayerEffects): JSONObject = JSONObject().apply {
        put("enabled", effects.enabled)
        put("gradeEnabled", effects.gradeEnabled)
        put("blurEnabled", effects.blurEnabled)
        put("grainEnabled", effects.grainEnabled)
        put("halftoneEnabled", effects.halftoneEnabled)
        put("exposure", effects.exposure.toDouble())
        put("contrast", effects.contrast.toDouble())
        put("saturation", effects.saturation.toDouble())
        put("shadows", effects.shadows.toDouble())
        put("midtones", effects.midtones.toDouble())
        put("highlights", effects.highlights.toDouble())
        put("blur", effects.blur.toDouble())
        put("grain", effects.grain.toDouble())
        put("halftone", effects.halftone.toDouble())
        put("duotone", effects.duotone)
        put("duotoneDark", effects.duotoneDark)
        put("duotoneLight", effects.duotoneLight)
    }

    private fun decodeEffects(o: JSONObject?): LayerEffects {
        if (o == null) return LayerEffects()
        return LayerEffects(
            enabled = o.optBoolean("enabled", true),
            gradeEnabled = o.optBoolean("gradeEnabled", true),
            blurEnabled = o.optBoolean("blurEnabled", true),
            grainEnabled = o.optBoolean("grainEnabled", true),
            halftoneEnabled = o.optBoolean("halftoneEnabled", true),
            exposure = o.optDouble("exposure", 0.0).toFloat(),
            contrast = o.optDouble("contrast", 0.0).toFloat(),
            saturation = o.optDouble("saturation", 0.0).toFloat(),
            shadows = o.optDouble("shadows", 0.0).toFloat(),
            midtones = o.optDouble("midtones", 0.0).toFloat(),
            highlights = o.optDouble("highlights", 0.0).toFloat(),
            blur = o.optDouble("blur", 0.0).toFloat(),
            grain = o.optDouble("grain", 0.0).toFloat(),
            halftone = o.optDouble("halftone", 0.0).toFloat(),
            duotone = o.optBoolean("duotone", false),
            duotoneDark = o.optInt("duotoneDark", 0xFF11231C.toInt()),
            duotoneLight = o.optInt("duotoneLight", 0xFFD6E381.toInt()),
        )
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
                // Region segments are strings, not objects. The object reader this used
                // to call swallowed every entry and left a region with no path, so a
                // restored vector layer painted nothing at all.
                segments = region.optJSONArray("segments")?.let { array ->
                    List(array.length()) { array.getString(it) }
                } ?: emptyList(),
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
        // This used to reuse the JSONObject array reader, which drops every number in
        // a number array, so every saved icon came back with no viewBox and drawing it
        // threw IndexOutOfBounds (IconCatalog.kt). Read the numbers as numbers.
        viewBox = o.optJSONArray("viewBox")?.let { array ->
            List(array.length()) { array.getDouble(it).toFloat() }
        } ?: emptyList(),
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
            is ImageLayer -> "image"
            is GroupLayer -> "group"
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
    private const val LIBRARY = "library"

    data class Entry(val key: String, val name: String, val layers: Int, val modified: Long)

    /**
     * Temp file, fsync, then rename: a crash between the write and the swap leaves
     * the previous document (and its backup) untouched. The last good file is kept
     * as .bak so a corrupt main file can still open.
     */
    fun save(context: Context, doc: EngineDocument): Boolean = runCatching {
        val target = File(context.filesDir, FILE)
        val temp = File(context.filesDir, "$FILE.tmp")
        val backup = File(context.filesDir, "$FILE.bak")
        val text = DocumentCodec.encode(
            doc.layers,
            doc.selectedId,
            doc.zoom,
            doc.lockedIds,
            doc.selectedIds,
            doc.editingGroupId,
            doc.motion,
            doc.texture,
            doc.ink,
        )
        FileOutputStream(temp).use { out ->
            out.write(text.toByteArray())
            out.flush()
            out.fd.sync()
        }
        if (target.exists()) {
            backup.delete()
            if (!target.renameTo(backup)) throw IOException("could not back up $FILE")
        }
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            throw IOException("could not replace $FILE")
        }
        true
    }.getOrDefault(false)

    fun restore(context: Context): DocumentCodec.Document? {
        val main = File(context.filesDir, FILE)
        val backup = File(context.filesDir, "$FILE.bak")
        return listOf(main, backup).firstNotNullOfOrNull { file ->
            if (!file.exists()) null else runCatching { DocumentCodec.decode(file.readText()) }.getOrNull()
        }
    }

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, FILE).delete()
            File(context.filesDir, "$FILE.tmp").delete()
            File(context.filesDir, "$FILE.bak").delete()
        }
    }

    private fun libraryDir(context: Context) = File(context.filesDir, LIBRARY).apply { mkdirs() }

    private fun slug(name: String): String = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "untitled" }

    /** Named documents, newest first. A broken file is skipped, never fatal. */
    fun library(context: Context): List<Entry> {
        val files = libraryDir(context).listFiles()?.filter { it.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            val doc = runCatching { DocumentCodec.decode(file.readText()) }.getOrNull() ?: return@mapNotNull null
            Entry(
                key = file.nameWithoutExtension,
                name = file.nameWithoutExtension.replace('-', ' '),
                layers = doc.layers.size,
                modified = file.lastModified(),
            )
        }.sortedByDescending { it.modified }
    }

    fun saveAs(context: Context, name: String, doc: EngineDocument): String? = runCatching {
        val key = slug(name)
        val target = File(libraryDir(context), "$key.json")
        val temp = File(libraryDir(context), "$key.json.tmp")
        temp.writeText(
            DocumentCodec.encode(
                doc.layers,
                doc.selectedId,
                doc.zoom,
                doc.lockedIds,
                doc.selectedIds,
                doc.editingGroupId,
                doc.motion,
                doc.texture,
                doc.ink,
            )
        )
        if (!temp.renameTo(target)) throw IOException("could not save $key")
        key
    }.getOrNull()

    fun open(context: Context, key: String): DocumentCodec.Document? = runCatching {
        val file = File(libraryDir(context), "$key.json")
        if (!file.exists()) null else DocumentCodec.decode(file.readText())
    }.getOrNull()

    fun delete(context: Context, key: String): Boolean = runCatching {
        File(libraryDir(context), "$key.json").delete()
    }.getOrDefault(false)
}
