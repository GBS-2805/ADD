package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BlendMathTest {
    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun red(pixel: Int) = (pixel ushr 16) and 0xFF
    private fun green(pixel: Int) = (pixel ushr 8) and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun alpha(pixel: Int) = (pixel ushr 24) and 0xFF

    private val backdrop = argb(255, 200, 120, 40)
    private val source = argb(255, 40, 60, 200)

    @Test
    fun multiplyScreensAndOverlayUseTheBackdrop() {
        val multiply = BlendMath.blendPixel(backdrop, source, BlendKind.Multiply, 1f)
        assertEquals(31, red(multiply))
        assertEquals(28, green(multiply))
        assertEquals(31, blue(multiply))

        val screen = BlendMath.blendPixel(backdrop, source, BlendKind.Screen, 1f)
        assertEquals(209, red(screen))
        assertEquals(152, green(screen))
        assertEquals(209, blue(screen))

        val overlay = BlendMath.blendPixel(backdrop, source, BlendKind.Overlay, 1f)
        assertEquals(162, red(overlay))
    }

    @Test
    fun differenceAndExclusionAreNonDestructive() {
        val difference = BlendMath.blendPixel(backdrop, source, BlendKind.Difference, 1f)
        assertEquals(160, red(difference))
        assertEquals(60, green(difference))
        assertEquals(160, blue(difference))

        val exclusion = BlendMath.blendPixel(backdrop, source, BlendKind.Exclusion, 1f)
        assertEquals(177, red(exclusion))
        assertEquals(124, green(exclusion))
        assertEquals(177, blue(exclusion))
    }

    @Test
    fun dodgeAndBurnMatchTheW3CFormulas() {
        val dodge = BlendMath.blendPixel(argb(255, 40, 40, 40), argb(255, 128, 128, 128), BlendKind.ColorDodge, 1f)
        assertEquals(80, red(dodge))

        val burn = BlendMath.blendPixel(argb(255, 200, 200, 200), argb(255, 128, 128, 128), BlendKind.ColorBurn, 1f)
        assertEquals(145, red(burn))
    }

    @Test
    fun opacityBlendsTheCompositeNotTheSource() {
        val half = BlendMath.blendPixel(backdrop, source, BlendKind.Normal, 0.5f)
        assertEquals(120, red(half))
        assertEquals(90, green(half))
        assertEquals(120, blue(half))
        assertEquals(255, alpha(half))
    }

    @Test
    fun transparentBackdropKeepsTheSource() {
        val out = BlendMath.blendPixel(0, argb(128, 10, 20, 30), BlendKind.Multiply, 1f)
        assertEquals(128, alpha(out))
        assertEquals(10, red(out))
        assertEquals(20, green(out))
        assertEquals(30, blue(out))
    }

    @Test
    fun everyModeStaysInRange() {
        BlendKind.entries.forEach { mode ->
            val out = BlendMath.blendPixel(backdrop, source, mode, 1f)
            assertEquals(255, alpha(out))
            listOf(red(out), green(out), blue(out)).forEach { channel ->
                assertEquals(true, channel in 0..255)
            }
        }
    }

    @Test
    fun theStackAccumulatesEachBlendAgainstEverythingBelow() {
        val paper = argb(255, 243, 235, 221)
        val ink = argb(255, 17, 35, 28)
        val mint = argb(255, 15, 191, 146)

        // A three-layer page: paper, an ink multiply, then mint screen. The mint must
        // see the multiplied ink, not the bare paper and not just the ink layer.
        val first = BlendMath.blendPixel(paper, ink, BlendKind.Multiply, 1f)
        val stack = BlendMath.blendPixel(first, mint, BlendKind.Screen, 1f)
        val mintAlone = BlendMath.blendPixel(paper, mint, BlendKind.Screen, 1f)

        assertNotEquals(mintAlone, stack)
        assertNotEquals(first, stack)
        assertEquals(
            BlendMath.blendPixel(first, mint, BlendKind.Screen, 1f),
            stack,
        )
    }

    @Test
    fun theStandardSetHasNineteenModes() {
        assertEquals(19, BlendKind.entries.size)
    }
}
