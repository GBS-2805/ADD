package app.add.engine

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.util.Locale

/** Display-only casing: the stored text is never rewritten. */
enum class TextCase { None, Upper, Lower }

fun TextCase.applyTo(text: String): String = when (this) {
    TextCase.None -> text
    TextCase.Upper -> text.uppercase(Locale.ROOT)
    TextCase.Lower -> text.lowercase(Locale.ROOT)
}

/**
 * The one place that turns a family name into a face. Bundled Poppins, serif and
 * mono always resolve; a font pack installed under filesDir/font-packs resolves by
 * its folder name; anything else lands on Poppins and says so, because a silent
 * substitution is how a document ends up looking different on another phone.
 */
class TypefaceRegistry(context: Context) {
    data class Resolution(val typeface: Typeface, val family: String, val substituted: Boolean)

    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val cache = mutableMapOf<String, Typeface>()
    private val bundled = listOf("Poppins", "Serif", "Mono")
    private val poppins = mapOf(
        400 to "fonts/poppins_regular.ttf",
        500 to "fonts/poppins_medium.ttf",
        600 to "fonts/poppins_semibold.ttf",
    )

    fun families(): List<String> {
        val packs = packFamilies().map { it.first }
        return (bundled + packs).distinctBy { it.lowercase(Locale.ROOT) }
    }

    fun resolve(family: String, weight: Int): Resolution {
        val requested = family.trim().ifBlank { "Poppins" }
        bundled.firstOrNull { it.equals(requested, ignoreCase = true) }?.let { match ->
            val face = when (match) {
                "Poppins" -> {
                    val path = poppins.entries.minByOrNull { kotlin.math.abs(it.key - weight) }!!.value
                    cache.getOrPut(path) { Typeface.createFromAsset(assets, path) }
                }
                "Serif" -> Typeface.create("serif", styleFor(weight))
                else -> Typeface.create("monospace", styleFor(weight))
            }
            return Resolution(face, match, false)
        }
        packFamilies().firstOrNull { it.first.equals(requested, ignoreCase = true) }?.let { (name, file) ->
            val face = cache.getOrPut(file.absolutePath) { Typeface.createFromFile(file) }
            return Resolution(face, name, false)
        }
        return Resolution(resolve("Poppins", weight).typeface, "Poppins", true)
    }

    private fun styleFor(weight: Int): Int =
        if (weight >= 600) Typeface.BOLD else Typeface.NORMAL

    private fun packFamilies(): List<Pair<String, File>> {
        val root = File(appContext.filesDir, "font-packs")
        if (!root.isDirectory) return emptyList()
        return root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val faces = dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("ttf", ignoreCase = true) }
                .toList()
            val face = faces.firstOrNull { it.name.contains("regular", true) } ?: faces.firstOrNull() ?: return@mapNotNull null
            displayName(dir.name) to face
        } ?: emptyList()
    }

    private fun displayName(slug: String): String = slug
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.ROOT) } }
}
