package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EngineLayout { Compact, Expanded }

/** One breakpoint, no window-size-class dependency. */
fun engineLayout(maxWidth: Dp): EngineLayout =
    if (maxWidth >= 720.dp) EngineLayout.Expanded else EngineLayout.Compact

/**
 * The one panel container. Compact is a bottom sheet; expanded is an edge panel.
 * Both are the same content and both expose the same dismiss action.
 */
@Composable
fun EnginePanelHost(
    layout: EngineLayout,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alignment = if (layout == EngineLayout.Compact) Alignment.BottomCenter else Alignment.CenterEnd
    Box(modifier.fillMaxSize(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .then(
                    if (layout == EngineLayout.Compact) {
                        Modifier.fillMaxWidth().fillMaxHeight(0.72f)
                    } else {
                        Modifier.widthIn(min = 320.dp, max = 360.dp).fillMaxHeight()
                    },
                )
                .clip(if (layout == EngineLayout.Compact) EngineTokens.SheetShape else RoundedCornerShape(18.dp))
                .background(Forest)
                .testTag(if (layout == EngineLayout.Compact) "bmo-panel-compact" else "bmo-panel-expanded"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TactileBox(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(99.dp),
                    background = PanelRaised,
                    modifier = Modifier
                        .width(48.dp)
                        .semantics { contentDescription = "Close panel" }
                        .testTag("bmo-panel-close"),
                ) { Text("×", color = Cream, fontSize = 18.sp) }
            }
            content()
        }
    }
}
