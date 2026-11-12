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
 *
 * Decoding is bounded and cached by path plus mtime: the stage asks for a preview no
 * larger than 2048 px, while export asks for a larger safe source, so a phone-sized
 * screen never pays for full-resolution pixels. A replaced file gets a new mtime and
 * therefore a new cache entry instead of drawing stale bytes.
 */
object ImageStore {
    const val PREVIEW_MAX_PX = 2048
    const val EXPORT_MAX_PX = 4096
    private const val DIR = "media"
    private const val CACHE_MAX_BYTES = 64 * 1024 * 1024

    private data class Key(val name: String, val modified: Long, val maxDimension: Int)

    private val cache = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {}
    private var cachedBytes = 0

    fun import(context: Context, uri: Uri): String? = runCatching {
        val name = "photo-${UUID.randomUUID().toString().take(8)}.jpg"
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, name).outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        name
    }.getOrNull()

    @Synchronized
    fun load(
        context: Context,
        name: String,
        maxDimension: Int = PREVIEW_MAX_PX,
    ): Bitmap? {
        if (name.isBlank()) return null
        val file = File(File(context.filesDir, DIR), name)
        if (!file.isFile) return null
        val key = Key(name, file.lastModified(), maxDimension)
        cache[key]?.let { bitmap ->
            if (!bitmap.isRecycled) return bitmap
            cache.remove(key)
            cachedBytes -= bitmap.byteCount
        }
        val bitmap = runCatching { decode(file, maxDimension) }.getOrNull() ?: return null
        if (bitmap.byteCount <= CACHE_MAX_BYTES) {
            cache[key] = bitmap
            cachedBytes += bitmap.byteCount
            trim()
        }
        return bitmap
    }

    /** The decoded bitmap can still be in a frame, so entries are dropped, never recycled. */
    @Synchronized
    fun forget(name: String) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.name == name) {
                cachedBytes -= entry.value.byteCount
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clearCache() {
        cache.clear()
        cachedBytes = 0
    }

    private fun decode(file: File, maxDimension: Int): Bitmap? {
        if (maxDimension <= 0) {
            return BitmapFactory.decodeFile(file.path)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDimension) sample *= 2
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun trim() {
        val iterator = cache.entries.iterator()
        while (cachedBytes > CACHE_MAX_BYTES && iterator.hasNext()) {
            val entry = iterator.next()
            cachedBytes -= entry.value.byteCount
            iterator.remove()
        }
    }
}
