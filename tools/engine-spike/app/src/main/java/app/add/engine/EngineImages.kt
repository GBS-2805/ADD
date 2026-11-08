package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Photos are copied into the app before anything else touches them: a picked file can
 * be moved, revoked or deleted, and a layer that points at it would draw as a hole.
 * The cache keeps a decoded page from being re-decoded on every frame.
 */
object ImageStore {
    private const val DIR = "media"
    private val cache = mutableMapOf<String, Bitmap>()

    fun import(context: Context, uri: Uri): String? = runCatching {
        val name = "photo-${UUID.randomUUID().toString().take(8)}.jpg"
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, name).outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        name
    }.getOrNull()

    fun load(context: Context, name: String): Bitmap? {
        cache[name]?.let { if (!it.isRecycled) return it }
        return runCatching {
            BitmapFactory.decodeFile(File(File(context.filesDir, DIR), name).path)
        }.getOrNull()?.also { cache[name] = it }
    }

    fun forget(name: String) {
        cache.remove(name)
    }
}
