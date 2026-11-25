package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EnginePluginHost(
    plugin: EnginePlugin,
    doc: EngineDocument,
    onHide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onToast: (String) -> Unit = {},
    layout: EngineLayout = EngineLayout.Compact,
    stateHolder: SaveableStateHolder = rememberSaveableStateHolder(),
) {
    val effects = remember(doc) { EngineDocumentEffects(doc) }
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(plugin.glyph, color = StudioAccent, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("RUNNING", color = StudioAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(horizontal = 4.dp))
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
                    .testTag("bmo-plugin-hide"),
            )
            Spacer(Modifier.padding(horizontal = 2.dp))
            Text(
                "CLOSE",
                color = StudioAccentInk,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioAccent)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("bmo-plugin-close"),
            )
        }
        Spacer(Modifier.padding(top = 4.dp))
        stateHolder.SaveableStateProvider(plugin.id) {
            plugin.Panel(
                EnginePluginScope(
                    doc = doc,
                    effects = effects,
                    close = onClose,
                    hide = onHide,
                    toast = onToast,
                ),
            )
        }
    }
    if (layout == EngineLayout.Expanded) {
        EnginePanelHost(layout = layout, title = plugin.name, onDismiss = onClose) {
            content()
        }
    } else {
        androidx.compose.foundation.layout.Box(
            modifier
                .fillMaxSize()
                .testTag("bmo-plugin-host"),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
        ) {
            EngineSheet(
                title = plugin.name,
                accessibilityTitle = "${plugin.name} plugin",
                onDismiss = onClose,
            ) { content() }
        }
    }
}
