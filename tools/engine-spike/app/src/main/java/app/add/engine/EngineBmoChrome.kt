package app.add.engine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Floating BMO page chip; the only chrome that survives Peek. */
@Composable
fun BmoPageChip(
    doc: EngineDocument,
    onBack: () -> Unit,
    onDocuments: () -> Unit,
    onHideChrome: () -> Unit,
    onInfo: () -> Unit,
    onCommands: () -> Unit,
    onToggleSafeArea: () -> Unit,
    modifier: Modifier = Modifier,
    restoreOnTap: Boolean = false,
    onRestore: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        CompactHitTarget(
            onClick = {
                if (restoreOnTap && onRestore != null) onRestore() else menu = true
            },
            shape = RoundedCornerShape(99.dp),
            background = StudioSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, StudioHairline),
            shadow = 10.dp,
            contentPadding = PaddingValues(start = 8.dp, end = 10.dp),
            visualHeight = 36.dp,
            haptic = true,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .testTag("bmo-page-chip")
                .semantics {
                    contentDescription = "Page controls"
                    role = Role.Button
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(StudioAccent),
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = StudioAccentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(5.dp))
                Column {
                    Text("PAGE 01", color = StudioText, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${doc.layers.size} LAYERS · ${doc.saveState.ifBlank { "DRAFT" }}",
                        color = StudioMuted,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("▾", color = StudioText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        MaterialTheme(
            colors = MaterialTheme.colors.copy(surface = StudioSurface),
            shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(16.dp)),
        ) {
            // The default dark elevation overlay would tint StudioSurface toward Mint.
            CompositionLocalProvider(LocalElevationOverlay provides null) {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    BmoMenuItem("Documents") { menu = false; onDocuments() }
                    BmoMenuItem("Actions") { menu = false; onCommands() }
                    BmoMenuItem(if (doc.safeArea) "Hide safe area" else "Show safe area") {
                        menu = false
                        onToggleSafeArea()
                    }
                    BmoMenuItem("Hide chrome") { menu = false; onHideChrome() }
                    BmoMenuItem("Back") { menu = false; onBack() }
                    BmoMenuItem("About") { menu = false; onInfo() }
                }
            }
        }
    }
}

@Composable
private fun BmoMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier
            .height(48.dp)
            .testTag("bmo-menu-${label.lowercase().replace(' ', '-')}"),
    ) {
        Text(label, color = Cream, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * One row needs this much dock width for five tools plus add, actions and plugins.
 * Below it the fifth tool sits under the scroll edge and its label is cut, so the
 * dock switches to two rows instead of hiding a tool or shrinking a target.
 */
internal val DockOneRowMinWidth = 398.dp

/**
 * The widest the single-row pill ever gets: six 48dp tools, the divider and the
 * three page actions need 447dp, so 456 leaves a little breathing room. Without
 * this the pill stretches edge to edge on an 840dp tablet with a dead middle.
 */
private val DockSingleRowMaxWidth = 456.dp
internal val DockOneRowHeight = 54.dp
internal val DockTwoRowHeight = 107.dp

/** The dock stacks below this width, so callers clear one row or two. */
internal fun dockHeight(dockWidth: Dp): Dp =
    if (dockWidth < DockOneRowMinWidth) DockTwoRowHeight else DockOneRowHeight

/** The floating bottom dock. No full-width band, no Material app bar. */
@Composable
fun BmoToolDock(
    active: Tool,
    onTool: (Tool) -> Unit,
    onAdd: () -> Unit,
    onCommands: () -> Unit,
    onPlugins: () -> Unit,
    onPluginLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.focusable().testTag("bmo-tool-dock"),
        contentAlignment = Alignment.Center,
    ) {
        // The nested Box below changes the implicit receiver, so keep the measured
        // width in a local before laying anything out.
        val dockWidth = maxWidth
        val surface = Modifier
            .shadow(12.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(StudioSurface.copy(alpha = 0.98f))
            .border(1.dp, StudioHairline, RoundedCornerShape(26.dp))
            .padding(horizontal = 2.dp, vertical = 3.dp)
        if (dockWidth < DockOneRowMinWidth) {
            // 360dp phones: every tool on its own row above the page actions, so no
            // label ever sits under a scroll edge.
            Column(surface) {
                ToolButtons(active = active, onTool = onTool, scrollable = false)
                Box(
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(StudioHairline),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BmoDockButton("+", "ADD", false, "bmo-add", "Add layer", onAdd, accent = true)
                    }
                    BmoDockButton(
                        "⋯", "ACTIONS", false, "bmo-actions", "Actions", onCommands,
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                    BmoDockButton(
                        glyph = "▦",
                        label = "PLUGINS",
                        active = false,
                        tag = "bmo-plugins",
                        content = "Plugins",
                        onClick = onPlugins,
                        onLongClick = onPluginLongPress,
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.widthIn(max = DockSingleRowMaxWidth).then(surface),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ToolButtons(active = active, onTool = onTool, scrollable = true)
                    // The fade only promises more tools when the pill is narrower than
                    // its natural width; at the cap every tool already fits.
                    if (dockWidth < DockSingleRowMaxWidth) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(12.dp)
                                .height(44.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, StudioSurface.copy(alpha = 0.98f)),
                                    ),
                                ),
                        )
                    }
                }
                Box(Modifier.width(1.dp).height(26.dp).background(StudioHairline))
                BmoDockButton("+", "ADD", false, "bmo-add", "Add layer", onAdd, accent = true)
                BmoDockButton("⋯", "ACTIONS", false, "bmo-actions", "Actions", onCommands)
                BmoDockButton(
                    glyph = "▦",
                    label = "PLUGINS",
                    active = false,
                    tag = "bmo-plugins",
                    content = "Plugins",
                    onClick = onPlugins,
                    onLongClick = onPluginLongPress,
                )
            }
        }
    }
}

@Composable
private fun ToolButtons(active: Tool, onTool: (Tool) -> Unit, scrollable: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tool.entries.forEach { tool ->
            BmoDockButton(
                glyph = tool.glyph,
                label = tool.label.uppercase(),
                active = active == tool,
                tag = "bmo-tool-${tool.name.lowercase()}",
                content = tool.label,
                hint = tool.hint,
                onClick = { onTool(tool) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BmoDockButton(
    glyph: String,
    label: String,
    active: Boolean,
    tag: String,
    content: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        accent -> StudioAccent
        active -> StudioText
        else -> Color.Transparent
    }
    val fg = when {
        accent -> StudioAccentInk
        active -> StudioInk
        else -> StudioMuted
    }
    val base = modifier
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .clip(if (accent) CircleShape else RoundedCornerShape(14.dp))
        .background(bg)
        .testTag(tag)
        .semantics {
            contentDescription = content
            role = Role.Button
        }
    val clickModifier = if (onLongClick == null) {
        base.clickable(onClick = onClick)
    } else {
        base.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }
    Column(
        modifier = clickModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(glyph, color = fg, fontSize = if (accent) 22.sp else 14.sp, fontWeight = FontWeight.SemiBold)
        if (!accent) {
            Text(
                label,
                color = fg.copy(alpha = 0.8f),
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                maxLines = 1,
            )
            if (hint != null) {
                Text(
                    hint,
                    color = fg.copy(alpha = 0.66f),
                    fontSize = 5.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Contextual actions for the selected layer, immediately above the dock. */
@Composable
fun BmoSelectionRail(
    doc: EngineDocument,
    onStyle: () -> Unit,
    onEffects: () -> Unit,
    onLayers: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onTexture: () -> Unit = {},
) {
    val layer = doc.selected ?: return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(StudioSurface.copy(alpha = 0.98f))
            .border(1.dp, StudioHairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("bmo-selection-rail"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            layer.name.substringAfter(" / ", layer.name).uppercase(),
            color = StudioMuted,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp),
        )
        BmoRailChip("STYLE", "Style", onStyle, tag = "object-style")
        BmoRailChip("FX", "Effects", onEffects, tag = "object-effects")
        BmoRailChip("INK", "Material ink", onTexture, tag = "object-texture")
        BmoRailChip("LAYERS", "Layers", onLayers, tag = "object-layers")
        BmoRailChip("DONE", "Done", onDone, accent = true, tag = "object-done")
    }
}

@Composable
private fun BmoRailChip(
    label: String,
    content: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    tag: String? = null,
) {
    CompactHitTarget(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        background = if (accent) StudioAccent else StudioRaised,
        contentPadding = PaddingValues(horizontal = 9.dp),
        visualHeight = 34.dp,
        haptic = true,
        modifier = Modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics {
                contentDescription = content
                role = Role.Button
            },
    ) {
        Text(
            label,
            color = if (accent) StudioAccentInk else StudioText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The only control left in Canvas mode. */
@Composable
fun BmoCanvasRestore(onRestore: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .testTag("bmo-restore")
            .semantics {
                contentDescription = "Show editor controls"
                role = Role.Button
            }
            .clickable(onClick = onRestore),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(StudioText.copy(alpha = 0.45f)),
        )
    }
}
