package app.add.engine

import android.content.Context
import android.provider.Settings

/**
 * The clock-independent half of path playback. The stage owns frames; this owns
 * when a frame is over, which point the layer sits on, and which control dot the
 * finger grabbed. Keeping it pure is what lets reduced-motion and interruption be
 * tested without a Compose host.
 */
object EngineMotion {
    const val DefaultDurationMs = 1400

    /** Android's global animation scale; 0 means "do not animate". */
    fun durationScale(context: Context): Float = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)

    /**
     * Progress after [elapsedMs] at [durationScale]. At scale 0 the path is already
     * finished, so playback jumps to the end instead of tweening.
     */
    fun progress(elapsedMs: Long, durationMs: Int, durationScale: Float): Float {
        if (durationScale <= 0f) return 1f
        val duration = durationMs.coerceAtLeast(1) * durationScale
        return (elapsedMs / duration).coerceIn(0f, 1f)
    }

    /** The layer's displacement from its centre at [progress], in parent space. */
    fun offset(path: MotionPath, progress: Float): Pair<Float, Float> {
        val point = path.pointAt(progress)
        return (point.x - path.start.x) to (point.y - path.start.y)
    }

    /**
     * Index of the control dot under the pointer, or null. Start is 0, c1 is 1,
     * c2 is 2 and end is 3; tolerances are separate because an artboard is not square.
     */
    fun nearestHandle(
        path: MotionPath,
        x: Float,
        y: Float,
        toleranceX: Float,
        toleranceY: Float,
    ): Int? {
        val points = listOf(path.start, path.c1, path.c2, path.end)
        var best: Int? = null
        var bestDistance = 1f
        points.forEachIndexed { index, point ->
            val dx = (point.x - x) / toleranceX.coerceAtLeast(0.00001f)
            val dy = (point.y - y) / toleranceY.coerceAtLeast(0.00001f)
            val distance = dx * dx + dy * dy
            if (distance <= 1f && distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /** A usable first path, anchored at the layer centre and arcing to the right. */
    fun defaultPath(centerX: Float, centerY: Float, durationMs: Int = DefaultDurationMs): MotionPath =
        MotionPath(
            start = MotionPoint(centerX, centerY),
            c1 = MotionPoint(centerX + 0.18f, centerY - 0.20f),
            c2 = MotionPoint(centerX + 0.24f, centerY + 0.22f),
            end = MotionPoint(centerX + 0.34f, centerY + 0.04f),
            durationMs = durationMs,
        )
}
