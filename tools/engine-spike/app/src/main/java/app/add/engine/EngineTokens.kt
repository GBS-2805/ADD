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
    val Ink = Color(0xFF0C1210)
    val Panel = Color(0xFF131A16)
    val PanelRaised = Color(0xFF1A241E)
    val Rule = Color(0xFF2A3830)
    val Cream = Color(0xFFF2EEE3)
    val Dim = Color(0xFF9EADA2)
    val Mint = Color(0xFF0FBF92)
    val Lime = Color(0xFFD6E381)
    val Clay = Color(0xFFE86A4A)
    val Artboard = Color(0xFFF3EBDD)
    val ArtboardInk = Color(0xFF11231C)

    val Forest = Color(0xFF101C16)
    val Paper = Color(0xFFF6EEDF)
    val PaperEdge = Color(0xFFE4D7C1)
    val MintWash = Color(0xFFE4F5EC)
    val StudioOutline = Color(0xFF17241C)

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
