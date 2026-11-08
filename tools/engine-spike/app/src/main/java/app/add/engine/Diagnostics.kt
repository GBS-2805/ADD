package app.add.engine

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A recording of what the reader actually did, in a form that can be pasted into a
 * message.
 *
 * It polls the document instead of instrumenting every control: a sampler
 * fingerprints the layers and writes a line only when a value really moved, so
 * drags, corner scales, every slider, duplicates, deletions and zoom all land in
 * the log without a hook per call site.
 *
 * A shape gesture is the exception, because four samples a second turn one drag into
 * half a dozen partial lines and none of them names the gesture. A gesture is opened
 * and closed instead, and writes one line with its mode, its target, its travel, its
 * duration and the geometry it changed. A tap that moves nothing also writes a line
 * now, naming what it hit: that is the case the sampler can never see, and it is the
 * case behind every "I tapped it and nothing happened" report.
 *
 * Nothing leaves the device unless COPY or SHARE is tapped. A crash while recording
 * is the one thing that does reach disk, so the session is not lost with the process.
 */
object Diagnostics {
    private const val LIMIT = 400
    private const val SAMPLE_MS = 250L
    private const val COALESCE_MS = 700L
    private const val FRAME_WINDOW_MS = 2000L
    private const val CRASH_FILE = "diagnostics-crash.txt"
    private const val NANOS = 1_000_000_000.0

    val sampleMillis: Long get() = SAMPLE_MS

    private val lines = ArrayDeque<String>()
    private val keys = ArrayDeque<String>()
    private val stamps = ArrayDeque<Long>()
    private var startedAt = 0L
    private var previous: Snapshot? = null
    private var dropped = 0

    private var gestureFrom: Snapshot? = null
    private var gestureLayer: String? = null
    private var gestureAt = 0L
    private var zoomSource: String? = null
    private var frameStart = 0L
    private var frameCount = 0
    private var frameWorst = 0L
    private var crashHandler: Thread.UncaughtExceptionHandler? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    var recording by mutableStateOf(false)
        private set
    var count by mutableStateOf(0)
        private set

    /** Bumped on every write, including one that replaces a coalesced line. */
    var revision by mutableStateOf(0)
        private set
    var status by mutableStateOf("RECORDING IS OFF")
        private set

    fun start(doc: EngineDocument, context: Context) {
        lines.clear()
        keys.clear()
        stamps.clear()
        dropped = 0
        count = 0
        startedAt = System.nanoTime()
        previous = snapshot(doc)
        gestureFrom = null
        zoomSource = null
        frameStart = 0L
        val earlier = lastCrash(context)
        recording = true
        installCrashHandler(context)
        status = "RECORDING · CLOSE THIS SCREEN AND DO THE THING"
        append("rec", "REC started · ${doc.layers.size} layers · zoom ${percent(doc.zoom)}")
        if (earlier != null) {
            append("crashPrevious:${System.nanoTime()}", "previous session crashed · ${earlier.removePrefix("CRASH ")}")
        }
    }

    fun stop(doc: EngineDocument) {
        sample(doc)
        gestureFrom = null
        recording = false
        previous = null
        zoomSource = null
        removeCrashHandler()
        status = if (count == 0) "STOPPED · NOTHING MOVED" else "STOPPED · $count LINES · COPY OR SHARE"
    }

    fun clear() {
        lines.clear()
        keys.clear()
        stamps.clear()
        dropped = 0
        count = 0
        recording = false
        previous = null
        gestureFrom = null
        zoomSource = null
        removeCrashHandler()
        status = "CLEARED"
        revision++
    }

    fun lines(): List<String> = lines.toList()

    /**
     * A line for something the sampler cannot see, such as which gesture ran and which
     * handle it took. The key is unique per call, so two gestures in a row are never
     * coalesced into one the way a slider's own changes are.
     */
    fun note(body: String) {
        if (!recording) return
        append("note:${System.nanoTime()}", body)
    }

    /** Three decimals, the precision the log prints, so a caller never rounds its own way. */
    fun three(value: Float): String = f(value)

    /**
     * Opens a shape gesture. Sampling stops until [endGesture], so the drag is one line
     * rather than one per sample.
     */
    fun beginGesture(doc: EngineDocument) {
        if (!recording) return
        gestureFrom = snapshot(doc)
        gestureLayer = doc.selectedId
        gestureAt = System.nanoTime()
    }

    /**
     * Closes a shape gesture with one line: its mode, what it took hold of, where the
     * finger went, how long it took and the geometry that changed. A gesture that
     * changed nothing prints as a tap, which is how a dead control shows up.
     */
    fun endGesture(
        doc: EngineDocument,
        mode: String,
        subject: String,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ) {
        val from = gestureFrom ?: return
        gestureFrom = null
        if (!recording) return
        val after = snapshot(doc)
        val ms = (System.nanoTime() - gestureAt) / 1_000_000L
        val moved = gestureLayer
            ?.let { id -> from.layers[id]?.let { a -> after.layers[id]?.let { b -> movedText(a, b) } } }
            .orEmpty()
        val picked = if (from.selected != after.selected) {
            after.selected?.let { id -> after.layers[id]?.name ?: id } ?: "nothing"
        } else {
            ""
        }
        val parts = listOfNotNull(
            moved.ifBlank { null },
            picked.ifBlank { null }?.let { "select $it" },
        )
        val place = "${f(x0)},${f(y0)} → ${f(x1)},${f(y1)}"
        if (parts.isEmpty()) {
            append("tap:${System.nanoTime()}", "tap   $mode · $subject @ $place · nothing changed")
        } else {
            append(
                "gesture:${System.nanoTime()}",
                "gesture $mode · $subject · $place · ${ms} ms · ${parts.joinToString(" · ")}",
            )
        }
        // The line above already carries the change; the next sample must not repeat it.
        previous = after
    }

    /** Names the control that moved the zoom, for the next sampled line. */
    fun zoomFrom(source: String) {
        zoomSource = source
    }

    /**
     * A dial's own drag, in its own units, written once when it is let go. The sampler
     * says a width moved; this says the SCALE dial moved it, which is what separates a
     * slider from a stray corner grip.
     */
    fun dial(name: String, from: String, to: String) {
        if (!recording) return
        append("dial:$name", "dial  $name $from → $to")
    }

    /**
     * One frame, in milliseconds since the previous one. The window is long enough to
     * survive the coalescing rule, so a steady 60 reads as one line every two seconds
     * and a stutter shows as the worst frame in that window.
     */
    fun frame(deltaMillis: Long) {
        if (!recording) return
        val now = System.nanoTime()
        if (frameStart == 0L) {
            frameStart = now
            frameCount = 0
            frameWorst = 0
            return
        }
        frameCount++
        if (deltaMillis > frameWorst) frameWorst = deltaMillis
        val elapsed = (now - frameStart) / 1_000_000L
        if (elapsed >= FRAME_WINDOW_MS) {
            append("frames", "frames ${frameCount * 1000 / elapsed}/s · worst ${frameWorst} ms")
            frameStart = now
            frameCount = 0
            frameWorst = 0
        }
    }

    /** The crash line an earlier session left on disk, or null. */
    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null
        return runCatching {
            file.useLines { lines -> lines.firstOrNull { it.contains("CRASH") } }?.substringAfter("  ")?.trim()
        }.getOrNull()
    }

    fun sample(doc: EngineDocument) {
        if (!recording) return
        // A gesture in flight is described by its own closing line. A cancelled one
        // never closes, so it expires rather than freezing the sampler for the session.
        if (gestureFrom != null) {
            if (System.nanoTime() - gestureAt > 8_000_000_000L) {
                gestureFrom = null
            } else {
                return
            }
        }
        val next = snapshot(doc)
        val before = previous
        previous = next
        if (before == null) return

        if (before.tool != next.tool) {
            append("tool", "tool ${before.tool.name.uppercase()} → ${next.tool.name.uppercase()}")
        }
        if (before.editing != next.editing) {
            val name = next.editing?.let { next.layers[it]?.name ?: it }
            append("edit", if (name == null) "3d edit off" else "3d edit on · $name")
        }
        if (before.selected != next.selected) {
            append("select", "select ${next.selected?.let { next.layers[it]?.name ?: it } ?: "nothing"}")
        }
        if (percent(before.zoom) != percent(next.zoom)) {
            val by = zoomSource?.let { " · $it" }.orEmpty()
            append("zoom", "zoom ${percent(before.zoom)} → ${percent(next.zoom)}$by")
        }
        // A tag that did not reach a real change would mislabel the next one.
        zoomSource = null

        next.order.filter { it !in before.layers }.forEach { id ->
            append("add:$id", "+ ${next.layers[id]?.name ?: id}")
        }
        before.order.filter { it !in next.layers }.forEach { id ->
            append("del:$id", "− ${before.layers[id]?.name ?: id}")
        }

        next.order.forEach { id ->
            val a = before.layers[id] ?: return@forEach
            val b = next.layers[id] ?: return@forEach
            val moved = movedText(a, b)
            if (moved.isNotEmpty()) append("geo:$id", "${b.name}  $moved")
            if (a.units != b.units) append("unit:$id", "${b.name}  unit ${f(a.units)}→${f(b.units)}")
            if (a.visible != b.visible) {
                append("vis:$id", "${b.name}  ${if (b.visible) "shown" else "hidden"}")
            }
        }
    }

    fun report(
        context: Context,
        doc: EngineDocument,
        handle: SceneHandle,
        notes: List<String>,
    ): String {
        val metrics = context.resources.displayMetrics
        val device = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val surface = if (handle.view?.holder?.surface?.isValid == true) "valid" else "not valid"
        val three = when {
            handle.error != null -> "ERROR · ${handle.error}"
            handle.ready -> "READY"
            else -> "LOADING"
        }
        val text = StringBuilder()
        text.appendLine("ADD ENGINE DIAGNOSTICS")
        text.appendLine(
            SimpleDateFormat("d MMM yyyy HH:mm", Locale.US).format(Date()) +
                " · $count lines" + (if (dropped > 0) " · $dropped dropped" else "")
        )
        text.appendLine(
            "device   ${device.ifBlank { "unknown" }} · API ${Build.VERSION.SDK_INT} · " +
                (Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
        )
        text.appendLine("screen   ${metrics.widthPixels} × ${metrics.heightPixels} @ ${metrics.densityDpi} dpi")
        text.appendLine(
            "3d       $three · model ${if (handle.modelLoaded) "loaded" else "not loaded"} · " +
                "view ${handle.width} × ${handle.height} · surface $surface"
        )
        text.appendLine(
            "state    zoom ${percent(doc.zoom)} · ${doc.layers.size} layers · " +
                "selected ${doc.selected?.name ?: "nothing"}"
        )
        text.appendLine("status   ${doc.status}")
        lastCrash(context)?.let { text.appendLine("crash    $it") }
        text.appendLine()
        text.appendLine("EVENTS")
        if (lines.isEmpty()) {
            text.appendLine("  (nothing recorded)")
        } else {
            lines.forEach { text.appendLine("  $it") }
        }
        text.appendLine()
        text.appendLine("LAYERS")
        doc.layers.forEach { layer ->
            val units = (layer as? ModelLayer)?.let { " unit ${f(it.scaleToUnits)}" } ?: ""
            text.appendLine(
                "  ${layer.name}  x ${f(layer.x)} y ${f(layer.y)} " +
                    "w ${f(layer.w)} h ${f(layer.h)} rot ${one(layer.rotation)}°$units" +
                    (if (layer.visible) "" else " hidden")
            )
        }
        if (notes.isNotEmpty()) {
            text.appendLine()
            text.appendLine("TICKED ON DEVICE")
            notes.forEach { text.appendLine("  ✓ $it") }
        }
        text.appendLine()
        text.appendLine("END")
        return text.toString()
    }

    /**
     * A crash while recording is written next to the app's files before the process
     * dies. The next session shows it in the header, so the session is not lost.
     */
    private fun installCrashHandler(context: Context) {
        if (crashHandler != null) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTrace.take(4).joinToString(" ← ") {
                    "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                }
                append("crash:${System.nanoTime()}", "CRASH ${error.javaClass.simpleName}: ${error.message ?: "no message"}")
                append("crashTrace:${System.nanoTime()}", "at $trace")
                File(context.filesDir, CRASH_FILE).writeText(lines.joinToString("\n"))
            }
            previousHandler?.uncaughtException(thread, error)
        }
        crashHandler = handler
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    private fun removeCrashHandler() {
        val handler = crashHandler ?: return
        if (Thread.getDefaultUncaughtExceptionHandler() === handler) {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
        crashHandler = null
        previousHandler = null
    }

    private fun movedText(a: LayerState, b: LayerState): String {
        val moved = mutableListOf<String>()
        // Compared at the precision that is printed: a change too small to alter a
        // digit would otherwise be logged as "y 0.245→0.245".
        if (f(a.x) != f(b.x)) moved += "x ${f(a.x)}→${f(b.x)}"
        if (f(a.y) != f(b.y)) moved += "y ${f(a.y)}→${f(b.y)}"
        if (f(a.w) != f(b.w)) moved += "w ${f(a.w)}→${f(b.w)}"
        if (f(a.h) != f(b.h)) moved += "h ${f(a.h)}→${f(b.h)}"
        if (one(a.rotation) != one(b.rotation)) {
            // A turn that crosses 360 is one continuous gesture. Printing the stored
            // value made 186°→0° read as a jump backwards when it was a +174° turn.
            val turned = a.rotation + shortestTurn(a.rotation, b.rotation)
            moved += "rot ${one(a.rotation)}°→${one(turned)}°"
        }
        return moved.joinToString("  ")
    }

    private fun append(key: String, body: String) {
        val now = System.nanoTime()
        val stamp = String.format(Locale.US, "%6.2fs", (now - startedAt) / NANOS)
        val line = "$stamp  $body"
        val last = lines.lastIndex
        if (last >= 0 && keys[last] == key && (now - stamps[last]) < COALESCE_MS * 1_000_000L) {
            // A drag or a slider is one entry that keeps its latest value.
            lines[last] = line
            stamps[last] = now
        } else {
            lines.addLast(line)
            keys.addLast(key)
            stamps.addLast(now)
            if (lines.size > LIMIT) {
                lines.removeFirst()
                keys.removeFirst()
                stamps.removeFirst()
                dropped++
            }
            count = lines.size
        }
        revision++
    }

    private fun snapshot(doc: EngineDocument): Snapshot = Snapshot(
        tool = doc.tool,
        selected = doc.selectedId,
        editing = doc.editingModelId,
        zoom = doc.zoom,
        order = doc.layers.map { it.id },
        layers = doc.layers.associate { layer ->
            layer.id to LayerState(
                name = layer.name,
                x = layer.x,
                y = layer.y,
                w = layer.w,
                h = layer.h,
                rotation = layer.rotation,
                units = (layer as? ModelLayer)?.scaleToUnits ?: 0f,
                visible = layer.visible,
            )
        },
    )

    private data class LayerState(
        val name: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val rotation: Float,
        val units: Float,
        val visible: Boolean,
    )

    private data class Snapshot(
        val tool: Tool,
        val selected: String?,
        val editing: String?,
        val zoom: Float,
        val order: List<String>,
        val layers: Map<String, LayerState>,
    )

    /** Signed turn from one angle to the other on the short arc. */
    private fun shortestTurn(from: Float, to: Float): Float {
        var turn = (to - from) % 360f
        if (turn > 180f) turn -= 360f
        if (turn < -180f) turn += 360f
        return turn
    }

    private fun f(value: Float): String = String.format(Locale.US, "%.3f", value)

    private fun one(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun percent(value: Float): String = "${(value * 100f).roundToInt()}%"
}
