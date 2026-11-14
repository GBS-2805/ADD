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

@Composable
internal fun LibraryDialog(
    context: android.content.Context,
    doc: EngineDocument,
    onChanged: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(DocumentStore.library(context)) }
    val legacy = remember { LegacyLookMigration.migrate(context) }
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
            if (legacy.looks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "LEGACY LOOKS · ${legacy.imported} IMPORTED",
                    color = Dim,
                    fontSize = 7.sp,
                    letterSpacing = 0.8.sp,
                )
                legacy.looks.take(4).forEach { look ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            look.name.uppercase(),
                            color = Cream,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        ActionChip(
                            "APPLY",
                            onClick = {
                                LegacyLookMigration.apply(doc, look)
                                onChanged()
                            },
                        )
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
    onSticker: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().padding(top = 90.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Panel)
                    .border(1.dp, Rule, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ADD TO THE PAGE", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.0.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "CLOSE",
                        color = Mint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every add lands selected and ready to move. Tap DONE to settle it.",
                    color = Dim,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddTile("T", "TYPE", "Editable line", Modifier.weight(1f)) { doc.addText(); onClose() }
                    AddTile("A", "VECTOR", "Editable network", Modifier.weight(1f)) { doc.addVector(); onClose() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddTile("I", "ICON", "4,373 glyphs", Modifier.weight(1f)) { onIcons() }
                    AddTile("S", "STICKER", "Local shapes", Modifier.weight(1f)) { onSticker() }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddTile("3", "3D", "Gem or ring", Modifier.weight(1f)) { doc.addModel(ModelAsset.Gem); onClose() }
                    AddTile("P", "PHOTO", "From the gallery", Modifier.weight(1f)) { onPhoto() }
                }
                Spacer(Modifier.height(12.dp))
                Text("The sheet closes after an add, so the new item is on the stage and selected.", color = Dim, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun AddTile(
    glyph: String,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .testTag("add-${title.lowercase(Locale.US)}")
            .clip(RoundedCornerShape(12.dp))
            .background(PanelRaised)
            .border(1.dp, Rule, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(glyph, color = Mint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(title, color = Cream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Text(note, color = Dim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun HistoryDialog(doc: EngineDocument, onClose: () -> Unit) {
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
internal fun ExportDialog(
    scale: Int,
    format: ExportFormat = ExportFormat.Png,
    onScale: (Int) -> Unit,
    onFormat: (ExportFormat) -> Unit = {},
    onExport: () -> Unit,
    onShare: () -> Unit = {},
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
            Text("EXPORT", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(4.dp))
            Text("${format.name.uppercase()} · ${(ArtboardPainter.W * scale).toInt()} × ${(ArtboardPainter.H * scale).toInt()}", color = Dim, fontSize = 8.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("1×", active = scale == 1, onClick = { onScale(1) })
                ActionChip("2×", active = scale == 2, onClick = { onScale(2) })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("PNG", active = format == ExportFormat.Png, onClick = { onFormat(ExportFormat.Png) })
                ActionChip("JPG", active = format == ExportFormat.Jpeg, onClick = { onFormat(ExportFormat.Jpeg) })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("EXPORT ${format.name.uppercase()}", active = true, onClick = onExport)
                ActionChip("SHARE LAST", onClick = onShare)
            }
        }
    }
}

