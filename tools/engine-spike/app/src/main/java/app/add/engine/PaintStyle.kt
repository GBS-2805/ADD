package app.add.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PaintKind(val label: String) {
    Solid("SOLID"),
    Linear("LINEAR"),
    Radial("RADIAL"),
    Sweep("SWEEP"),
    Diamond("DIAMOND"),
    Image("IMAGE"),
    Pattern("PATTERN"),
}

enum class PatternKind(val label: String) {
    Checks("CHECKS"),
    Dots("DOTS"),
    Stripes("STRIPES"),
    Grid("GRID"),
}

data class PaintStop(val offset: Float, val color: Int)

/**
 * One paint interface for every fill the engine can draw. A layer keeps the
 * original solid colour fields so schema-1/2 documents open unchanged; this
 * object is the optional richer paint on top of them.
 */
sealed interface PaintSpec {
    data class Solid(val color: Int) : PaintSpec

    data class Gradient(
        val kind: PaintKind,
        val stops: List<PaintStop>,
    ) : PaintSpec

    data class Image(
        val path: String,
        val fill: Boolean = true,
    ) : PaintSpec

    data class Pattern(
        val kind: PatternKind,
        val foreground: Int,
        val background: Int,
        val scale: Float = 1f,
    ) : PaintSpec
}

val PaintSpec.label: String
    get() = when (this) {
        is PaintSpec.Solid -> "SOLID"
        is PaintSpec.Gradient -> kind.label
        is PaintSpec.Image -> "IMAGE"
        is PaintSpec.Pattern -> "PATTERN / ${kind.label}"
    }

val PaintSpec.gradient: PaintSpec.Gradient?
    get() = this as? PaintSpec.Gradient

fun defaultGradient(color: Int, kind: PaintKind): PaintSpec.Gradient {
    val light = mixColor(color, 0xFFFFFFFF.toInt(), 0.55f)
    val dark = mixColor(color, 0xFF000000.toInt(), 0.35f)
    return PaintSpec.Gradient(
        kind = kind,
        stops = listOf(
            PaintStop(0f, dark),
            PaintStop(0.5f, color),
            PaintStop(1f, light),
        ),
    )
}

fun colorWithAlpha(color: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    return (color and 0x00FFFFFF) or (a shl 24)
}

fun colorAlpha(color: Int): Float = ((color ushr 24) and 0xFF) / 255f

fun mixColor(first: Int, second: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (first ushr shift) and 0xFF
        val b = (second ushr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return (channel(24) shl 24) or
        (channel(16) shl 16) or
        (channel(8) shl 8) or
        channel(0)
}

fun colorAt(stops: List<PaintStop>, t: Float): Int {
    if (stops.isEmpty()) return 0
    val ordered = stops.sortedBy { it.offset }
    val x = t.coerceIn(0f, 1f)
    if (x <= ordered.first().offset) return ordered.first().color
    if (x >= ordered.last().offset) return ordered.last().color
    var index = 0
    while (index < ordered.size - 1 && x > ordered[index + 1].offset) index++
    val a = ordered[index]
    val b = ordered[(index + 1).coerceAtMost(ordered.lastIndex)]
    val span = (b.offset - a.offset).coerceAtLeast(0.0001f)
    return mixColor(a.color, b.color, (x - a.offset) / span)
}

/** The stops a shader can use, with a strictly increasing offset list. */
fun shaderStops(stops: List<PaintStop>): Pair<IntArray, FloatArray>? {
    if (stops.size < 2) return null
    val ordered = stops.sortedBy { it.offset }
    val colors = IntArray(ordered.size)
    val positions = FloatArray(ordered.size)
    var previous = -1f
    ordered.forEachIndexed { index, stop ->
        colors[index] = stop.color
        positions[index] = max(previous + 0.0001f, stop.offset.coerceIn(0f, 1f))
            .coerceAtMost(1f)
        previous = positions[index]
    }
    if (positions.last() < 1f) {
        positions[positions.lastIndex] = 1f
    }
    return colors to positions
}

fun gradientMidpoint(stops: List<PaintStop>): Float {
    if (stops.size < 3) return 1f
    return stops.sortedBy { it.offset }[1].offset.coerceIn(0.01f, 0.99f)
}

fun lighten(color: Int, amount: Float): Int = mixColor(color, 0xFFFFFFFF.toInt(), amount)
fun darken(color: Int, amount: Float): Int = mixColor(color, 0xFF000000.toInt(), amount)

fun minSideFraction(first: Float, second: Float): Float = min(first, second)
