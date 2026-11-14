package app.add.engine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    modifier: Modifier = Modifier,
    restoreOnTap: Boolean = false,
    onRestore: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        TactileBox(
            onClick = {
                if (restoreOnTap && onRestore != null) onRestore() else menu = true
            },
            shape = RoundedCornerShape(99.dp),
            background = Forest.copy(alpha = 0.94f),
            contentPadding = PaddingValues(start = 10.dp, end = 12.dp),
            haptic = true,
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 48.dp)
                .testTag("bmo-page-chip")
                .semantics {
                    contentDescription = "Page controls"
                    role = Role.Button
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(Mint),
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("PAGE 01", color = Cream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${doc.layers.size} LAYERS · ${doc.saveState.ifBlank { "DRAFT" }}",
                        color = Dim,
                        fontSize = 7.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("▾", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        androidx.compose.material.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            BmoMenuItem("Documents") { menu = false; onDocuments() }
            BmoMenuItem("Actions") { menu = false; onCommands() }
            BmoMenuItem("Hide chrome") { menu = false; onHideChrome() }
            BmoMenuItem("Back") { menu = false; onBack() }
            BmoMenuItem("About") { menu = false; onInfo() }
        }
    }
}

@Composable
private fun BmoMenuItem(label: String, onClick: () -> Unit) {
    androidx.compose.material.DropdownMenuItem(onClick = onClick) {
        Text(label, color = Cream, fontSize = 12.sp)
    }
}

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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Forest.copy(alpha = 0.97f))
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState())
            .focusable()
            .testTag("bmo-tool-dock"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tool.entries.forEach { tool ->
            BmoDockButton(
                glyph = tool.glyph,
                label = tool.label.uppercase(),
                active = active == tool,
                tag = "bmo-tool-${tool.name.lowercase()}",
                content = tool.label,
                onClick = { onTool(tool) },
            )
        }
        BmoDockButton("+", "ADD", false, "bmo-add", "Add layer", onAdd, accent = true)
        BmoDockButton("⌕", "ACTIONS", false, "bmo-actions", "Actions", onCommands)
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
) {
    val bg = when {
        accent -> Lime
        active -> Mint
        else -> PanelRaised.copy(alpha = 0.72f)
    }
    val fg = when {
        accent || active -> Ink
        else -> Cream.copy(alpha = 0.88f)
    }
    val base = Modifier
        .size(50.dp)
        .clip(RoundedCornerShape(15.dp))
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
        Text(glyph, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            color = fg.copy(alpha = 0.8f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
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
            .clip(RoundedCornerShape(16.dp))
            .background(Forest.copy(alpha = 0.96f))
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .testTag("bmo-selection-rail"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            layer.name.substringAfter(" / ", layer.name).uppercase(),
            color = Dim,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 92.dp),
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
    TactileBox(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        background = if (accent) Lime else PanelRaised,
        contentPadding = PaddingValues(horizontal = 9.dp),
        haptic = true,
        modifier = Modifier
            .height(40.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics {
                contentDescription = content
                role = Role.Button
            },
    ) {
        Text(
            label,
            color = if (accent) Ink else Cream,
            fontSize = 8.5.sp,
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
                .background(Cream.copy(alpha = 0.45f)),
        )
    }
}
