package app.add.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

const val ARTBOARD_WIDTH = 1080f
const val ARTBOARD_HEIGHT = 1350f

/** Height in normalized layer space for a frame that is square in artboard pixels. */
fun squareFrameHeight(width: Float): Float = width * ARTBOARD_WIDTH / ARTBOARD_HEIGHT

/** Viewport zoom. It belongs to the stage, never to a layer. */
const val ZOOM_MIN = 0.5f
const val ZOOM_MAX = 4f

fun clampZoom(value: Float): Float = value.coerceIn(ZOOM_MIN, ZOOM_MAX)

/**
 * How far a pinch has to move before it reaches the artboard. Finger tremor, a
 * resting second finger and the last frame of a lift all move the pointer span by a
 * fraction of a percent; without this band the zoom drifts a point at a time while
 * nothing is being pinched, and crossing 100% silently commits the pan to zero.
 */
const val ZOOM_DEADBAND = 0.015f

/**
 * Folds one pointer-span ratio into the pending zoom, returning the factor to apply
 * now and the remainder to carry. Accumulating is the point: a slow deliberate pinch
 * steps over the band in a few events, while a jitter that reverses itself never does.
 */
fun foldZoom(pending: Float, ratio: Float): Pair<Float, Float> {
    val next = pending * ratio
    return if (abs(next - 1f) >= ZOOM_DEADBAND) next to 1f else 1f to next
}

enum class LayerHandle { Rotate, TopLeft, TopRight, BottomRight, BottomLeft }

data class HandlePoint(val handle: LayerHandle, val x: Float, val y: Float)

val Layer.centerX: Float get() = x + w / 2f
val Layer.centerY: Float get() = y + h / 2f

/**
 * The frame angle. A model's `rotation` is its internal turntable angle, so its
 * viewport stays axis-aligned and the SurfaceView is never rotated as a 2D quad.
 */
val Layer.frameRotation: Float get() = if (this is ModelLayer) 0f else rotation

/**
 * Placement geometry, deliberately free of Android types so a plain JVM test can
 * exercise hit testing and resizing without an emulator.
 */
object Geometry {

    fun rotateVector(dx: Float, dy: Float, degrees: Float): Pair<Float, Float> {
        if (degrees == 0f) return dx to dy
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return (dx * c - dy * s) to (dx * s + dy * c)
    }

    fun rotatePoint(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
        val (dx, dy) = rotateVector(x - cx, y - cy, degrees)
        return (cx + dx) to (cy + dy)
    }

    /** Pointer position in the layer's unrotated local space. */
    fun localPoint(layer: Layer, x: Float, y: Float): Pair<Float, Float> =
        rotatePoint(x, y, layer.centerX, layer.centerY, -layer.frameRotation)

    fun contains(layer: Layer, x: Float, y: Float): Boolean {
        if (layer is GroupLayer) return layer.containsWorldPoint(x, y)
        val (lx, ly) = localPoint(layer, x, y)
        return lx >= layer.x && lx <= layer.x + layer.w && ly >= layer.y && ly <= layer.y + layer.h
    }

    fun handleInset(layer: Layer, insetX: Float, insetY: Float): Pair<Float, Float> {
        val x = min(insetX, layer.w * 0.34f)
        val y = min(insetY, layer.h * 0.34f)
        return x to y
    }

    fun corners(layer: Layer, insetX: Float, insetY: Float): List<HandlePoint> {
        val (ix, iy) = handleInset(layer, insetX, insetY)
        if (layer is GroupLayer) {
            val matrix = layer.localToWorld
            fun at(handle: LayerHandle, x: Float, y: Float) =
                HandlePoint(handle, matrix.map(x, y).first, matrix.map(x, y).second)
            return listOf(
                at(LayerHandle.TopLeft, ix, iy),
                at(LayerHandle.TopRight, layer.w - ix, iy),
                at(LayerHandle.BottomRight, layer.w - ix, layer.h - iy),
                at(LayerHandle.BottomLeft, ix, layer.h - iy),
            )
        }
        val left = layer.x + ix
        val right = layer.x + layer.w - ix
        val top = layer.y + iy
        val bottom = layer.y + layer.h - iy
        fun at(handle: LayerHandle, x: Float, y: Float): HandlePoint {
            val (rx, ry) = rotatePoint(x, y, layer.centerX, layer.centerY, layer.frameRotation)
            return HandlePoint(handle, rx, ry)
        }
        return listOf(
            at(LayerHandle.TopLeft, left, top),
            at(LayerHandle.TopRight, right, top),
            at(LayerHandle.BottomRight, right, bottom),
            at(LayerHandle.BottomLeft, left, bottom),
        )
    }

    /** The rotate handle sits outside the frame, or below it when the frame touches the top. */
    fun rotationHandle(layer: Layer, insetX: Float, insetY: Float): HandlePoint {
        val (ix, iy) = handleInset(layer, insetX, insetY)
        if (layer is GroupLayer) {
            val above = iy - 0.045f
            val y = if (above > 0.015f) above else layer.h - iy + 0.045f
            val (x, wy) = layer.localToWorld.map(layer.w / 2f, y)
            return HandlePoint(LayerHandle.Rotate, x, wy)
        }
        val above = layer.y + iy - 0.045f
        val y = if (above > 0.015f) above else layer.y + layer.h - iy + 0.045f
        val x = (layer.x + layer.w / 2f).coerceIn(layer.x + ix, layer.x + layer.w - ix)
        val (rx, ry) = rotatePoint(x, y, layer.centerX, layer.centerY, layer.frameRotation)
        return HandlePoint(LayerHandle.Rotate, rx, ry)
    }

    fun nearestHandle(
        layer: Layer,
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        insetX: Float,
        insetY: Float,
        allowRotate: Boolean = true,
    ): LayerHandle? {
        val handles = corners(layer, insetX, insetY) +
            if (allowRotate) listOf(rotationHandle(layer, insetX, insetY)) else emptyList()
        // A 22 dp target is most of a small icon's frame, so every drag on one used to
        // take a corner instead of moving it: a hundred pixels of drag scaled a 22% icon
        // to 75% of the artboard. A grip never reaches past a quarter of the layer's
        // smaller side, which leaves the body of a small layer free to move.
        val side = minOf(layer.w, layer.h) * 0.25f
        val reachX = radiusX.coerceAtMost(maxOf(side, 0.012f))
        val reachY = radiusY.coerceAtMost(maxOf(side, 0.012f))
        return handles
            .map { handle ->
                val dx = (handle.x - x) / reachX.coerceAtLeast(0.0001f)
                val dy = (handle.y - y) / reachY.coerceAtLeast(0.0001f)
                handle to dx * dx + dy * dy
            }
            .filter { it.second <= 1f }
            .minByOrNull { it.second }
            ?.first
            ?.handle
    }

    /**
     * Uniform scale anchored at the corner opposite the dragged one. Text scales its
     * type with the box, so a corner drag never leaves the words clipped in a bigger box.
     */
    fun scaleLayer(layer: Layer, handle: LayerHandle, factor: Float): Layer {
        if (handle == LayerHandle.Rotate) return layer
        if (layer is GroupLayer) {
            val scale = factor.coerceIn(0.2f, 5f)
            if (abs(scale - 1f) < 0.0001f) return layer
            // Scaling the group scales the children through its matrix; no child
            // coordinate is rewritten and no motion path is touched.
            return layer.copy(
                scaleX = (layer.scaleX * scale).coerceIn(0.05f, 20f),
                scaleY = (layer.scaleY * scale).coerceIn(0.05f, 20f),
            )
        }
        val scale = clampedScale(layer, factor)
        if (abs(scale - 1f) < 0.0001f) return layer

        val anchorX = when (handle) {
            LayerHandle.TopLeft, LayerHandle.BottomLeft -> layer.w / 2f
            else -> -layer.w / 2f
        }
        val anchorY = when (handle) {
            LayerHandle.TopLeft, LayerHandle.TopRight -> layer.h / 2f
            else -> -layer.h / 2f
        }
        return scaledAt(layer, scale, anchorX, anchorY)
    }

    /** Uniform scale around the frame centre, used by the inspector scale dial. */
    fun scaleCentered(layer: Layer, factor: Float): Layer {
        val scale = clampedScale(layer, factor)
        if (abs(scale - 1f) < 0.0001f) return layer
        return scaledAt(layer, scale, 0f, 0f)
    }

    /**
     * Bounds on the frame, not on the type inside it: a text layer already at 180 pt
     * keeps growing its box, and its font saturates separately in [scaledAt].
     */
    private fun clampedScale(layer: Layer, factor: Float): Float {
        val minScale = maxOf(0.02f / layer.w, 0.02f / layer.h)
        val maxScale = minOf(1.8f / layer.w, 1.8f / layer.h)
        return factor.coerceIn(minScale, maxScale)
    }

    private fun scaledAt(layer: Layer, scale: Float, anchorX: Float, anchorY: Float): Layer {
        val (worldX, worldY) = rotateVector(anchorX, anchorY, layer.frameRotation)
        val anchorWorldX = layer.centerX + worldX
        val anchorWorldY = layer.centerY + worldY
        val (scaledX, scaledY) = rotateVector(anchorX * scale, anchorY * scale, layer.frameRotation)
        val newW = layer.w * scale
        val newH = layer.h * scale
        val newX = anchorWorldX - scaledX - newW / 2f
        val newY = anchorWorldY - scaledY - newH / 2f
        val resized = layer.withBounds(newX, newY, newW, newH)
        val baseFont = (layer as? TextLayer)?.fontSize
        return if (resized is TextLayer && baseFont != null) {
            resized.copy(fontSize = (baseFont * scale).coerceIn(18f, 180f))
        } else {
            resized
        }
    }

    fun normalizeAngle(value: Float): Float = ((value % 360f) + 360f) % 360f

    /** Magnetic snap within three degrees of a 15-degree stop; free rotation elsewhere. */
    fun snapAngle(value: Float): Float {
        val normalized = normalizeAngle(value)
        val target = normalizeAngle(round(normalized / 15f) * 15f)
        val delta = abs(normalized - target)
        val wrapped = 360f - delta
        return if (min(delta, wrapped) <= 3f) target else normalized
    }

    fun distance(cx: Float, cy: Float, x: Float, y: Float): Float = hypot(x - cx, y - cy)

    fun angle(cx: Float, cy: Float, x: Float, y: Float): Float =
        Math.toDegrees(Math.atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
}

fun Layer.withBounds(x: Float, y: Float, w: Float, h: Float): Layer = when (this) {
    is VectorLayer -> copy(x = x, y = y, w = w, h = h)
    is ImageLayer -> copy(x = x, y = y, w = w, h = h)
    is TextLayer -> copy(x = x, y = y, w = w, h = h)
    is IconLayer -> copy(x = x, y = y, w = w, h = h)
    is ModelLayer -> copy(x = x, y = y, w = w, h = h)
    is GroupLayer -> copy(x = x, y = y, w = w, h = h)
}

fun Layer.withRotation(degrees: Float): Layer {
    val value = Geometry.normalizeAngle(degrees)
    return when (this) {
        is VectorLayer -> copy(rotation = value)
        is ImageLayer -> copy(rotation = value)
        is TextLayer -> copy(rotation = value)
        is IconLayer -> copy(rotation = value)
        is ModelLayer -> copy(rotation = value)
        is GroupLayer -> copy(rotation = value)
    }
}
