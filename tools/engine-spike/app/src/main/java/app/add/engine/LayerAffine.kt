package app.add.engine

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * A 2x3 affine map in one coordinate space.
 *
 *   x' = a*x + c*y + tx
 *   y' = b*x + d*y + ty
 *
 * Composition is column-vector and right-to-left: `outer * inner` applies `inner`
 * first. This is the single place the editor composes a group transform, so the
 * painter, compositor, hit test and bounds cannot disagree about the order.
 */
data class Affine(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
) {
    companion object {
        val Identity = Affine()

        fun translate(dx: Float, dy: Float): Affine = Affine(tx = dx, ty = dy)

        fun scale(sx: Float, sy: Float): Affine = Affine(a = sx, d = sy)

        fun rotate(degrees: Float): Affine {
            if (degrees == 0f) return Identity
            val radians = Math.toRadians(degrees.toDouble())
            val c = cos(radians).toFloat()
            val s = sin(radians).toFloat()
            return Affine(a = c, b = s, c = -s, d = c)
        }

        fun skewX(degrees: Float): Affine {
            if (degrees == 0f) return Identity
            return Affine(c = tan(Math.toRadians(degrees.toDouble())).toFloat())
        }
    }

    operator fun times(other: Affine): Affine = Affine(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    fun map(x: Float, y: Float): Pair<Float, Float> =
        (a * x + c * y + tx) to (b * x + d * y + ty)

    /** Direction only: the translation column is ignored. */
    fun mapVector(x: Float, y: Float): Pair<Float, Float> =
        (a * x + c * y) to (b * x + d * y)

    val determinant: Float get() = a * d - b * c

    /**
     * The inverse, or null when the map collapses the plane. A zero-scaled group must
     * refuse hit tests and flattening rather than divide by ~0 and scatter the children.
     */
    fun inverse(): Affine? {
        val det = determinant
        if (abs(det) < EPSILON) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(
            a = ia,
            b = ib,
            c = ic,
            d = id,
            tx = -(ia * tx + ic * ty),
            ty = -(ib * tx + id * ty),
        )
    }

}

private const val EPSILON = 1e-6f

/** Axis-aligned bounds in one space; group bounds are the union of child bounds. */
data class FrameBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun union(other: FrameBounds): FrameBounds = FrameBounds(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )
}

/**
 * A layer or group frame: a rectangle centred on [centerX]/[centerY] with rotation,
 * horizontal skew and non-uniform scale. The matrix is
 *
 *   T(center) * R(rotation) * SkewX(skewX) * S(scaleX, scaleY)
 *
 * Points are expressed relative to the centre, so the translation column is the
 * centre itself. Non-uniform group scaling on a rotated child is exactly the case
 * that produces skew; dropping it would silently shear the child back.
 */
data class LayerFrame(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val skewX: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
) {
    val matrix: Affine
        get() = Affine.translate(centerX, centerY) *
            Affine.rotate(rotation) *
            Affine.skewX(skewX) *
            Affine.scale(scaleX, scaleY)

    /**
     * Inverse-maps the pointer and tests the real quad, not its bounding box. A
     * rotated or skewed group's empty AABB corners must not hit anything.
     */
    fun containsWorld(x: Float, y: Float): Boolean {
        val inverse = matrix.inverse() ?: return false
        val (lx, ly) = inverse.map(x, y)
        return abs(lx) <= width / 2f + EPSILON && abs(ly) <= height / 2f + EPSILON
    }

    fun cornersWorld(): List<Pair<Float, Float>> {
        val matrix = matrix
        return listOf(
            matrix.map(-width / 2f, -height / 2f),
            matrix.map(width / 2f, -height / 2f),
            matrix.map(width / 2f, height / 2f),
            matrix.map(-width / 2f, height / 2f),
        )
    }

    fun boundsWorld(): FrameBounds {
        val corners = cornersWorld()
        return FrameBounds(
            left = corners.minOf { it.first },
            top = corners.minOf { it.second },
            right = corners.maxOf { it.first },
            bottom = corners.maxOf { it.second },
        )
    }
}

/** The parts of `T * R * SkewX * S`, recovered from the linear map. */
data class DecomposedFrame(
    val centerX: Float,
    val centerY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val skewX: Float,
)

/**
 * Group and layer transform operations. Pure Kotlin so a JVM test can prove the
 * geometry without an emulator, and so the UI has no second copy of the math.
 */
object LayerAffine {

    /** The frame a leaf contributes today; `skewX` and group scale are parameters so
     *  Task 2 can pass the stored values without changing this helper. */
    fun frameOf(
        layer: Layer,
        skewX: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
    ): LayerFrame = LayerFrame(
        centerX = layer.centerX,
        centerY = layer.centerY,
        width = layer.w,
        height = layer.h,
        rotation = layer.frameRotation,
        skewX = skewX,
        scaleX = scaleX,
        scaleY = scaleY,
    )

    /** The child's matrix in the group's local space: `G^-1 * C`. */
    fun childToGroup(child: Affine, group: Affine): Affine? =
        group.inverse()?.times(child)

    /** The child's world matrix from its group-local matrix: `G * L`. */
    fun childToWorld(local: Affine, group: Affine): Affine = group.times(local)

    /**
     * Recovers centre, scales, rotation and skew from `T * R * SkewX * S`.
     * Returns null for a collapsed or zero-scaled matrix that has no useful frame.
     */
    fun decompose(matrix: Affine): DecomposedFrame? {
        val rotation = Math.toDegrees(atan2(matrix.b, matrix.a).toDouble()).toFloat()
        val radians = Math.toRadians(rotation.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        // First column length is scaleX; the second column's projection onto the
        // rotated y axis is scaleY, and what remains is the skew.
        val scaleX = hypot(matrix.a, matrix.b)
        if (scaleX < EPSILON) return null
        val scaleY = -matrix.c * s + matrix.d * c
        if (abs(scaleY) < EPSILON) return null
        val skew = Math.toDegrees(atan(((matrix.c * c + matrix.d * s) / scaleY).toDouble())).toFloat()

        return DecomposedFrame(
            centerX = matrix.tx,
            centerY = matrix.ty,
            scaleX = scaleX,
            scaleY = scaleY,
            rotation = rotation,
            skewX = skew,
        )
    }

    /**
     * Places a world-space child inside a group: `G^-1 * C`. The group's inverse
     * scale is folded into the child's width and height, so a grouped child keeps
     * its exact world geometry without needing a scale field of its own.
     */
    fun grouped(child: LayerFrame, group: LayerFrame): LayerFrame? {
        val local = childToGroup(child.matrix, group.matrix) ?: return null
        val parts = decompose(local) ?: return null
        return LayerFrame(
            centerX = parts.centerX,
            centerY = parts.centerY,
            width = child.width * parts.scaleX,
            height = child.height * parts.scaleY,
            rotation = parts.rotation,
            skewX = parts.skewX,
            scaleX = 1f,
            scaleY = 1f,
        )
    }

    /**
     * Flattens a child out of a group: `G * C` in artboard space. The group's scale
     * is folded into the child's width and height, and the remaining shear becomes
     * [LayerFrame.skewX]. The resulting corners map to the same world points as
     * `group.matrix * child.matrix`.
     */
    fun flatten(child: LayerFrame, group: LayerFrame): LayerFrame? {
        val world = group.matrix * child.matrix
        val parts = decompose(world) ?: return null
        return LayerFrame(
            centerX = parts.centerX,
            centerY = parts.centerY,
            width = child.width * parts.scaleX,
            height = child.height * parts.scaleY,
            rotation = parts.rotation,
            skewX = parts.skewX,
            scaleX = 1f,
            scaleY = 1f,
        )
    }

    /** Furthest a corner of [frame] reaches from its centre, used for handle reach. */
    fun radius(frame: LayerFrame): Float =
        hypot(frame.width * frame.scaleX / 2f, frame.height * frame.scaleY / 2f)
}
