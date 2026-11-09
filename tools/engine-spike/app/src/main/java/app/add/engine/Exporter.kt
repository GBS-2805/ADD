package app.add.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import io.github.sceneview.SceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class CaptureResult(
    val success: Boolean,
    val width: Int,
    val height: Int,
    val litSamples: Int,
    val totalSamples: Int,
    val message: String,
)

/**
 * What an export produced, not just whether it worked: the size of the file, the pixels
 * it holds and how many 3D views made it in. A model that drops out of the capture is
 * invisible in the status string, so the counts travel with the result.
 */
data class ExportResult(
    val status: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val models: Int,
    val missing: Int,
    val black: Int,
    val litPercent: Int,
)

private data class CaptureAttempt(
    val bitmap: Bitmap?,
    val width: Int,
    val height: Int,
    val message: String,
)

object Exporter {
    suspend fun export(
        context: Context,
        layers: List<Layer>,
        typefaces: Typefaces,
        sceneViews: Map<String, SceneView?>,
        scale: Int = 1,
    ): ExportResult {
        val factor = scale.coerceIn(1, 4)
        // A 2x export is the same page at twice the pixels: the painter draws in
        // artboard units, so the canvas carries the multiplier and nothing else moves.
        val artboard = Bitmap.createBitmap(
            (ArtboardPainter.W * factor).toInt(),
            (ArtboardPainter.H * factor).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(artboard)
        val visibleModels = layers.filterIsInstance<ModelLayer>().filter { it.visible }
        val captures = mutableMapOf<String, Bitmap>()
        var lit = 0
        var sampled = 0
        var black = 0
        visibleModels.forEach { model ->
            val view = sceneViews[model.id] ?: return@forEach
            val raw = requestCapture(view).bitmap ?: return@forEach
            // A surface can still hand back an opaque frame with a black clear colour,
            // which painted a black rectangle over the paper in the export. Read that
            // black as alpha, so the model composites instead of blocking the page.
            val bitmap = keyedCapture(raw)
            if (bitmap !== raw) raw.recycle()
            // Same counter the test capture uses: a model that came back 0% lit is in
            // the file but is not visible in it, which a missing model never is.
            val (modelLit, modelTotal) = litSamples(bitmap)
            lit += modelLit
            sampled += modelTotal
            if (modelLit == 0) black++
            captures[model.id] = bitmap
        }
        val images = layers.filterIsInstance<ImageLayer>().mapNotNull { layer ->
            val bitmap = ImageStore.load(context, layer.path) ?: return@mapNotNull null
            layer.path to bitmap
        }.toMap()
        ArtboardPainter.draw(
            canvas,
            layers,
            typefaces,
            captures,
            images,
            scale = factor.toFloat(),
        )
        captures.values.forEach { it.recycle() }
        val missing = visibleModels.count { it.id !in captures }

        val saved = withContext(Dispatchers.IO) { save(context, artboard) }
        return ExportResult(
            status = buildString {
                append(saved.message)
                if (missing > 0) append(" · 3D CAPTURE UNAVAILABLE")
                if (black > 0) append(" · ${black} 3D BLACK")
            },
            width = artboard.width,
            height = artboard.height,
            bytes = saved.bytes,
            models = visibleModels.size,
            missing = missing,
            black = black,
            litPercent = if (sampled > 0) lit * 100 / sampled else 0,
        )
    }

    /**
     * Runs the same PixelCopy path as export without writing a PNG. This is the
     * diagnostic that separates "the SceneView is attached" from "a frame can be read".
     */
    suspend fun testCapture(view: SceneView?): CaptureResult {
        if (view == null) {
            return CaptureResult(false, 0, 0, 0, 0, "VIEW NOT ATTACHED")
        }
        return try {
            val attempt = requestCapture(view)
            val bitmap = attempt.bitmap
                ?: return CaptureResult(
                    success = false,
                    width = attempt.width,
                    height = attempt.height,
                    litSamples = 0,
                    totalSamples = 0,
                    message = attempt.message,
                )
            val (lit, total) = litSamples(bitmap)
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()
            CaptureResult(
                success = true,
                width = width,
                height = height,
                litSamples = lit,
                totalSamples = total,
                message = if (lit == 0) "PIXELCOPY OK · BLACK FRAME" else "PIXELCOPY OK · $lit/$total LIT",
            )
        } catch (error: Exception) {
            CaptureResult(false, 0, 0, 0, 0, "PIXELCOPY ERROR · ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private suspend fun requestCapture(view: SceneView): CaptureAttempt {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            return CaptureAttempt(null, width, height, "VIEW NOT LAID OUT")
        }
        if (!view.holder.surface.isValid) {
            return CaptureAttempt(null, width, height, "SURFACE NOT VALID")
        }
        return suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                view as SurfaceView,
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        bitmap.recycle()
                    } else {
                        val success = result == PixelCopy.SUCCESS
                        if (!success) bitmap.recycle()
                        continuation.resume(
                            CaptureAttempt(
                                bitmap = if (success) bitmap else null,
                                width = width,
                                height = height,
                                message = pixelCopyName(result),
                            )
                        )
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /**
     * Turns a frame rendered on black into straight alpha: a pixel's brightest channel
     * becomes its opacity and the colour is un-premultiplied, so a black background
     * disappears while the model keeps its shading. A frame that already carries alpha
     * is returned untouched.
     */
    private fun keyedCapture(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var changed = false
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val alpha = maxOf(r, g, b)
            if (alpha == 255) continue
            changed = true
            if (alpha == 0) {
                pixels[i] = 0
                continue
            }
            val scale = 255f / alpha
            val rr = (r * scale).toInt().coerceAtMost(255)
            val gg = (g * scale).toInt().coerceAtMost(255)
            val bb = (b * scale).toInt().coerceAtMost(255)
            pixels[i] = (alpha shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        if (!changed) return bitmap
        val keyed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        keyed.setPixels(pixels, 0, width, 0, 0, width, height)
        return keyed
    }

    private fun litSamples(bitmap: Bitmap): Pair<Int, Int> {
        var lit = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                total += 1
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 30) {
                    lit += 1
                }
                x += 8
            }
            y += 8
        }
        return lit to total
    }

    private fun pixelCopyName(result: Int): String = when (result) {
        PixelCopy.SUCCESS -> "PIXELCOPY OK"
        PixelCopy.ERROR_SOURCE_INVALID -> "SOURCE INVALID"
        PixelCopy.ERROR_TIMEOUT -> "TIMEOUT"
        PixelCopy.ERROR_SOURCE_NO_DATA -> "SOURCE NO DATA"
        PixelCopy.ERROR_DESTINATION_INVALID -> "DESTINATION INVALID"
        PixelCopy.ERROR_UNKNOWN -> "UNKNOWN"
        else -> "CODE $result"
    }

    private data class Saved(val message: String, val bytes: Long)

    private fun save(context: Context, bitmap: Bitmap): Saved {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "add-engine-$stamp.png"
        // One encode, so the size reported is the size written.
        val png = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Engine Spike")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Saved("EXPORT FAILED · could not create a MediaStore entry", 0L)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(png)
            } ?: return Saved("EXPORT FAILED · could not open the destination", 0L)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return Saved("SAVED · Pictures/Engine Spike/$name", png.size.toLong())
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            stream.write(png)
        }
        return Saved("SAVED · ${file.absolutePath}", png.size.toLong())
    }
}
