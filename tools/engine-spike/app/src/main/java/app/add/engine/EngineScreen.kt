package app.add.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EngineScreen() {
    val context = LocalContext.current
    BootTrace.mark(context, "engine screen composed")
    val restored = remember { DocumentStore.restore(context) }
    val doc = remember {
        EngineDocument(restored?.layers).also { document ->
            restored?.let {
                (it.selectedId ?: it.layers.firstOrNull()?.id)?.let { id -> document.select(id) }
                document.zoom = it.zoom
                document.status = "RESTORED · ${it.layers.size} LAYERS"
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        BootTrace.mark(context, "first frame drawn")
        BootTrace.ready(context)
    }
    LaunchedEffect(Diagnostics.recording) {
        while (Diagnostics.recording) { delay(Diagnostics.sampleMillis); Diagnostics.sample(doc) }
    }
    LaunchedEffect(Diagnostics.recording) {
        if (!Diagnostics.recording) return@LaunchedEffect
        var last = 0L
        while (Diagnostics.recording) {
            withFrameNanos { now ->
                if (last != 0L) Diagnostics.frame((now - last) / 1_000_000L)
                last = now
            }
        }
    }
    LaunchedEffect(doc.layers, doc.zoom) {
        delay(700)
        DocumentStore.save(context, doc)
        doc.markSaved(SimpleDateFormat("HH:mm", Locale.UK).format(Date()))
    }
    val sceneHandles = remember { mutableMapOf<String, SceneHandle>() }
    val fallbackHandle = remember { SceneHandle() }
    fun handleFor(layer: ModelLayer?): SceneHandle =
        layer?.let { sceneHandles.getOrPut("${it.id}:${it.asset.name}") { SceneHandle() } } ?: fallbackHandle
    val primaryHandle = doc.modelLayers.firstOrNull()?.let { handleFor(it) } ?: fallbackHandle
    val selectedHandle = handleFor(doc.selectedModel)
    val typefaces = remember(context) { Typefaces(context) }
    // Photos are copied in, then decoded once and handed to the painter as a map.
    val images = remember(doc.layers) {
        doc.layers.filterIsInstance<ImageLayer>().mapNotNull { layer ->
            ImageStore.load(context, layer.path)?.let { layer.path to it }
        }.toMap()
    }
    BootTrace.mark(context, "document, typefaces and photos ready")
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ImageStore.import(context, uri)
        if (name == null) {
            doc.status = "PHOTO IMPORT FAILED · could not copy the file"
        } else {
            doc.addImage(name)
        }
    }
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryKey by remember { mutableStateOf(0) }
    var exportScale by remember { mutableStateOf(1) }
    val basicEditor: (() -> Unit)? = remember(context) {
        runCatching {
            val cls = Class.forName("add.EditorActivity")
            val intent = android.content.Intent(context, cls)
                .putExtra("add.BASIC_EDITOR", true)
            val action: () -> Unit = {
                // A missing or refused editor must say so on the stage, not close the app.
                runCatching { context.startActivity(intent) }.onFailure { error ->
                    doc.status = "EDITOR UNAVAILABLE · " +
                        (error.message ?: error.javaClass.simpleName)
                }
            }
            action
        }.getOrNull()
    }
    val exportArtboard: (Int) -> Unit = { scale ->
        if (!doc.exporting) {
            doc.exporting = true
            doc.status = "CAPTURING ARTBOARD + 3D LAYERS…"
            scope.launch {
                try {
                    val result = Exporter.export(
                        context, doc.layers, typefaces,
                        doc.modelLayers.associate { it.id to handleFor(it).view },
                        scale,
                    )
                    doc.status = result.status
                    Diagnostics.note(
                        "export " + result.width + " × " + result.height +
                            " · " + result.bytes + " bytes" +
                            " · 3d " + (result.models - result.missing) + "/" + result.models +
                            (if (result.missing > 0) " · 3D CAPTURE MISSING" else "") +
                            (if (result.black > 0) " · ${result.black} 3D BLACK" else "") +
                            (if (result.models > 0) " · ${result.litPercent}% lit" else "")
                    )
                } catch (error: Exception) {
                    doc.status = "EXPORT FAILED · ${error.message ?: "unknown error"}"
                    Diagnostics.note("export failed · ${error.message ?: error.javaClass.simpleName}")
                } finally {
                    doc.exporting = false
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().imePadding()) {
        Stage(
            doc = doc,
            sceneHandles = sceneHandles,
            typefaces = typefaces,
            images = images,
            onCanvasTouch = { chrome = false },
            modifier = Modifier.fillMaxSize(),
        )
        if (chrome) {
            TopBar(
                doc = doc,
                onInfo = { showInfo = true },
                onExport = { showExport = true },
                onLegend = { showLegend = true },
                onLibrary = { showLibrary = true },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 8.dp, end = 8.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(9.dp)
                    .pointerInput(Unit) { detectTapGestures { chrome = true } },
            )
        }
        ToolPill(
            doc = doc,
            onIcons = { showIcons = true },
            onAdd = { showAdd = true },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
                .alpha(if (chrome) 1f else 0.45f),
        )
        StatusChip(
            doc = doc,
            handle = primaryHandle,
            visible = chrome && !sheet,
            onDiagnostics = { showDiagnostics = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 48.dp),
        )
        if (!sheet) {
            PeekStrip(
                doc = doc,
                onOpen = { sheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        } else {
            Sheet(
                doc = doc,
                handle = selectedHandle,
                onIcons = { showIcons = true },
                onHistory = { showHistory = true },
                onClose = { sheet = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    if (showIcons) IconSheet(doc = doc, onClose = { showIcons = false })
    if (showAdd) {
        AddSheet(
            doc = doc,
            onPhoto = {
                showAdd = false
                photoPicker.launch("image/*")
            },
            onIcons = {
                showAdd = false
                showIcons = true
            },
            onClose = { showAdd = false },
        )
    }
    if (showHistory) HistoryDialog(doc = doc, onClose = { showHistory = false })
    if (showLibrary) {
        // The key forces the list to be read again after a save or a delete.
        key(libraryKey) {
            LibraryDialog(
                context = context,
                doc = doc,
                onChanged = { libraryKey++ },
                onClose = { showLibrary = false },
            )
        }
    }
    if (showExport) {
        ExportDialog(
            scale = exportScale,
            onScale = { exportScale = it },
            onExport = {
                showExport = false
                exportArtboard(exportScale)
            },
            onClose = { showExport = false },
        )
    }
    if (showLegend) GestureLegend(onClose = { showLegend = false })
    if (showInfo) {
        InfoDialog(
            onClose = { showInfo = false },
            onDiagnostics = { showInfo = false; showDiagnostics = true },
            onBasicEditor = basicEditor,
        )
    }
    if (showDiagnostics) {
        DiagnosticsDialog(
            doc = doc,
            handle = primaryHandle,
            onExport = { exportArtboard(exportScale) },
            onClose = { showDiagnostics = false },
        )
    }
}

@Composable
private fun TopBar(doc: EngineDocument, onInfo: () -> Unit, onExport: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(25.dp).clip(RoundedCornerShape(4.dp)).background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(7.dp))
            Column {
                Text("ADD", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Text("ENGINE LAB", color = Dim, fontSize = 7.sp, letterSpacing = 1.4.sp)
            }
            Spacer(Modifier.weight(1f))
            Tag("SKIA")
            Spacer(Modifier.width(4.dp))
            Tag("3D")
            Spacer(Modifier.width(8.dp))
            NavButton("↶", enabled = doc.canUndo) { doc.undo() }
            NavButton("↷", enabled = doc.canRedo) { doc.redo() }
            Spacer(Modifier.width(4.dp))
            ActionChip(
                label = if (doc.exporting) "SAVING" else "EXPORT",
                active = true,
                enabled = !doc.exporting,
                onClick = onExport,
            )
            Spacer(Modifier.width(4.dp))
            ActionChip(label = "i", enabled = true, onClick = onInfo)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
    }
}

@Composable
private fun TopBar(
    doc: EngineDocument,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onLegend: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wraps its own content on purpose. A full-width row is a band however it is
    // coloured, and the artboard is supposed to own the width.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Panel.copy(alpha = 0.93f))
            .border(1.dp, Rule, RoundedCornerShape(99.dp))
            .height(40.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(Mint),
            contentAlignment = Alignment.Center,
        ) { Text("A", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(7.dp))
        ActionChip(label = "DOC", enabled = true, onClick = onLibrary)
        Spacer(Modifier.width(8.dp))
        NavButton("↶", enabled = doc.canUndo) { doc.undo() }
        NavButton("↷", enabled = doc.canRedo) { doc.redo() }
        Spacer(Modifier.width(6.dp))
        ActionChip(
            label = if (doc.exporting) "SAVING" else "EXPORT",
            active = true,
            enabled = !doc.exporting,
            onClick = onExport,
        )
        Spacer(Modifier.width(4.dp))
        ActionChip(label = "?", enabled = true, onClick = onLegend)
        Spacer(Modifier.width(3.dp))
        ActionChip(label = "i", enabled = true, onClick = onInfo)
    }
}

@Composable
private fun StatusChip(
    doc: EngineDocument,
    handle: SceneHandle,
    visible: Boolean,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = if (handle.error != null) "3D ERROR · ${handle.error}" else doc.status
    val alarm = message.contains("FAILED") || message.contains("ERROR") || message.contains("UNAVAILABLE")
    // Silent by default: it appears when there is something to say, or when the bar is
    // already up and the reader is looking for state.
    if (!(visible || alarm || doc.exporting)) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Panel.copy(alpha = 0.9f))
            .border(1.dp, if (alarm) Clay else Rule, RoundedCornerShape(99.dp))
            .clickable(onClick = onDiagnostics)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (alarm) Clay else if (doc.exporting) Lime else Mint))
        Spacer(Modifier.width(6.dp))
        Text(
            text = message,
            color = if (alarm) Clay else Cream,
            fontSize = 6.sp,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 230.dp),
        )
    }
}

@Composable
private fun ToolPill(doc: EngineDocument, onIcons: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Panel.copy(alpha = 0.74f))
            .border(1.dp, Rule.copy(alpha = 0.8f), RoundedCornerShape(99.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tool.entries.forEach { tool ->
            val active = doc.tool == tool
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (active) Mint else Color.Transparent)
                    .clickable {
                        doc.chooseTool(tool)
                        if (tool == Tool.Icon) onIcons()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tool.glyph,
                    color = if (active) Ink else Cream.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // The photograph is not a tool with a mode: it is one tap that puts a picture
        // on the page, which is why it sits under the tools rather than among them.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Lime.copy(alpha = 0.18f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Lime, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stage(
    doc: EngineDocument,
    sceneHandles: MutableMap<String, SceneHandle>,
    typefaces: Typefaces,
    images: Map<String, android.graphics.Bitmap>,
    onCanvasTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val fitWidth: Dp = minOf(maxWidth, maxHeight * 0.8f)
        val fitHeight: Dp = fitWidth / 0.8f
        val zoom = doc.zoom
        var modelDrag by remember { mutableStateOf<Pair<String, Offset>?>(null) }
        val stageWidth: Dp = fitWidth * zoom
        val stageHeight: Dp = fitHeight * zoom
        val density = LocalDensity.current
        // The stage is centred and never panned. Panning was how the artboard could be
        // pushed out of the window and left as an empty stage; a zoom that cannot be
        // panned cannot lose the drawing, and the pill still reaches the whole range.
        val setZoom: (Float) -> Unit = { raw -> doc.zoom = clampZoom(raw) }
        // The 3D viewport is sized from this, so a change here moves the surface out
        // from under a model. "The model got small" and "the frame got small" are the
        // same report until the frame's own size is on the record.
        LaunchedEffect(stageWidth, stageHeight) {
            Diagnostics.note(
                "view " + stageWidth.value.roundToInt() + " × " + stageHeight.value.roundToInt() + " dp"
            )
        }

        // The stage is sized rather than scaled: a SurfaceView ignores a graphics
        // layer, so zoom has to reach the 3D viewport through the layout to stay
        // crisp. Two fingers zoom and pan; one finger still belongs to the layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(maxWidth, maxHeight) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pinching = false
                        var firstId: PointerId? = null
                        var secondId: PointerId? = null
                        var lastSpan = 0f
                        var pendingZoom = 1f
                        var maxFingers = 0
                        var zoomed = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            maxFingers = maxOf(maxFingers, pressed.size)
                            if (pressed.size < 2) {
                                if (pinching || pressed.isEmpty()) break
                                continue
                            }
                            // Latch the two fingers the pinch began with, so a third finger
                            // or one lifting mid-gesture cannot swap the pair.
                            val first = pressed.firstOrNull { it.id == firstId } ?: pressed.first()
                            val second = pressed.firstOrNull { it.id == secondId && it.id != first.id }
                                ?: pressed.firstOrNull { it.id != first.id }
                            if (second == null) break
                            val span = (first.position - second.position).getDistance()
                            if (!pinching || first.id != firstId || second.id != secondId) {
                                pinching = true
                                firstId = first.id
                                secondId = second.id
                                lastSpan = span
                                pendingZoom = 1f
                            } else {
                                if (lastSpan > 1f) {
                                    val (apply, carry) = foldZoom(pendingZoom, span / lastSpan)
                                    pendingZoom = carry
                                    if (apply != 1f) {
                                        Diagnostics.zoomFrom("pinch")
                                        setZoom(doc.zoom * apply)
                                        zoomed = true
                                    }
                                }
                                lastSpan = span
                            }
                            pressed.forEach { it.consume() }
                        }
                        // Fingers that came and went without zooming are a tap: two undo,
                        // three redo. The gestures carry what the old bar carried.
                        if (!zoomed && maxFingers == 2) {
                            doc.undo()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (!zoomed && maxFingers >= 3) {
                            doc.redo()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
contentAlignment = Alignment.Center,
        ) {
        val scale = with(density) { stageWidth.toPx() / ArtboardPainter.W }
        val hitPx = with(density) { 22.dp.toPx() } / scale
        val hitX = hitPx / ArtboardPainter.W
        val hitY = hitPx / ArtboardPainter.H
        val inset = with(density) { 10.dp.toPx() } / scale
        val insetX = inset / ArtboardPainter.W
        val insetY = inset / ArtboardPainter.H
        val models = doc.modelLayers.filter { it.visible }
        val editing = doc.editingModel

            Box(
                modifier = Modifier
                    .size(stageWidth, stageHeight)
                    .background(Artboard)
                    .border(1.dp, Rule)
                    .clip(RoundedCornerShape(2.dp))
                    .clipToBounds(),
            ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(doc.tool, doc.selectedId, scale, hitX, hitY, insetX, insetY) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onCanvasTouch()
                            var nx = (down.position.x / scale) / ArtboardPainter.W
                            var ny = (down.position.y / scale) / ArtboardPainter.H
                            val selectedAtDown = doc.selected
                            val handle = selectedAtDown
                                ?.takeIf { it !is ModelLayer }
                                ?.let { Geometry.nearestHandle(it, nx, ny, hitX, hitY, insetX, insetY) }
                            val startX = nx
                            val startY = ny
                            var mode = "none"
                            var subject = "empty stage"
                            var startLayer: Layer? = null
                            var startAngle = 0f
                            var startDistance = 0f

                            if (handle != null && selectedAtDown != null) {
                                doc.beginGesture()
                                startLayer = selectedAtDown
                                startAngle = Geometry.angle(selectedAtDown.centerX, selectedAtDown.centerY, nx, ny)
                                startDistance = Geometry.distance(selectedAtDown.centerX, selectedAtDown.centerY, nx, ny)
                                mode = if (handle == LayerHandle.Rotate) "rotate" else "scale"
                                subject = "${selectedAtDown.name} grip ${handle.name}"
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                doc.select(selectedAtDown.id)
                            } else {
                                val hit = doc.layerAt(nx, ny)
                                if (hit != null) {
                                    doc.select(hit.id)
                                    if (hit is VectorLayer && doc.tool == Tool.Vector) {
                                        val (lx, ly) = Geometry.localPoint(hit, nx, ny)
                                        val localX = ((lx - hit.x) / hit.w).coerceIn(0f, 1f)
                                        val localY = ((ly - hit.y) / hit.h).coerceIn(0f, 1f)
                                        doc.selectedNodeId = hit.network.nearestPoint(localX, localY, 0.055f)?.id
                                    }
                                }
                                doc.beginGesture()
                                startLayer = doc.selected
                                mode = when {
                                    hit == null -> "none"
                                    doc.tool == Tool.Vector && doc.selectedNodeId != null -> "node"
                                    else -> "move"
                                }
                                subject = hit?.name ?: "empty stage"
                            }
                            // Opened after the branches have decided what this is, so the
                            // sampler stops describing the drag one sample at a time.
                            Diagnostics.beginGesture(doc)

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                nx = (change.position.x / scale) / ArtboardPainter.W
                                ny = (change.position.y / scale) / ArtboardPainter.H
                                val dx = (change.position.x - change.previousPosition.x) / scale / ArtboardPainter.W
                                val dy = (change.position.y - change.previousPosition.y) / scale / ArtboardPainter.H
                                when (mode) {
                                    "move" -> doc.moveSelectedSnapped(dx, dy)
                                    "node" -> doc.selectedNodeId?.let { doc.movePointLocal(it, dx, dy) }
                                    "rotate" -> {
                                        val start = startLayer
                                        if (start != null) {
                                            val angle = Geometry.angle(start.centerX, start.centerY, nx, ny)
                                            doc.updateLayer(start.id) {
                                                it.withRotation(Geometry.snapAngle(start.rotation + angle - startAngle))
                                            }
                                        }
                                    }
                                    "scale" -> {
                                        val start = startLayer
                                        val grip = handle
                                        if (start != null && grip != null) {
                                            val distance = Geometry.distance(start.centerX, start.centerY, nx, ny)
                                            val factor = distance / startDistance.coerceAtLeast(0.0001f)
                                            doc.updateLayer(start.id) { Geometry.scaleLayer(start, grip, factor) }
                                        }
                                    }
                                }
                                change.consume()
                            }
                            doc.endGesture()
                            // One line for the whole gesture, and a line even when nothing
                            // moved: a width that jumped to 75% is one entry whichever
                            // path did it, and a tap on a dead control is no longer silent.
                            Diagnostics.endGesture(doc, mode.uppercase(), subject, startX, startY, nx, ny)
                        }
                    },
            ) {
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    scale(scale, scale)
                    ArtboardPainter.draw(this, doc.layers, typefaces, images = images, safeArea = doc.safeArea)
                    restore()
                }
            }

            models.forEach { model ->
                key(model.id, model.asset.name) {
                    ModelLayerStage(
                        doc = doc,
                        model = model,
                        handle = sceneHandles.getOrPut("${model.id}:${model.asset.name}") { SceneHandle() },
                        stageWidth = stageWidth,
                        stageHeight = stageHeight,
                        scale = scale,
                        onDrag = { offset -> modelDrag = model.id to offset },
                        onDragEnd = { modelDrag = null },
                    )
                }
            }

            Canvas(Modifier.matchParentSize()) {
                val selected = doc.selected ?: return@Canvas
                val isModel = selected is ModelLayer
                val carried = modelDrag?.takeIf { it.first == selected.id }?.second ?: Offset.Zero
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    scale(scale, scale)
                    translate(carried.x / scale, carried.y / scale)
                    ArtboardPainter.drawSelection(
                        this,
                        selected,
                        doc.selectedNodeId,
                        scale,
                        showHandles = !isModel,
                        inset = inset,
                        showRotate = !isModel,
                    )
                    restore()
                }
            }

            if (editing != null) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(editing.id, editing.asset.name, scale, hitX, hitY, insetX, insetY) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var nx = (down.position.x / scale) / ArtboardPainter.W
                                var ny = (down.position.y / scale) / ArtboardPainter.H
                                val handle = Geometry.nearestHandle(
                                    editing,
                                    nx,
                                    ny,
                                    hitX,
                                    hitY,
                                    insetX,
                                    insetY,
                                    allowRotate = false,
                                )
                                val inside = Geometry.contains(editing, nx, ny)
                                val mode = when {
                                    handle != null -> "scale"
                                    inside -> "turn"
                                    else -> "none"
                                }
                                val startX = nx
                                val startRotation = editing.rotation
                                val startDistance = Geometry.distance(editing.centerX, editing.centerY, nx, ny)
                                val sceneHandle = sceneHandles["${editing.id}:${editing.asset.name}"]
                                var pendingRotation = startRotation
                                var directRotation = false
                                if (mode != "none") doc.beginGesture()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    nx = (change.position.x / scale) / ArtboardPainter.W
                                    ny = (change.position.y / scale) / ArtboardPainter.H
                                    when (mode) {
                                        "turn" -> {
                                            pendingRotation = startRotation + (nx - startX) * 360f
                                            val apply = sceneHandle?.applyRotation
                                            if (apply != null) {
                                                apply(pendingRotation)
                                                directRotation = true
                                            } else {
                                                doc.updateLayer(editing.id) {
                                                    it.withRotation(pendingRotation)
                                                }
                                            }
                                        }
                                        "scale" -> if (handle != null) {
                                            val distance = Geometry.distance(editing.centerX, editing.centerY, nx, ny)
                                            val factor = distance / startDistance.coerceAtLeast(0.0001f)
                                            doc.updateLayer(editing.id) { Geometry.scaleLayer(editing, handle, factor) }
                                        }
                                    }
                                    change.consume()
                                }
                                if (mode == "turn" && directRotation) {
                                    doc.updateLayer(editing.id) { it.withRotation(pendingRotation) }
                                }
                                if (mode != "none") doc.endGesture()
                            }
                        },
                ) {
                    drawContext.canvas.nativeCanvas.apply {
                        save()
                        scale(scale, scale)
                        ArtboardPainter.drawSelection(
                        this,
                        editing,
                        null,
                        scale,
                        showHandles = true,
                        inset = inset,
                        showRotate = false,
                    )
                        restore()
                    }
                }
            }

            doc.guides?.let { guides ->
                Canvas(Modifier.matchParentSize()) {
                    val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFE86A4A.toInt()
                        strokeWidth = 2f
                    }
                    guides.vertical.forEach { x ->
                        drawContext.canvas.nativeCanvas.drawLine(x * size.width, 0f, x * size.width, size.height, line)
                    }
                    guides.horizontal.forEach { y ->
                        drawContext.canvas.nativeCanvas.drawLine(0f, y * size.height, size.width, y * size.height, line)
                    }
                }
            }

            val selectedModel = doc.selectedModel
            if (selectedModel != null) {
                val editingNow = doc.editingModelId == selectedModel.id
                val maxX = maxOf(4.dp, stageWidth - 78.dp)
                val maxY = maxOf(4.dp, stageHeight - 30.dp)
                val chipX = ((selectedModel.centerX * stageWidth.value).dp - 34.dp).coerceIn(4.dp, maxX)
                val below = ((selectedModel.y + selectedModel.h) * stageHeight.value).dp + 6.dp
                val rawY = if (below > stageHeight - 30.dp) {
                    ((selectedModel.y * stageHeight.value).dp - 32.dp)
                } else {
                    below
                }
                val carried = modelDrag?.takeIf { it.first == selectedModel.id }?.second ?: Offset.Zero
                Box(
                    modifier = Modifier
                        .offset(chipX, rawY.coerceIn(4.dp, maxY))
                        .graphicsLayer {
                            translationX = carried.x
                            translationY = carried.y
                        }
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (editingNow) Lime else Mint)
                        .clickable { doc.toggleModelEdit(selectedModel.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (editingNow) "DONE" else "EDIT 3D",
                        color = Ink,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }

        ZoomPill(
            zoom = zoom,
            onZoom = { next ->
                Diagnostics.zoomFrom("pill")
                setZoom(next)
            },
            onReset = {
                Diagnostics.zoomFrom("reset")
                doc.zoom = 1f
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 42.dp),
        )
        }
    }
}

@Composable
private fun ModelLayerStage(
    doc: EngineDocument,
    model: ModelLayer,
    handle: SceneHandle,
    stageWidth: Dp,
    stageHeight: Dp,
    scale: Float,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var drag by remember(model.id) { mutableStateOf(Offset.Zero) }
    val editing = doc.editingModelId == model.id
    Box(
        modifier = Modifier
            .offset(
                x = (model.x * stageWidth.value).dp,
                y = (model.y * stageHeight.value).dp,
            )
            .size(
                width = (model.w * stageWidth.value).dp,
                height = (model.h * stageHeight.value).dp,
            ),
    ) {
        FilamentLayer(
            layer = model,
            handle = handle,
            rotation = model.rotation,
            modifier = Modifier.matchParentSize(),
        )

        // The 3D surface is a SurfaceView, so it is moved directly rather than
        // relaid out on every pointer event. The transparent frame follows on a
        // graphics layer and captures the gesture.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = drag.x
                    translationY = drag.y
                }
                .border(1.dp, Mint.copy(alpha = 0.55f))
                .then(
                    if (editing) {
                        Modifier
                    } else {
                        Modifier.pointerInput(model.id, scale) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                doc.select(model.id)
                                doc.beginGesture()
                                var last = down.position
                                var total = Offset.Zero
                                while (true) {                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } > 1) break
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val step = change.position - last
                                    total += step
                                    drag = total
                                    onDrag(total)
                                    handle.view?.let { view ->
                                        view.translationX = total.x
                                        view.translationY = total.y
                                    }
                                    last = change.position
                                    change.consume()
                                }
                                if (total != Offset.Zero) {
                                    doc.moveSelected(
                                        total.x / scale / ArtboardPainter.W,
                                        total.y / scale / ArtboardPainter.H,
                                    )
                                }
                                handle.view?.translationX = 0f
                                handle.view?.translationY = 0f
                                drag = Offset.Zero
                                onDragEnd()
                                doc.endGesture()
                            }
                        }
                    },
                ),
        )
    }
}

@Composable
private fun PeekStrip(doc: EngineDocument, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // The panel is a strip until it is wanted. Drag up to open the inspector; tap does
    // the same for anyone who has not learned the drag yet.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Panel.copy(alpha = 0.9f))
            .border(1.dp, Rule, RoundedCornerShape(99.dp))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, drag -> total += drag },
                    onDragEnd = { if (total < -24f) onOpen() },
                )
            }
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▲", color = Mint, fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = doc.selected?.name ?: "NO SELECTION",
            color = Cream,
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = doc.savedAt?.let { "SAVED $it" } ?: "SAVE…",
            color = if (doc.savedAt != null) Dim else Mint,
            fontSize = 6.sp,
            letterSpacing = 0.3.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text("LAYERS", color = Mint, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun Sheet(
    doc: EngineDocument,
    handle: SceneHandle,
    onIcons: () -> Unit,
    onHistory: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.64f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Panel)
            .border(1.dp, Rule, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, drag -> total += drag },
                        onDragEnd = { if (total > 40f) onClose() },
                    )
                }
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(34.dp).height(3.dp).clip(RoundedCornerShape(99.dp)).background(Dim.copy(alpha = 0.5f)))
            Spacer(Modifier.width(10.dp))
            Text("LAYERS + INSPECT", color = Cream, fontSize = 7.sp, letterSpacing = 1.0.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "HISTORY",
                color = Mint,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onHistory).padding(horizontal = 8.dp),
            )
            Text("▾", color = Mint, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            LayerPanel(doc, Modifier.weight(0.42f).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(Rule))
            Inspector(doc, handle, onIcons, Modifier.weight(0.58f).fillMaxHeight())
        }
    }
}

@Composable
private fun LayerPanel(doc: EngineDocument, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LAYERS", color = Cream, fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${doc.layers.size}", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            doc.layers.asReversed().forEach { layer ->
                val selected = layer.id == doc.selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) PanelRaised else Color.Transparent)
                        .clickable { doc.select(layer.id) }
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (layer.visible) "◉" else "○",
                        color = if (layer.visible) Mint else Dim,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable { doc.toggleVisibility(layer.id) }.padding(end = 4.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = layer.name,
                            color = if (selected) Cream else Dim,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = layer.kindLabel,
                            color = if (selected) Mint else Dim.copy(alpha = 0.7f),
                            fontSize = 5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Text(
                            "▲",
                            color = Mint,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clickable { doc.moveLayer(layer.id, 1) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Text(
                            "▼",
                            color = Mint,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clickable { doc.moveLayer(layer.id, -1) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Inspector(doc: EngineDocument, handle: SceneHandle, onIcons: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("INSPECT", color = Cream, fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(doc.selected?.kindLabel ?: "NONE", color = Dim, fontSize = 6.sp, maxLines = 1)
        }
        Spacer(Modifier.height(5.dp))
        if (doc.tool == Tool.Sticker) {
            StickerTray(doc)
        } else if (doc.tool == Tool.Icon) {
            IconTray(onIcons)
        } else {
            doc.selected?.let { layer -> LayerIdentityRow(doc, layer); Spacer(Modifier.height(6.dp)) }
            when (val layer = doc.selected) {
                null -> Column {
                    Text("Nothing selected.", color = Dim, fontSize = 9.sp)
                    Spacer(Modifier.height(5.dp))
                    ActionChip("ADD TEXT", active = true, onClick = { doc.addText() })
                }
                is VectorLayer -> {
                    TransformInspector(doc, layer)
                    VectorInspector(doc, layer)
                }
                is TextLayer -> {
                    TransformInspector(doc, layer)
                    TextInspector(doc, layer)
                }
                is IconLayer -> {
                    TransformInspector(doc, layer)
                    IconInspector(doc, layer, onIcons)
                }
                is ModelLayer -> {
                    TransformInspector(doc, layer)
                    ModelInspector(doc, layer, handle)
                }
                is ImageLayer -> {
                    TransformInspector(doc, layer)
                    ImageInspector(doc, layer)
                }
            }
        }
    }
}

@Composable
private fun LayerIdentityRow(doc: EngineDocument, layer: Layer) {
    var name by remember(layer.id) { mutableStateOf(layer.name) }
    LaunchedEffect(layer.name) { if (name != layer.name) name = layer.name }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionChip(
            label = if (doc.isLocked(layer.id)) "LOCKED" else "LOCK",
            active = doc.isLocked(layer.id),
            onClick = { doc.toggleLock(layer.id) },
        )
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(28) },
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 9.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(PanelRaised)
                .padding(horizontal = 5.dp, vertical = 4.dp),
        )
        if (name.trim() != layer.name && name.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            ActionChip("SET", active = true, onClick = { doc.rename(layer.id, name) })
        }
    }
}

@Composable
private fun ImageInspector(doc: EngineDocument, layer: ImageLayer) {
    InfoLine("PHOTO · ${layer.path} · ${if (layer.fill) "FILL" else "FIT"}")
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("FILL", active = layer.fill, onClick = { doc.setImageFill(true) })
        ActionChip("FIT", active = !layer.fill, onClick = { doc.setImageFill(false) })
        Spacer(Modifier.width(4.dp))
        ActionChip("DUP", onClick = { doc.duplicateSelected() })
        ActionChip("DEL", warn = true, onClick = { doc.deleteSelected() })
    }
    Spacer(Modifier.height(4.dp))
    OpacityRow(doc, layer.opacity)
    Spacer(Modifier.height(3.dp))
    InfoLine("FILL CROPS TO THE FRAME · FIT KEEPS THE WHOLE PHOTO")
}

@Composable
private fun TransformInspector(doc: EngineDocument, layer: Layer) {
    Spacer(Modifier.height(2.dp))
    PlacementRow(doc, layer)
    Spacer(Modifier.height(4.dp))
    TransformSlider(
        doc = doc,
        label = if (layer is ModelLayer) "MODEL TURN" else "ROTATION",
        value = layer.rotation,
        range = 0f..360f,
        display = { "${it.toInt()}°" },
    ) { doc.setRotation(it) }
    TransformSlider(
        doc = doc,
        label = "SCALE",
        value = layer.w,
        range = 0.06f..1.2f,
        display = { "${(it * 100).toInt()}%" },
    ) { doc.setScaleToWidth(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("RESET / CENTER", onClick = { doc.centerSelected() })
    }
    Spacer(Modifier.height(3.dp))
    InfoLine(
        if (layer is ModelLayer) {
            "DRAG TO MOVE · SCALE DIAL RESIZES THE FRAME"
        } else {
            "MOVE · SCALE DIAL OR CORNERS · STEM ROTATES"
        }
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun LibraryDialog(
    context: android.content.Context,
    doc: EngineDocument,
    onChanged: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(DocumentStore.library(context)) }
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("DOCUMENTS", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(4.dp))
            Text("A named page reopens as it was left.", color = Dim, fontSize = 8.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(28) },
                    textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 9.sp),
                    cursorBrush = SolidColor(Mint),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PanelRaised)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(5.dp))
                ActionChip(
                    label = "SAVE AS",
                    active = name.isNotBlank(),
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (DocumentStore.saveAs(context, name, doc) != null) {
                            entries = DocumentStore.library(context)
                            name = ""
                            onChanged()
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text("No saved pages yet.", color = Dim, fontSize = 9.sp)
            } else {
                entries.take(6).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name.uppercase(),
                            color = Cream,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${entry.layers} L", color = Dim, fontSize = 6.sp)
                        Spacer(Modifier.width(6.dp))
                        ActionChip("OPEN", onClick = {
                            DocumentStore.open(context, entry.key)?.let { doc.replaceAll(it) }
                            onClose()
                        })
                        Spacer(Modifier.width(3.dp))
                        ActionChip("DEL", warn = true, onClick = {
                            DocumentStore.delete(context, entry.key)
                            entries = DocumentStore.library(context)
                            onChanged()
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionChip("NEW PAGE", onClick = { doc.reset(); onClose() })
                ActionChip("CLOSE", onClick = onClose)
            }
        }
    }
}

@Composable
private fun AddSheet(
    doc: EngineDocument,
    onPhoto: () -> Unit,
    onIcons: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("ADD TO THE PAGE", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(4.dp))
            Text("The artboard stays where it is; this only puts something on it.", color = Dim, fontSize = 8.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("TYPE", active = true, onClick = { doc.addText(); onClose() })
                ActionChip("VECTOR", onClick = { doc.addVector(); onClose() })
                ActionChip("PHOTO", onClick = onPhoto)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("ICON", onClick = onIcons)
                ActionChip("STICKER", onClick = { doc.chooseTool(Tool.Sticker); onClose() })
                ActionChip("3D", onClick = { doc.addModel(ModelAsset.Gem); onClose() })
            }
            Spacer(Modifier.height(12.dp))
            ActionChip("CLOSE", onClick = onClose)
        }
    }
}

@Composable
private fun HistoryDialog(doc: EngineDocument, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("HISTORY", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(4.dp))
            Text("Tap a line to go back to just before it.", color = Dim, fontSize = 8.sp)
            Spacer(Modifier.height(8.dp))
            if (doc.recentActions.isEmpty()) {
                Text("Nothing yet.", color = Dim, fontSize = 9.sp)
            } else {
                doc.recentActions.take(10).forEachIndexed { index, label ->
                    Text(
                        text = "${index + 1} · $label",
                        color = Cream,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(PanelRaised)
                            .clickable {
                                doc.undoSteps(index + 1)
                                onClose()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            ActionChip("CLOSE", active = true, onClick = onClose)
        }
    }
}

@Composable
private fun ExportDialog(scale: Int, onScale: (Int) -> Unit, onExport: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("EXPORT", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(4.dp))
            Text("PNG · ${(ArtboardPainter.W * scale).toInt()} × ${(ArtboardPainter.H * scale).toInt()}", color = Dim, fontSize = 8.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("1×", active = scale == 1, onClick = { onScale(1) })
                ActionChip("2×", active = scale == 2, onClick = { onScale(2) })
            }
            Spacer(Modifier.height(12.dp))
            ActionChip("EXPORT PNG", active = true, onClick = onExport)
        }
    }
}

@Composable
private fun PlacementRow(doc: EngineDocument, layer: Layer) {
    var step by remember { mutableStateOf(1f) }
    val pxW = ArtboardPainter.W
    val pxH = ArtboardPainter.H
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NumberField("X", (layer.x * pxW).roundToInt(), Modifier.weight(1f)) { v ->
            doc.setBounds(v / pxW, layer.y, layer.w, layer.h)
        }
        NumberField("Y", (layer.y * pxH).roundToInt(), Modifier.weight(1f)) { v ->
            doc.setBounds(layer.x, v / pxH, layer.w, layer.h)
        }
        NumberField("W", (layer.w * pxW).roundToInt(), Modifier.weight(1f)) { v ->
            doc.setBounds(layer.x, layer.y, v / pxW, layer.h)
        }
        NumberField("H", (layer.h * pxH).roundToInt(), Modifier.weight(1f)) { v ->
            doc.setBounds(layer.x, layer.y, layer.w, v / pxH)
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        NudgeChip("←") { doc.nudge(-step, 0f) }
        NudgeChip("→") { doc.nudge(step, 0f) }
        NudgeChip("↑") { doc.nudge(0f, -step) }
        NudgeChip("↓") { doc.nudge(0f, step) }
        Spacer(Modifier.width(2.dp))
        ActionChip(
            label = if (step == 1f) "1 PX" else "10 PX",
            active = step > 1f,
            onClick = { step = if (step == 1f) 10f else 1f },
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onSet: (Float) -> Unit) {
    var text by remember(label) { mutableStateOf(value.toString()) }
    // A typed number is the one place the field, not the document, owns the string.
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(PanelRaised)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Mint, fontSize = 6.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() || it == '-' }.take(5)
                text = clean
                clean.toIntOrNull()?.let { onSet(it.toFloat()) }
            },
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 8.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NudgeChip(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Cream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GestureLegend(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("GESTURES", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(9.dp))
            LegendRow("ONE FINGER", "Move the selected layer")
            LegendRow("CORNER GRIP", "Scale from that corner")
            LegendRow("STEM GRIP", "Rotate, snapping to 15°")
            LegendRow("TWO FINGERS", "Pinch to zoom")
            LegendRow("TWO-FINGER TAP", "Undo")
            LegendRow("THREE-FINGER TAP", "Redo")
            LegendRow("TOUCH THE ARTBOARD", "Chrome gets out of the way")
            LegendRow("TAP THE TOP EDGE", "Bring the bar back")
            Spacer(Modifier.height(12.dp))
            ActionChip("GOT IT", active = true, onClick = onClose)
        }
    }
}

@Composable
private fun LegendRow(gesture: String, action: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(gesture, color = Mint, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.45f))
        Text(action, color = Dim, fontSize = 8.sp, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun TransformSlider(
    doc: EngineDocument,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var tracking by remember { mutableStateOf(false) }
    SliderRow(
        label = label,
        value = value,
        range = range,
        display = display,
        onChange = {
            if (!tracking) {
                doc.beginGesture()
                tracking = true
            }
            onChange(it)
        },
        onChangeFinished = {
            if (tracking) {
                doc.endGesture()
                tracking = false
            }
        },
    )
}

@Composable
private fun IconTray(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        InfoLine("4 BRANDS · 4,373 GLYPHS · OFFLINE PATH DATA")
        Spacer(Modifier.height(5.dp))
        ActionChip("BROWSE ICON CATALOG", active = true, onClick = onOpen)
        Spacer(Modifier.height(6.dp))
        InfoLine("LINEICONS 606 · COOLICONS 1,258")
        InfoLine("PHOSPHOR 1,512 · STREAMLINE 997")
        Spacer(Modifier.height(6.dp))
        InfoLine("TAP A GLYPH TO PLACE A SCALABLE VECTOR ICON LAYER")
        Spacer(Modifier.height(3.dp))
        InfoLine("ICONS KEEP THEIR VENDOR FILL AND STROKE RULES")
    }
}

@Composable
private fun IconInspector(doc: EngineDocument, layer: IconLayer, onIcons: () -> Unit) {
    val brand = listOf(layer.glyph.brandLabel, layer.glyph.style).filter { it.isNotBlank() }.joinToString(" ")
    InfoLine("$brand · ${layer.glyph.category.ifBlank { "UNCATEGORIZED" }}")
    InfoLine("${layer.glyph.paths.size} PATHS · VIEWBOX ${layer.glyph.viewBox[2].toInt()} × ${layer.glyph.viewBox[3].toInt()}")
    Spacer(Modifier.height(4.dp))
    Swatches(doc, layer.tint)
    OpacityRow(doc, layer.opacity)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("REPLACE", onClick = onIcons)
        ActionChip("DUP", onClick = { doc.duplicateSelected() })
        ActionChip("DEL", warn = true, onClick = { doc.deleteSelected() })
    }
    Spacer(Modifier.height(4.dp))
    InfoLine("PATH PARSER → SKIA · TINT FOLLOWS SWATCH")
}

@Composable
private fun StickerTray(doc: EngineDocument) {
    Column(Modifier.fillMaxWidth()) {
        InfoLine("${StickerLibrary.presets.size} LOCAL VECTOR SHAPES · OFFLINE")
        Spacer(Modifier.height(3.dp))
        StickerLibrary.categories.forEach { category ->
            Text(
                text = category.label,
                color = Mint,
                fontSize = 6.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(3.dp))
            category.presets.chunked(4).forEach { presets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    presets.forEach { preset ->
                        StickerCard(preset, Modifier.weight(1f)) { doc.addSticker(preset) }
                    }
                    repeat(4 - presets.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        InfoLine("TAP TO PLACE · TAP SELECT TO EDIT")
    }
}

@Composable
private fun StickerCard(preset: StickerPreset, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PanelRaised)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StickerPreview(preset, Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = preset.label,
            color = Dim,
            fontSize = 5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StickerPreview(preset: StickerPreset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = preset.network.buildPath()
        val padding = size.minDimension * 0.12f
        val side = size.minDimension - padding * 2f
        path.transform(
            android.graphics.Matrix().apply {
                setScale(side, side)
                postTranslate((size.width - side) / 2f, (size.height - side) / 2f)
            },
        )
        val native = drawContext.canvas.nativeCanvas
        if (android.graphics.Color.alpha(preset.fill) > 0) {
            native.drawPath(
                path,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = preset.fill
                },
            )
        }
        native.drawPath(
            path,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = preset.stroke
                strokeWidth = preset.strokeWidth * 0.55f
                strokeJoin = android.graphics.Paint.Join.ROUND
                strokeCap = android.graphics.Paint.Cap.ROUND
            },
        )
    }
}

@Composable
private fun VectorInspector(doc: EngineDocument, layer: VectorLayer) {
    InfoLine("${layer.network.points.size} NODES / ${layer.network.segments.size} SEGMENTS")
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("ADD NODE", onClick = { doc.addNode() })
        ActionChip(
            "DEL NODE",
            enabled = doc.selectedNodeId != null,
            onClick = { doc.deleteNode() },
        )
        ActionChip("DUP", onClick = { doc.duplicateSelected() })
        ActionChip("DEL", warn = true, onClick = { doc.deleteSelected() })
    }
    Spacer(Modifier.height(5.dp))
    Swatches(doc, layer.fill)
    SliderRow(
        label = "STROKE",
        value = layer.strokeWidth,
        range = 0f..18f,
        display = { "${it.toInt()} px" },
        onChange = { doc.setStrokeWidth(it) },
    )
    OpacityRow(doc, layer.opacity)
    Spacer(Modifier.height(4.dp))
    InfoLine("SKIA PATH · EVEN-ODD · REGIONS ${layer.network.regions.size}")
    Actions(doc)
}

@Composable
private fun TextInspector(doc: EngineDocument, layer: TextLayer) {
    InfoLine("TYPE BOX · ${layer.align.name.uppercase()} ALIGN")
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = layer.text,
        onValueChange = { doc.setText(it) },
        textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 10.sp, lineHeight = 12.sp),
        cursorBrush = SolidColor(Mint),
        maxLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(PanelRaised)
            .padding(6.dp),
    )
    Spacer(Modifier.height(4.dp))
    Swatches(doc, layer.color)
    SliderRow(
        label = "SIZE",
        value = layer.fontSize,
        range = 18f..180f,
        display = { "${it.toInt()} pt" },
        onChange = { doc.setFontSize(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextAlign.entries.forEach { align ->
            ActionChip(
                label = align.name.take(1),
                active = layer.align == align,
                onClick = { doc.setAlign(align) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    SliderRow(
        label = "LEADING",
        value = layer.lineHeight,
        range = 0.7f..1.6f,
        display = { String.format("%.2f", it) },
        onChange = { doc.setLineHeight(it) },
    )
    OpacityRow(doc, layer.opacity)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip(
            label = "FONT ${layer.weight}",
            onClick = {
                // The three bundled Poppins faces: Regular, Medium, SemiBold.
                doc.setWeight(if (layer.weight >= 600) 400 else layer.weight + 100)
            },
        )
        ActionChip("ADD TEXT", active = true, onClick = { doc.addText() })
    }
    Spacer(Modifier.height(4.dp))
    InfoLine("HARFBUZZ SHAPING VIA ANDROID MINIKIN")
    Actions(doc)
}

@Composable
private fun ModelInspector(doc: EngineDocument, layer: ModelLayer, handle: SceneHandle) {
    InfoLine(if (handle.ready) "FILAMENT 1.56 · VIEWPORT READY" else "FILAMENT 1.56 · LOADING")
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ModelAsset.entries.forEach { asset ->
            ActionChip(
                label = asset.label.uppercase(),
                active = layer.asset == asset,
                onClick = { doc.setModelAsset(asset) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    ActionChip(
        label = if (doc.editingModelId == layer.id) "DONE 3D EDIT" else "EDIT 3D",
        active = doc.editingModelId == layer.id,
        onClick = { doc.toggleModelEdit(layer.id) },
    )
    Spacer(Modifier.height(4.dp))
    SliderRow(
        label = "UNIT SCALE",
        value = layer.scaleToUnits,
        // 1.0 fills the frame; the camera is pinned so this is the only thing that
        // changes how much of the viewport the model occupies.
        range = 0.3f..1f,
        display = { String.format("%.2f", it) },
        onChange = { doc.setModelScale(it) },
    )
    InfoLine("glTF 2.0 / PBR / TURN INSIDE 3D EDIT")
    Spacer(Modifier.height(2.dp))
    InfoLine("${layer.asset.path} · ${if (handle.modelLoaded) "LOADED" else "LOADING"}")
    handle.error?.let {
        Spacer(Modifier.height(3.dp))
        Text(it, color = Clay, fontSize = 7.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(4.dp))
    InfoLine("UNIT SCALE = MODEL IN FRAME · SCALE DIAL = FRAME")
    Actions(doc)
}

@Composable
private fun Swatches(doc: EngineDocument, selected: Int) {
    var hex by remember(selected) { mutableStateOf(String.format("%06X", selected and 0xFFFFFF)) }
    LaunchedEffect(selected) { hex = String.format("%06X", selected and 0xFFFFFF) }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(
            0xFF0FBF92.toInt(),
            0xFFE86A4A.toInt(),
            0xFFD6E381.toInt(),
            0xFF11231C.toInt(),
            0xFFF2EEE3.toInt(),
        ).forEach { value ->
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(1.dp, if (value == selected) Lime else Rule, CircleShape)
                    .clickable { doc.setFill(value) },
            )
        }
        Spacer(Modifier.width(3.dp))
        BasicTextField(
            value = hex,
            onValueChange = { raw ->
                hex = raw.filter { it.isLetterOrDigit() }.take(6).uppercase()
                if (hex.length == 6) doc.setFillHex(hex)
            },
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 7.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PanelRaised)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun OpacityRow(doc: EngineDocument, opacity: Float) {
    SliderRow(
        label = "OPACITY",
        value = opacity,
        range = 0.1f..1f,
        display = { "${(it * 100).toInt()}%" },
        onChange = { doc.setOpacity(it) },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null,
) {
    // A dial's drag is one line, written when it is let go and in the dial's own units.
    // The sampler's geometry lines say a width moved; this says SCALE moved it, which
    // is what separates the dial from a stray corner grip.
    var dialFrom by remember(label) { mutableStateOf<Float?>(null) }
    var dialTo by remember(label) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dim, fontSize = 6.sp, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        Text(display(value), color = Cream, fontSize = 7.sp)
    }
    Slider(
        value = value,
        onValueChange = {
            if (dialFrom == null) dialFrom = value
            dialTo = it
            onChange(it)
        },
        valueRange = range,
        onValueChangeFinished = {
            val start = dialFrom
            if (start != null) Diagnostics.dial(label, display(start), display(dialTo))
            dialFrom = null
            onChangeFinished?.invoke()
        },
        modifier = Modifier.fillMaxWidth().height(24.dp),
        colors = SliderDefaults.colors(
            thumbColor = Mint,
            activeTrackColor = Mint,
            inactiveTrackColor = Rule,
        ),
    )
}

@Composable
private fun Actions(doc: EngineDocument) {
    Spacer(Modifier.height(3.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("BACK", onClick = { doc.reorderSelected(-1) })
        ActionChip("FRONT", onClick = { doc.reorderSelected(1) })
        ActionChip("DUP", onClick = { doc.duplicateSelected() })
        ActionChip("DEL", warn = true, onClick = { doc.deleteSelected() })
        ActionChip("SAFE", active = doc.safeArea, onClick = { doc.safeArea = !doc.safeArea })
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text, color = Dim, fontSize = 6.sp, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Mint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text, color = Mint, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PanelRaised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Cream else Dim.copy(alpha = 0.35f), fontSize = 15.sp)
    }
}

@Composable
private fun ActionChip(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> PanelRaised.copy(alpha = 0.4f)
        warn -> Clay
        active -> Mint
        else -> PanelRaised
    }
    val foreground = when {
        !enabled -> Dim.copy(alpha = 0.45f)
        warn || active -> Ink
        else -> Cream
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}

@Composable
private fun ZoomPill(
    zoom: Float,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Panel.copy(alpha = 0.94f))
            .border(1.dp, Rule, RoundedCornerShape(99.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomStep("−") { onZoom(zoom / 1.3f) }
        Text(
            text = "${(zoom * 100).toInt()}%",
            color = if (zoom > 1.001f) Lime else Cream,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        ZoomStep("+") { onZoom(zoom * 1.3f) }
    }
}

@Composable
private fun ZoomStep(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoDialog(
    onClose: () -> Unit,
    onDiagnostics: () -> Unit,
    onBasicEditor: (() -> Unit)?,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Panel)
                .border(1.dp, Rule, RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("NATIVE ENGINE / 001", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Skia-backed vector artboard with an editable network graph.\n" +
                    "4,373 offline icons from Lineicons, Coolicons, Phosphor and Streamline.\n" +
                    "Rotate, scale and place every layer with corner handles.\n" +
                    "Filament 1.56 renders the glTF 2.0 gem or ring through SceneView.\n" +
                    "EDIT 3D turns the model inside its viewport; corners resize the frame.\n" +
                    "Type is shaped by HarfBuzz through Android Minikin.\n" +
                    "PNG export captures the artboard and PixelCopies each 3D surface.\n\n" +
                    "OpenColorIO is deferred; this spike exports sRGB.\n\n" +
                    "SceneView and Filament are Apache-2.0. Poppins is OFL. " +
                    "glTF-Transform and meshoptimizer are MIT. Lineicons, Coolicons " +
                    "and Phosphor are MIT. Streamline is CC BY 4.0. " +
                    "Blender and Krita were UX references only; no GPL code is used.",
                color = Dim,
                fontSize = 9.sp,
                lineHeight = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ActionChip("DIAGNOSTICS", onClick = onDiagnostics)
                if (onBasicEditor != null) {
                    Spacer(Modifier.width(6.dp))
                    ActionChip("BASIC EDITOR", onClick = onBasicEditor)
                }
                Spacer(Modifier.width(6.dp))
                ActionChip("CLOSE", active = true, onClick = onClose)
            }
        }
    }
}
