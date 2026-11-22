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
     * Best of nine CPU-time samples at the measured size, after [WARMUP_PASSES] untimed
     * passes. A shorter warmup was not enough: the loop is compiled lazily, and the probe
     * needed 25 passes before it stopped sampling C1 code (the 22 November visual runs
     * failed a family that a twin run measured inside the budget). An adaptive settle
     * can stop on cold code because it cannot see a tier it has not reached yet, so
     * every family and the probe use the same fixed warmup.
     *
     * A retry window was tried on 22 November and did not earn its place: a normal host
     * passed the first window, and a host that was about 1.3x slower failed all three,
     * because the difference was the CPU, not a transient stall. CPU time already
     * ignores descheduling, and [measureHost] handles the clock difference, so one
     * settled window is enough.
     */
    private fun measureFamily(family: TextureFamily, width: Int, height: Int): Timing {
        val seed = TexturePresets.seedFor(family)
        var warmupPasses = 0
        var bestWarmMs = Double.MAX_VALUE
        repeat(WARMUP_PASSES) {
            bestWarmMs = minOf(bestWarmMs, timeRender(family, seed, width, height))
            warmupPasses += 1
        }
        val timing = Timing(
            warmupPasses,
            bestWarmMs,
            (1..SAMPLES).map { timeRender(family, seed, width, height) },
        )
        println(
            ("texture family=$family ${width}x$height warmupPasses=$warmupPasses " +
                "warmBestMs=%.3f bestMs=%.3f samplesMs=%s").format(
                    bestWarmMs,
                    timing.bestMs,
                    timing.samplesText(),
                ),
        )
        return timing
    }

    /**
     * The 16ms budget is a claim about work on the host it was measured on, not about
     * whichever CPU a CI runner happens to hand us. On 22 November the same commit
     * measured WoodGrain at 15.0ms on one runner and 19.8ms on another, and all three
     * retry windows moved together, so an absolute threshold was reading clock speed.
     *
     * This fixed integer-and-double probe gives the host's scale: the family budget is
     * 16ms times the probe's ratio to [CALIBRATION_REFERENCE_MS], measured on the host
     * where the 16ms budget was established. A family must still do the same amount of
     * work to pass; the ratio is floored at 1 so a faster host cannot tighten the
     * budget, and capped at [MAX_HOST_FACTOR] because beyond that the probe, not the
     * host, is the suspect.
     *
     * The probe runs after the ten families, when the compiler queue has drained, and
     * uses the same [WARMUP_PASSES]-pass warmup as they do: a short warmup let tiered
     * compilation masquerade as a slow host, because the probe sampled C1 code while
     * the renderer ran at C2 speed (one suite run's budget jumped to 28ms on this host).
     */
    private fun measureHost(): HostSpeed {
        var checksum = 0
        fun pass(): Double {
            val started = cpuNow()
            checksum += calibrationPass()
            return (cpuNow() - started) / 1_000_000.0
        }
        repeat(WARMUP_PASSES) { pass() }
        val samples = (1..SAMPLES).map { pass() }
        val calibrationMs = samples.min()
        val host = hostBudget(calibrationMs)
        println(
            ("texture host warmupPasses=$WARMUP_PASSES calibrationMs=%.3f " +
                "referenceMs=%.3f factor=%.3f budgetMs=%.3f checksum=%d samplesMs=%s").format(
                    calibrationMs,
                    CALIBRATION_REFERENCE_MS,
                    host.factor,
                    host.budgetMs,
                    checksum,
                    samples.joinToString(", ") { "%.3f".format(it) },
                ),
        )
        return host
    }

    /** Pure so the floor, the slow-host scale and the cap are testable without timing. */
    private fun hostBudget(calibrationMs: Double): HostSpeed {
        val factor = (calibrationMs / CALIBRATION_REFERENCE_MS)
            .coerceIn(1.0, MAX_HOST_FACTOR)
        return HostSpeed(factor, FRAME_BUDGET_MS * factor)
    }

    /** Fixed probe data, so the host measurement is cheap, deterministic and JIT-stable. */
    private val calibrationData = IntArray(CALIBRATION_MASK + 1) { index ->
        ((index * 2_654_435_761L) xor (index.toLong() shl 13)).toInt()
    }

    private fun calibrationPass(): Int {
        var hash = 0x811C9DC5.toInt()
        var scalar = 0.25
        for (index in 0 until CALIBRATION_STEPS) {
            val value = calibrationData[index and CALIBRATION_MASK]
            hash = (hash xor value) * 16_777_619
            scalar += (value and 0xFF) * 0.000_1
        }
        return hash xor scalar.toInt()
    }

    private fun Timing.samplesText(): String =
        samples.joinToString(", ") { "%.3f".format(it) }

    private fun timeRender(family: TextureFamily, seed: Int, width: Int, height: Int): Double {
        val started = cpuNow()
        TexturePresets.render(family, seed, width, height)
        return (cpuNow() - started) / 1_000_000.0
    }

    private data class Timing(
        val warmupPasses: Int,
        val warmBestMs: Double,
        val samples: List<Double>,
    ) {
        val bestMs: Double = samples.min()
    }

    private data class HostSpeed(val factor: Double, val budgetMs: Double)

    private companion object {
        const val FRAME_BUDGET_MS = 16.0
        // Best of nine probe samples on the host where the 16ms budget was measured.
        const val CALIBRATION_REFERENCE_MS = 2.65
        const val MAX_HOST_FACTOR = 4.0
        const val WARMUP_PASSES = 25
        const val SAMPLES = 9
        const val CALIBRATION_STEPS = 1 shl 21
        const val CALIBRATION_MASK = 4_095
    }

    @Test
    fun hostBudgetCoversFloorSlowHostAndCap() {
        val faster = hostBudget(CALIBRATION_REFERENCE_MS / 2)
        assertEquals(1.0, faster.factor, 0.0)
        assertEquals(FRAME_BUDGET_MS, faster.budgetMs, 0.0)

        val slow = hostBudget(CALIBRATION_REFERENCE_MS * 1.75)
        assertEquals(1.75, slow.factor, 1e-9)
        assertEquals(FRAME_BUDGET_MS * 1.75, slow.budgetMs, 1e-9)

        val capped = hostBudget(CALIBRATION_REFERENCE_MS * 6.0)
        assertEquals(MAX_HOST_FACTOR, capped.factor, 0.0)
        assertEquals(FRAME_BUDGET_MS * MAX_HOST_FACTOR, capped.budgetMs, 0.0)
    }

    @Test
    fun oneFamilyAtThePreviewFieldFitsTheFrameBudget() {
        // The renderer caps a field at 240k pixels (about 300x400). The inspector
        // asks for one family when the reader picks it, then the cache serves frames.
        // Every family is measured before the verdict, so a failing family still logs
        // its samples next to the nine that passed; asserting inside this loop used to
        // stop the run at the first failure and hide the rest of the measurements.
        val timings = TextureFamily.entries.map { family ->
            family to measureFamily(family, 300, 375)
        }
        // After the families, not before: tiered compilation has drained by then, so a
        // C1-compiled probe cannot pass itself off as a slow host.
        val host = measureHost()
        val overBudget = timings.filter { (_, timing) -> timing.bestMs >= host.budgetMs }
        // The samples travel with the failure: a CI log otherwise says only "took Nms",
        // which cannot tell a cold compiler from a slow family or from a slow host.
        val detail = overBudget.joinToString("\n") { (family, timing) ->
            ("%s took %.2fms at best over %d samples (warmup %d passes, warm best %.2fms) " +
                "against %.2fms (%.2fms x %.2f host); samples: %s").format(
                    family, timing.bestMs, timing.samples.size, timing.warmupPasses,
                    timing.warmBestMs, host.budgetMs, FRAME_BUDGET_MS, host.factor,
                    timing.samplesText(),
                )
        }
        assertTrue(detail, overBudget.isEmpty())
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
