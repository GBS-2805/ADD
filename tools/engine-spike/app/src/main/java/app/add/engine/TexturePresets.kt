package app.add.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Textures are materials, not a stock-image shelf. Each family is a small seeded
 * field that an engraver, laser or press can actually use: masonry and stone for
 * architectural work, grain and cloth for physical stock, halftone and scanlines
 * for print, and calibration patterns for checking output.
 */
enum class TextureFamily(
    val id: String,
    val label: String,
    val note: String,
    val defaultOpacity: Float,
    val defaultBlend: BlendKind,
) {
    Brick("brick", "BRICK", "Masonry courses and mortar", 0.34f, BlendKind.Multiply),
    WoodGrain("wood-grain", "WOOD", "Laser-cut grain and rings", 0.38f, BlendKind.Multiply),
    Marble("marble", "MARBLE", "Veined stone etching", 0.30f, BlendKind.Multiply),
    Concrete("concrete", "CONCRETE", "Cast stone and plaster tooth", 0.34f, BlendKind.Multiply),
    Fabric("fabric", "FABRIC", "Woven cloth and knit lines", 0.32f, BlendKind.Multiply),
    Halftone("halftone", "HALFTONE", "Print dot screens", 0.40f, BlendKind.Multiply),
    GradientMesh("gradient-mesh", "MESH", "Smooth tonal meshes", 0.26f, BlendKind.Overlay),
    Grain("grain", "GRAIN", "Film and ink tooth", 0.30f, BlendKind.Overlay),
    Checker("checker", "CHECKER", "Registration and tile grid", 0.28f, BlendKind.Multiply),
    Scanline("scanline", "SCANLINE", "CRT and line-screen print", 0.30f, BlendKind.Multiply),
    ;

    companion object {
        fun fromId(id: String?): TextureFamily? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/** The catalog entry the inspector shows; its id is also the persisted family id. */
data class TexturePreset(
    val id: String,
    val name: String,
    val seed: Int,
    val kind: TextureFamily,
    val defaultOpacity: Float,
    val defaultBlend: BlendKind,
)

/**
 * An artboard texture. The family id is the document reference; the seed and
 * scale are the complete recipe. No bitmap is written to the document.
 */
data class TextureSpec(
    val family: TextureFamily,
    val seed: Int,
    val opacity: Float = family.defaultOpacity,
    val scale: Float = 1f,
    val blend: BlendKind = family.defaultBlend,
) {
    companion object {
        fun create(family: TextureFamily, seed: Int = TexturePresets.seedFor(family)): TextureSpec =
            TextureSpec(
                family = family,
                seed = seed,
                opacity = family.defaultOpacity,
                scale = 1f,
                blend = family.defaultBlend,
            )
    }
}

/** A deterministic raster, independent of Android's Bitmap allocation. */
data class TexturePixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    fun pixel(x: Int, y: Int): Int = argb[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
}

object TexturePresets {
    /** The ten families in the order the inspector presents them. */
    val presets: List<TexturePreset> = TextureFamily.entries.map { family ->
        TexturePreset(
            id = family.id,
            name = family.label,
            seed = seedFor(family),
            kind = family,
            defaultOpacity = family.defaultOpacity,
            defaultBlend = family.defaultBlend,
        )
    }

    fun byId(id: String?): TexturePreset? = presets.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun seedFor(family: TextureFamily): Int = family.ordinal * 7_919 + 104_729

    /**
     * Pure generator: the same family, seed, size and scale always returns the
     * same pixels. Coordinates are normalized to the requested artboard, so a
     * different size is a new sampling of the field, never a stretched bitmap.
     */
    fun render(
        family: TextureFamily,
        seed: Int,
        width: Int,
        height: Int,
        scale: Float = 1f,
    ): TexturePixels {
        val w = width.coerceIn(1, 4_096)
        val h = height.coerceIn(1, 4_096)
        val s = if (scale.isFinite()) scale.coerceIn(0.25f, 4f) else 1f
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val v = (y + 0.5f) / h
            for (x in 0 until w) {
                val u = (x + 0.5f) / w
                val density = density(family, u, v, s, seed, x, y)
                val alpha = (density.coerceIn(0f, 1f) * 210f).roundToInt()
                // A graphite ink keeps the texture inside ADD's paper palette.
                pixels[y * w + x] = (alpha shl 24) or 0x0011231C
            }
        }
        return TexturePixels(w, h, pixels)
    }

    private fun density(
        family: TextureFamily,
        u: Float,
        v: Float,
        scale: Float,
        seed: Int,
        px: Int,
        py: Int,
    ): Float = when (family) {
        TextureFamily.Brick -> {
            val rows = max(2, (7f * scale).roundToInt())
            val cols = max(2, (4f * scale).roundToInt())
            val row = floor(v * rows).toInt()
            val shifted = u * cols + if (row % 2 == 0) 0f else 0.5f
            val column = floor(shifted).toInt()
            val fx = shifted - column
            val fy = v * rows - row
            val mortar = fx < 0.055f || fy < 0.09f
            if (mortar) {
                0.82f
            } else {
                val grain = noise(px * 0.15f, py * 0.15f, seed + row * 31 + column)
                0.07f + grain * 0.15f
            }
        }
        TextureFamily.WoodGrain -> {
            val warp = fbm(u * 3f * scale, v * 9f * scale, seed, 3)
            val rings = sin((v * 18f + warp * 3.2f + (seed and 31) * 0.013f) * PI2 + u * 1.7f)
            val fibre = noise(px * 0.38f, py * 0.08f, seed + 17)
            (0.09f + 0.42f * (0.5f + 0.5f * rings).pow(2f) + fibre * 0.10f)
        }
        TextureFamily.Marble -> {
            val turb = fbm(u * 3f * scale, v * 5f * scale, seed, 3)
            val vein = abs(sin((u * 5f + v * 3f + turb * 4.2f + (seed and 63) * 0.01f) * PI2))
            val fine = noise(px * 0.22f, py * 0.22f, seed + 43)
            0.05f + 0.58f * (1f - vein).pow(4f) + fine * 0.05f
        }
        TextureFamily.Concrete -> {
            val body = fbm(u * 10f * scale, v * 10f * scale, seed, 3)
            val speck = noise(px * 0.72f, py * 0.72f, seed + 71)
            val chip = if (speck > 0.93f) 0.28f else 0f
            0.07f + body * 0.25f + chip
        }
        TextureFamily.Fabric -> {
            val warp = sin(u * 70f * scale * PI2)
            val weft = sin(v * 70f * scale * PI2)
            val diagonal = sin((u + v) * 24f * scale * PI2 + (seed and 31) * 0.02f)
            val tooth = noise(px * 0.5f, py * 0.5f, seed + 101)
            0.05f + 0.22f * (warp + 1f) / 2f + 0.16f * (weft + 1f) / 2f +
                0.12f * (diagonal + 1f) / 2f + tooth * 0.04f
        }
        TextureFamily.Halftone -> {
            val grid = max(2, (13f * scale).roundToInt())
            val fx = (u * grid) % 1f - 0.5f
            val fy = (v * grid) % 1f - 0.5f
            val radius = 0.16f + 0.25f * (0.5f + 0.5f * sin((u + v) * 3f * PI2 + seed * 0.01f))
            val dot = sqrt(fx * fx + fy * fy)
            if (dot < radius) 0.84f else 0.035f
        }
        TextureFamily.GradientMesh -> {
            val a = sin((u * 2.2f + v * 1.4f) * PI2 + seed * 0.017f)
            val b = sin((u * 3.7f - v * 2.3f) * PI2 - seed * 0.011f)
            val mesh = (0.5f + 0.24f * a + 0.18f * b).coerceIn(0f, 1f)
            0.04f + mesh * 0.30f
        }
        TextureFamily.Grain -> {
            val fine = (mix(px + seed * 31, py - seed * 17) ushr 8 and 0xFF) / 255f
            val tooth = valueNoise(px * 0.55f, py * 0.55f, seed + 131)
            fine * 0.28f + tooth * 0.16f
        }
        TextureFamily.Checker -> {
            val cell = max(2, (9f * scale).roundToInt())
            val check = ((floor(u * cell).toInt() + floor(v * cell).toInt()) and 1) == 0
            val grain = noise(px * 0.4f, py * 0.4f, seed + 151)
            if (check) 0.22f + grain * 0.06f else 0.045f + grain * 0.025f
        }
        TextureFamily.Scanline -> {
            // Frequency is in artboard fractions, not source pixels: a larger
            // artboard gets more scanlines at the same visual pitch, not thicker ones.
            val period = max(2f, 220f * scale)
            val line = if ((v * period) % 1f < 0.16f) 0.62f else 0.045f
            val sweep = 0.5f + 0.5f * sin(u * 5f * PI2 + seed * 0.02f)
            (line + sweep * 0.08f).coerceAtMost(1f)
        }
    }

    private const val PI2 = 6.2831855f

    private fun mix(x: Int, y: Int): Int {
        var h = x * 374_761_393 + y * 668_265_263
        h = (h xor (h ushr 13)) * 1_274_126_177
        return h xor (h ushr 16)
    }

    private fun unit(hash: Int): Float = (hash and 0xFFFF) / 65_535f

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = smooth(x - x0)
        val ty = smooth(y - y0)
        val a = unit(mix(x0 + seed * 31, y0 - seed * 17))
        val b = unit(mix(x0 + 1 + seed * 31, y0 - seed * 17))
        val c = unit(mix(x0 + seed * 31, y0 + 1 - seed * 17))
        val d = unit(mix(x0 + 1 + seed * 31, y0 + 1 - seed * 17))
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty)
    }

    private fun valueNoise(x: Float, y: Float, seed: Int): Float = noise(x, y, seed)

    private fun fbm(x: Float, y: Float, seed: Int, octaves: Int): Float {
        var sum = 0f
        var amplitude = 0.55f
        var frequency = 1f
        for (octave in 0 until octaves) {
            sum += noise(x * frequency, y * frequency, seed + octave * 97) * amplitude
            frequency *= 2.02f
            amplitude *= 0.5f
        }
        return sum
    }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)
    private fun lerp(a: Float, b: Float, amount: Float): Float = a + (b - a) * amount
}

/**
 * The interactive renderer never generates a full 1080x1350 field on every frame.
 * It caps the procedural field at a quarter-megapixel, caches it by recipe and
 * size, and lets the canvas scale that raster to the current backing surface.
 */
object TextureRenderer {
    internal const val MAX_FIELD_PIXELS = 240_000
    internal const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private data class CacheKey(
        val family: TextureFamily,
        val seed: Int,
        val scaleBits: Int,
        val width: Int,
        val height: Int,
    )

    private val cache = object : LinkedHashMap<CacheKey, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Bitmap>?): Boolean {
            var bytes = 0L
            values.forEach { bytes += it.byteCount }
            return size > 8 || bytes > MAX_CACHE_BYTES
        }
    }

    /** The cached raster's dimensions for a requested backing size. */
    internal fun fieldDimensions(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val pixels = w.toLong() * h
        val factor = if (pixels > MAX_FIELD_PIXELS) sqrt(MAX_FIELD_PIXELS.toDouble() / pixels).toFloat() else 1f
        // Floor, not round: rounding both axes can land just over the cap
        // (438 × 548 = 240,024), which would make the cache bound a lie.
        return floor(w * factor).toInt().coerceAtLeast(1) to
            floor(h * factor).toInt().coerceAtLeast(1)
    }

    @Synchronized
    fun overlay(spec: TextureSpec, width: Int, height: Int): Bitmap {
        val (fieldWidth, fieldHeight) = fieldDimensions(width, height)
        val key = CacheKey(spec.family, spec.seed, spec.scale.toRawBits(), fieldWidth, fieldHeight)
        cache[key]?.let { return it }
        val field = TexturePresets.render(spec.family, spec.seed, fieldWidth, fieldHeight, spec.scale)
        val bitmap = Bitmap.createBitmap(fieldWidth, fieldHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(field.argb, 0, fieldWidth, 0, 0, fieldWidth, fieldHeight)
        cache[key] = bitmap
        return bitmap
    }

    fun draw(canvas: Canvas, spec: TextureSpec, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || spec.opacity <= 0f) return
        val bitmap = overlay(spec, width, height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (spec.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
            xfermode = when (spec.blend) {
                BlendKind.Multiply -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                BlendKind.Screen -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                BlendKind.Overlay -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                BlendKind.Darken -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                BlendKind.Lighten -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                else -> null
            }
        }
        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}
