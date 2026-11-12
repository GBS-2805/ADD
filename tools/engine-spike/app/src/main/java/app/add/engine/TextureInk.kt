package app.add.engine

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.roundToInt

/** One normalized point in the artboard, stored in the document rather than a bitmap. */
data class InkPoint(val x: Float, val y: Float)

/**
 * A material stroke is a recipe plus a path. The same family, seed, width and points
 * always rebuild the same mark at any preview or export size.
 */
data class TextureInk(
    val id: String,
    val recipe: TextureSpec,
    val width: Float = 0.075f,
    val points: List<InkPoint> = emptyList(),
) {
    fun clean(): TextureInk = copy(
        width = if (width.isFinite()) width.coerceIn(0.012f, 0.25f) else 0.075f,
        points = points.take(MAX_POINTS).map {
            InkPoint(
                x = if (it.x.isFinite()) it.x.coerceIn(-0.25f, 1.25f) else 0f,
                y = if (it.y.isFinite()) it.y.coerceIn(-0.25f, 1.25f) else 0f,
            )
        },
    )

    companion object {
        const val MAX_POINTS = 1_024

        fun create(
            id: String,
            family: TextureFamily,
            seed: Int,
            width: Float,
            point: InkPoint,
        ): TextureInk = TextureInk(
            id = id,
            recipe = TextureSpec(
                family = family,
                seed = seed,
                opacity = 0.72f,
                scale = 1f,
                blend = family.defaultBlend,
            ),
            width = width,
            points = listOf(point),
        )
    }
}

/**
 * Draws the seeded field through a stroked path. The field is a small cached tile,
 * repeated in artboard space; no stroke is ever flattened into the document.
 */
object TextureInkRenderer {
    private const val TILE_PX = 256
    private const val TILE_FRACTION = 0.18f

    fun prewarm(ink: TextureInk) {
        TextureRenderer.overlay(ink.recipe, TILE_PX, TILE_PX)
    }

    fun draw(canvas: Canvas, strokes: List<TextureInk>, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val tileUnits = min(width, height) * TILE_FRACTION
        strokes.forEach { raw ->
            val ink = raw.clean()
            if (ink.points.isEmpty() || ink.recipe.opacity <= 0f) return@forEach
            val tile = TextureRenderer.overlay(ink.recipe, TILE_PX, TILE_PX)
            val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            shader.setLocalMatrix(
                Matrix().apply {
                    setScale(tileUnits / tile.width, tileUnits / tile.height)
                },
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                style = Paint.Style.STROKE
                strokeWidth = (ink.width * min(width, height)).coerceAtLeast(1f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                alpha = (ink.recipe.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                xfermode = when (ink.recipe.blend) {
                    BlendKind.Multiply -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    BlendKind.Screen -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    BlendKind.Overlay -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    BlendKind.Darken -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                    BlendKind.Lighten -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                    else -> null
                }
            }
            val path = Path()
            val first = ink.points.first()
            path.moveTo(first.x * width, first.y * height)
            ink.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            if (ink.points.size == 1) {
                canvas.drawCircle(
                    first.x * width,
                    first.y * height,
                    paint.strokeWidth / 2f,
                    paint,
                )
            } else {
                canvas.drawPath(path, paint)
            }
        }
    }
}
