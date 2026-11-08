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
    ): String {
        val artboard = Bitmap.createBitmap(
            ArtboardPainter.W.toInt(),
            ArtboardPainter.H.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(artboard)
        val visibleModels = layers.filterIsInstance<ModelLayer>().filter { it.visible }
        val captures = mutableMapOf<String, Bitmap>()
        visibleModels.forEach { model ->
            val view = sceneViews[model.id] ?: return@forEach
            capture(view)?.let { captures[model.id] = it }
        }
        ArtboardPainter.draw(canvas, layers, typefaces, captures)
        captures.values.forEach { it.recycle() }

        val saved = withContext(Dispatchers.IO) { save(context, artboard) }
        return if (visibleModels.any { it.id !in captures }) "$saved · 3D CAPTURE UNAVAILABLE" else saved
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

    private suspend fun capture(view: SceneView): Bitmap? = requestCapture(view).bitmap

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

    private fun save(context: Context, bitmap: Bitmap): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "add-engine-$stamp.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Engine Spike")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "EXPORT FAILED · could not create a MediaStore entry"
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: return "EXPORT FAILED · could not open the destination"
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return "SAVED · Pictures/Engine Spike/$name"
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return "SAVED · ${file.absolutePath}"
    }
}
