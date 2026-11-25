package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun orderedPluginCatalogue(
    plugins: List<EnginePlugin>,
    recentIds: List<String>,
): List<EnginePlugin> = (recentIds.mapNotNull { id -> plugins.firstOrNull { it.id == id } } +
    plugins.filter { it.id !in recentIds }).distinctBy { it.id }

internal fun filterPluginCatalogue(
    plugins: List<EnginePlugin>,
    recentIds: List<String>,
    query: String,
): List<EnginePlugin> {
    val ordered = orderedPluginCatalogue(plugins, recentIds)
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return ordered
    return ordered.filter { plugin ->
        val haystack = buildString {
            append(plugin.id)
            append(' ')
            append(plugin.name)
            append(' ')
            append(plugin.summary)
            plugin.commands.forEach { command ->
                append(' ')
                append(command.id)
                append(' ')
                append(command.label)
                append(' ')
                append(command.keywords)
            }
        }.lowercase()
        terms.all { term -> haystack.contains(term) }
    }
}

@Composable
fun EnginePluginShelf(
    plugins: List<EnginePlugin>,
    activeId: String?,
    recentIds: List<String>,
    onRun: (String) -> Unit,
    onOpen: (String) -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val ordered = filterPluginCatalogue(plugins, recentIds, query)
    EngineSheet(
        title = "Plugins",
        accessibilityTitle = "Plugins",
        onDismiss = onClose,
        modifier = modifier.testTag("bmo-plugin-shelf"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PLUGIN CATALOGUE",
                color = Mint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    query.isBlank() -> "${plugins.size} PLUGINS"
                    ordered.size == 1 -> "1 MATCH"
                    else -> "${ordered.size} MATCHES"
                },
                color = Dim,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
        Text(
            "Compiled-in, offline, one at a time.",
            color = Dim,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Cream, fontSize = 11.sp),
                cursorBrush = SolidColor(Mint),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = EngineTokens.MinTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelRaised)
                    .padding(horizontal = 12.dp)
                    .testTag("bmo-plugin-catalogue-search")
                    .semantics { contentDescription = "Search plugin catalogue" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search plugins, effects or commands",
                                color = Dim,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                TactileBox(
                    onClick = { query = "" },
                    modifier = Modifier
                        .height(EngineTokens.MinTarget)
                        .testTag("bmo-plugin-catalogue-clear")
                        .semantics {
                            contentDescription = "Clear plugin catalogue search"
                            role = Role.Button
                        },
                    background = PanelRaised,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text("CLEAR", color = Cream, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (ordered.isEmpty()) {
            Text(
                "NO PLUGINS MATCH",
                color = Dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .testTag("bmo-plugin-catalogue-empty"),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ordered.forEach { plugin ->
                    val running = plugin.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PanelRaised)
                            .clickable { if (running) onOpen(plugin.id) else onRun(plugin.id) }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .testTag("bmo-plugin-${plugin.id}")
                            .semantics {
                                contentDescription = "${if (running) "Open" else "Run"} ${plugin.name}"
                                role = Role.Button
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(plugin.glyph, color = Mint, fontSize = 16.sp)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plugin.name, color = Cream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                if (running) {
                                    Spacer(Modifier.padding(horizontal = 3.dp))
                                    Text("RUNNING", color = Mint, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(plugin.summary, color = Dim, fontSize = 8.5.sp, maxLines = 2)
                        }
                        Text(
                            if (running) "OPEN" else "RUN",
                            // Mint and Lime are both the accent after the restyle; RUN
                            // needs the neutral colour to stay distinct from OPEN.
                            color = if (running) Mint else Cream,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
            if (activeId != null) {
                Text(
                    "HIDE",
                    color = Cream,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelRaised)
                        .clickable(onClick = onHide)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("bmo-plugin-shelf-hide"),
                )
                Text(
                    "CLOSE",
                    color = Ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Lime)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("bmo-plugin-shelf-close"),
                )
            }
        }
    }
}
