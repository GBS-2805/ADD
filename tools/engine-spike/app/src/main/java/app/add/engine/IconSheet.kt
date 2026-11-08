package app.add.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun IconThumb(glyph: IconGlyph, tint: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val padding = size.minDimension * 0.16f
        drawContext.canvas.nativeCanvas.apply {
            save()
            IconRenderer.draw(
                canvas = this,
                glyph = glyph,
                tint = tint,
                left = padding,
                top = padding,
                width = size.width - padding * 2f,
                height = size.height - padding * 2f,
            )
            restore()
        }
    }
}

@Composable
fun IconSheet(doc: EngineDocument, onClose: () -> Unit) {
    val context = LocalContext.current
    val state = IconCatalog.state
    var query by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) { IconCatalog.load(context) }

    val ready = state as? IconCatalogState.Ready
    val entries = remember(ready, query, brand, category) {
        ready?.let { IconCatalog.search(it, query, brand, category) }.orEmpty()
    }
    val selectedBrand = ready?.brands?.firstOrNull { it.slug == brand }

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
                    Text("ICON CATALOG", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                    Text(
                        "4 BRANDS / OFFLINE VECTOR GLYPHS",
                        color = Dim,
                        fontSize = 7.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = ready?.let { "${it.total} / ${it.entries.size}" } ?: "LOADING",
                    color = Mint,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                SheetChip("CLOSE", active = true, onClick = onClose)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))

            when (state) {
                is IconCatalogState.Failed -> {
                    Column(Modifier.padding(24.dp)) {
                        Text("CATALOG FAILED", color = Clay, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(state.message, color = Dim, fontSize = 10.sp, lineHeight = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        SheetChip("RETRY", active = true, onClick = {
                            IconCatalog.reset()
                            attempt += 1
                        })
                    }
                }
                is IconCatalogState.Idle, is IconCatalogState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("PARSING 4 BRAND FILES…", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                is IconCatalogState.Ready -> {
                    val index = state
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        SheetChip(
                            label = "ALL ${index.total}",
                            active = brand == null,
                            onClick = {
                                brand = null
                                category = null
                            },
                        )
                        index.brands.forEach { item ->
                            SheetChip(
                                label = "${item.displayName.uppercase()} ${item.count}",
                                active = brand == item.slug,
                                onClick = {
                                    brand = item.slug
                                    category = null
                                },
                            )
                        }
                    }
                    selectedBrand?.categories?.takeIf { it.isNotEmpty() }?.let { categories ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SheetChip(
                                label = "ALL CATEGORIES",
                                active = category == null,
                                onClick = { category = null },
                            )
                            categories.forEach { item ->
                                SheetChip(
                                    label = "${item.name.uppercase()} ${item.count}",
                                    active = category == item.name,
                                    onClick = { category = item.name },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(PanelRaised)
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("SEARCH", color = Dim, fontSize = 6.sp, letterSpacing = 0.6.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text("name or category", color = Dim.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Cream, fontSize = 10.sp),
                                cursorBrush = SolidColor(Mint),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = "${entries.size}",
                            color = Mint,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            val glyph = index.glyphs[entry.id] ?: return@items
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PanelRaised)
                                    .clickable {
                                        doc.addIcon(glyph)
                                        onClose()
                                    }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                IconThumb(
                                    glyph = glyph,
                                    tint = 0xFFF2EEE3.toInt(),
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = entry.name,
                                    color = Cream,
                                    fontSize = 5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = entry.brandLabel.uppercase(),
                                    color = Mint.copy(alpha = 0.75f),
                                    fontSize = 4.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "LINEICONS MIT · COOLICONS MIT · PHOSPHOR MIT · STREAMLINE CC BY 4.0",
                            color = Dim,
                            fontSize = 5.sp,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("TAP TO PLACE", color = Mint, fontSize = 6.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Mint else PanelRaised)
            .border(1.dp, if (active) Mint else Rule, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Ink else Cream,
            fontSize = 6.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
        )
    }
}
