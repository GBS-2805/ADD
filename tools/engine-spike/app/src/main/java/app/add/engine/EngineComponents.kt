package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Floating chrome surface: elevation and a border, never a full-width band. */
@Composable
internal fun EngineFloatingToolbar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    docked: Boolean = false,
    focusRequester: FocusRequester? = null,
    nextFocus: FocusRequester? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .focusProperties { if (nextFocus != null) next = nextFocus }
            .shadow(if (docked) 0.dp else 12.dp, shape, clip = false)
            .clip(shape)
            .background(StudioSurface.copy(alpha = 0.97f))
            .border(1.dp, StudioHairline, shape)
            .heightIn(min = EngineTokens.MinTarget + 6.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The one bottom-sheet surface. The sheet's visible header carries its title and close
 * action; this wrapper keeps those semantics on the surface itself so the whole sheet
 * is announced and can be dismissed from an accessibility action.
 */
@Composable
internal fun EngineSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    accessibilityTitle: String = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .clip(EngineTokens.SheetShape)
            .background(Forest)
            .border(1.dp, Cream.copy(alpha = 0.16f), EngineTokens.SheetShape)
            .semantics {
                contentDescription = "$accessibilityTitle sheet"
                customActions = listOf(
                    CustomAccessibilityAction("Dismiss $accessibilityTitle") {
                        onDismiss()
                        true
                    },
                )
            }
            .testTag("inspector-sheet"),
        content = content,
    )
}

/** Main action plus a small menu: export opens the dialog, the menu exports at a scale. */
@Composable
internal fun EngineSplitButton(
    label: String,
    onPrimary: () -> Unit,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = EngineTokens.MinTarget)
            .clip(RoundedCornerShape(99.dp))
            .background(if (enabled) Lime else Lime.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .heightIn(min = EngineTokens.MinTarget)
                .clickable(enabled = enabled, onClick = onPrimary)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }
                .padding(horizontal = 11.dp)
                .testTag("split-export-main"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (enabled) Ink else Ink.copy(alpha = 0.5f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Cream.copy(alpha = 0.2f)))
        Box {
            Box(
                modifier = Modifier
                    .size(EngineTokens.MinTarget)
                    .clickable(enabled = enabled) { menu = true }
                    .semantics {
                        contentDescription = "$label options"
                        role = Role.Button
                    }
                    .testTag("split-export-options"),
                contentAlignment = Alignment.Center,
            ) {
                Text("▾", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { (text, action) ->
                    DropdownMenuItem(
                        onClick = {
                            menu = false
                            action()
                        },
                    ) {
                        Text(text, color = Cream, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

internal data class EngineSegment(
    val label: String,
    val accessibilityLabel: String = label,
    val tag: String? = null,
    val enabled: Boolean = true,
)

/**
 * Pajamas segmented control: two to five equal options, one active, every option
 * labelled, disabled options kept visible, and the selection exposed as semantics.
 */
@Composable
internal fun EngineSegmentedControl(
    options: List<EngineSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    compact: Boolean = false,
) {
    require(options.size in 2..5) { "a segmented control holds 2–5 options" }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(Panel)
            .padding(3.dp)
            .semantics {
                if (description != null) contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = EngineTokens.MinTarget)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (active) Cream else Color.Transparent)
                    .clickable(enabled = option.enabled) { onSelect(index) }
                    .semantics {
                        selected = active
                        role = Role.RadioButton
                        contentDescription = option.accessibilityLabel
                    }
                    .testTag(option.tag ?: "segment-${option.label.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    color = when {
                        !option.enabled -> Dim.copy(alpha = 0.35f)
                        active -> Ink
                        else -> Cream.copy(alpha = 0.72f)
                    },
                    fontSize = if (compact) 9.5.sp else 10.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
