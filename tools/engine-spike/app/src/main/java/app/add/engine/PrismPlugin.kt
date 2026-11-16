package app.add.engine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun prismPresetTag(name: String): String =
    "prism-preset-" + name.lowercase().replace(' ', '-')

/**
 * A curated field-gradient plugin. It deliberately reuses the native FieldSpec
 * recipe and renderer instead of adding a second gradient engine: the preview and
 * the artboard/export path therefore agree, and every change is undoable through
 * the same document history as the inspector.
 */
object PrismPlugin : EnginePlugin {
    override val id = "prism"
    override val name = "Prism"
    override val glyph = "◈"
    override val summary = "Flow-gradient presets for the artboard or selected layer."
    override val commands = listOf(
        EnginePluginCommand("prism.open", "Open Prism", "gradient field artboard layer"),
    )

    @Composable
    override fun Panel(scope: EnginePluginScope) {
        val layerAvailable = scope.effects.canTarget(EngineEffectTarget.Layer)
        var targetIndex by rememberSaveable { mutableIntStateOf(0) }
        val target = if (targetIndex == 1 && layerAvailable) {
            EngineEffectTarget.Layer
        } else {
            EngineEffectTarget.Artboard
        }
        val current = scope.effects.fieldFor(target)?.normalized()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PRISM",
                    color = Mint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "FIELD GRADIENT",
                    color = Dim,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (layerAvailable) {
                    "Pick a recipe. The artboard or the selected layer changes in one step."
                } else {
                    "Artboard recipes are ready. Select type or a shape to target a layer."
                },
                color = Dim,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))

            EngineSegmentedControl(
                options = listOf(
                    EngineSegment(
                        label = "ARTBOARD",
                        accessibilityLabel = "Artboard effect target",
                        tag = "prism-target-artboard",
                    ),
                    EngineSegment(
                        label = "LAYER",
                        accessibilityLabel = "Selected layer effect target",
                        tag = "prism-target-layer",
                        enabled = layerAvailable,
                    ),
                ),
                selectedIndex = if (target == EngineEffectTarget.Artboard) 0 else 1,
                onSelect = { targetIndex = it },
                description = "Prism effect target",
            )
            Spacer(Modifier.height(10.dp))

            FieldPresets.all.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { preset ->
                        PrismPresetTile(
                            preset = preset,
                            selected = current == preset.field.normalized(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (scope.effects.applyFieldPreset(target, preset.field)) {
                                    scope.toast("PRISM · ${preset.name.uppercase()} · ${target.label}")
                                }
                            },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "TAP A PRESET TO APPLY · ONE UNDO STEP",
                color = Dim,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            val canClear = current != null
            TactileBox(
                onClick = {
                    if (scope.effects.clearField(target)) scope.toast("PRISM · CLEARED")
                },
                enabled = canClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = EngineTokens.MinTarget)
                    .testTag("prism-clear")
                    .semantics {
                        contentDescription = "Clear ${target.label.lowercase()} field"
                        role = Role.Button
                    },
                background = PanelRaised,
            ) {
                Text(
                    "CLEAR FIELD",
                    color = if (canClear) Cream else Dim.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun PrismPresetTile(
    preset: FieldPresets.Preset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // The thumbnail is rasterized once per recipe and retained by the renderer's
    // cache; it is never regenerated on every recomposition/frame.
    val preview = remember(preset.field) {
        FieldRenderer.bitmap(preset.field, 112, 62).asImageBitmap()
    }
    TactileBox(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = EngineTokens.MinTarget)
            .testTag(prismPresetTag(preset.name))
            .semantics {
                contentDescription = "Apply ${preset.name} gradient"
                role = Role.Button
                this.selected = selected
            },
        background = if (selected) MintWash else PanelRaised,
        border = BorderStroke(1.dp, if (selected) Mint else Rule),
        contentPadding = PaddingValues(4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                preset.name.uppercase(),
                color = if (selected) Cream else Dim,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selected) "SELECTED" else "APPLY",
                color = if (selected) Mint else Dim.copy(alpha = 0.7f),
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
