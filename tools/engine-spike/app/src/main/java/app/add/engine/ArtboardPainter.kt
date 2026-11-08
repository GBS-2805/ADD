package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

class Typefaces(context: Context) {
    private val assets = context.assets
    private val regular = load("fonts/poppins_regular.ttf", Typeface.NORMAL)
    private val medium = load("fonts/poppins_medium.ttf", Typeface.NORMAL)
    private val semibold = load("fonts/poppins_semibold.ttf", Typeface.BOLD)

    fun forWeight(weight: Int): Typeface = when {
        weight >= 600 -> semibold
        weight >= 500 -> medium
        else -> regular
    }

    private fun load(path: String, fallbackStyle: Int): Typeface = runCatching {
        Typeface.createFromAsset(assets, path)
    }.getOrElse {
        Typeface.create("sans-serif", fallbackStyle)
    }
}

/**
 * The artboard is drawn once here for both the interactive Stage and the PNG
 * exporter. Android's Canvas is Skia, so the same path geometry and type runs hit
 * the same rasterizer in both places.
 */
object ArtboardPainter {
    const val W = 1080f
    const val H = 1350f

    fun draw(
        canvas: Canvas,
        layers: List<Layer>,
        typefaces: Typefaces,
        modelCaptures: Map<String, Bitmap> = emptyMap(),
    ) {
        canvas.drawColor(0xFFF3EBDD.toInt())
        drawPaper(canvas, typefaces)
        layers.filter { it.visible }.forEach { layer ->
            val rotated = layer.frameRotation != 0f
            if (rotated) {
                canvas.save()
                canvas.rotate(layer.frameRotation, layer.centerX * W, layer.centerY * H)
            }
            when (layer) {
                is VectorLayer -> drawVector(canvas, layer)
                is TextLayer -> drawText(canvas, layer, typefaces)
                is IconLayer -> drawIcon(canvas, layer)
                is ModelLayer -> modelCaptures[layer.id]?.let {
                    drawModelCapture(canvas, layer, it)
                } ?: drawModelPlaceholder(canvas, layer, typefaces)
            }
            if (rotated) canvas.restore()
        }
        drawFooter(canvas, typefaces)
    }

    fun drawSelection(
        canvas: Canvas,
        layer: Layer,
        selectedNodeId: String?,
        scale: Float,
        showHandles: Boolean = true,
        inset: Float = 30f,
        showRotate: Boolean = true,
    ) {
        val unit = 1f / scale.coerceAtLeast(0.01f)
        val left = layer.x * W
        val top = layer.y * H
        val right = (layer.x + layer.w) * W
        val bottom = (layer.y + layer.h) * H
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val ix = min(inset, (right - left) * 0.34f)
        val iy = min(inset, (bottom - top) * 0.34f)
        val innerLeft = left + ix
        val innerRight = right - ix
        val innerTop = top + iy
        val innerBottom = bottom - iy

        canvas.save()
        if (layer.frameRotation != 0f) canvas.rotate(layer.frameRotation, centerX, centerY)

        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD6E381.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f * unit
            pathEffect = DashPathEffect(floatArrayOf(9f * unit, 6f * unit), 0f)
        }
        canvas.drawRect(innerLeft, innerTop, innerRight, innerBottom, box)

        if (showHandles) {
            val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF2EEE3.toInt()
                style = Paint.Style.FILL
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF11231C.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f * unit
            }
            listOf(
                innerLeft to innerTop,
                innerRight to innerTop,
                innerRight to innerBottom,
                innerLeft to innerBottom,
            ).forEach { (x, y) ->
                canvas.drawCircle(x, y, 9f * unit, corner)
                canvas.drawCircle(x, y, 9f * unit, ring)
            }

            if (showRotate) {
                val gap = 62f
                val rotateY = if (innerTop - gap > 18f) innerTop - gap else innerBottom + gap
                val rotateX = centerX.coerceIn(innerLeft, innerRight)
                canvas.drawLine(centerX.coerceIn(innerLeft, innerRight), if (rotateY < innerTop) innerTop else innerBottom, rotateX, rotateY, ring)
                val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD6E381.toInt() }
                canvas.drawCircle(rotateX, rotateY, 10f * unit, grip)
                canvas.drawCircle(rotateX, rotateY, 10f * unit, ring)
            }
        }

        if (layer is VectorLayer) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF2EEE3.toInt()
                style = Paint.Style.FILL
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF11231C.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f * unit
            }
            layer.network.points.forEach { point ->
                val x = left + point.x * layer.w * W
                val y = top + point.y * layer.h * H
                val radius = if (point.id == selectedNodeId) 11f * unit else 6f * unit
                canvas.drawCircle(x, y, radius, dot)
                canvas.drawCircle(x, y, radius, ring)
                if (point.id == selectedNodeId) {
                    dot.color = 0xFFD6E381.toInt()
                    canvas.drawCircle(x, y, 4f * unit, dot)
                    dot.color = 0xFFF2EEE3.toInt()
                }
            }
        }
        canvas.restore()
    }

    private fun drawPaper(canvas: Canvas, typefaces: Typefaces) {
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1411231C.toInt()
            strokeWidth = 1f
        }
        var x = 54f
        while (x < W) {
            canvas.drawLine(x, 0f, x, H, grid)
            x += 54f
        }
        var y = 54f
        while (y < H) {
            canvas.drawLine(0f, y, W, y, grid)
            y += 54f
        }

        val band = Paint().apply { color = 0xFF0FBF92.toInt() }
        canvas.drawRect(0f, 0f, W, 15f, band)

        val margin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3311231C.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(54f, 54f, W - 54f, H - 54f, margin)

        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x6611231C.toInt()
            strokeWidth = 1f
        }
        canvas.drawLine(54f, 54f, 78f, 54f, cross)
        canvas.drawLine(54f, 54f, 54f, 78f, cross)
        canvas.drawLine(W - 54f, H - 54f, W - 78f, H - 54f, cross)
        canvas.drawLine(W - 54f, H - 54f, W - 54f, H - 78f, cross)

        val index = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x9911231C.toInt()
            textSize = 15f
            typeface = typefaces.forWeight(500)
            letterSpacing = 0.12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("ADD / ARTBOARD 001", W - 54f, 93f, index)
    }

    private fun drawVector(canvas: Canvas, layer: VectorLayer) {
        val path = layer.network.buildPath()
        val matrix = Matrix().apply {
            setScale(layer.w * W, layer.h * H)
            postTranslate(layer.x * W, layer.y * H)
        }
        path.transform(matrix)

        if (Color.alpha(layer.fill) > 0) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = layer.fill
                this.alpha = (Color.alpha(layer.fill) * layer.opacity).toInt()
            }
            canvas.drawPath(path, fill)
        }
        if (Color.alpha(layer.stroke) > 0) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = layer.stroke
                strokeWidth = layer.strokeWidth
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                this.alpha = (Color.alpha(layer.stroke) * layer.opacity).toInt()
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawIcon(canvas: Canvas, layer: IconLayer) {
        val alpha = (Color.alpha(layer.tint) * layer.opacity).toInt().coerceIn(0, 255)
        val tint = (layer.tint and 0x00FFFFFF) or (alpha shl 24)
        IconRenderer.draw(
            canvas = canvas,
            glyph = layer.glyph,
            tint = tint,
            left = layer.x * W,
            top = layer.y * H,
            width = layer.w * W,
            height = layer.h * H,
        )
    }

    private fun drawText(canvas: Canvas, layer: TextLayer, typefaces: Typefaces) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            textSize = layer.fontSize
            typeface = typefaces.forWeight(layer.weight)
            textAlign = when (layer.align) {
                TextAlign.Left -> Paint.Align.LEFT
                TextAlign.Center -> Paint.Align.CENTER
                TextAlign.Right -> Paint.Align.RIGHT
            }
        }
        val left = layer.x * W
        val right = (layer.x + layer.w) * W
        val top = layer.y * H
        val x = when (layer.align) {
            TextAlign.Left -> left
            TextAlign.Center -> (left + right) / 2f
            TextAlign.Right -> right
        }
        val lines = layer.text.ifBlank { " " }.split("\n")
        canvas.save()
        canvas.clipRect(left, top, right, (layer.y + layer.h) * H)
        var baseline = top + layer.fontSize
        lines.forEach { line ->
            canvas.drawText(line, x, baseline, paint)
            baseline += layer.fontSize * layer.lineHeight
        }
        canvas.restore()
    }

    private fun drawModelPlaceholder(canvas: Canvas, layer: ModelLayer, typefaces: Typefaces) {
        val rect = layerRect(layer)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0C1210.toInt()
            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, 18f, 18f, fill)

        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x553EF0C0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, 18f, 18f, rule)

        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x663EF0C0.toInt()
            strokeWidth = 1f
        }
        canvas.drawLine(rect.centerX(), rect.top, rect.centerX(), rect.bottom, cross)
        canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), cross)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD6E381.toInt()
            textSize = 16f
            typeface = typefaces.forWeight(500)
            letterSpacing = 0.1f
        }
        canvas.drawText("3D LAYER / ${layer.asset.label.uppercase()}", rect.left + 18f, rect.bottom - 20f, label)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99F2EEE3.toInt()
            textSize = 12f
            typeface = typefaces.forWeight(400)
        }
        canvas.drawText("glTF 2.0 · PBR · TURN", rect.left + 18f, rect.bottom - 42f, sub)
    }

    fun drawModelCapture(canvas: Canvas, layer: ModelLayer, bitmap: Bitmap) {
        val rect = layerRect(layer)
        val source = centerCrop(bitmap, rect)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawBitmap(bitmap, source, rect, paint)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, typefaces: Typefaces) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x9911231C.toInt()
            textSize = 14f
            typeface = typefaces.forWeight(500)
            letterSpacing = 0.12f
        }
        canvas.drawText("1080 × 1350 / sRGB", 54f, H - 72f, paint)
        val right = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("ADD ENGINE SPIKE", W - 54f, H - 72f, right)
    }

    private fun layerRect(layer: Layer): RectF = RectF(
        layer.x * W,
        layer.y * H,
        (layer.x + layer.w) * W,
        (layer.y + layer.h) * H,
    )

    private fun centerCrop(bitmap: Bitmap, target: RectF): Rect {
        val targetRatio = target.width() / target.height()
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return if (bitmapRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
    }
}
