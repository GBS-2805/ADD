package app.add.engine

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

internal enum class StudioTab(val label: String) {
    Style("STYLE"),
    Effects("FX"),
    Texture("INK"),
    Layers("LAYERS"),
}

/**
 * The accessibility setting that means "no unnecessary motion". The chrome still
 * changes state, but it does so without overshoot or stagger.
 */
@Composable
internal fun prefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val resolver = context.contentResolver
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
                Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * One tactile surface for the new chrome. It compresses on touch and springs back
 * through its resting size; the document and the artboard are never part of this
 * animation.
 */
@Composable
internal fun TactileBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = PebbleShape,
    background: Color = PanelRaised,
    border: BorderStroke? = null,
    shadow: androidx.compose.ui.unit.Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    haptic: Boolean = false,
    pressScale: Float = StudioMotion.PressScale,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = prefersReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) pressScale else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.48f, stiffness = 760f),
        label = "tactile-press",
    )
    Box(
        modifier = Modifier
            .sizeIn(minWidth = EngineTokens.MinTarget, minHeight = EngineTokens.MinTarget)
            .then(modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
            .clip(shape)
            .background(if (enabled) background else background.copy(alpha = 0.52f))
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (haptic) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * A 48dp click/semantics bound around a compact drawn surface. The outer box owns
 * the touch target and tag; the inner box is visual only, so density does not
 * shrink the area a finger can actually hit.
 */
@Composable
internal fun CompactHitTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(99.dp),
    background: Color = PanelRaised,
    border: BorderStroke? = null,
    shadow: androidx.compose.ui.unit.Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    visualHeight: androidx.compose.ui.unit.Dp,
    visualWidth: androidx.compose.ui.unit.Dp? = null,
    haptic: Boolean = false,
    pressScale: Float = StudioMotion.PressScale,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    TactileBox(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        background = Color.Transparent,
        contentPadding = PaddingValues(0.dp),
        haptic = haptic,
        pressScale = pressScale,
    ) {
        Box(
            modifier = Modifier
                .then(if (visualWidth != null) Modifier.width(visualWidth) else Modifier)
                .height(visualHeight)
                .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
                .clip(shape)
                .background(background)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

@Composable
internal fun StudioTopBar(
    doc: EngineDocument,
    onBack: () -> Unit,
    onDocs: () -> Unit,
    onExport: () -> Unit,
    onExportScale: (Int) -> Unit,
    onInfo: () -> Unit,
    onLegend: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    nextFocus: FocusRequester? = null,
) {
    var menu by remember { mutableStateOf(false) }
    EngineFloatingToolbar(
        modifier = modifier.height(54.dp).testTag("engine-toolbar"),
        focusRequester = focusRequester,
        nextFocus = nextFocus,
    ) {
        BarIcon("←", "Back", onClick = onBack)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "PAGE 01",
                color = Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
            )
            Text(
                "${doc.layers.size} · ${doc.saveState.replace("…", "").ifBlank { doc.savedAt ?: "draft" }}",
                color = Dim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BarIcon("↶", "Undo", enabled = doc.canUndo, onClick = { doc.undo() })
        BarIcon("↷", "Redo", enabled = doc.canRedo, onClick = { doc.redo() })
        Spacer(Modifier.width(4.dp))
        EngineSplitButton(
            label = if (doc.exporting) "SAVING" else "EXPORT",
            onPrimary = onExport,
            options = listOf(
                "1× PNG" to { onExportScale(1) },
                "2× PNG" to { onExportScale(2) },
                "3× PNG" to { onExportScale(3) },
                "4× PNG" to { onExportScale(4) },
            ),
            enabled = !doc.exporting,
        )
        Spacer(Modifier.width(3.dp))
        Box {
            BarIcon("•••", "More", onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(onClick = { menu = false; onDocs() }) {
                    Text("Documents", color = Cream, fontSize = 12.sp)
                }
                DropdownMenuItem(onClick = { menu = false; onLegend() }) {
                    Text("Gestures", color = Cream, fontSize = 12.sp)
                }
                DropdownMenuItem(onClick = { menu = false; onInfo() }) {
                    Text("About the engine", color = Cream, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BarIcon(
    glyph: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TactileBox(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        background = PanelRaised.copy(alpha = 0.72f),
        pressScale = 0.9f,
        modifier = Modifier
            .testTag("bar-${label.lowercase()}")
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Text(
            glyph,
            color = if (enabled) Cream else Dim.copy(alpha = 0.35f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun layerGlyph(layer: Layer): String = when (layer) {
    is TextLayer -> "T"
    is VectorLayer -> "◇"
    is IconLayer -> "✦"
    is ImageLayer -> "▧"
    is ModelLayer -> "3"
    is GroupLayer -> "▣"
}

internal fun layerStickerInk(layer: Layer): Color = when (layer) {
    is TextLayer -> Color(layer.color)
    is VectorLayer -> if ((layer.fill ushr 24) == 0) Mint else Color(layer.fill)
    is IconLayer -> Color(layer.tint)
    is ImageLayer -> Mint
    is ModelLayer -> Mint
    is GroupLayer -> Cream
}

internal fun layerStickerWash(layer: Layer): Color = when (layer) {
    is TextLayer -> MintWash
    is VectorLayer -> MintWash
    is IconLayer -> MintWash
    is ImageLayer -> MintWash
    is ModelLayer -> MintWash
    is GroupLayer -> MintWash
}

@Composable
internal fun CreateDeck(
    onClose: () -> Unit,
    onType: () -> Unit,
    onShape: () -> Unit,
    onSticker: () -> Unit,
    onIcon: () -> Unit,
    onModel: () -> Unit,
    onPhoto: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        val reduceMotion = prefersReducedMotion()
        LaunchedEffect(Unit) { shown = true }
        val slide by animateDpAsState(
            targetValue = if (shown || reduceMotion) 0.dp else 48.dp,
            animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "create-deck-slide",
        )
        val fade by animateFloatAsState(
            if (shown || reduceMotion) 1f else 0f,
            if (reduceMotion) snap() else tween(160),
            label = "create-deck-fade",
        )
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioInk.copy(alpha = 0.72f))
                    .clickable(onClick = onClose),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(y = slide)
                    .alpha(fade)
                    .safeDrawingPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(StudioSurface)
                    .border(1.dp, StudioHairline, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .padding(12.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Make something", color = StudioText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Six ways in. Everything lands selected.", color = StudioMuted, fontSize = 10.sp)
                }
                CompactHitTarget(
                    onClick = onClose,
                    shape = RoundedCornerShape(99.dp),
                    background = StudioRaised,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    visualHeight = 36.dp,
                    modifier = Modifier.testTag("create-dismiss"),
                ) {
                    Text("NOT NOW", color = StudioText, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CreateCard("¶", "TYPE", "Make a statement", "Type anything", 0, onType, Modifier.weight(1f))
                CreateCard("◇", "SHAPE", "Draw a line or curve", "Start with a shape", 1, onShape, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CreateCard("✿", "STICKER", "Pick a ready-made shape", "Local vector shapes", 2, onSticker, Modifier.weight(1f))
                CreateCard("✦", "ICON", "Search 4,373 icons", "Four offline sets", 3, onIcon, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CreateCard("◈", "3D", "Place the gem or ring", "A lit 3D object", 4, onModel, Modifier.weight(1f))
                CreateCard("▧", "PHOTO", "Use a photo", "From your gallery", 5, onPhoto, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing is permanent. Undo is always one tap away.",
                color = StudioMuted,
                fontSize = 8.5.sp,
            )
            }
        }
    }
}

@Composable
private fun CreateCard(
    glyph: String,
    title: String,
    note: String,
    hint: String,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    val reduceMotion = prefersReducedMotion()
    LaunchedEffect(Unit) {
        delay(if (reduceMotion) 0L else index * StudioMotion.StaggerMillis)
        shown = true
    }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 480f),
        label = "create-card-$index",
    )
    TactileBox(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        background = StudioRaised,
        border = BorderStroke(1.dp, StudioHairline),
        shadow = 0.dp,
        contentPadding = PaddingValues(9.dp),
        haptic = true,
        modifier = modifier
            .height(96.dp)
            .graphicsLayer {
                alpha = entrance
                scaleX = 0.93f + entrance * 0.07f
                scaleY = 0.93f + entrance * 0.07f
                translationY = (1f - entrance) * 14f
            }
            .testTag("create-${title.lowercase()}"),
        contentAlignment = Alignment.TopStart,
    ) {
        Column {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(StudioInk),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, color = StudioAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = StudioText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(1.dp))
            Text(note, color = StudioText, fontSize = 9.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
            Text(hint, color = StudioMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun InspectorSheet(
    doc: EngineDocument,
    handle: SceneHandle,
    tab: StudioTab,
    onTab: (StudioTab) -> Unit,
    onIcons: () -> Unit,
    onHistory: () -> Unit,
    onClose: () -> Unit,
    onMask: () -> Unit,
    onTurn3D: () -> Unit,
    layout: EngineLayout = EngineLayout.Compact,
    focusRequester: FocusRequester? = null,
) {
    val density = LocalDensity.current
    val reduceMotion = prefersReducedMotion()
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragPx by animateFloatAsState(
        targetValue = if (dragging) dragOffset else 0f,
        animationSpec = if (dragging || reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "sheet-drag",
    )
    val layer = doc.selected
    // Keep the type prefix in the sheet header: "NEW" alone loses the fact that
    // this is a text layer. The object bar shows the shorter leaf name.
    val displayName = layer?.name ?: "The page"
    val content: @Composable ColumnScope.() -> Unit = {
        if (layout == EngineLayout.Compact) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dy ->
                                dragOffset = (dragOffset + dy / density.density * 0.42f).coerceIn(0f, 88f)
                            },
                            onDragEnd = {
                                dragging = false
                                if (dragOffset > 58f) onClose() else dragOffset = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = 0f
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(99.dp)).background(Cream.copy(alpha = 0.3f)))
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("EDITING", color = StudioAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(
                    displayName,
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactHitTarget(
                onClick = onHistory,
                shape = RoundedCornerShape(99.dp),
                background = PanelRaised,
                contentPadding = PaddingValues(horizontal = 9.dp),
                visualHeight = 36.dp,
                modifier = Modifier.testTag("sheet-history"),
            ) {
                Text("HISTORY", color = Cream, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
            }
            Spacer(Modifier.width(5.dp))
            CompactHitTarget(
                onClick = {
                    doc.settle()
                    onClose()
                },
                shape = RoundedCornerShape(99.dp),
                background = StudioAccent,
                contentPadding = PaddingValues(horizontal = 11.dp),
                visualHeight = 38.dp,
                haptic = true,
                modifier = Modifier.testTag("sheet-done"),
            ) {
                Text("DONE", color = StudioAccentInk, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        EngineSegmentedControl(
            options = StudioTab.entries.map { entry ->
                EngineSegment(
                    label = entry.label,
                    accessibilityLabel = entry.label.lowercase().replaceFirstChar { it.uppercase() },
                    tag = "tab-${entry.name.lowercase()}",
                )
            },
            selectedIndex = StudioTab.entries.indexOf(tab),
            onSelect = { index -> onTab(StudioTab.entries[index]) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            description = "Inspector sections",
            compact = true,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                StudioTab.Style -> StyleTab(
                    doc = doc,
                    handle = handle,
                    onIcons = onIcons,
                    onMask = onMask,
                    onTurn3D = onTurn3D,
                    modifier = Modifier.fillMaxSize(),
                )
                StudioTab.Effects -> FxTab(doc = doc, modifier = Modifier.fillMaxSize())
                StudioTab.Texture -> TextureTab(
                    doc = doc,
                    modifier = Modifier.fillMaxSize(),
                    onPaintInk = onClose,
                )
                StudioTab.Layers -> LayersTab(doc = doc, modifier = Modifier.fillMaxSize())
            }
        }
    }
    if (layout == EngineLayout.Expanded) {
        // The content's own EDITING header carries the title and the close action,
        // so the panel host must not repeat them above it.
        EnginePanelHost(
            layout = layout,
            title = displayName,
            onDismiss = onClose,
            showHeader = false,
        ) { content() }
    } else {
        EngineSheet(
            title = displayName,
            accessibilityTitle = layer?.name ?: "The page",
            onDismiss = onClose,
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(StudioMotion.SheetFraction)
                .graphicsLayer { translationY = with(density) { dragPx.dp.toPx() } },
        ) { content() }
    }
}

@Composable
internal fun InkHud(doc: EngineDocument, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, PebbleShape, clip = false)
            .clip(PebbleShape)
            .background(Forest)
            .border(1.5.dp, Lime.copy(alpha = 0.55f), PebbleShape)
            .padding(12.dp)
            .testTag("ink-hud"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Mint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", color = Mint, fontSize = 18.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("MATERIAL INK", color = Lime, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(
                    "${doc.inkFamily.label} · ${doc.ink.size} stroke${if (doc.ink.size == 1) "" else "s"}",
                    color = Cream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Draw directly on the page. No bitmap is stored.", color = Dim, fontSize = 9.sp)
            }
            TactileBox(
                onClick = onDone,
                shape = RoundedCornerShape(99.dp),
                background = Lime,
                contentPadding = PaddingValues(horizontal = 12.dp),
                haptic = true,
                modifier = Modifier.height(42.dp).testTag("ink-done"),
            ) {
                Text("DONE", color = Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            HudChip("UNDO", enabled = doc.canUndo) { doc.undo() }
            HudChip("REMOVE LAST", enabled = doc.ink.isNotEmpty()) { doc.removeLastInk() }
            HudChip("CLEAR", warn = true, enabled = doc.ink.isNotEmpty()) { doc.clearInk() }
        }
        SliderRow(
            label = "BRUSH WIDTH",
            value = doc.inkWidth,
            range = 0.012f..0.2f,
            display = { "${(it * 100).toInt()}%" },
            onChange = { doc.setInkBrushWidth(it) },
        )
    }
}

@Composable
internal fun MaskHud(doc: EngineDocument, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, PebbleShape, clip = false)
            .clip(PebbleShape)
            .background(Forest)
            .border(1.5.dp, Mint.copy(alpha = 0.55f), PebbleShape)
            .padding(12.dp)
            .testTag("mask-hud"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Mint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("◐", color = Mint, fontSize = 19.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("MASK BRUSH", color = Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Brush away parts of this layer.", color = Dim, fontSize = 9.5.sp, lineHeight = 12.sp)
            }
            TactileBox(
                onClick = onDone,
                shape = RoundedCornerShape(99.dp),
                background = Lime,
                contentPadding = PaddingValues(horizontal = 12.dp),
                haptic = true,
                modifier = Modifier.height(40.dp).testTag("mask-done"),
            ) {
                Text("DONE", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            HudChip(if (doc.maskErase) "ERASE" else "RESTORE", active = true) { doc.toggleMaskErase() }
            HudChip(
                when {
                    doc.selected?.style?.mask == null -> "ADD MASK"
                    doc.selected?.style?.mask?.enabled == true -> "MASK ON"
                    else -> "MASK OFF"
                },
            ) { doc.toggleMask() }
            HudChip("CLEAR", enabled = doc.selected?.style?.mask?.strokes?.isNotEmpty() == true) { doc.clearMask() }
            HudChip("REMOVE", warn = true, enabled = doc.selected?.style?.mask != null) { doc.removeMask() }
        }
        SliderRow(
            label = "BRUSH SIZE",
            value = doc.maskBrushSize,
            range = 0.02f..0.35f,
            display = { "${(it * 100).toInt()}%" },
            onChange = { doc.setMaskRadius(it) },
        )
    }
}

@Composable
internal fun ModelTurnHud(doc: EngineDocument, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, PebbleShape, clip = false)
            .clip(PebbleShape)
            .background(Forest)
            .border(1.5.dp, Lime.copy(alpha = 0.55f), PebbleShape)
            .padding(12.dp)
            .testTag("model-hud"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(color = Mint.copy(alpha = 0.28f), radius = size.minDimension * 0.42f, style = Stroke(2.dp.toPx()))
                drawCircle(color = Mint, radius = size.minDimension * 0.19f, style = Stroke(1.5.dp.toPx()))
                drawLine(
                    color = Lime,
                    start = Offset(size.width * 0.5f, size.height * 0.08f),
                    end = Offset(size.width * 0.5f, size.height * 0.2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Lime,
                    start = Offset(size.width * 0.5f, size.height * 0.92f),
                    end = Offset(size.width * 0.5f, size.height * 0.8f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("3D TURNTABLE", color = Lime, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("Drag across the page to turn it.", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("Corners resize the frame.", color = Dim, fontSize = 9.5.sp)
        }
        TactileBox(
            onClick = onDone,
            shape = RoundedCornerShape(99.dp),
            background = Lime,
            contentPadding = PaddingValues(horizontal = 12.dp),
            haptic = true,
            modifier = Modifier.height(42.dp).testTag("model-done"),
        ) {
            Text("DONE", color = Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun LayerLanded(
    name: String,
    onInspect: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entered by remember(name) { mutableStateOf(false) }
    val reduceMotion = prefersReducedMotion()
    LaunchedEffect(name) { entered = true }
    val ring by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(520, easing = FastOutSlowInEasing),
        label = "landed-ring",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("engine-notice")
            .shadow(12.dp, PebbleShape, clip = false)
            .clip(PebbleShape)
            .background(StudioSurface.copy(alpha = 0.98f))
            .border(1.dp, StudioHairline, PebbleShape)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = StudioAccent.copy(alpha = (1f - ring) * 0.55f),
                    radius = size.minDimension * (0.34f + ring * 0.5f),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            Box(Modifier.size(24.dp).clip(CircleShape).background(StudioAccent), contentAlignment = Alignment.Center) {
                Text("✓", color = StudioAccentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text("LAYER LANDED", color = StudioAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(name, color = StudioText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CompactHitTarget(
            onClick = onInspect,
            shape = RoundedCornerShape(99.dp),
            background = StudioRaised,
            contentPadding = PaddingValues(horizontal = 8.dp),
            visualHeight = 34.dp,
            modifier = Modifier.testTag("notice-inspect"),
        ) {
            Text("STYLE", color = StudioText, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(4.dp))
        CompactHitTarget(
            onClick = onDone,
            shape = RoundedCornerShape(99.dp),
            background = StudioAccent,
            contentPadding = PaddingValues(horizontal = 9.dp),
            visualHeight = 36.dp,
            haptic = true,
            modifier = Modifier.testTag("notice-done"),
        ) {
            Text("DONE", color = StudioAccentInk, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun HudChip(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    TactileBox(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        background = when {
            !enabled -> PanelRaised.copy(alpha = 0.45f)
            warn -> Clay
            active -> StudioAccent
            else -> PanelRaised
        },
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.semantics {
            contentDescription = label
            role = Role.Button
        },
    ) {
        Text(
            label,
            color = when {
                !enabled -> Dim.copy(alpha = 0.5f)
                warn -> Ink
                active -> StudioAccentInk
                else -> Cream
            },
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.35.sp,
        )
    }
}

@Composable
internal fun StudioSectionLabel(title: String, note: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Cream, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, color = Dim, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SheetBackgroundScrim(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(StudioInk.copy(alpha = 0.62f))
            .clickable(onClick = onClose),
    )
}

