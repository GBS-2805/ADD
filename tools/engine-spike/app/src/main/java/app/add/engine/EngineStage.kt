package app.add.engine

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The artboard stage and its model surface. Kept separate from the shell so the
 * real activity composition stays a shell, not a renderer.
 */

private fun List<Layer>.rendersSomething(): Boolean = any { layer ->
    layer.visible && layer.opacity > 0f &&
        (layer !is GroupLayer || layer.children.rendersSomething())
}

@Composable
internal fun Stage(
    doc: EngineDocument,
    sceneHandles: MutableMap<String, SceneHandle>,
    typefaces: Typefaces,
    images: Map<String, android.graphics.Bitmap>,
    fieldPhase: State<Float>,
    onCanvasTouch: () -> Unit,
    sheetOpen: Boolean = false,
    showZoom: Boolean = true,
    focusRequester: FocusRequester? = null,
    nextFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .focusProperties { if (nextFocus != null) next = nextFocus }
            .testTag("engine-stage"),
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
        // The 3D view is a SurfaceView: it does not clip to the artboard and can sit
        // above Compose on some builds. While the panel is open it stands down, so the
        // inspector can never be eaten by the model it is inspecting.
        val models = doc.modelLayers.filter { it.visible && !sheetOpen }
        val editing = doc.editingModel
        val playingId = doc.playingLayerId
        val motionOffsets = playingId
            ?.let { id -> doc.motionOffset(id)?.let { mapOf(id to it) } }
            ?: emptyMap()
        val stageContext = LocalContext.current
        LaunchedEffect(playingId) {
            val id = playingId ?: return@LaunchedEffect
            val path = doc.motionPathInWorld(id) ?: return@LaunchedEffect
            val durationScale = EngineMotion.durationScale(stageContext)
            if (durationScale <= 0f) {
                doc.seekMotion(1f)
                return@LaunchedEffect
            }
            val started = EngineClock.nowMillis()
            while (doc.playingLayerId == id) {
                val progress = EngineMotion.progress(
                    EngineClock.nowMillis() - started,
                    path.durationMs,
                    durationScale,
                )
                doc.seekMotion(progress)
                if (progress >= 1f) break
                withFrameNanos { }
            }
        }

            Box(
                modifier = Modifier
                    .size(stageWidth, stageHeight)
                    .background(Artboard)
                    .border(1.dp, Rule)
                    .clip(RoundedCornerShape(2.dp))
                    .clipToBounds()
                    .testTag("engine-artboard"),
            ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(doc.tool, doc.selectedId, doc.maskMode, doc.inkMode, scale, hitX, hitY, insetX, insetY) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onCanvasTouch()
                            var nx = (down.position.x / scale) / ArtboardPainter.W
                            var ny = (down.position.y / scale) / ArtboardPainter.H
                            val selectedAtDown = doc.selected
                            val handle = selectedAtDown
                                ?.takeIf { it !is ModelLayer }
                                ?.let { Geometry.nearestHandle(it, nx, ny, hitX, hitY, insetX, insetY) }
                            val motionPath = selectedAtDown?.let { doc.motionPathInWorld(it.id) }
                            val motionHandle = motionPath?.let {
                                EngineMotion.nearestHandle(it, nx, ny, hitX, hitY)
                            }
                            val startX = nx
                            val startY = ny
                            var mode = "none"
                            var subject = "empty stage"
                            var startLayer: Layer? = null
                            var startAngle = 0f
                            var startDistance = 0f
                            var hitLayer: Layer? = null
                            var moved = false
                            var lastMotionX = nx
                            var lastMotionY = ny

                            // Material ink owns the drag while the seeded brush is armed.
                            // It writes a new document stroke; it never moves a layer.
                            if (doc.inkMode) {
                                doc.beginInkStroke(nx, ny)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    nx = (change.position.x / scale) / ArtboardPainter.W
                                    ny = (change.position.y / scale) / ArtboardPainter.H
                                    doc.extendInkStroke(nx, ny)
                                    change.consume()
                                }
                                doc.endInkStroke()
                                Diagnostics.endGesture(doc, "INK", doc.inkFamily.label, startX, startY, nx, ny)
                                return@awaitEachGesture
                            }

                            // Mask painting owns the drag before any transform handle does:
                            // while the brush is armed the stage writes mask strokes, not moves.
                            if (doc.maskMode && selectedAtDown != null) {
                                val maskLayer = selectedAtDown
                                fun localX(x: Float) = ((x - maskLayer.x) / maskLayer.w).coerceIn(-0.2f, 1.2f)
                                fun localY(y: Float) = ((y - maskLayer.y) / maskLayer.h).coerceIn(-0.2f, 1.2f)
                                doc.beginGesture()
                                doc.beginMaskStroke(localX(nx), localY(ny))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    nx = (change.position.x / scale) / ArtboardPainter.W
                                    ny = (change.position.y / scale) / ArtboardPainter.H
                                    doc.extendMaskStroke(localX(nx), localY(ny))
                                    change.consume()
                                }
                                doc.endMaskStroke()
                                doc.endGesture()
                                Diagnostics.endGesture(doc, "MASK", maskLayer.name, startX, startY, nx, ny)
                                return@awaitEachGesture
                            }

                            if (motionHandle != null && selectedAtDown != null) {
                                doc.beginGesture()
                                startLayer = selectedAtDown
                                mode = "motion"
                                subject = "${selectedAtDown.name} path dot $motionHandle"
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                doc.select(selectedAtDown.id)
                            } else if (handle != null && selectedAtDown != null) {
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
                                hitLayer = hit
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
                                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                }
                                nx = (change.position.x / scale) / ArtboardPainter.W
                                ny = (change.position.y / scale) / ArtboardPainter.H
                                val dx = (change.position.x - change.previousPosition.x) / scale / ArtboardPainter.W
                                val dy = (change.position.y - change.previousPosition.y) / scale / ArtboardPainter.H
                                when (mode) {
                                    "motion" -> {
                                        val subjectLayer = startLayer
                                        if (subjectLayer != null && motionHandle != null) {
                                            if (motionHandle == 0) {
                                                doc.moveSelected(nx - lastMotionX, ny - lastMotionY)
                                            } else {
                                                doc.setMotionPoint(subjectLayer.id, motionHandle, nx, ny)
                                            }
                                        }
                                    }
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
                                lastMotionX = nx
                                lastMotionY = ny
                                change.consume()
                            }
                            doc.endGesture()
                            // Tap arbitration on the tree: a second tap on the selected
                            // group enters it; bare space inside a group leaves one level;
                            // bare space at the top still returns to the Create deck.
                            if (!moved && handle == null && motionHandle == null && hitLayer is GroupLayer &&
                                hitLayer.id == selectedAtDown?.id
                            ) {
                                doc.enterGroup()
                            } else if (!moved && handle == null && motionHandle == null && hitLayer == null &&
                                doc.editingGroupId != null && doc.tool == Tool.Select && !doc.maskMode
                            ) {
                                doc.exitGroup()
                            } else if (handle == null && motionHandle == null && hitLayer == null && !moved &&
                                doc.tool == Tool.Select && !doc.maskMode
                            ) {
                                doc.clearSelection()
                            }
                            // One line for the whole gesture, and a line even when nothing
                            // moved: a width that jumped to 75% is one entry whichever
                            // path did it, and a tap on a dead control is no longer silent.
                            Diagnostics.endGesture(doc, mode.uppercase(), subject, startX, startY, nx, ny)
                        }
                    },
            ) {
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    ArtboardPainter.draw(
                        this,
                        doc.layers,
                        typefaces,
                        images = images,
                        motionOffsets = motionOffsets,
                        safeArea = doc.safeArea,
                        scale = scale,
                        preview = true,
                        texture = doc.texture,
                        ink = doc.ink,
                        backgroundField = doc.backgroundField,
                        fieldPhase = fieldPhase.value,
                    )
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
                    doc.motionPathInWorld(selected.id)?.let { path ->
                        ArtboardPainter.drawMotionPath(this, path, scale)
                    }
                    restore()
                }
            }

            if (!doc.layers.rendersSomething()) {
                // Never leave a cream rectangle with no explanation. This is the
                // explicit state for an empty document or a stack with every eye off.
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Forest.copy(alpha = 0.94f))
                        .border(1.dp, Mint.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .testTag("stage-empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (doc.layers.isEmpty()) "EMPTY PAGE" else "NOTHING VISIBLE",
                        color = Cream,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (doc.layers.isEmpty()) {
                            "Tap + to add a layer."
                        } else {
                            "Layers are on the page. Open LAYERS and enable one."
                        },
                        color = Dim,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
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
                        color = Mint.toArgb()
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
                TactileBox(
                    onClick = { doc.toggleModelEdit(selectedModel.id) },
                    shape = RoundedCornerShape(99.dp),
                    background = if (editingNow) Lime else Forest.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, if (editingNow) Lime else Mint.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                    haptic = true,
                    modifier = Modifier
                        .offset(chipX, rawY.coerceIn(4.dp, maxY))
                        .testTag("stage-turn-3d")
                        .graphicsLayer {
                            translationX = carried.x
                            translationY = carried.y
                        },
                ) {
                    Text(
                        text = if (editingNow) "DONE 3D" else "TURN 3D",
                        color = if (editingNow) Ink else Mint,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }

        if (showZoom && !sheetOpen && !doc.maskMode && !doc.inkMode && doc.editingModelId == null) {
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 128.dp),
            )
        }
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
            )
            .shadow(if (editing) 18.dp else 5.dp, RoundedCornerShape(15.dp), clip = false)
            .clip(RoundedCornerShape(15.dp))
            .background(Forest)
            .border(
                if (editing) 2.dp else 1.dp,
                if (editing) Lime else Mint.copy(alpha = 0.5f),
                RoundedCornerShape(15.dp),
            ),
    ) {
        FilamentLayer(
            layer = model,
            handle = handle,
            rotation = model.rotation,
            modifier = Modifier.matchParentSize(),
        )

        // The border follows the finger on a graphics layer, but the pointer input
        // stays on a stationary node. Moving the pointer node with the same
        // translation makes change.position relative to the moving frame, so the
        // reported delta collapses after the first few pixels and the layer lags.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = drag.x
                    translationY = drag.y
                }
                .border(
                    if (editing) 2.dp else 1.dp,
                    if (editing) Lime else Mint.copy(alpha = 0.55f),
                    RoundedCornerShape(1.dp),
                ),
        ) { }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (editing) Lime else Forest.copy(alpha = 0.8f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                if (editing) "3D · TURNING" else "3D STUDIO",
                color = if (editing) Ink else Mint,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
        }
        if (!editing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(model.id, scale) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            doc.select(model.id)
                            doc.beginGesture()
                            var last = down.position
                            var total = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
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
                    },
            ) { }
        }
    }
}


@Composable
internal fun StatusChip(
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
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (alarm) Clay else if (doc.exporting) Lime else Mint))
        Spacer(Modifier.width(7.dp))
        Text(
            text = message,
            color = if (alarm) Clay else Cream,
            fontSize = 8.sp,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}
