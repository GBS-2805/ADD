package app.add.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Composites one layer at a time onto a software artboard. Every layer is first
 * rendered to its own bitmap, so effects and masks never mutate source content,
 * and a blend is always evaluated against everything already below it. This is
 * the part the old painter did not have.
 *
 * The pass order on one layer is deliberate: content, then effects, then the
 * paint mask, then the clip to the layer below, then the blend. A blurred edge
 * therefore cannot leak past its own mask, and a clipped layer cannot paint on
 * anything but its base. A run of clipped layers shares the base that starts the
 * run; each one is not narrowed by the clipped layer above it.
 */
private data class LayerCacheKey(
    val layerId: String,
    val layerHash: Int,
    val unitBits: Int,
)

private class LayerBitmapCache {
    private class Entry(val bitmap: Bitmap, val left: Int, val top: Int) {
        val bytes: Int get() = bitmap.byteCount
    }

    private val entries = LinkedHashMap<LayerCacheKey, Entry>(8, 0.75f, true)
    private var bytes = 0
    private val maxBytes = 40 * 1024 * 1024

    @Synchronized
    fun get(key: LayerCacheKey): Triple<Bitmap, Int, Int>? {
        val entry = entries[key] ?: return null
        return Triple(entry.bitmap, entry.left, entry.top)
    }

    @Synchronized
    fun put(key: LayerCacheKey, bitmap: Bitmap, left: Int, top: Int): Boolean {
        if (bitmap.byteCount > maxBytes) return false
        entries.remove(key)?.let {
            bytes -= it.bytes
            // Do not recycle here: this bitmap may still be the clip base of the
            // frame in progress. Dropping the reference lets GC collect it safely.
        }
        entries[key] = Entry(bitmap, left, top)
        bytes += bitmap.byteCount
        val iterator = entries.entries.iterator()
        while (bytes > maxBytes && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == key) continue
            bytes -= entry.value.bytes
            // Same reason as above: an evicted bitmap can still be in use.
            iterator.remove()
        }
        return true
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { it.bitmap.recycle() }
        entries.clear()
        bytes = 0
    }
}

private val layerBitmapCache = LayerBitmapCache()

fun clearLayerBitmapCache() = layerBitmapCache.clear()

class LayerCompositor(
    private val scale: Float,
    private val cacheLayers: Boolean = false,
    private val drawContent: (Canvas, Layer) -> Unit,
) {
    private val unitBits = scale.toRawBits()

    private data class RenderedLayer(
        val bitmap: Bitmap,
        val left: Int,
        val top: Int,
        val cached: Boolean = false,
    )

    fun draw(destCanvas: Canvas, destBitmap: Bitmap, layers: List<Layer>) {
        var previous: RenderedLayer? = null
        var clipBase: RenderedLayer? = null
        layers.filter { it.visible }.forEach { layer ->
            val rendered = render(layer) ?: return@forEach
            if (layer.style.clipToBelow) {
                // The first clipped layer claims the layer below as the whole run's
                // base. Later clipped layers keep clipping to that base instead of to
                // the previous clipped layer, which is what makes a multi-layer
                // clipping mask behave like one group rather than a narrowing chain.
                if (clipBase == null) clipBase = previous
                val current = clipBase?.let { applyClip(rendered, it) } ?: rendered
                composite(destCanvas, destBitmap, current, layer.style.blend, layer.opacity)
                if (previous != null && previous !== clipBase) release(previous)
                previous = current
            } else {
                // A normal layer ends the clipped run: the base has no more consumers.
                clipBase?.let { release(it) }
                clipBase = null
                composite(destCanvas, destBitmap, rendered, layer.style.blend, layer.opacity)
                previous?.let { release(it) }
                previous = rendered
            }
        }
        clipBase?.let { release(it) }
        previous?.let { release(it) }
    }

    private fun render(layer: Layer): RenderedLayer? {
        val bounds = boundsFor(layer)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val cacheKey = if (cacheLayers && layer.style.effects.activePasses.isNotEmpty() && cacheable(layer)) {
            LayerCacheKey(layer.id, layer.hashCode(), unitBits)
        } else null
        cacheKey?.let { key ->
            layerBitmapCache.get(key)?.let { (bitmap, left, top) ->
                return RenderedLayer(bitmap, left, top, cached = true)
            }
        }
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-bounds.left.toFloat() / scale, -bounds.top.toFloat() / scale)
        val rotated = layer.frameRotation != 0f
        if (rotated) {
            canvas.save()
            canvas.rotate(layer.frameRotation, layer.centerX * ArtboardPainter.W, layer.centerY * ArtboardPainter.H)
        }
        drawContent(canvas, layer)
        if (rotated) canvas.restore()

        applyEffects(bitmap, layer.style.effects)
        applyPaintMask(bitmap, layer, bounds)
        val cached = cacheKey != null &&
            layerBitmapCache.put(cacheKey, bitmap, bounds.left, bounds.top)
        return RenderedLayer(bitmap, bounds.left, bounds.top, cached)
    }

    private fun cacheable(layer: Layer): Boolean = when (layer) {
        is IconLayer -> true
        is TextLayer -> layer.paint !is PaintSpec.Image
        is VectorLayer -> layer.paint !is PaintSpec.Image
        else -> false
    }

    private fun release(rendered: RenderedLayer) {
        if (!rendered.cached) rendered.bitmap.recycle()
    }

    private fun boundsFor(layer: Layer): Rect {
        val x = layer.x * ArtboardPainter.W
        val y = layer.y * ArtboardPainter.H
        val w = layer.w * ArtboardPainter.W
        val h = layer.h * ArtboardPainter.H
        val cx = layer.centerX * ArtboardPainter.W
        val cy = layer.centerY * ArtboardPainter.H
        val corners = listOf(
            Geometry.rotatePoint(x, y, cx, cy, layer.frameRotation),
            Geometry.rotatePoint(x + w, y, cx, cy, layer.frameRotation),
            Geometry.rotatePoint(x + w, y + h, cx, cy, layer.frameRotation),
            Geometry.rotatePoint(x, y + h, cx, cy, layer.frameRotation),
        )
        val effects = layer.style.effects
        val maskRadius = layer.style.mask?.strokes?.maxOfOrNull { it.radius } ?: 0f
        val blurPad = if (effects.enabled && EffectPass.Blur in effects.activePasses) effects.blur else 0f
        val pad = blurPad * 2f + maskRadius * min(w, h) + 6f
        val left = floor((corners.minOf { it.first } - pad) * scale).toInt()
            .coerceIn(0, (ArtboardPainter.W * scale).roundToInt())
        val top = floor((corners.minOf { it.second } - pad) * scale).toInt()
            .coerceIn(0, (ArtboardPainter.H * scale).roundToInt())
        val right = floor((corners.maxOf { it.first } + pad) * scale).toInt()
            .coerceIn(0, (ArtboardPainter.W * scale).roundToInt())
        val bottom = floor((corners.maxOf { it.second } + pad) * scale).toInt()
            .coerceIn(0, (ArtboardPainter.H * scale).roundToInt())
        return Rect(left, top, max(left, right), max(top, bottom))
    }

    private fun composite(
        destCanvas: Canvas,
        destBitmap: Bitmap,
        rendered: RenderedLayer,
        blend: BlendKind,
        opacity: Float,
    ) {
        if (opacity <= 0f) return
        if (blend == BlendKind.Normal) {
            destCanvas.drawBitmap(
                rendered.bitmap,
                rendered.left.toFloat(),
                rendered.top.toFloat(),
                Paint(Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                },
            )
            return
        }
        val width = rendered.bitmap.width
        val height = rendered.bitmap.height
        val left = max(0, rendered.left)
        val top = max(0, rendered.top)
        val right = min(destBitmap.width, rendered.left + width)
        val bottom = min(destBitmap.height, rendered.top + height)
        if (right <= left || bottom <= top) return
        val src = IntArray(width * height)
        rendered.bitmap.getPixels(src, 0, width, 0, 0, width, height)
        val destWidth = right - left
        val destHeight = bottom - top
        val dest = IntArray(destWidth * destHeight)
        destBitmap.getPixels(dest, 0, destWidth, left, top, destWidth, destHeight)
        val ox = left - rendered.left
        val oy = top - rendered.top
        for (y in 0 until destHeight) {
            for (x in 0 until destWidth) {
                val srcIndex = (y + oy) * width + (x + ox)
                val srcPixel = src[srcIndex]
                if ((srcPixel ushr 24) == 0) continue
                val destIndex = y * destWidth + x
                dest[destIndex] = BlendMath.blendPixel(dest[destIndex], srcPixel, blend, opacity)
            }
        }
        destBitmap.setPixels(dest, 0, destWidth, left, top, destWidth, destHeight)
    }

    /** Clips the current layer to the alpha of the layer directly below it. */
    private fun applyClip(current: RenderedLayer, previous: RenderedLayer): RenderedLayer {
        val clipped = if (current.cached) {
            val copy = Bitmap.createBitmap(current.bitmap.width, current.bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(copy).drawBitmap(current.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            RenderedLayer(copy, current.left, current.top)
        } else {
            current
        }
        val mask = Bitmap.createBitmap(clipped.bitmap.width, clipped.bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        canvas.drawBitmap(
            previous.bitmap,
            (previous.left - clipped.left).toFloat(),
            (previous.top - clipped.top).toFloat(),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        Canvas(clipped.bitmap).drawBitmap(mask, 0f, 0f, paint)
        mask.recycle()
        return clipped
    }

    /**
     * A paint mask is a white bitmap with the document's strokes cut out of it:
     * erase strokes clear alpha, restore strokes paint it back. The layer keeps
     * its alpha everywhere a stroke never touched.
     */
    private fun applyPaintMask(bitmap: Bitmap, layer: Layer, bounds: Rect) {
        val maskSpec = layer.style.mask ?: return
        if (!maskSpec.enabled || maskSpec.strokes.isEmpty()) return
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        canvas.translate(-bounds.left.toFloat() / scale, -bounds.top.toFloat() / scale)
        val centerX = layer.centerX * ArtboardPainter.W
        val centerY = layer.centerY * ArtboardPainter.H
        val minSide = min(layer.w * ArtboardPainter.W, layer.h * ArtboardPainter.H)
        maskSpec.strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val path = Path()
            val points = stroke.points.map { point ->
                val rawX = layer.x * ArtboardPainter.W + point.x * layer.w * ArtboardPainter.W
                val rawY = layer.y * ArtboardPainter.H + point.y * layer.h * ArtboardPainter.H
                Geometry.rotatePoint(rawX, rawY, centerX, centerY, layer.frameRotation)
            }
            path.moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { path.lineTo(it.first, it.second) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = (stroke.radius * minSide).coerceAtLeast(1f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (stroke.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            if (points.size == 1) {
                canvas.drawCircle(points.first().first, points.first().second, paint.strokeWidth / 2f, paint)
            } else {
                canvas.drawPath(path, paint)
            }
        }
        val apply = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        Canvas(bitmap).drawBitmap(mask, 0f, 0f, apply)
        mask.recycle()
    }

    /**
     * The non-destructive grade: exposure, contrast, tone curves, saturation,
     * duotone LUT, then a separable blur, halftone and grain. Every step works on
     * the layer's own bitmap, so the document and the source pixels never change.
     */
    private fun applyEffects(bitmap: Bitmap, effects: LayerEffects) {
        val passes = effects.activePasses
        if (!effects.enabled || passes.isEmpty()) return
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val exposure = 2f.pow(effects.exposure.coerceIn(-4f, 4f))
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue
            var r = ((pixel ushr 16) and 0xFF) / 255f
            var g = ((pixel ushr 8) and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            if (EffectPass.Grade in passes) {
                r = grade(r, effects, exposure)
                g = grade(g, effects, exposure)
                b = grade(b, effects, exposure)

                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                if (effects.saturation != 0f) {
                    val factor = 1f + effects.saturation.coerceIn(-1f, 1f)
                    r = (lum + (r - lum) * factor).coerceIn(0f, 1f)
                    g = (lum + (g - lum) * factor).coerceIn(0f, 1f)
                    b = (lum + (b - lum) * factor).coerceIn(0f, 1f)
                }
            }
            if (EffectPass.Duotone in passes) {
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val mapped = mixColor(effects.duotoneDark, effects.duotoneLight, lum)
                r = ((mapped ushr 16) and 0xFF) / 255f
                g = ((mapped ushr 8) and 0xFF) / 255f
                b = (mapped and 0xFF) / 255f
            }
            pixels[index] = (a shl 24) or
                ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or
                (b * 255f).roundToInt().coerceIn(0, 255)
        }

        if (EffectPass.Blur in passes) {
            val radius = (effects.blur * scale).roundToInt().coerceIn(0, 32)
            if (radius > 0) {
                val temp = IntArray(pixels.size)
                blurPass(pixels, temp, width, height, radius, horizontal = true)
                blurPass(temp, pixels, width, height, radius, horizontal = false)
            }
        }

        if (EffectPass.Halftone in passes) {
            val strength = effects.halftone.coerceIn(0f, 1f)
            val cell = (5f + (1f - strength) * 9f).roundToInt().coerceAtLeast(3)
            for (y in 0 until height) for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val a = (pixel ushr 24) and 0xFF
                if (a == 0) continue
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val dx = (x % cell) - cell / 2
                val dy = (y % cell) - cell / 2
                val dot = sqrt((dx * dx + dy * dy).toFloat()) / (cell / 2f)
                val threshold = (1.05f - lum).coerceIn(0f, 1f)
                val ink = if (dot < threshold) 1f else 0f
                val amount = ink * strength * 0.85f
                val shade = mixColor((r shl 16) or (g shl 8) or b, 0x000000, amount)
                pixels[index] = (a shl 24) or (shade and 0xFFFFFF)
            }
        }

        if (EffectPass.Grain in passes) {
            val strength = effects.grain.coerceIn(0f, 1f)
            for (y in 0 until height) for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val a = (pixel ushr 24) and 0xFF
                if (a == 0) continue
                // A 256x256 tile generated once. Hashing every pixel on every frame
                // was measurable on a phone at 400% with grain near maximum.
                val delta = (GRAIN_NOISE[((y and GRAIN_MASK) shl 8) or (x and GRAIN_MASK)] * strength).roundToInt()
                val r = (((pixel ushr 16) and 0xFF) + delta).coerceIn(0, 255)
                val g = (((pixel ushr 8) and 0xFF) + delta).coerceIn(0, 255)
                val b = ((pixel and 0xFF) + delta).coerceIn(0, 255)
                pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun grade(value: Float, effects: LayerEffects, exposure: Float): Float {
        var v = (value * exposure).coerceIn(0f, 1f)
        v = ((v - 0.5f) * (1f + effects.contrast.coerceIn(-1f, 1f)) + 0.5f).coerceIn(0f, 1f)
        val x = v
        v += effects.shadows.coerceIn(-1f, 1f) * (1f - x) * (1f - x) * 0.35f
        v += effects.midtones.coerceIn(-1f, 1f) * 4f * x * (1f - x) * 0.30f
        v += effects.highlights.coerceIn(-1f, 1f) * x * x * 0.35f
        return v.coerceIn(0f, 1f)
    }

    private fun blurPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val window = radius * 2 + 1
        if (horizontal) {
            for (y in 0 until height) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (dx in -radius..radius) {
                    val pixel = src[y * width + dx.coerceIn(0, width - 1)]
                    val alpha = (pixel ushr 24) and 0xFF
                    a += alpha
                    r += ((pixel ushr 16) and 0xFF) * alpha / 255
                    g += ((pixel ushr 8) and 0xFF) * alpha / 255
                    b += (pixel and 0xFF) * alpha / 255
                }
                for (x in 0 until width) {
                    dst[y * width + x] = packBlur(a, r, g, b, window)
                    val remove = src[y * width + (x - radius).coerceIn(0, width - 1)]
                    val add = src[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                    a += alpha(add) - alpha(remove)
                    r += red(add) * alpha(add) / 255 - red(remove) * alpha(remove) / 255
                    g += green(add) * alpha(add) / 255 - green(remove) * alpha(remove) / 255
                    b += blue(add) * alpha(add) / 255 - blue(remove) * alpha(remove) / 255
                }
            }
        } else {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (dy in -radius..radius) {
                    val pixel = src[dy.coerceIn(0, height - 1) * width + x]
                    val alpha = (pixel ushr 24) and 0xFF
                    a += alpha
                    r += ((pixel ushr 16) and 0xFF) * alpha / 255
                    g += ((pixel ushr 8) and 0xFF) * alpha / 255
                    b += (pixel and 0xFF) * alpha / 255
                }
                for (y in 0 until height) {
                    dst[y * width + x] = packBlur(a, r, g, b, window)
                    val remove = src[(y - radius).coerceIn(0, height - 1) * width + x]
                    val add = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                    a += alpha(add) - alpha(remove)
                    r += red(add) * alpha(add) / 255 - red(remove) * alpha(remove) / 255
                    g += green(add) * alpha(add) / 255 - green(remove) * alpha(remove) / 255
                    b += blue(add) * alpha(add) / 255 - blue(remove) * alpha(remove) / 255
                }
            }
        }
    }

    private fun packBlur(a: Int, r: Int, g: Int, b: Int, window: Int): Int {
        val alpha = (a / window).coerceIn(0, 255)
        if (alpha == 0) return 0
        val red = (r * 255 / a).coerceIn(0, 255)
        val green = (g * 255 / a).coerceIn(0, 255)
        val blue = (b * 255 / a).coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun alpha(pixel: Int) = (pixel ushr 24) and 0xFF
    private fun red(pixel: Int) = (pixel ushr 16) and 0xFF
    private fun green(pixel: Int) = (pixel ushr 8) and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF

    private companion object {
        const val GRAIN_TILE = 256
        const val GRAIN_MASK = GRAIN_TILE - 1
        val GRAIN_NOISE = IntArray(GRAIN_TILE * GRAIN_TILE) { index ->
            val x = index and GRAIN_MASK
            val y = index shr 8
            var hash = x * 374761393 + y * 668265263
            hash = (hash xor (hash shr 13)) * 1274126177
            val noise = ((hash xor (hash shr 16)) and 0xFF) / 127.5f - 1f
            (noise * 30f).roundToInt()
        }
    }
}
