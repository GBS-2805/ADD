package app.add.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Straight-alpha W3C compositing. Kept free of Android types so the blend
 * formulas are unit-testable on the JVM and shared by preview and export.
 */
object BlendMath {
    fun blendPixel(dst: Int, src: Int, mode: BlendKind, opacity: Float): Int {
        val sa = ((src ushr 24) and 0xFF) / 255f * opacity.coerceIn(0f, 1f)
        if (sa <= 0f) return dst
        val da = ((dst ushr 24) and 0xFF) / 255f
        val sc = floatArrayOf(
            ((src ushr 16) and 0xFF) / 255f,
            ((src ushr 8) and 0xFF) / 255f,
            (src and 0xFF) / 255f,
        )
        val dc = floatArrayOf(
            ((dst ushr 16) and 0xFF) / 255f,
            ((dst ushr 8) and 0xFF) / 255f,
            (dst and 0xFF) / 255f,
        )
        val ao = sa + da * (1f - sa)
        if (ao <= 0f) return 0
        val blended = if (mode.nonSeparable) {
            nonSeparable(mode, dc.copyOf(), sc.copyOf())
        } else {
            null
        }
        val out = FloatArray(3)
        for (i in 0..2) {
            val b = blended?.get(i) ?: channel(mode, dc[i], sc[i])
            val co = (1f - da) * sc[i] * sa +
                (1f - sa) * dc[i] * da +
                sa * da * b
            out[i] = (co / ao).coerceIn(0f, 1f)
        }
        return ((ao * 255f).roundToInt().coerceIn(0, 255) shl 24) or
            ((out[0] * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((out[1] * 255f).roundToInt().coerceIn(0, 255) shl 8) or
            (out[2] * 255f).roundToInt().coerceIn(0, 255)
    }

    private val BlendKind.nonSeparable: Boolean
        get() = this == BlendKind.Hue || this == BlendKind.Saturation ||
            this == BlendKind.Color || this == BlendKind.Luminosity

    fun channel(mode: BlendKind, cb: Float, cs: Float): Float = when (mode) {
        BlendKind.Normal -> cs
        BlendKind.Multiply -> cb * cs
        BlendKind.Screen -> cb + cs - cb * cs
        BlendKind.Overlay -> if (cb <= 0.5f) 2f * cb * cs else 1f - 2f * (1f - cb) * (1f - cs)
        BlendKind.Darken -> min(cb, cs)
        BlendKind.Lighten -> max(cb, cs)
        BlendKind.ColorDodge -> if (cs >= 1f) 1f else min(1f, cb / (1f - cs))
        BlendKind.ColorBurn -> if (cs <= 0f) 0f else 1f - min(1f, (1f - cb) / cs)
        BlendKind.HardLight -> if (cs <= 0.5f) 2f * cb * cs else 1f - 2f * (1f - cb) * (1f - cs)
        BlendKind.SoftLight -> if (cs <= 0.5f) {
            cb - (1f - 2f * cs) * cb * (1f - cb)
        } else {
            val d = if (cb <= 0.25f) ((16f * cb - 12f) * cb + 4f) * cb else sqrt(cb)
            cb + (2f * cs - 1f) * (d - cb)
        }
        BlendKind.Difference -> abs(cb - cs)
        BlendKind.Exclusion -> cb + cs - 2f * cb * cs
        BlendKind.LinearBurn -> cb + cs - 1f
        BlendKind.LinearDodge -> cb + cs
        BlendKind.LinearLight -> cb + 2f * cs - 1f
        BlendKind.Hue, BlendKind.Saturation, BlendKind.Color, BlendKind.Luminosity -> cs
    }.coerceIn(0f, 1f)

    private fun nonSeparable(mode: BlendKind, cb: FloatArray, cs: FloatArray): FloatArray {
        val result = when (mode) {
            BlendKind.Hue -> setLum(setSat(cs, sat(cb)), lum(cb))
            BlendKind.Saturation -> setLum(setSat(cb, sat(cs)), lum(cb))
            BlendKind.Color -> setLum(cs, lum(cb))
            BlendKind.Luminosity -> setLum(cb, lum(cs))
            else -> cs
        }
        return clipColor(result)
    }

    private fun lum(c: FloatArray): Float = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]

    private fun sat(c: FloatArray): Float = max(c[0], max(c[1], c[2])) - min(c[0], min(c[1], c[2]))

    private fun setLum(c: FloatArray, l: Float): FloatArray {
        val d = l - lum(c)
        c[0] += d
        c[1] += d
        c[2] += d
        return clipColor(c)
    }

    private fun clipColor(c: FloatArray): FloatArray {
        val l = lum(c)
        val n = min(c[0], min(c[1], c[2]))
        val x = max(c[0], max(c[1], c[2]))
        if (n < 0f) {
            val denom = (l - n).coerceAtLeast(0.0001f)
            for (i in 0..2) c[i] = l + (c[i] - l) * l / denom
        }
        if (x > 1f) {
            val denom = (x - l).coerceAtLeast(0.0001f)
            for (i in 0..2) c[i] = l + (c[i] - l) * (1f - l) / denom
        }
        return c
    }

    private fun setSat(c: FloatArray, s: Float): FloatArray {
        val maxIndex = if (c[0] >= c[1] && c[0] >= c[2]) 0 else if (c[1] >= c[2]) 1 else 2
        val minIndex = if (c[0] <= c[1] && c[0] <= c[2]) 0 else if (c[1] <= c[2]) 1 else 2
        if (maxIndex == minIndex) {
            c[0] = 0f
            c[1] = 0f
            c[2] = 0f
            return c
        }
        val midIndex = 3 - maxIndex - minIndex
        val oldMax = c[maxIndex]
        val oldMin = c[minIndex]
        val range = (oldMax - oldMin).coerceAtLeast(0.0001f)
        c[midIndex] = (c[midIndex] - oldMin) * s / range
        c[maxIndex] = s
        c[minIndex] = 0f
        return c
    }
}
