package app.add.engine

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * the log without a hook per call site. The trade is that a gesture which ends
 * where it started leaves no line, because nothing changed.
 *
 * Nothing leaves the device unless COPY or SHARE is tapped.
 */
object Diagnostics {
    private const val LIMIT = 400
    private const val SAMPLE_MS = 250L
    private const val COALESCE_MS = 700L
    private const val NANOS = 1_000_000_000.0

    val sampleMillis: Long get() = SAMPLE_MS

    private val lines = ArrayDeque<String>()
    private val keys = ArrayDeque<String>()
    private val stamps = ArrayDeque<Long>()
    private var startedAt = 0L
    private var previous: Snapshot? = null
    private var dropped = 0

    var recording by mutableStateOf(false)
        private set
    var count by mutableStateOf(0)
        private set

    /** Bumped on every write, including one that replaces a coalesced line. */
    var revision by mutableStateOf(0)
        private set
    var status by mutableStateOf("RECORDING IS OFF")
        private set

    fun start(doc: EngineDocument) {
        lines.clear()
        keys.clear()
        stamps.clear()
        dropped = 0
        count = 0
        startedAt = System.nanoTime()
        previous = snapshot(doc)
        recording = true
        status = "RECORDING · CLOSE THIS SCREEN AND DO THE THING"
        append("rec", "REC started · ${doc.layers.size} layers · zoom ${percent(doc.zoom)}")
    }

    fun stop(doc: EngineDocument) {
        sample(doc)
        recording = false
        previous = null
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
        status = "CLEARED"
        revision++
    }

    fun lines(): List<String> = lines.toList()

    fun sample(doc: EngineDocument) {
        if (!recording) return
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
        if (before.zoom != next.zoom) {
            append("zoom", "zoom ${percent(before.zoom)} → ${percent(next.zoom)}")
        }

        next.order.filter { it !in before.layers }.forEach { id ->
            append("add:$id", "+ ${next.layers[id]?.name ?: id}")
        }
        before.order.filter { it !in next.layers }.forEach { id ->
            append("del:$id", "− ${before.layers[id]?.name ?: id}")
        }

        next.order.forEach { id ->
            val a = before.layers[id] ?: return@forEach
            val b = next.layers[id] ?: return@forEach
            val moved = mutableListOf<String>()
            if (a.x != b.x) moved += "x ${f(a.x)}→${f(b.x)}"
            if (a.y != b.y) moved += "y ${f(a.y)}→${f(b.y)}"
            if (a.w != b.w) moved += "w ${f(a.w)}→${f(b.w)}"
            if (a.h != b.h) moved += "h ${f(a.h)}→${f(b.h)}"
            if (a.rotation != b.rotation) moved += "rot ${one(a.rotation)}°→${one(b.rotation)}°"
            if (moved.isNotEmpty()) append("geo:$id", "${b.name}  ${moved.joinToString("  ")}")
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

    private fun f(value: Float): String = String.format(Locale.US, "%.3f", value)

    private fun one(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun percent(value: Float): String = "${(value * 100f).roundToInt()}%"
}
