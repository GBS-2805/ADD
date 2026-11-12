package app.add.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolves the document paint model to a real Android shader. Native gradients
 * cover linear/radial/sweep on every supported API; diamond has an AGSL path on
 * API 33+ and a bitmap shader fallback below it, so the same document renders on
 * an old phone and on a new one.
 */
object PaintRenderer {
    private val diamondCache = mutableMapOf<String, Bitmap>()
    private val patternCache = mutableMapOf<String, Bitmap>()

    fun shaderFor(
        spec: PaintSpec?,
        layer: Layer,
        rect: RectF,
        images: Map<String, Bitmap>,
        fieldPhase: Float = 0f,
    ): Shader? {
        if (spec == null || rect.width() <= 0f || rect.height() <= 0f) return null
        return when (spec) {
            is PaintSpec.Solid -> null
            is PaintSpec.Gradient -> gradientShader(spec, rect)
            is PaintSpec.Image -> imageShader(spec, rect, images)
            is PaintSpec.Pattern -> patternShader(spec, rect)
            is PaintSpec.Field -> FieldRenderer.shader(spec.field, rect, fieldPhase)
        }
    }

    private fun gradientShader(spec: PaintSpec.Gradient, rect: RectF): Shader? {
        val resolved = shaderStops(spec.stops) ?: return null
        val colors = resolved.first
        val positions = resolved.second
        return when (spec.kind) {
            PaintKind.Linear -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
            PaintKind.Radial -> RadialGradient(
                rect.centerX(),
                rect.centerY(),
                max(rect.width(), rect.height()) / 2f,
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
            PaintKind.Sweep -> SweepGradient(
                rect.centerX(),
                rect.centerY(),
                colors,
                positions,
            )
            PaintKind.Diamond -> diamondShader(spec, rect)
            else -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
        }
    }

    private fun diamondShader(spec: PaintSpec.Gradient, rect: RectF): Shader {
        val ordered = spec.stops.sortedBy { it.offset }
        if (Build.VERSION.SDK_INT >= 33 && ordered.size in 2..MAX_AGSL_STOPS) {
            val agsl = runCatching {
                val shader = RuntimeShader(diamondAgs(ordered.size))
                shader.setFloatUniform("uOrigin", rect.left, rect.top)
                shader.setFloatUniform("uSize", rect.width(), rect.height())
                ordered.forEachIndexed { index, stop ->
                    shader.setColorUniform("uColor$index", stop.color)
                    shader.setFloatUniform("uOffset$index", stop.offset.coerceIn(0f, 1f))
                }
                shader
            }.getOrNull()
            if (agsl != null) return agsl
        }
        return diamondBitmapShader(spec, rect)
    }

    /**
     * Diamond has no platform gradient, so AGSL does the work on API 33+. The shader
     * is generated for the stop count rather than hard-coded for three, which keeps
     * every stop and its alpha on the GPU; older phones keep the bitmap-shader path.
     */
    private fun diamondAgs(count: Int): String = buildString {
        appendLine("uniform float2 uOrigin;")
        appendLine("uniform float2 uSize;")
        for (index in 0 until count) {
            appendLine("uniform half4 uColor$index;")
            appendLine("uniform float uOffset$index;")
        }
        appendLine("half4 main(float2 p) {")
        appendLine("    float2 uv = (p - uOrigin) / max(uSize, float2(1.0, 1.0));")
        appendLine("    float t = clamp((abs(uv.x - 0.5) + abs(uv.y - 0.5)) * 2.0, 0.0, 1.0);")
        for (index in 1 until count) {
            appendLine("    if (t <= uOffset$index) {")
            appendLine("        float span = max(uOffset$index - uOffset${index - 1}, 0.0001);")
            appendLine("        return mix(uColor${index - 1}, uColor$index, clamp((t - uOffset${index - 1}) / span, 0.0, 1.0));")
            appendLine("    }")
        }
        appendLine("    return uColor${count - 1};")
        appendLine("}")
    }

    private fun diamondBitmapShader(spec: PaintSpec.Gradient, rect: RectF): Shader {
        val key = spec.stops.joinToString("|") { "${it.offset}:${it.color}" } + ":" + spec.kind.name
        val bitmap = diamondCache.getOrPut(key) { buildDiamond(spec.stops) }
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(rectMatrix(bitmap, rect))
        return shader
    }

    private fun buildDiamond(stops: List<PaintStop>): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = (x + 0.5f) / size
                val ny = (y + 0.5f) / size
                val t = ((abs(nx - 0.5f) + abs(ny - 0.5f)) * 2f).coerceIn(0f, 1f)
                pixels[y * size + x] = colorAt(stops, t)
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun imageShader(spec: PaintSpec.Image, rect: RectF, images: Map<String, Bitmap>): Shader? {
        val bitmap = images[spec.path] ?: return null
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = if (spec.fill) {
            max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        } else {
            min(rect.width() / bitmap.width, rect.height() / bitmap.height)
        }
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            rect.left + (rect.width() - bitmap.width * scale) / 2f,
            rect.top + (rect.height() - bitmap.height * scale) / 2f,
        )
        shader.setLocalMatrix(matrix)
        return shader
    }

    private fun patternShader(spec: PaintSpec.Pattern, rect: RectF): Shader {
        val key = "${spec.kind}:${spec.foreground}:${spec.background}:${spec.scale}"
        val bitmap = patternCache.getOrPut(key) { buildPattern(spec) }
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val scale = spec.scale.coerceIn(0.25f, 8f)
        shader.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate(rect.left, rect.top)
        })
        return shader
    }

    private fun buildPattern(spec: PaintSpec.Pattern): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { spec.background }
        fun put(x: Int, y: Int, color: Int) {
            if (x in 0 until size && y in 0 until size) pixels[y * size + x] = color
        }
        when (spec.kind) {
            PatternKind.Checks -> {
                val cell = 16
                for (y in 0 until size) for (x in 0 until size) {
                    if (((x / cell) + (y / cell)) % 2 == 0) put(x, y, spec.foreground)
                }
            }
            PatternKind.Dots -> {
                val cell = 16
                for (y in 0 until size) for (x in 0 until size) {
                    val dx = x % cell - cell / 2
                    val dy = y % cell - cell / 2
                    if (dx * dx + dy * dy <= 25) put(x, y, spec.foreground)
                }
            }
            PatternKind.Stripes -> {
                for (y in 0 until size) for (x in 0 until size) {
                    if ((x + y) % 16 < 8) put(x, y, spec.foreground)
                }
            }
            PatternKind.Grid -> {
                for (y in 0 until size) for (x in 0 until size) {
                    if (x % 16 < 2 || y % 16 < 2) put(x, y, spec.foreground)
                }
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun rectMatrix(bitmap: Bitmap, rect: RectF): Matrix = Matrix().apply {
        setScale(rect.width() / bitmap.width, rect.height() / bitmap.height)
        postTranslate(rect.left, rect.top)
    }

    /** A uniform block has a practical size; beyond this the bitmap shader carries it. */
    private const val MAX_AGSL_STOPS = 24
}
