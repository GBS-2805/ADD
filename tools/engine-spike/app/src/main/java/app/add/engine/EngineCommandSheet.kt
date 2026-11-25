package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun EngineCommandSheet(
    commands: List<EngineCommand>,
    onRun: (EngineCommand) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(StudioInk.copy(alpha = 0.62f)).clickable(onClick = onClose)) {
            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Forest)
                    .clickable { }
                    .padding(10.dp)
                    .testTag("bmo-command-sheet"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTIONS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "CLOSE",
                        color = Cream,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClose)
                            .padding(7.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Cream, fontSize = 13.sp),
                    cursorBrush = SolidColor(Mint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelRaised)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("bmo-command-search")
                        .semantics { contentDescription = "Search actions" },
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search actions", color = Dim, fontSize = 11.sp)
                        inner()
                    },
                )
                Spacer(Modifier.height(6.dp))
                val filtered = filterCommands(commands, query)
                if (filtered.isEmpty()) {
                    Text("No actions match.", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        filtered.groupBy { it.group }.forEach { (group, items) ->
                            Text(group.uppercase(), color = Dim, fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                            items.forEach { command ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = command.enabled) { onRun(command) }
                                        .padding(horizontal = 7.dp, vertical = 8.dp)
                                        .testTag("bmo-command-${command.id}")
                                        .semantics {
                                            contentDescription = command.label
                                            role = Role.Button
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(command.glyph, color = if (command.enabled) Mint else Dim, fontSize = 13.sp)
                                    Text(
                                        command.label,
                                        color = if (command.enabled) Cream else Dim.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.widthIn(min = 160.dp),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text("ENTER", color = Dim.copy(alpha = 0.55f), fontSize = 7.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
