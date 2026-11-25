package app.add.engine

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One source for the editor's colour, shape, target and motion tokens. The shell
 * components in EngineComponents.kt read only these values, so the editor cannot
 * accidentally fall back to a default Material palette.
 */
object EngineTokens {
    // Native studio chrome: charcoal around the bone artboard, warm vermilion
    // accent. The artwork renderer reads EngineEdition directly, so exporting a
    // page never follows the editor theme.
    val StudioInk = Color(0xFF101010)
    val StudioSurface = Color(0xFF1B1A17)
    val StudioRaised = Color(0xFF2A2722)
    val StudioText = Color(0xFFF4EDE0)
    val StudioMuted = Color(0xFF9D9486)
    val StudioAccent = Color(0xFFE8452A)
    val StudioAccentInk = Color(0xFFFFF2E8)
    val StudioWarn = Color(0xFFFF8A6A)
    val StudioHairline = Color(0x24FFFFFF)
    val StudioSelection = Color(0xFF8DB4FF)

    // Chrome roles. The old names stay for call-site compatibility.
    val Ink = StudioInk
    val Panel = StudioSurface
    val PanelRaised = StudioRaised
    val Rule = StudioHairline
    val Cream = StudioText
    val Dim = StudioMuted
    val Mint = StudioAccent
    val Lime = StudioAccent
    val Clay = StudioWarn
    val Forest = StudioSurface
    val PaperEdge = StudioRaised
    val MintWash = StudioRaised
    val StudioOutline = StudioText

    // Artwork values: these never follow the chrome theme.
    val Artboard = EngineEdition.BandInk
    val ArtboardInk = EngineEdition.Ink
    val Paper = EngineEdition.BandInk

    val MinTarget = 48.dp
    val PebbleShape = RoundedCornerShape(18.dp)
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val CardShape = RoundedCornerShape(16.dp)

    /** Chrome movement: spatial, slightly underdamped, never a bounce loop. */
    fun <T> spatial() = spring<T>(dampingRatio = 0.78f, stiffness = 380f)

    /** Opacity and colour: quick effects spring, no positional travel. */
    fun <T> effects() = spring<T>(dampingRatio = 0.9f, stiffness = 650f)
}

val Ink = EngineTokens.Ink
val Panel = EngineTokens.Panel
val PanelRaised = EngineTokens.PanelRaised
val Rule = EngineTokens.Rule
val Cream = EngineTokens.Cream
val Dim = EngineTokens.Dim
val Mint = EngineTokens.Mint
val Lime = EngineTokens.Lime
val Clay = EngineTokens.Clay
val Artboard = EngineTokens.Artboard
val ArtboardInk = EngineTokens.ArtboardInk

val Forest = EngineTokens.Forest
val Paper = EngineTokens.Paper
val PaperEdge = EngineTokens.PaperEdge
val MintWash = EngineTokens.MintWash
val StudioOutline = EngineTokens.StudioOutline
val PebbleShape = EngineTokens.PebbleShape
val SheetShape = EngineTokens.SheetShape
val CardShape = EngineTokens.CardShape

val StudioInk = EngineTokens.StudioInk
val StudioSurface = EngineTokens.StudioSurface
val StudioRaised = EngineTokens.StudioRaised
val StudioText = EngineTokens.StudioText
val StudioMuted = EngineTokens.StudioMuted
val StudioAccent = EngineTokens.StudioAccent
val StudioAccentInk = EngineTokens.StudioAccentInk
val StudioWarn = EngineTokens.StudioWarn
val StudioHairline = EngineTokens.StudioHairline
val StudioSelection = EngineTokens.StudioSelection
