package app.add.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Locale

private val DeviceChecklist = listOf(
    "The gem is visible inside the artboard frame.",
    "EDIT 3D shows a chip; dragging inside it turns the model.",
    "In 3D edit, corners resize the viewport without shearing the model.",
    "The RING chip swaps the model without leaving the layer.",
    "Corner handles rotate and resize a text layer.",
    "Sticker and vector shapes stay square when corner-scaled.",
    "Dragging an unedited 3D layer moves it on the artboard.",
    "The icon catalog opens and places a glyph on the artboard.",
    "Icon thumbnails are legible against the dark catalog cards.",
    "EXPORT writes a PNG to Pictures/Engine Spike.",
    "The exported PNG shows the model, not the placeholder.",
)

@Composable
fun DiagnosticsDialog(
    doc: EngineDocument,
    handle: SceneHandle,
    onExport: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capture by remember { mutableStateOf<CaptureResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    var transfer by remember { mutableStateOf("") }
    // Read so this screen recomposes while the engine screen keeps sampling.
    val revision = Diagnostics.revision
    val buildReport: () -> String = {
        Diagnostics.report(context, doc, handle, checked.sorted().map { DeviceChecklist[it] })
    }
    // Read once: it only changes when the process has already died, which reloads this screen.
    val crash = remember { Diagnostics.lastCrash(context) }

    val gemBytes = remember(context) { readAssetSize(context, ModelAsset.Gem.path) }
    val ringBytes = remember(context) { readAssetSize(context, ModelAsset.Ring.path) }
    val icons = IconCatalog.state
    LaunchedEffect(Unit) { IconCatalog.load(context) }
    val view = handle.view
    val surfaceValid = view?.holder?.surface?.isValid == true
    val captureResult = capture
    val captureColor = when {
        captureResult == null -> Dim
        !captureResult.success -> Clay
        captureResult.litSamples == 0 -> Clay
        else -> Mint
    }
    val device = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).background(Panel).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("DIAGNOSTICS", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                    Text("LOCAL DEVICE / 3D + EXPORT", color = Dim, fontSize = 7.sp, letterSpacing = 1.2.sp)
                }
                Spacer(Modifier.weight(1f))
                DiagAction("CLOSE", onClick = onClose)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiagAction(
                        label = if (Diagnostics.recording) "STOP" else "REC",
                        active = Diagnostics.recording,
                        onClick = {
                            if (Diagnostics.recording) {
                                Diagnostics.stop(doc)
                            } else {
                                Diagnostics.start(doc, context)
                                transfer = ""
                            }
                        },
                    )
                    DiagAction(
                        label = "COPY",
                        enabled = Diagnostics.count > 0,
                        onClick = {
                            val text = buildReport()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ADD diagnostics", text))
                            transfer = "COPIED · ${text.length} CHARS · PASTE IT INTO THE CHAT"
                        },
                    )
                    DiagAction(
                        label = "SHARE",
                        enabled = Diagnostics.count > 0,
                        onClick = {
                            val text = buildReport()
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(
                                Intent.createChooser(send, "Send ADD diagnostics")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            transfer = "SHARED · ${text.length} CHARS"
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${Diagnostics.count} LINES",
                        color = if (Diagnostics.count > 0) Mint else Dim,
                        fontSize = 6.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiagAction(
                        label = if (testing) "CAPTURING" else "TEST 3D",
                        active = true,
                        enabled = !testing && view != null,
                        onClick = {
                            testing = true
                            capture = null
                            scope.launch {
                                capture = runCatching { Exporter.testCapture(view) }.getOrElse { error ->
                                    CaptureResult(
                                        success = false,
                                        width = 0,
                                        height = 0,
                                        litSamples = 0,
                                        totalSamples = 0,
                                        message = "DIAGNOSTIC ERROR · ${error.message ?: "unknown"}",
                                    )
                                }
                                testing = false
                            }
                        },
                    )
                    DiagAction(
                        label = if (doc.exporting) "EXPORTING" else "EXPORT PNG",
                        enabled = !doc.exporting,
                        onClick = onExport,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("NO ACCOUNT", color = Dim, fontSize = 6.sp, letterSpacing = 0.8.sp)
                }
                if (transfer.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(transfer, color = Mint, fontSize = 6.sp, letterSpacing = 0.4.sp)
                }

                DiagSection("DEVICE / RENDERER")
                DiagnosticRow("SCENEVIEW", if (view == null) "NOT ATTACHED" else "ATTACHED", if (view == null) Clay else Mint)
                DiagnosticRow("SURFACE", if (surfaceValid) "VALID" else "NOT VALID", if (surfaceValid) Mint else Clay)
                DiagnosticRow("VIEW SIZE", if (handle.width > 0) "${handle.width} × ${handle.height}" else "NOT LAID OUT")
                DiagnosticRow("MODEL", if (handle.modelLoaded) "LOADED" else "NOT LOADED", if (handle.modelLoaded) Mint else Clay)
                ModelAsset.entries.forEach { asset ->
                    val bytes = if (asset == ModelAsset.Gem) gemBytes else ringBytes
                    DiagnosticRow(
                        asset.label.uppercase(),
                        bytes?.let { String.format(Locale.US, "%,d B · %s", it, asset.path) }
                            ?: "MISSING · ${asset.path}",
                        if (bytes == null) Clay else Cream,
                    )
                }
                handle.error?.let { DiagnosticRow("ERROR", it, Clay) }
                DiagnosticRow("RENDERER", "SceneView 2.3.0 / Filament 1.56")
                DiagnosticRow("DEVICE", device.ifBlank { "unknown" })
                DiagnosticRow("ANDROID", "API ${Build.VERSION.SDK_INT} · ${Build.VERSION.RELEASE} · $abi")
                DiagnosticRow("LAST CRASH", crash ?: "NONE", if (crash == null) Dim else Clay)

                DiagSection("RECORDED INTERACTION", "${Diagnostics.count} LINES")
                Text(
                    "Press REC, CLOSE this screen, then do the thing you want looked at. The sampler writes a line only when a value moved: selection, tool, every layer's position, size, rotation and unit scale, zoom, and layers added or removed. A shape gesture is one line rather than one per sample, naming its mode, the handle it took, its travel and the geometry it changed; a tap that changes nothing writes a line naming what it hit. Zoom lines name the control that moved it, frames are sampled every two seconds, and a crash while recording is written to a file the next session shows. A dial writes one line when it is let go, with the value it started from, and an export records its pixels, its bytes and how many 3D views reached the file. Come back here to STOP and SHARE.",
                    color = Dim,
                    fontSize = 8.sp,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    Diagnostics.status,
                    color = if (Diagnostics.recording) Lime else Dim,
                    fontSize = 7.sp,
                    letterSpacing = 0.4.sp,
                )
                Spacer(Modifier.height(4.dp))
                val tail = Diagnostics.lines().takeLast(14)
                if (tail.isEmpty()) {
                    Text(
                        "Nothing recorded yet. Tap REC, close this screen, do the thing, then come back and SHARE.",
                        color = Dim,
                        fontSize = 8.sp,
                        lineHeight = 12.sp,
                    )
                } else {
                    tail.forEach { line ->
                        Text(
                            line,
                            color = Cream,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp,
                        )
                    }
                }
                if (transfer.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(transfer, color = Mint, fontSize = 7.sp)
                }

                DiagSection("ICON CATALOG")
                when (val catalog = icons) {
                    is IconCatalogState.Ready -> {
                        DiagnosticRow("GLYPHS", "${catalog.total} · ${catalog.brands.size} BRANDS", Mint)
                        DiagnosticRow("SEARCH", "${catalog.entries.size} INDEXED GLYPHS", Cream)
                        catalog.brands.forEach { brand ->
                            DiagnosticRow(
                                brand.label.uppercase(),
                                "${brand.count} · ${brand.licence} · ${brand.categories.size} CATEGORIES",
                                Cream,
                            )
                        }
                        val samples = catalog.brands.mapNotNull { brand ->
                            catalog.entries.firstOrNull { it.brandSlug == brand.slug }
                                ?.let { catalog.glyphs[it.id] }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            samples.forEach { glyph ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconThumb(glyph, 0xFFF2EEE3.toInt(), Modifier.size(26.dp))
                                    Text(glyph.brandSlug.uppercase(), color = Dim, fontSize = 5.sp)
                                }
                            }
                        }
                    }
                    is IconCatalogState.Failed -> DiagnosticRow("CATALOG", catalog.message, Clay)
                    else -> DiagnosticRow("CATALOG", "PARSING BRAND FILES…", Dim)
                }

                DiagSection("CAPTURE TEST")
                Text(
                    "PixelCopy reads the live SceneView without saving a file. Wait until the gem is visible, then run the test. A black frame means the 3D surface has not rendered yet.",
                    color = Dim,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                DiagnosticRow("LAST TEST", captureResult?.message ?: "NOT RUN", captureColor)
                if (captureResult != null) {
                    DiagnosticRow(
                        "CAPTURE",
                        "${captureResult.width} × ${captureResult.height} · ${captureResult.litSamples}/${captureResult.totalSamples} lit",
                        captureColor,
                    )
                }
                DiagnosticRow(
                    "LAST EXPORT",
                    if (doc.exporting) "EXPORTING…" else doc.status,
                    if (doc.status.contains("FAILED") || doc.status.contains("ERROR") || doc.status.contains("UNAVAILABLE")) Clay else Cream,
                )

                DiagSection("MANUAL CHECKLIST", "${checked.size}/${DeviceChecklist.size}")
                DeviceChecklist.forEachIndexed { index, item ->
                    ChecklistRow(
                        text = item,
                        checked = index in checked,
                        onToggle = {
                            checked = if (index in checked) checked - index else checked + index
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "This checklist is local to this screen. It records your observation; it does not prove rendering by itself.",
                    color = Dim,
                    fontSize = 8.sp,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

        }
    }
}

private fun readAssetSize(context: Context, path: String): Long? = runCatching {
    context.assets.openFd(path).use { it.length }
}.getOrElse {
    runCatching {
        context.assets.open(path).use { it.readBytes().size.toLong() }
    }.getOrNull()
}

@Composable
private fun DiagSection(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Mint, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(it, color = Lime, fontSize = 8.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, color: Color = Cream) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = Dim,
            fontSize = 8.sp,
            letterSpacing = 0.4.sp,
            modifier = Modifier.width(86.dp),
        )
        Text(
            value,
            color = color,
            fontSize = 8.sp,
            lineHeight = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChecklistRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .background(if (checked) Mint else Color.Transparent, RoundedCornerShape(3.dp))
                .border(1.dp, if (checked) Mint else Rule, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Box(Modifier.size(7.dp).background(Ink, RoundedCornerShape(1.dp)))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            color = if (checked) Cream else Dim,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiagAction(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> PanelRaised.copy(alpha = 0.4f)
        active -> Mint
        else -> PanelRaised
    }
    val foreground = when {
        !enabled -> Dim.copy(alpha = 0.45f)
        active -> Ink
        else -> Cream
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}
