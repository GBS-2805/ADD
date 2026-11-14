package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TONE_SWATCHES = listOf("#5B2A4B", "#6D3558", "#1B1212", "#F5ECD8", "#8A3B00")

fun toneEnabled(doc: EngineDocument): Boolean = doc.selected is VectorLayer || doc.selected is TextLayer

fun applyTone(doc: EngineDocument, hex: String): Boolean {
    if (!toneEnabled(doc)) return false
    doc.setFillHex(hex)
    return true
}

object TonePlugin : EnginePlugin {
    override val id = "tone"
    override val name = "Tone"
    override val glyph = "◐"
    override val summary = "Apply a paper-safe fill to the selected type or shape."
    override val commands = listOf(
        EnginePluginCommand("tone.open", "Open Tone", "colour fill swatch"),
    )

    @Composable
    override fun Panel(scope: EnginePluginScope) {
        val enabled = toneEnabled(scope.doc)
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("TONE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                if (enabled) "A fill for the selected layer." else "Select type or a shape first.",
                color = if (enabled) Cream else Dim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TONE_SWATCHES.forEach { hex ->
                    val colour = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colour)
                            .clickable(enabled = enabled) {
                                if (applyTone(scope.doc, hex)) scope.toast("Tone applied")
                            }
                            .testTag("tone-$hex"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            hex.removePrefix("#").take(2),
                            color = if (colour.luminance() > 0.5f) Color.Black else Color.White,
                            fontSize = 8.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TactileBox(
                    onClick = scope.hide,
                    shape = RoundedCornerShape(12.dp),
                    background = PanelRaised,
                    modifier = Modifier.height(42.dp).testTag("tone-hide"),
                ) { Text("HIDE", color = Cream, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                TactileBox(
                    onClick = scope.close,
                    shape = RoundedCornerShape(12.dp),
                    background = Lime,
                    modifier = Modifier.height(42.dp).testTag("tone-close"),
                ) { Text("CLOSE", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
