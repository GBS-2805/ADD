package app.add.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8's contract is algorithmic, so every proof here uses the pure IntArray
 * renderer. No Robolectric bitmap or device is involved in determinism.
 */
class TexturePresetTest {
    // Reflective because the Android test compile classpath does not expose
    // java.lang.management, while the JVM that runs these tests does.
    private val cpuBean: Any? = runCatching {
        Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)
    }.getOrNull()

    /**
     * CPU time, not wall time: a shared CI runner can deschedule the test thread
     * mid-render, and a frame budget is about the work, not the wait.
     */
    private fun cpuNow(): Long {
        val bean = cpuBean ?: return System.nanoTime()
        return runCatching {
            bean.javaClass.getMethod("getCurrentThreadCpuTime").invoke(bean) as Long
        }.getOrDefault(System.nanoTime())
    }

    @Test
    fun tenFamiliesArePresentAndStable() {
        assertEquals(10, TextureFamily.entries.size)
        assertEquals(10, TexturePresets.presets.size)
        assertEquals(TexturePresets.presets.map { it.id }.distinct().size, TexturePresets.presets.size)
        TextureFamily.entries.forEach { family ->
            assertEquals(family, TexturePresets.byId(family.id)?.kind)
            assertTrue(TexturePresets.seedFor(family) != 0)
        }
    }

    @Test
    fun sameSeedProducesByteIdenticalPixelsForEveryFamily() {
        TextureFamily.entries.forEach { family ->
            val first = TexturePresets.render(family, seed = 4_242, width = 96, height = 120)
            val second = TexturePresets.render(family, seed = 4_242, width = 96, height = 120)
            assertEquals(96, first.width)
            assertEquals(120, first.height)
            assertArrayEquals("$family must be deterministic", first.argb, second.argb)
        }
    }

    @Test
    fun differentSeedsProduceVisiblyDifferentPixels() {
        TextureFamily.entries.forEach { family ->
            val a = TexturePresets.render(family, seed = 1_001, width = 96, height = 120)
            val b = TexturePresets.render(family, seed = 9_001, width = 96, height = 120)
            val changed = a.argb.indices.count { a.argb[it] != b.argb[it] }
            assertTrue("$family should change with its seed, changed $changed px", changed > a.argb.size / 8)
        }
    }

    @Test
    fun sizeChangeResamplesTheFieldAtTheNewSizeAndNeverStretchesIt() {
        TextureFamily.entries.forEach { family ->
            val small = TexturePresets.render(family, seed = 77, width = 120, height = 150)
            val large = TexturePresets.render(family, seed = 77, width = 240, height = 300)
            assertEquals(120 * 150, small.argb.size)
            assertEquals(240 * 300, large.argb.size)
            assertEquals(240, large.width)
            assertEquals(300, large.height)
            assertTrue(small.argb.any { it ushr 24 != 0 })
            assertTrue(large.argb.any { it ushr 24 != 0 })
        }

        // A nearest-neighbour upscale of the small field is what a stretched bitmap
        // would look like. The large render must have new detail at most pixels, not
        // the same pixel repeated into a bigger grid.
        TextureFamily.entries.forEach { family ->
            val small = TexturePresets.render(family, seed = 8, width = 120, height = 150)
            val large = TexturePresets.render(family, seed = 8, width = 240, height = 300)
            val repeated = IntArray(large.argb.size) { index ->
                val x = index % large.width
                val y = index / large.width
                small.pixel(x * small.width / large.width, y * small.height / large.height)
            }
            val fresh = large.argb.indices.count { large.argb[it] != repeated[it] }
            assertTrue("$family looks stretched ($fresh fresh pixels)", fresh > large.argb.size / 100)
        }
    }

    @Test
    fun dimensionGuardsReturnAValidRasterInsteadOfAnIndexBlowup() {
        val zero = TexturePresets.render(TextureFamily.Brick, seed = 1, width = 0, height = -4)
        assertEquals(1, zero.width)
        assertEquals(1, zero.height)
        assertEquals(1, zero.argb.size)

        val huge = TexturePresets.render(TextureFamily.Concrete, seed = 2, width = 99_999, height = 99_999)
        assertEquals(4_096, huge.width)
        assertEquals(4_096, huge.height)
        assertEquals(4_096 * 4_096, huge.argb.size)
    }

    @Test
    fun everyFamilyUsesOnlyLocalProceduralPixels() {
        // A rendered family must already contain varied material. There is no decode
        // fallback, no resource id and no bitmap in TexturePixels.
        TextureFamily.entries.forEach { family ->
            val pixels = TexturePresets.render(family, seed = 5, width = 64, height = 80)
            assertEquals(64 * 80, pixels.argb.size)
            assertTrue("$family rendered a blank field", pixels.argb.distinct().size > 1)
            assertTrue(pixels.argb.all { (it ushr 24) in 0..210 })
        }
    }

        /**
     * Best of nine at the measured size, after untimed passes until the cost settles.
     * Warming on a small field was not enough, and neither was a fixed three passes at
     * the measured size: the loop is compiled lazily, and on a contended runner the
     * compiler threads starve, so a fixed count can still sample half-compiled code
     * (the 22 November visual runs failed a family that a twin run measured inside the
     * budget). The warmup runs for at least eight passes, then until three consecutive
     * passes are within 10% of the best, capped at thirty. The samples stay min-based
     * and the budget stays at one frame, so only a family that is slow once compiled
     * can fail.
     */
    private fun previewSamplesMs(family: TextureFamily, width: Int, height: Int): List<Double> {
        val seed = TexturePresets.seedFor(family)
        var bestWarmMs = Double.MAX_VALUE
        var settledPasses = 0
        var warmupPasses = 0
        while (warmupPasses < MAX_WARMUP_PASSES) {
            val started = cpuNow()
            TexturePresets.render(family, seed, width, height)
            val passMs = (cpuNow() - started) / 1_000_000.0
            warmupPasses += 1
            bestWarmMs = minOf(bestWarmMs, passMs)
            settledPasses = if (passMs <= bestWarmMs * 1.10) settledPasses + 1 else 0
            if (warmupPasses >= MIN_WARMUP_PASSES && settledPasses >= SETTLED_PASSES) break
        }
        val samples = (1..SAMPLES).map {
            val started = cpuNow()
            TexturePresets.render(family, seed, width, height)
            (cpuNow() - started) / 1_000_000.0
        }
        println(
            "texture family=$family ${width}x$height warmupPasses=$warmupPasses " +
                "warmBestMs=${"%.3f".format(bestWarmMs)} " +
                "samplesMs=${samples.joinToString(", ") { "%.3f".format(it) }}",
        )
        return samples
    }

    private companion object {
        const val MIN_WARMUP_PASSES = 8
        const val MAX_WARMUP_PASSES = 30
        const val SETTLED_PASSES = 3
        const val SAMPLES = 9
    }

    @Test
    fun oneFamilyAtThePreviewFieldFitsTheFrameBudget() {
        // The renderer caps a field at 240k pixels (about 300x400). The inspector
        // asks for one family when the reader picks it, then the cache serves frames.
        TextureFamily.entries.forEach { family ->
            val samples = previewSamplesMs(family, 300, 375)
            val best = samples.min()
            // The samples travel with the failure: a CI log otherwise says only
            // "took Nms", which cannot tell a cold compiler from a slow family.
            assertTrue(
                "$family took ${"%.2f".format(best)}ms over $SAMPLES samples " +
                    "(${samples.joinToString(",") { "%.2f".format(it) }})",
                best < 16.0,
            )
        }
    }

    @Test
    fun allTenFamilyThumbnailsWarmInsideOneFrame() {
        // The shelf can describe all ten without decoding anything. This is the
        // all-families cold measurement at a representative thumbnail field.
        TextureFamily.entries.forEach { family ->
            TexturePresets.render(family, TexturePresets.seedFor(family), 24, 30)
        }
        val times = (0 until 5).map { pass ->
            var checksum = 0
            val started = cpuNow()
            TextureFamily.entries.forEach { family ->
                checksum += TexturePresets.render(
                    family,
                    TexturePresets.seedFor(family) + pass,
                    96,
                    120,
                ).argb[0]
            }
            val elapsedMs = (cpuNow() - started) / 1_000_000.0
            println("texture ten-family thumbnails pass=$pass elapsedMs=$elapsedMs checksum=$checksum")
            elapsedMs
        }
        val elapsedMs = times.min()
        assertTrue("ten thumbnails took ${elapsedMs}ms", elapsedMs < 16.0)
    }

    @Test
    fun tenFamilyColdWarmIsBoundedEvenAtTheOldPreviewSize() {
        // A full ten-family warm at 180x225 is intentionally off the frame path. It
        // stays bounded and is the number recorded in the status notes.
        val started = System.nanoTime()
        TextureFamily.entries.forEach { family ->
            TexturePresets.render(family, TexturePresets.seedFor(family), 180, 225)
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        println("texture ten-family 180x225 warmMs=$elapsedMs")
        assertTrue("ten-family warm took ${elapsedMs}ms", elapsedMs < 100.0)
    }
}
