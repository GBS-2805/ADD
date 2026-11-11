package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.roundToInt

/**
 * The texture inspector is deliberately a material shelf, not a file picker:
 * every tile is generated from the seed shown here, and nothing leaves the APK.
 */
@Composable
internal fun TextureTab(
    doc: EngineDocument,
    modifier: Modifier = Modifier,
    onPaintInk: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    val active = doc.texture
    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        StudioSectionLabel("ARTBOARD TEXTURE", "10 seeded materials")
        Spacer(Modifier.height(4.dp))
        Text(
            "Algorithmic engraving, laser and print fields. The document stores a recipe, " +
                "so the same seed rebuilds the same pixels at any page size.",
            color = Dim,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(10.dp))

        doc.textureWarning?.let { warning ->
            Text(
                warning,
                color = Clay,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.testTag("texture-warning"),
            )
            Spacer(Modifier.height(8.dp))
        }

        TexturePresets.presets.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { preset ->
                    FamilyTile(
                        preset = preset,
                        selected = if (doc.inkMode) doc.inkFamily == preset.kind else active?.family == preset.kind,
                        onClick = {
                            if (doc.inkMode) doc.chooseInkFamily(preset.kind)
                            else doc.applyTexture(preset.kind, preset.seed)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
        StudioSectionLabel("MATERIAL INK", "${doc.ink.size} stroke${if (doc.ink.size == 1) "" else "s"}")
        Spacer(Modifier.height(4.dp))
        Text(
            "Paint a seeded material straight onto the page. Each stroke is a recipe and " +
                "normalized points; the document never stores a bitmap.",
            color = Dim,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TactileBox(
                onClick = {
                    val wasArmed = doc.inkMode
                    doc.toggleInkMode()
                    if (!wasArmed) onPaintInk()
                },
                shape = RoundedCornerShape(12.dp),
                background = if (doc.inkMode) Mint else PanelRaised,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (doc.inkMode) Mint else StudioOutline),
                contentPadding = PaddingValues(horizontal = 12.dp),
                haptic = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("texture-ink-arm"),
            ) {
                Text(
                    if (doc.inkMode) "STOP INK" else "PAINT INK",
                    color = if (doc.inkMode) Ink else Cream,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            if (doc.ink.isNotEmpty()) {
                TactileBox(
                    onClick = { doc.clearInk() },
                    shape = RoundedCornerShape(12.dp),
                    background = PanelRaised,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioOutline),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("texture-ink-clear"),
                ) {
                    Text("CLEAR", color = Cream, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (doc.inkMode) "Drag on the stage. Pick a family above to change the material."
            else "Arm the brush, then draw on the artboard. It writes a new document stroke.",
            color = if (doc.inkMode) Mint else Dim,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
        )
        if (doc.inkMode) {
            TextureSlider(
                label = "BRUSH WIDTH",
                value = doc.inkWidth,
                range = 0.012f..0.2f,
                display = "${(doc.inkWidth * 100).toInt()}%",
                description = "Material ink brush width",
                tag = "ink-brush-width",
                onChange = { doc.setInkBrushWidth(it) },
            )
        }

        Spacer(Modifier.height(10.dp))
        if (active == null) {
            StudioSectionLabel("PAPER", "Nothing applied")
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a family to give the paper its default seed. " +
                    "The material sits under the layers and behind the print footer.",
                color = Dim,
                fontSize = 10.sp,
                lineHeight = 13.sp,
            )
            return@Column
        }

        StudioSectionLabel("RECIPE", active.family.note)
        Spacer(Modifier.height(6.dp))
        Text(
            active.family.label + " · " + active.blend.label + " · " + active.family.id,
            color = Cream,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepButton(
                text = "−",
                description = "Previous seed",
                tag = "texture-seed-back",
                onClick = { doc.setTextureSeed(active.seed - 1) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelRaised)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text("SEED", color = Dim, fontSize = 8.sp, letterSpacing = 1.2.sp)
                Text(
                    active.seed.toString(),
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            StepButton(
                text = "+",
                description = "Next seed",
                tag = "texture-seed-next",
                onClick = { doc.setTextureSeed(active.seed + 1) },
            )
            StepButton(
                text = "NEW",
                description = "New seed",
                tag = "texture-seed-new",
                onClick = { doc.setTextureSeed(active.seed + 7_919) },
            )
        }

        Spacer(Modifier.height(10.dp))
        TextureSlider(
            label = "OPACITY",
            value = active.opacity,
            range = 0.02f..1f,
            display = "${(active.opacity * 100).roundToInt()}%",
            description = "Texture opacity",
            tag = "texture-opacity",
            onChange = { doc.setTextureOpacity(it) },
        )
        TextureSlider(
            label = "SCALE",
            value = active.scale,
            range = 0.25f..4f,
            display = String.format("%.2f×", active.scale),
            description = "Texture pattern scale",
            tag = "texture-scale",
            onChange = { doc.setTextureScale(it) },
        )

        Spacer(Modifier.height(10.dp))
        TactileBox(
            onClick = { doc.removeTexture() },
            shape = RoundedCornerShape(12.dp),
            background = PanelRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("texture-remove")
                .semantics {
                    contentDescription = "Remove artboard texture"
                    role = Role.Button
                },
        ) {
            Text(
                "REMOVE TEXTURE",
                color = Cream,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun FamilyTile(
    preset: TexturePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TactileBox(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        background = if (selected) Mint else PanelRaised,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Mint else StudioOutline,
        ),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
        haptic = true,
        modifier = modifier
            .height(48.dp)
            .testTag("texture-family-${preset.id}")
            .semantics {
                contentDescription = "Apply ${preset.name} texture"
                this.selected = selected
                role = Role.Button
            },
    ) {
        Text(
            preset.name,
            color = if (selected) Ink else Cream,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    TactileBox(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        background = PanelRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioOutline.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 7.dp),
        modifier = Modifier
            .height(48.dp)
            .width(48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text(
            text,
            color = Mint,
            fontSize = if (text.length > 1) 8.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TextureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    description: String,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Dim, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.width(58.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Mint,
                activeTrackColor = Mint,
                inactiveTrackColor = StudioOutline.copy(alpha = 0.4f),
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(tag)
                .semantics { contentDescription = description },
        )
        Text(
            display,
            color = Cream,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp),
        )
    }
}
