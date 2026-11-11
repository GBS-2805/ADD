package app.add.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The compositor renders a whole group to one bitmap and applies the group's
 * opacity once, so two translucent children cannot multiply into each other.
 * A single-child group at full opacity must be pixel-identical to the ungrouped
 * layer; that is the proof the transform path adds no drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupRenderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val typefaces = Typefaces(context)

    private fun photo(id: String): ImageLayer = ImageLayer(
        id = id,
        name = id,
        x = 0f,
        y = 0f,
        w = 1f,
        h = 0.8f,
        path = "red.png",
        fill = true,
    )

    private fun render(layers: List<Layer>): Int {
        // Blue, measured on the green channel: the cream page is red-dominant, so a
        // red fixture would make the layer indistinguishable from the paper.
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF2F6BFF.toInt())
        }
        val bitmap = Bitmap.createBitmap(
            ArtboardPainter.W.toInt(),
            ArtboardPainter.H.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        ArtboardPainter.draw(
            canvas = Canvas(bitmap),
            layers = layers,
            typefaces = typefaces,
            images = mapOf("red.png" to source),
            scale = 1f,
        )
        return bitmap.getPixel(540, 500)
    }

    private fun green(pixel: Int) = (pixel ushr 8) and 0xFF

    @Test
    fun aSingleChildGroupMatchesTheUngroupedLayer() {
        val plain = render(listOf(photo("one")))
        val grouped = render(
            listOf(
                GroupLayer(
                    id = "g",
                    name = "G",
                    x = 0f,
                    y = 0f,
                    w = 1f,
                    h = 0.8f,
                    children = listOf(photo("one")),
                )
            )
        )
        assertEquals(plain, grouped)
    }

    @Test
    fun groupOpacityCompositesOnceOverTheArtboard() {
        val plain = render(listOf(photo("a")))
        fun group(opacity: Float) = GroupLayer(
            id = "g",
            name = "G",
            x = 0f,
            y = 0f,
            w = 1f,
            h = 0.8f,
            opacity = opacity,
            children = listOf(photo("a")),
        )
        assertEquals(0.5f, group(0.5f).opacity, 0f)
        val backdrop = green(render(emptyList()))
        val groupHalf = render(listOf(group(0.5f)))
        val groupInvisible = render(listOf(group(0f)))
        // The photo's green is 107 and the page's is ~233; one isolated 50% composite
        // lands halfway, and a fully transparent group lets the page through.
        assertEquals(107.0, green(plain).toDouble(), 6.0)
        assertEquals(backdrop.toDouble(), green(groupInvisible).toDouble(), 2.0)
        assertEquals((107 + backdrop) / 2.0, green(groupHalf).toDouble(), 4.0)
    }

    @Test
    fun groupOpacityIsStoredOnTheGroupNotPerChild() {
        val group = GroupLayer(
            id = "g",
            name = "G",
            x = 0f,
            y = 0f,
            w = 1f,
            h = 0.8f,
            opacity = 0.5f,
            children = listOf(photo("a"), photo("b")),
        )
        // The compositor renders this group to one bitmap and composites it once with
        // group.opacity; children carry no opacity of their own.
        assertEquals(0.5f, group.opacity, 0.0001f)
        assertTrue(group.children.all { it.opacity == 1f })
    }

    @Test
    fun nestedGroupChildrenKeepTheirPositions() {
        val inner = GroupLayer(
            id = "inner",
            name = "INNER",
            x = 0.1f,
            y = 0.1f,
            w = 0.4f,
            h = 0.3f,
            rotation = 15f,
            children = listOf(photo("one").copy(x = 0.1f, y = 0.05f, w = 0.2f, h = 0.15f)),
        )
        val outer = GroupLayer(
            id = "outer",
            name = "OUTER",
            x = 0f,
            y = 0f,
            w = 1f,
            h = 0.8f,
            children = listOf(inner),
        )
        val plain = render(listOf(inner))
        val nested = render(listOf(outer))
        // The outer group is an identity frame, so nesting changes nothing.
        assertEquals(plain, nested)
    }
}
