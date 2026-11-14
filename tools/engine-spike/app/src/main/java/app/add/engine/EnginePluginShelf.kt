package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val ordered = (recentIds.mapNotNull { id -> plugins.firstOrNull { it.id == id } } +
        plugins.filter { it.id !in recentIds }).distinctBy { it.id }
    EngineSheet(
        title = "Plugins",
        accessibilityTitle = "Plugins",
        onDismiss = onClose,
        modifier = modifier.testTag("bmo-plugin-shelf"),
    ) {
        Text(
            "Compiled-in, offline, one at a time.",
            color = Dim,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ordered.forEach { plugin ->
                val running = plugin.id == activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PanelRaised)
                        .clickable { if (running) onOpen(plugin.id) else onRun(plugin.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("bmo-plugin-${plugin.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(plugin.glyph, color = Mint, fontSize = 18.sp)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(plugin.name, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (running) {
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Text("RUNNING", color = Mint, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(plugin.summary, color = Dim, fontSize = 9.5.sp, maxLines = 2)
                    }
                    Text(
                        if (running) "OPEN" else "RUN",
                        color = if (running) Mint else Lime,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
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
