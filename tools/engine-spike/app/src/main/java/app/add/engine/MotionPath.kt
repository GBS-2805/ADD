package app.add.engine

/**
 * One control point of a motion path. Coordinates are in the space of the layer's
 * parent: artboard units at the top level, group-local units inside a group.
 */
data class MotionPoint(val x: Float, val y: Float)

/**
 * A cubic path that belongs to one layer.
 *
 * Points are never rewritten to follow a group: a child's path is stored in the
 * group's local space, so moving, scaling or rotating the group carries it through
 * [LayerAffine] alone. Only a change to the layer's own centre inside its own space
 * translates the points, and it translates all four by one delta.
 */
data class MotionPath(
    val start: MotionPoint,
    val c1: MotionPoint,
    val c2: MotionPoint,
    val end: MotionPoint,
    val durationMs: Int = 1400,
) {
    /** The cubic position at [progress], clamped to 0..1. */
    fun pointAt(progress: Float): MotionPoint {
        val t = progress.coerceIn(0f, 1f)
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        return MotionPoint(
            x = a * start.x + b * c1.x + c * c2.x + d * end.x,
            y = a * start.y + b * c1.y + c * c2.y + d * end.y,
        )
    }

    /** [count] + 1 points from start to end, for playback and for a visible curve. */
    fun sample(count: Int): List<MotionPoint> {
        val steps = count.coerceAtLeast(1)
        return (0..steps).map { pointAt(it / steps.toFloat()) }
    }

    fun translated(dx: Float, dy: Float): MotionPath = MotionPath(
        start = MotionPoint(start.x + dx, start.y + dy),
        c1 = MotionPoint(c1.x + dx, c1.y + dy),
        c2 = MotionPoint(c2.x + dx, c2.y + dy),
        end = MotionPoint(end.x + dx, end.y + dy),
        durationMs = durationMs,
    )

    /** Applies one affine map to every point; used to cross a group boundary. */
    fun mapped(matrix: Affine): MotionPath {
        fun map(point: MotionPoint): MotionPoint {
            val (x, y) = matrix.map(point.x, point.y)
            return MotionPoint(x, y)
        }
        return MotionPath(
            start = map(start),
            c1 = map(c1),
            c2 = map(c2),
            end = map(end),
            durationMs = durationMs,
        )
    }

    /** Bounds of the drawn curve, sampled rather than taken from the control hull. */
    fun bounds(): FrameBounds {
        val points = sample(SAMPLE_COUNT)
        return FrameBounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
    }

    fun length(): Float {
        val points = sample(SAMPLE_COUNT)
        return points.zipWithNext().sumOf { (a, b) ->
            kotlin.math.hypot(b.x - a.x, b.y - a.y).toDouble()
        }.toFloat()
    }

    companion object {
        const val SAMPLE_COUNT = 64
    }
}

/**
 * The rules that keep a path on its layer. These are deliberately separate from
 * playback: the document owns anchoring, the stage owns the clock.
 */
object MotionAnchoring {

    /** A path created at the layer centre, whatever the control points become later. */
    fun anchoredTo(centerX: Float, centerY: Float, path: MotionPath): MotionPath =
        path.translated(centerX - path.start.x, centerY - path.start.y)

    /** The one rigid delta that re-anchors a path after its layer's centre moves. */
    fun deltaForCentreChange(
        oldCenterX: Float,
        oldCenterY: Float,
        newCenterX: Float,
        newCenterY: Float,
    ): Pair<Float, Float> = (newCenterX - oldCenterX) to (newCenterY - oldCenterY)

    /**
     * Keeps the whole path on the artboard with a single offset. Points are never
     * clamped one at a time: that shortens the curve and changes its shape.
     */
    fun clampedToArtboard(
        path: MotionPath,
        // Motion paths use the layer coordinate space, not pixels: the artboard is
        // 1.0 wide and 1.25 tall, so a 1080 px default would never clamp anything.
        width: Float = 1f,
        height: Float = ARTBOARD_HEIGHT / ARTBOARD_WIDTH,
    ): MotionPath {
        val bounds = path.bounds()
        val dx = when {
            bounds.width >= width -> (width - bounds.width) / 2f - bounds.left
            bounds.left < 0f -> -bounds.left
            bounds.right > width -> width - bounds.right
            else -> 0f
        }
        val dy = when {
            bounds.height >= height -> (height - bounds.height) / 2f - bounds.top
            bounds.top < 0f -> -bounds.top
            bounds.bottom > height -> height - bounds.bottom
            else -> 0f
        }
        return if (dx == 0f && dy == 0f) path else path.translated(dx, dy)
    }

    /**
     * Stores a world-space path inside a group. Returns null when the group is
     * singular, because a zero-scaled group has no invertible local space.
     */
    fun intoGroup(path: MotionPath, group: Affine): MotionPath? =
        group.inverse()?.let { path.mapped(it) }

    /** Maps a group-local path back to the artboard when the group is painted. */
    fun outOfGroup(path: MotionPath, group: Affine): MotionPath = path.mapped(group)

    /**
     * Moves a group's children's paths for a group change without touching the
     * points: the stored local path is unchanged and the new group matrix carries it.
     * Exposed as a named no-op so call sites read as intentional.
     */
    fun underGroupTransform(pathInGroupSpace: MotionPath): MotionPath = pathInGroupSpace
}
