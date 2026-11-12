package app.add.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * One colour placed in the field. Coordinates and reach are normalized to the
 * paint rectangle, so the same recipe renders at preview size and export size.
 */
data class FieldColor(
    val color: Int,
    val x: Float,
    val y: Float,
    val band: Float = 0.30f,
)

/**
 * The FeralUI-style flow field as a document recipe: colour spots, Gaussian reach,
 * domain-warped fBm, swirl, soften, grain and a speed. It is deliberately data,
 * never a shader string or a bitmap, so it can travel in a signed pack.
 */
data class FieldSpec(
    val colors: List<FieldColor>,
    val scale: Float = 0.50f,
    val distortion: Float = 0.60f,
    val swirl: Float = 0.10f,
    val speed: Float = 0.30f,
    val soften: Float = 0f,
    val noise: Float = 0.06f,
    val animated: Boolean = true,
) {
    fun normalized(): FieldSpec = copy(
        colors = colors.take(MAX_FIELD_COLORS).map { spot ->
            spot.copy(
                x = spot.x.coerceIn(0f, 1f),
                y = spot.y.coerceIn(0f, 1f),
                band = spot.band.coerceIn(MIN_BAND, MAX_BAND),
            )
        }.ifEmpty { defaultFieldColors() },
        scale = scale.finite(0f, 1f, 0.5f),
        distortion = distortion.finite(0f, 1f, 0.6f),
        swirl = swirl.finite(0f, 1f, 0.1f),
        speed = speed.finite(0f, 1f, 0.3f),
        soften = soften.finite(0f, 1f, 0f),
        noise = noise.finite(0f, 1f, 0.06f),
    )

    fun withSpot(index: Int, transform: (FieldColor) -> FieldColor): FieldSpec {
        if (index !in colors.indices) return this
        return copy(colors = colors.mapIndexed { i, spot -> if (i == index) transform(spot) else spot })
    }

    fun addColor(color: Int): FieldSpec {
        if (colors.size >= MAX_FIELD_COLORS) return this
        val position = FieldPresets.ringPosition(colors.size)
        return copy(colors = colors + FieldColor(color, position.first, position.second, 0.30f))
    }

    fun removeColor(index: Int): FieldSpec {
        if (colors.size <= 2 || index !in colors.indices) return this
        return copy(colors = colors.filterIndexed { i, _ -> i != index })
    }

    companion object {
        const val MAX_FIELD_COLORS = 8
        const val MIN_BAND = 0.06f
        const val MAX_BAND = 0.70f
    }
}

private fun Float.finite(min: Float, max: Float, fallback: Float): Float =
    if (isFinite()) coerceIn(min, max) else fallback

fun defaultFieldColors(): List<FieldColor> = listOf(
    FieldColor(0xFFF3E9DA.toInt(), 0.50f, 0.22f, 0.34f),
    FieldColor(0xFFC7A88B.toInt(), 0.78f, 0.50f, 0.30f),
    FieldColor(0xFF6B5A4E.toInt(), 0.50f, 0.78f, 0.32f),
    FieldColor(0xFF2C2723.toInt(), 0.22f, 0.50f, 0.30f),
)

fun defaultField(): FieldSpec = FieldSpec(defaultFieldColors())

/** Original presets; FeralUI's published set is reference material, not shipped names. */
object FieldPresets {
    data class Preset(val name: String, val field: FieldSpec)

    private fun preset(name: String, vararg colors: Int): Preset {
        val spots = colors.mapIndexed { index, color ->
            val (x, y) = ringPosition(index)
            FieldColor(color, x, y, 0.30f)
        }
        return Preset(name, FieldSpec(spots))
    }

    val all: List<Preset> = listOf(
        preset("Mica dawn", 0xFFF3E9DA.toInt(), 0xFFC7A88B.toInt(), 0xFF6B5A4E.toInt(), 0xFF2C2723.toInt()),
        preset("Sea glass", 0xFFE7F4EF.toInt(), 0xFF9CD3C4.toInt(), 0xFF3A8A7A.toInt(), 0xFF123F3A.toInt()),
        preset("Plum smoke", 0xFFF2E6EE.toInt(), 0xFFC89DBB.toInt(), 0xFF7B4C6D.toInt(), 0xFF2B1B2A.toInt()),
        preset("Citrus haze", 0xFFFFF6D9.toInt(), 0xFFFFD37A.toInt(), 0xFFF08A4B.toInt(), 0xFF7A2E1F.toInt()),
        preset("Cobalt rain", 0xFFE8F1FA.toInt(), 0xFF9CBCE8.toInt(), 0xFF3767B1.toInt(), 0xFF14274D.toInt()),
        preset("Olive dusk", 0xFFF0EEDC.toInt(), 0xFFC7C58F.toInt(), 0xFF6C7A4A.toInt(), 0xFF28301F.toInt()),
        preset("Rose quartz", 0xFFFBEDEE.toInt(), 0xFFE7B7BC.toInt(), 0xFFB86B78.toInt(), 0xFF4A2430.toInt()),
        preset("Ink tide", 0xFFE4EEF0.toInt(), 0xFF8FB6C2.toInt(), 0xFF3C6C7D.toInt(), 0xFF0E2630.toInt()),
    )

    fun ringPosition(index: Int, count: Int = 4): Pair<Float, Float> {
        val angle = (index.toFloat() / max(count, 1)) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
        return (0.5f + cos(angle) * 0.27f) to (0.5f + sin(angle) * 0.24f)
    }
}

enum class FieldTarget(val label: String) {
    Background("ARTBOARD"),
    Layer("LAYER"),
}

/**
 * Deterministic software renderer for the same recipe the WebGL reference uses.
 *
 * The engine composites every layer into a software bitmap before blending, so an
 * AGSL RuntimeShader cannot be used for fills or PNG export. Rendering the field to
 * a bounded bitmap and using BitmapShader keeps preview and export on one path.
 */
object FieldBudget {
    /** Preview raster width; the hardware path uses the full 360. */
    @Volatile var previewWidth = 360

    /** Export raster width; the hardware path uses the full 1080. */
    @Volatile var exportWidth = 1080
}

object FieldRenderer {
    private data class Key(
        val field: FieldSpec,
        val width: Int,
        val height: Int,
        val phase: Int,
    )

    // The raster is pure per-pixel work. Splitting rows across the device's cores keeps
    // the first field frame inside Android's focus deadline on software GPUs, and the
    // pixels are identical because each row is written by exactly one worker.
    private val rasterPoolSize = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    private val rasterPool = Executors.newFixedThreadPool(rasterPoolSize) { task ->
        Thread(task, "field-raster").apply { isDaemon = true }
    }

    private val cache = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?): Boolean =
            size > 12
    }

    fun draw(
        canvas: Canvas,
        field: FieldSpec,
        width: Float,
        height: Float,
        phase: Float = 0f,
        maxWidth: Int = 512,
    ) {
        if (width <= 0f || height <= 0f) return
        val pixelWidth = min(maxWidth, width.roundToInt().coerceAtLeast(1))
        val pixelHeight = (pixelWidth * height / width).roundToInt().coerceAtLeast(1)
        val bitmap = bitmap(field, pixelWidth, pixelHeight, phase)
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun shader(field: FieldSpec, rect: RectF, phase: Float = 0f): Shader {
        val target = min(512, max(1, rect.width().roundToInt()))
        val targetHeight = (target * rect.height() / max(rect.width(), 1f)).roundToInt().coerceAtLeast(1)
        val bitmap = bitmap(field, target, targetHeight, phase)
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply {
            setScale(rect.width() / bitmap.width, rect.height() / bitmap.height)
            postTranslate(rect.left, rect.top)
        })
        return shader
    }

    @Synchronized
    fun bitmap(field: FieldSpec, width: Int, height: Int, phase: Float = 0f): Bitmap {
        val clean = field.normalized()
        val safeWidth = width.coerceIn(1, 1600)
        val safeHeight = height.coerceIn(1, 2000)
        val frame = (phase.coerceIn(0f, 120f) * 24f).roundToInt()
        val key = Key(clean, safeWidth, safeHeight, frame)
        cache[key]?.let { return it }
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(raster(clean, safeWidth, safeHeight, phase), 0, safeWidth, 0, 0, safeWidth, safeHeight)
        cache[key] = bitmap
        return bitmap
    }

    private fun raster(field: FieldSpec, width: Int, height: Int, phase: Float): IntArray {
        val pixels = IntArray(width * height)
        val aspect = width.toFloat() / height.toFloat()
        val spots = field.colors.toList()
        val colors = spots.map { it.color }
        val reds = FloatArray(colors.size) { ((colors[it] shr 16) and 0xFF) / 255f }
        val greens = FloatArray(colors.size) { ((colors[it] shr 8) and 0xFF) / 255f }
        val blues = FloatArray(colors.size) { (colors[it] and 0xFF) / 255f }
        val bands = min(rasterPoolSize, height)
        if (bands <= 1) {
            rasterRows(field, width, height, phase, 0, height, pixels, spots, colors, reds, greens, blues, aspect)
            return pixels
        }
        val latch = CountDownLatch(bands)
        for (band in 0 until bands) {
            val yStart = height * band / bands
            val yEnd = height * (band + 1) / bands
            rasterPool.execute {
                try {
                    rasterRows(
                        field, width, height, phase, yStart, yEnd, pixels,
                        spots, colors, reds, greens, blues, aspect,
                    )
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        return pixels
    }

    private fun rasterRows(
        field: FieldSpec,
        width: Int,
        height: Int,
        phase: Float,
        yStart: Int,
        yEnd: Int,
        pixels: IntArray,
        spots: List<FieldColor>,
        colors: List<Int>,
        reds: FloatArray,
        greens: FloatArray,
        blues: FloatArray,
        aspect: Float,
    ) {
        val time = phase.coerceIn(0f, 120f)
        val warpTime = time * 0.14f
        for (y in yStart until yEnd) {
            for (x in 0 until width) {
                var px = (x + 0.5f) / width * aspect
                var py = (y + 0.5f) / height

                val qx = px * (1.2f + field.scale * 5f)
                val qy = py * (1.2f + field.scale * 5f)
                val warpX = fbm(qx, qy + warpTime)
                val warpY = fbm(qx + 5.2f, qy + 1.3f - warpTime)
                px += (warpX - 0.5f) * (field.distortion * 1.2f)
                py += (warpY - 0.5f) * (field.distortion * 1.2f)

                val cx = 0.5f * aspect
                val cy = 0.5f
                var dx = px - cx
                var dy = py - cy
                val angle = field.swirl * 2.4f * sqrt(dx * dx + dy * dy) + time * 0.05f * field.swirl
                val ca = cos(angle)
                val sa = sin(angle)
                val sx = cx + ca * dx - sa * dy
                val sy = cy + sa * dx + ca * dy
                dx = sx
                dy = sy

                var r = 0f
                var g = 0f
                var b = 0f
                var weight = 0f
                spots.forEachIndexed { index, spot ->
                    val driftX = sin(time * 0.11f + index * 1.7f) * 0.05f
                    val driftY = cos(time * 0.09f + index * 2.3f) * 0.05f
                    val spotX = spot.x * aspect + driftX
                    val spotY = spot.y + driftY
                    val ddx = dx - spotX
                    val ddy = dy - spotY
                    val band = max(spot.band, 0.06f)
                    val w = exp(-(ddx * ddx + ddy * ddy) / (band * band))
                    r += reds[index] * w
                    g += greens[index] * w
                    b += blues[index] * w
                    weight += w
                }
                val inverse = 1f / max(weight, 1e-5f)
                r *= inverse
                g *= inverse
                b *= inverse

                if (field.soften > 0.001f) {
                    val e = field.soften * 0.05f
                    val soft = sample(field, dx + e, dy, colors, aspect, time)
                    val left = sample(field, dx - e, dy, colors, aspect, time)
                    val up = sample(field, dx, dy + e, colors, aspect, time)
                    val down = sample(field, dx, dy - e, colors, aspect, time)
                    r = (r + soft.first + left.first + up.first + down.first) / 5f
                    g = (g + soft.second + left.second + up.second + down.second) / 5f
                    b = (b + soft.third + left.third + up.third + down.third) / 5f
                }

                val grain = (hash21(x.toFloat(), y.toFloat()) - 0.5f) * field.noise * 0.4f
                r = (r + grain).coerceIn(0f, 1f)
                g = (g + grain).coerceIn(0f, 1f)
                b = (b + grain).coerceIn(0f, 1f)
                pixels[y * width + x] = (0xFF shl 24) or
                    ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
                    ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or
                    (b * 255f).roundToInt().coerceIn(0, 255)
            }
        }
    }

    private fun sample(
        field: FieldSpec,
        x: Float,
        y: Float,
        colors: List<Int>,
        aspect: Float,
        time: Float,
    ): Triple<Float, Float, Float> {
        var r = 0f
        var g = 0f
        var b = 0f
        var weight = 0f
        field.colors.forEachIndexed { index, spot ->
            val spotX = spot.x * aspect + sin(time * 0.11f + index * 1.7f) * 0.05f
            val spotY = spot.y + cos(time * 0.09f + index * 2.3f) * 0.05f
            val ddx = x - spotX
            val ddy = y - spotY
            val band = max(spot.band, 0.06f)
            val w = exp(-(ddx * ddx + ddy * ddy) / (band * band))
            val color = colors[index]
            r += ((color shr 16) and 0xFF) / 255f * w
            g += ((color shr 8) and 0xFF) / 255f * w
            b += (color and 0xFF) / 255f * w
            weight += w
        }
        val inverse = 1f / max(weight, 1e-5f)
        return Triple(r * inverse, g * inverse, b * inverse)
    }

    private fun fbm(x: Float, y: Float): Float {
        var px = x
        var py = y
        var value = 0f
        var amplitude = 0.5f
        repeat(4) {
            value += amplitude * vnoise(px, py)
            px = px * 2.02f + 17.3f
            py = py * 2.02f + 17.3f
            amplitude *= 0.5f
        }
        return value
    }

    private fun vnoise(x: Float, y: Float): Float {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash21(ix, iy)
        val b = hash21(ix + 1f, iy)
        val c = hash21(ix, iy + 1f)
        val d = hash21(ix + 1f, iy + 1f)
        return mix(mix(a, b, ux), mix(c, d, ux), uy)
    }

    private fun hash21(x: Float, y: Float): Float {
        var px = fract(x * 123.34f)
        var py = fract(y * 456.21f)
        val dot = px * (px + 45.32f) + py * (py + 45.32f)
        px += dot
        py += dot
        return fract(px * py)
    }

    private fun fract(value: Float): Float = value - floor(value)

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
