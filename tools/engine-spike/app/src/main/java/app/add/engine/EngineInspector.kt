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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * Inspector bodies, panels and their shared property controls. This is the
 * detail-side surface; the shell owns placement and dismissal.
 */

@Composable
internal fun LayerIdentityRow(doc: EngineDocument, layer: Layer) {
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
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 12.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(5.dp))
                .background(PanelRaised)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        )
        if (name.trim() != layer.name && name.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            ActionChip("SET", active = true, onClick = { doc.rename(layer.id, name) })
        }
    }
}

@Composable
internal fun ImageInspector(doc: EngineDocument, layer: ImageLayer) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip(
            label = "RESET CROP",
            active = layer.crop != ImageCrop(),
            onClick = { doc.resetImageCrop(layer.id) },
        )
    }
    TransformSlider(
        doc = doc,
        label = "CROP X",
        value = layer.crop.x,
        range = 0f..(1f - layer.crop.w).coerceAtLeast(0.0001f),
        display = { "${(it * 100).toInt()}%" },
    ) { doc.setImageCrop(layer.id, layer.crop.copy(x = it)) }
    TransformSlider(
        doc = doc,
        label = "CROP Y",
        value = layer.crop.y,
        range = 0f..(1f - layer.crop.h).coerceAtLeast(0.0001f),
        display = { "${(it * 100).toInt()}%" },
    ) { doc.setImageCrop(layer.id, layer.crop.copy(y = it)) }
    TransformSlider(
        doc = doc,
        label = "CROP W",
        value = layer.crop.w,
        range = 0.05f..1f,
        display = { "${(it * 100).toInt()}%" },
    ) { doc.setImageCrop(layer.id, layer.crop.copy(w = it)) }
    TransformSlider(
        doc = doc,
        label = "CROP H",
        value = layer.crop.h,
        range = 0.05f..1f,
        display = { "${(it * 100).toInt()}%" },
    ) { doc.setImageCrop(layer.id, layer.crop.copy(h = it)) }
    OpacityRow(doc, layer.opacity)
    Spacer(Modifier.height(3.dp))
    InfoLine("CROP IS NON-DESTRUCTIVE · FILL AND FIT BOTH USE THE CROPPED SOURCE")
}

@Composable
internal fun TransformInspector(doc: EngineDocument, layer: Layer) {
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
            .clip(RoundedCornerShape(4.dp))
            .background(PanelRaised)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Mint, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() || it == '-' }.take(5)
                text = clean
                clean.toIntOrNull()?.let { onSet(it.toFloat()) }
            },
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 9.5.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NudgeChip(glyph: String, onClick: () -> Unit) {
    TactileBox(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        background = PanelRaised,
        pressScale = 0.9f,
        modifier = Modifier.size(32.dp),
    ) {
        Text(glyph, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun GestureLegend(onClose: () -> Unit) {
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
internal fun IconTray(onOpen: () -> Unit) {
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
internal fun IconInspector(doc: EngineDocument, layer: IconLayer, onIcons: () -> Unit) {
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
internal fun StickerTray(doc: EngineDocument) {
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
            fontSize = 7.sp,
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
internal fun VectorInspector(doc: EngineDocument, layer: VectorLayer) {
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
internal fun TextInspector(doc: EngineDocument, layer: TextLayer) {
    val context = LocalContext.current
    val registry = remember(context) { TypefaceRegistry(context) }
    val families = remember(registry) { registry.families() }
    val resolution = remember(layer.fontFamily, layer.weight) {
        registry.resolve(layer.fontFamily, layer.weight)
    }
    var familyQuery by remember { mutableStateOf("") }
    var weightMenu by remember { mutableStateOf(false) }
    val visibleFamilies = families.filter { it.contains(familyQuery.trim(), ignoreCase = true) }
    InfoLine("TYPE BOX · ${layer.align.name.uppercase()} ALIGN")
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = familyQuery,
        onValueChange = { familyQuery = it },
        singleLine = true,
        textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 10.sp),
        cursorBrush = SolidColor(Mint),
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(PanelRaised)
            .padding(horizontal = 7.dp, vertical = 7.dp)
            .testTag("font-search"),
        decorationBox = { inner ->
            if (familyQuery.isEmpty()) Text("Search families", color = Dim, fontSize = 10.sp)
            inner()
        },
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        visibleFamilies.forEach { family ->
            ActionChip(
                label = family.uppercase(),
                active = layer.fontFamily.equals(family, ignoreCase = true),
                onClick = { doc.setFontFamily(family) },
            )
        }
    }
    if (resolution.substituted) {
        Spacer(Modifier.height(3.dp))
        InfoLine("SUBSTITUTED · ${layer.fontFamily.uppercase()} → POPPINS")
    }
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = layer.text,
        onValueChange = { doc.setText(it) },
        textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 12.5.sp, lineHeight = 15.sp),
        cursorBrush = SolidColor(Mint),
        maxLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(PanelRaised)
            .padding(7.dp),
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
    EngineSegmentedControl(
        options = TextAlign.entries.map { align ->
            EngineSegment(
                label = align.name.take(1),
                accessibilityLabel = align.name.lowercase().replaceFirstChar { it.uppercase() } + " alignment",
                tag = "align-${align.name.lowercase()}",
            )
        },
        selectedIndex = TextAlign.entries.indexOf(layer.align),
        onSelect = { index -> doc.setAlign(TextAlign.entries[index]) },
        description = "Text alignment",
    )
    Spacer(Modifier.height(4.dp))
    SliderRow(
        label = "TRACKING",
        value = layer.tracking,
        range = -0.04f..0.3f,
        display = { String.format("%+.2f", it) },
        onChange = { doc.setTracking(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextCase.entries.forEach { case ->
            ActionChip(
                label = case.name.uppercase(),
                active = layer.textCase == case,
                onClick = { doc.setTextCase(case) },
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
        Box {
            ActionChip(
                label = "FONT ${layer.weight}",
                onClick = { weightMenu = true },
            )
            DropdownMenu(expanded = weightMenu, onDismissRequest = { weightMenu = false }) {
                listOf(100, 200, 300, 400, 500, 600, 700, 800, 900).forEach { weight ->
                    DropdownMenuItem(onClick = {
                        doc.setWeight(weight)
                        weightMenu = false
                    }) {
                        Text("$weight", color = if (layer.weight == weight) Mint else Cream, fontSize = 11.sp)
                    }
                }
            }
        }
        ActionChip("ADD TEXT", active = true, onClick = { doc.addText() })
    }
    Spacer(Modifier.height(4.dp))
    InfoLine("HARFBUZZ SHAPING VIA ANDROID MINIKIN")
    Actions(doc)
}

@Composable
internal fun ModelInspector(
    doc: EngineDocument,
    layer: ModelLayer,
    handle: SceneHandle,
    onEdit3D: (() -> Unit)? = null,
) {
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
        label = if (doc.editingModelId == layer.id) "DONE 3D EDIT" else "TURN IN 3D",
        active = doc.editingModelId == layer.id,
        onClick = {
            doc.toggleModelEdit(layer.id)
            onEdit3D?.invoke()
        },
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionChip("RESET FRAME", onClick = { doc.resetModelFrame() })
        ActionChip("RESET TURN", onClick = { doc.resetModelTurn() })
    }
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
    Spacer(Modifier.height(3.dp))
    InfoLine("3D PREVIEW PAUSES WHILE THIS PANEL IS OPEN · CLOSE IT TO SEE THE MODEL")
    Actions(doc)
}

@Composable
internal fun GradientStops(doc: EngineDocument, layerId: String, gradient: PaintSpec.Gradient) {
    var chosen by remember(layerId) { mutableStateOf(0) }
    val index = chosen.coerceIn(0, gradient.stops.lastIndex)
    val stop = gradient.stops.getOrNull(index) ?: return
    var hex by remember(index, stop.color) {
        mutableStateOf(String.format("%06X", stop.color and 0xFFFFFF))
    }
    Spacer(Modifier.height(3.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gradient.stops.forEachIndexed { i, _ ->
            ActionChip("${i + 1}", active = i == index, onClick = { chosen = i })
        }
        ActionChip(
            label = "◀",
            enabled = index > 0,
            onClick = {
                doc.movePaintStop(index, -1)
                chosen = index - 1
            },
        )
        ActionChip(
            label = "▶",
            enabled = index < gradient.stops.lastIndex,
            onClick = {
                doc.movePaintStop(index, 1)
                chosen = index + 1
            },
        )
        ActionChip("+ STOP", onClick = { doc.addPaintStop() })
        ActionChip(
            label = "− STOP",
            warn = gradient.stops.size > 2,
            onClick = {
                doc.removePaintStop(index)
                chosen = 0
            },
        )
    }
    SliderRow(
        label = "STOP POSITION",
        value = stop.offset,
        range = 0f..1f,
        display = { "${(it * 100).toInt()}%" },
        onChange = { doc.setPaintStopOffset(index, it) },
    )
    SliderRow(
        label = "STOP ALPHA",
        value = colorAlpha(stop.color),
        range = 0f..1f,
        display = { "${(it * 100).toInt()}%" },
        onChange = { doc.setPaintStopAlpha(index, it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(
            EngineEdition.Plum.toArgb(),
            EngineEdition.PlumHover.toArgb(),
            EngineEdition.Warn.toArgb(),
            EngineEdition.Ink.toArgb(),
            EngineEdition.BandInk.toArgb(),
        ).forEach { value ->
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(
                        1.dp,
                        if ((value and 0xFFFFFF) == (stop.color and 0xFFFFFF)) StudioSelection else StudioHairline,
                        CircleShape,
                    )
                    .clickable { doc.setPaintStopColor(index, value) },
            )
        }
    }
    Spacer(Modifier.height(3.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("STOP HEX  #", color = Dim, fontSize = 8.sp, letterSpacing = 0.3.sp)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = hex,
            onValueChange = { raw ->
                hex = raw.filter { it.isLetterOrDigit() }.take(6).uppercase()
                if (hex.length == 6) {
                    hex.toIntOrNull(16)?.let { value ->
                        doc.setPaintStopColor(index, 0xFF000000.toInt() or value)
                    }
                }
            },
            textStyle = TextStyle(color = Cream, fontFamily = Poppins, fontSize = 7.sp),
            cursorBrush = SolidColor(Mint),
            singleLine = true,
            modifier = Modifier
                .width(58.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PanelRaised)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Swatches(doc: EngineDocument, selected: Int) {
    var hex by remember(selected) { mutableStateOf(String.format("%06X", selected and 0xFFFFFF)) }
    LaunchedEffect(selected) { hex = String.format("%06X", selected and 0xFFFFFF) }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(
            EngineEdition.Plum.toArgb(),
            EngineEdition.PlumHover.toArgb(),
            EngineEdition.Warn.toArgb(),
            EngineEdition.Ink.toArgb(),
            EngineEdition.BandInk.toArgb(),
        ).forEach { value ->
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(1.dp, if (value == selected) StudioSelection else StudioHairline, CircleShape)
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
internal fun SliderRow(
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
        Text(label, color = Dim, fontSize = 9.5.sp, letterSpacing = 0.45.sp, modifier = Modifier.weight(1f))
        Text(display(value), color = Cream, fontSize = 10.5.sp)
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
        modifier = Modifier.fillMaxWidth().height(30.dp),
        colors = SliderDefaults.colors(
            thumbColor = StudioAccent,
            activeTrackColor = StudioAccent,
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
    }
}

@Composable
internal fun InfoLine(text: String) {
    Text(text, color = Dim, fontSize = 9.5.sp, lineHeight = 13.sp, letterSpacing = 0.2.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun ActionChip(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> PanelRaised.copy(alpha = 0.4f)
        warn -> Clay
        active -> StudioAccent
        else -> PanelRaised
    }
    val foreground = when {
        !enabled -> Dim.copy(alpha = 0.45f)
        warn -> Ink
        active -> StudioAccentInk
        else -> Cream
    }
    TactileBox(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        background = background,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
        pressScale = 0.94f,
    ) {
        Text(label, color = foreground, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}

@Composable
internal fun ZoomPill(
    zoom: Float,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag("stage-zoom"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(StudioSurface.copy(alpha = 0.94f))
                .border(1.dp, StudioHairline, RoundedCornerShape(99.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZoomStep("−", "zoom-out") { onZoom(zoom / 1.3f) }
            TactileBox(
                onClick = onReset,
                shape = RoundedCornerShape(99.dp),
                background = Color.Transparent,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("zoom-reset")
                    .semantics {
                        contentDescription = "Reset zoom"
                        role = Role.Button
                    },
            ) {
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    color = if (zoom > 1.001f) StudioAccent else StudioText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ZoomStep("+", "zoom-in") { onZoom(zoom * 1.3f) }
        }
    }
}

@Composable
private fun ZoomStep(label: String, tag: String, onClick: () -> Unit) {
    TactileBox(
        onClick = onClick,
        shape = CircleShape,
        background = Color.Transparent,
        pressScale = 0.88f,
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(StudioRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun InfoDialog(
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
