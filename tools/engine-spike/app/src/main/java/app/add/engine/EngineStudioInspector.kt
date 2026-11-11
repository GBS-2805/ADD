package app.add.engine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
internal fun StyleTab(
    doc: EngineDocument,
    handle: SceneHandle,
    onIcons: () -> Unit,
    onMask: () -> Unit,
    onTurn3D: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (doc.tool == Tool.Sticker) {
            StudioSectionLabel("PICK A SHAPE", "${StickerLibrary.presets.size} ready")
            Spacer(Modifier.height(8.dp))
            StickerTray(doc)
            Spacer(Modifier.height(18.dp))
            return@Column
        }
        if (doc.tool == Tool.Icon) {
            StudioSectionLabel("ICON", "4,373 glyphs")
            Spacer(Modifier.height(8.dp))
            IconTray(onIcons)
            Spacer(Modifier.height(18.dp))
            return@Column
        }
        val layer = doc.selected
        if (layer == null) {
            Text("Nothing selected yet.", color = Cream, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("Tap a layer, or make something new.", color = Dim, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            ActionChip("MAKE SOMETHING", active = true, onClick = { doc.addText() })
            return@Column
        }
        StudioSectionLabel("STYLE", layer.kindLabel)
        Spacer(Modifier.height(6.dp))
        LayerIdentityRow(doc, layer)
        Spacer(Modifier.height(8.dp))
        MaskCallout(doc, layer, onMask)
        Spacer(Modifier.height(12.dp))
        StudioSectionLabel("TRANSFORM", "Move it, size it, turn it")
        Spacer(Modifier.height(2.dp))
        TransformInspector(doc, layer)
        Spacer(Modifier.height(10.dp))
        when (layer) {
            is VectorLayer -> {
                StudioSectionLabel("DETAILS", "Shape and stroke")
                Spacer(Modifier.height(4.dp))
                VectorInspector(doc, layer)
            }
            is TextLayer -> {
                StudioSectionLabel("TYPE", "Words and rhythm")
                Spacer(Modifier.height(4.dp))
                TextInspector(doc, layer)
            }
            is IconLayer -> {
                StudioSectionLabel("ICON", "Tint and path")
                Spacer(Modifier.height(4.dp))
                IconInspector(doc, layer, onIcons)
            }
            is ModelLayer -> {
                StudioSectionLabel("3D STUDIO", "The object and its frame")
                Spacer(Modifier.height(4.dp))
                ModelInspector(doc, layer, handle, onEdit3D = onTurn3D)
            }
            is ImageLayer -> {
                StudioSectionLabel("PHOTO", "Fill the frame")
                Spacer(Modifier.height(4.dp))
                ImageInspector(doc, layer)
            }
        }
        Spacer(Modifier.height(10.dp))
        PaintAndBlend(doc, layer)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MaskCallout(doc: EngineDocument, layer: Layer, onMask: () -> Unit) {
    val mask = layer.style.mask
    TactileBox(
        onClick = onMask,
        shape = RoundedCornerShape(15.dp),
        background = Mint.copy(alpha = 0.12f),
        border = BorderStroke(1.2.dp, Mint.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(11.dp),
        haptic = true,
        modifier = Modifier.fillMaxWidth().testTag("mask-entry"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(35.dp).clip(CircleShape).background(Mint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("◐", color = Mint, fontSize = 18.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("MASK BRUSH", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(
                    when {
                        mask == null -> "Cut into this layer with a soft brush."
                        mask.strokes.isEmpty() -> "The mask is ready. Paint a shape through it."
                        else -> "${mask.strokes.size} brush ${if (mask.strokes.size == 1) "stroke" else "strokes"} · keep going"
                    },
                    color = Cream,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
            Text("OPEN", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun PaintAndBlend(doc: EngineDocument, layer: Layer) {
    var open by remember(layer.id) { mutableStateOf(false) }
    val reduceMotion = prefersReducedMotion()
    val paint = when (layer) {
        is VectorLayer -> layer.paint
        is TextLayer -> layer.paint
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PanelRaised)
            .border(1.dp, Rule, CardShape)
            .padding(10.dp),
    ) {
        TactileBox(
            onClick = { open = !open },
            shape = RoundedCornerShape(12.dp),
            background = Color.Transparent,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAINT & BLEND", color = Cream, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
                    Text(
                        "${paint?.label ?: "LAYER COLOUR"} · ${layer.style.blend.label}",
                        color = Dim,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (open) "▴" else "▾", color = Mint, fontSize = 12.sp)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.75f, stiffness = 380f)) +
                fadeIn(if (reduceMotion) tween(0) else tween(120)),
            exit = shrinkVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.85f, stiffness = 500f)) +
                fadeOut(if (reduceMotion) tween(0) else tween(90)),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                if (layer is VectorLayer || layer is TextLayer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PaintKind.entries.forEach { kind ->
                            ActionChip(
                                label = kind.label,
                                active = when (paint) {
                                    null -> kind == PaintKind.Solid
                                    is PaintSpec.Solid -> kind == PaintKind.Solid
                                    is PaintSpec.Gradient -> paint.kind == kind
                                    is PaintSpec.Image -> kind == PaintKind.Image
                                    is PaintSpec.Pattern -> kind == PaintKind.Pattern
                                },
                                onClick = { doc.setPaintKind(kind) },
                            )
                        }
                    }
                    (paint as? PaintSpec.Gradient)?.let { GradientStops(doc, layer.id, it) }
                    (paint as? PaintSpec.Image)?.let { image ->
                        val photos = doc.layers.filterIsInstance<ImageLayer>()
                        Spacer(Modifier.height(4.dp))
                        if (photos.isEmpty()) {
                            InfoLine("NO PHOTO IN THE DOCUMENT TO FILL FROM")
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                photos.forEach { photo ->
                                    ActionChip(
                                        label = photo.name,
                                        active = photo.path == image.path,
                                        onClick = { doc.setPaintImage(photo.path, image.fill) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ActionChip("FILL FRAME", active = image.fill) { doc.setPaintImageFill(true) }
                                ActionChip("FIT INSIDE", active = !image.fill) { doc.setPaintImageFill(false) }
                            }
                        }
                    }
                    (paint as? PaintSpec.Pattern)?.let { pattern ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PatternKind.entries.forEach { kind ->
                                ActionChip(kind.label, active = pattern.kind == kind) { doc.setPatternKind(kind) }
                            }
                        }
                        SliderRow(
                            label = "PATTERN SCALE",
                            value = pattern.scale,
                            range = 0.25f..4f,
                            display = { String.format("%.2f×", it) },
                            onChange = { doc.setPatternScale(it) },
                        )
                    }
                } else {
                    InfoLine("PAINT APPLIES TO TYPE AND VECTOR LAYERS.")
                }
                Spacer(Modifier.height(6.dp))
                Text("BLEND", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionChip("◀", onClick = { doc.cycleBlend(-1) })
                    Spacer(Modifier.width(5.dp))
                    Text(
                        layer.style.blend.label,
                        color = Cream,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    ActionChip("▶", onClick = { doc.cycleBlend(1) })
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BlendKind.entries.forEach { kind ->
                        ActionChip(kind.label, active = layer.style.blend == kind) { doc.setBlend(kind) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionChip("CLIP TO BELOW", active = layer.style.clipToBelow) { doc.toggleClip() }
                    ActionChip("SAFE AREA", active = doc.safeArea) { doc.safeArea = !doc.safeArea }
                }
                Spacer(Modifier.height(4.dp))
                InfoLine("PAINT AND BLEND STAY EDITABLE · NOTHING IS FLATTENED")
            }
        }
    }
}

private enum class EffectKind(val label: String, val glyph: String) {
    Grade("Grade", "◒"),
    Blur("Blur", "◌"),
    Grain("Grain", "⁙"),
    Halftone("Halftone", "◍"),
    Duotone("Duotone", "◐"),
}

@Composable
internal fun FxTab(doc: EngineDocument, modifier: Modifier = Modifier) {
    val layer = doc.selected
    if (layer == null) {
        Column(modifier.padding(16.dp)) {
            Text("Pick a layer first.", color = Cream, fontSize = 13.sp)
        }
        return
    }
    val fx = layer.style.effects
    var openCard by remember(layer.id) { mutableStateOf<EffectKind?>(null) }
    var fineTune by remember(layer.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reduceMotion = prefersReducedMotion()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EFFECTS", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
            Spacer(Modifier.weight(1f))
            Text(if (fx.enabled) "LIVE" else "BYPASSED", color = Dim, fontSize = 9.5.sp, letterSpacing = 0.4.sp)
            Spacer(Modifier.width(8.dp))
            TactileBox(
                onClick = {
                    val choices = EffectPreset.entries.filter { it.result(fx) != fx }
                    val recipe = choices.ifEmpty { EffectPreset.entries }.random()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    doc.applyEffectsPreset("Surprise ${recipe.label}", recipe.result(fx))
                },
                shape = RoundedCornerShape(99.dp),
                background = Lime,
                contentPadding = PaddingValues(horizontal = 10.dp),
                haptic = false,
                modifier = Modifier.height(38.dp).testTag("fx-surprise"),
            ) {
                Text("SURPRISE ME", color = Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Start with a recipe. Open one card to change the dials.",
            color = Dim,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            EffectPreset.entries.forEach { preset ->
                TactileBox(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        doc.applyEffectsPreset(preset.label, preset.result(fx))
                    },
                    shape = RoundedCornerShape(14.dp),
                    background = Paper,
                    border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.16f)),
                    shadow = 3.dp,
                    haptic = false,
                    modifier = Modifier.weight(1f).height(67.dp),
                    contentPadding = PaddingValues(7.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(preset.dot))
                            Spacer(Modifier.width(4.dp))
                            Text(preset.label, color = StudioOutline, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.note,
                            color = StudioOutline.copy(alpha = 0.55f),
                            fontSize = 9.5.sp,
                            lineHeight = 11.5.sp,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelRaised)
                .border(1.dp, Rule, RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("THE WHOLE STACK", color = Cream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                Text(if (fx.enabled) "All effects are on." else "Bypassed — the clean layer is showing.", color = Dim, fontSize = 9.5.sp)
            }
            MiniSwitch(on = fx.enabled) { doc.toggleEffects() }
        }
        Spacer(Modifier.height(10.dp))
        EffectCard(
            title = "Grade",
            glyph = EffectKind.Grade.glyph,
            on = fx.gradeEnabled,
            summary = gradeSummary(fx),
            expanded = openCard == EffectKind.Grade,
            onExpand = { openCard = if (openCard == EffectKind.Grade) null else EffectKind.Grade },
            onToggle = { doc.toggleGrade() },
        ) {
            TactileBox(
                onClick = { fineTune = !fineTune },
                shape = RoundedCornerShape(10.dp),
                background = Ink.copy(alpha = 0.08f),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FINE TUNE", color = StudioOutline, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (fineTune) "▴" else "▾", color = StudioOutline, fontSize = 11.sp)
                }
            }
            AnimatedVisibility(
                visible = fineTune,
                enter = expandVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 420f)) +
                    fadeIn(if (reduceMotion) tween(0) else tween(100)),
                exit = shrinkVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.85f, stiffness = 500f)) +
                    fadeOut(if (reduceMotion) tween(0) else tween(80)),
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("LIGHT", color = StudioOutline.copy(alpha = 0.48f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    CreamSlider("Exposure", fx.exposure, -2f..2f, { signed(it) }) { doc.setExposure(it) }
                    CreamSlider("Contrast", fx.contrast, -1f..1f, { signed(it) }) { doc.setContrast(it) }
                    CreamSlider("Shadows", fx.shadows, -1f..1f, { signed(it) }) { doc.setShadows(it) }
                    CreamSlider("Midtones", fx.midtones, -1f..1f, { signed(it) }) { doc.setMidtones(it) }
                    CreamSlider("Highlights", fx.highlights, -1f..1f, { signed(it) }) { doc.setHighlights(it) }
                    Spacer(Modifier.height(5.dp))
                    Text("COLOR", color = StudioOutline.copy(alpha = 0.48f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    CreamSlider("Saturation", fx.saturation, -1f..1f, { signed(it) }) { doc.setSaturation(it) }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        EffectCard(
            title = "Blur",
            glyph = EffectKind.Blur.glyph,
            on = fx.blurEnabled,
            summary = when {
                !fx.blurEnabled -> "Bypassed"
                fx.blur <= 0f -> "Ready to soften"
                else -> "${fx.blur.toInt()} px soft"
            },
            expanded = openCard == EffectKind.Blur,
            onExpand = { openCard = if (openCard == EffectKind.Blur) null else EffectKind.Blur },
            onToggle = { doc.toggleBlur() },
        ) {
            CreamSlider("Blur amount", fx.blur, 0f..24f, { "${it.toInt()} px" }) { doc.setBlur(it) }
        }
        Spacer(Modifier.height(7.dp))
        EffectCard(
            title = "Grain",
            glyph = EffectKind.Grain.glyph,
            on = fx.grainEnabled,
            summary = when {
                !fx.grainEnabled -> "Bypassed"
                fx.grain <= 0f -> "Ready to add texture"
                else -> "${(fx.grain * 100).toInt()}% texture"
            },
            expanded = openCard == EffectKind.Grain,
            onExpand = { openCard = if (openCard == EffectKind.Grain) null else EffectKind.Grain },
            onToggle = { doc.toggleGrain() },
        ) {
            CreamSlider("Grain amount", fx.grain, 0f..1f, { "${(it * 100).toInt()}%" }) { doc.setGrain(it) }
        }
        Spacer(Modifier.height(7.dp))
        EffectCard(
            title = "Halftone",
            glyph = EffectKind.Halftone.glyph,
            on = fx.halftoneEnabled,
            summary = when {
                !fx.halftoneEnabled -> "Bypassed"
                fx.halftone <= 0f -> "Ready to add dots"
                else -> "${(fx.halftone * 100).toInt()}% dots"
            },
            expanded = openCard == EffectKind.Halftone,
            onExpand = { openCard = if (openCard == EffectKind.Halftone) null else EffectKind.Halftone },
            onToggle = { doc.toggleHalftone() },
        ) {
            CreamSlider("Dot size", fx.halftone, 0f..1f, { "${(it * 100).toInt()}%" }) { doc.setHalftone(it) }
        }
        Spacer(Modifier.height(7.dp))
        EffectCard(
            title = "Duotone",
            glyph = EffectKind.Duotone.glyph,
            on = fx.duotone,
            summary = if (fx.duotone) "Two-colour ink" else "Off",
            expanded = openCard == EffectKind.Duotone,
            onExpand = { openCard = if (openCard == EffectKind.Duotone) null else EffectKind.Duotone },
            onToggle = { doc.toggleDuotone() },
        ) {
            Text(
                "A dark and light ink pair, mixed by luminance.",
                color = StudioOutline.copy(alpha = 0.6f),
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(fx.duotoneDark)).border(1.dp, StudioOutline.copy(alpha = 0.2f), CircleShape))
                Text("→", color = StudioOutline.copy(alpha = 0.4f), fontSize = 11.sp)
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(fx.duotoneLight)).border(1.dp, StudioOutline.copy(alpha = 0.2f), CircleShape))
                Spacer(Modifier.weight(1f))
                Text("INK / LIGHT", color = StudioOutline.copy(alpha = 0.45f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(9.dp))
        InfoLine("EFFECTS STACK ON THE ORIGINAL · NOTHING IS DESTRUCTIVE")
        Spacer(Modifier.height(22.dp))
    }
}

private enum class EffectPreset(val label: String, val note: String, val dot: Color) {
    Clean("Clean", "True colour", Mint),
    Film("Film", "Soft contrast", Lime),
    Print("Print", "Halftone ink", Clay),
    Faded("Faded", "Low contrast", Dim),
    Punch("Punch", "Bright + bold", Color(0xFFFFC857)),
    ;

    fun result(current: LayerEffects): LayerEffects = when (this) {
        Clean -> current.copy(
            enabled = true,
            gradeEnabled = true,
            blurEnabled = true,
            grainEnabled = true,
            halftoneEnabled = true,
            duotone = false,
            exposure = 0f,
            contrast = 0f,
            saturation = 0f,
            shadows = 0f,
            midtones = 0f,
            highlights = 0f,
            blur = 0f,
            grain = 0f,
            halftone = 0f,
        )
        Film -> current.copy(
            enabled = true,
            gradeEnabled = true,
            blurEnabled = false,
            grainEnabled = true,
            halftoneEnabled = false,
            duotone = false,
            exposure = -0.04f,
            contrast = 0.18f,
            saturation = -0.08f,
            shadows = 0.15f,
            midtones = 0.03f,
            highlights = -0.08f,
            blur = 0f,
            grain = 0.16f,
            halftone = 0f,
        )
        Print -> current.copy(
            enabled = true,
            gradeEnabled = true,
            blurEnabled = false,
            grainEnabled = true,
            halftoneEnabled = true,
            duotone = true,
            exposure = 0.04f,
            contrast = 0.28f,
            saturation = -0.18f,
            shadows = 0.08f,
            midtones = 0.04f,
            highlights = -0.12f,
            blur = 0f,
            grain = 0.12f,
            halftone = 0.28f,
            duotoneDark = 0xFF14231B.toInt(),
            duotoneLight = 0xFFF1E7D2.toInt(),
        )
        Faded -> current.copy(
            enabled = true,
            gradeEnabled = true,
            blurEnabled = false,
            grainEnabled = true,
            halftoneEnabled = false,
            duotone = false,
            exposure = 0.03f,
            contrast = -0.2f,
            saturation = -0.24f,
            shadows = 0.22f,
            midtones = 0.06f,
            highlights = -0.1f,
            blur = 0f,
            grain = 0.1f,
            halftone = 0f,
        )
        Punch -> current.copy(
            enabled = true,
            gradeEnabled = true,
            blurEnabled = false,
            grainEnabled = true,
            halftoneEnabled = false,
            duotone = false,
            exposure = 0.1f,
            contrast = 0.34f,
            saturation = 0.3f,
            shadows = -0.08f,
            midtones = 0.02f,
            highlights = 0.1f,
            blur = 0f,
            grain = 0.05f,
            halftone = 0f,
        )
    }
}

@Composable
private fun EffectCard(
    title: String,
    glyph: String,
    on: Boolean,
    summary: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = prefersReducedMotion()
    TactileBox(
        onClick = onExpand,
        shape = RoundedCornerShape(17.dp),
        background = Paper,
        border = BorderStroke(1.2.dp, if (expanded) Mint else StudioOutline.copy(alpha = 0.14f)),
        shadow = if (expanded) 5.dp else 3.dp,
        contentPadding = PaddingValues(11.dp),
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(35.dp).clip(CircleShape).background(if (on) MintWash else Ink.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, color = if (on) StudioOutline else StudioOutline.copy(alpha = 0.38f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = StudioOutline, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(summary, color = StudioOutline.copy(alpha = 0.55f), fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "▴" else "▾", color = StudioOutline.copy(alpha = 0.42f), fontSize = 11.sp)
                Spacer(Modifier.width(7.dp))
                MiniSwitch(on = on, onToggle = onToggle)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 420f)) +
                    fadeIn(if (reduceMotion) tween(0) else tween(100)),
                exit = shrinkVertically(if (reduceMotion) tween(0) else spring(dampingRatio = 0.85f, stiffness = 500f)) +
                    fadeOut(if (reduceMotion) tween(0) else tween(80)),
            ) {
                Column(Modifier.padding(top = 9.dp), content = content)
            }
        }
    }
}

@Composable
private fun CreamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                color = StudioOutline.copy(alpha = 0.55f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.55.sp,
                modifier = Modifier.weight(1f),
            )
            Text(display(value), color = StudioOutline, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(31.dp),
            colors = SliderDefaults.colors(
                thumbColor = Mint,
                activeTrackColor = Mint,
                inactiveTrackColor = StudioOutline.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun MiniSwitch(on: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val reduceMotion = prefersReducedMotion()
    val knob by animateDpAsState(
        targetValue = if (on) 18.dp else 0.dp,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "mini-switch",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) Mint else StudioOutline.copy(alpha = 0.16f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = knob).size(22.dp).clip(CircleShape).background(if (on) Ink else Paper))
    }
}

@Composable
internal fun LayersTab(doc: EngineDocument, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 42.dp.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var carried by remember { mutableFloatStateOf(0f) }
    val selected = doc.selected
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        StudioSectionLabel("YOUR STACK", "${doc.layers.size}")
        Spacer(Modifier.height(3.dp))
        Text("Front of the page is at the top. Hold a card to move it.", color = Dim, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            doc.layers.asReversed().forEach { layer ->
                key(layer.id) {
                    LayerCard(
                        layer = layer,
                        selected = layer.id == doc.selectedId,
                        dragging = draggingId == layer.id,
                        onSelect = {
                            doc.select(layer.id)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onVisibility = { doc.toggleVisibility(layer.id) },
                        onDragStart = {
                            draggingId = layer.id
                            carried = 0f
                        },
                        onDrag = { dy ->
                            if (draggingId == layer.id) {
                                carried += dy
                                if (carried < -threshold) {
                                    doc.moveLayer(layer.id, 1)
                                    carried = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (carried > threshold) {
                                    doc.moveLayer(layer.id, -1)
                                    carried = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            carried = 0f
                        },
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text("HOLD + DRAG TO REORDER", color = Dim.copy(alpha = 0.65f), fontSize = 9.5.sp, letterSpacing = 0.5.sp)
        }
        if (selected != null) {
            Spacer(Modifier.height(9.dp))
            StudioSectionLabel("THIS LAYER", selected.name)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                HudChip("DUPLICATE") { doc.duplicateSelected() }
                HudChip("BRING FORWARD") { doc.reorderSelected(1) }
                HudChip("SEND BACK") { doc.reorderSelected(-1) }
                HudChip("DELETE", warn = true) { doc.deleteSelected() }
            }
        }
    }
}

@Composable
private fun LayerCard(
    layer: Layer,
    selected: Boolean,
    dragging: Boolean,
    onSelect: () -> Unit,
    onVisibility: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    TactileBox(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        background = if (selected) MintWash else Paper,
        border = BorderStroke(1.4.dp, if (selected) Mint else StudioOutline.copy(alpha = 0.14f)),
        shadow = 0.dp,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = if (selected) (-2).dp else 0.dp)
            .graphicsLayer {
                translationX = if (dragging) 5.dp.toPx() else 0f
            }
            .testTag("layer-${layer.id}")
            .pointerInput(layer.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(layerStickerWash(layer)),
                contentAlignment = Alignment.Center,
            ) {
                Text(layerGlyph(layer), color = layerStickerInk(layer), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    layer.name,
                    color = StudioOutline,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    layer.kindLabel.lowercase().replaceFirstChar { it.uppercase() },
                    color = StudioOutline.copy(alpha = 0.52f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TactileBox(
                onClick = onVisibility,
                shape = CircleShape,
                background = StudioOutline.copy(alpha = 0.05f),
                modifier = Modifier.size(38.dp).testTag("layer-vis-${layer.id}"),
            ) {
                Text(if (layer.visible) "◉" else "○", color = if (layer.visible) Mint else StudioOutline.copy(alpha = 0.35f), fontSize = 15.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text("⠿", color = StudioOutline.copy(alpha = 0.3f), fontSize = 16.sp)
        }
    }
}

private fun gradeSummary(fx: LayerEffects): String = when {
    !fx.gradeEnabled -> "Off"
    fx.exposure == 0f && fx.contrast == 0f && fx.saturation == 0f &&
        fx.shadows == 0f && fx.midtones == 0f && fx.highlights == 0f -> "Neutral"
    else -> "${signed(fx.contrast)} contrast · ${signed(fx.saturation)} colour"
}

private fun signed(value: Float): String = String.format("%+.2f", value)
