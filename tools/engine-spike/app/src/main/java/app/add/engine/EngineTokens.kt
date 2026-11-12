package app.add.engine

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * One source for the editor's colour, shape, target and motion tokens. The shell
 * components in EngineComponents.kt read only these values, so the editor cannot
 * accidentally fall back to a default Material palette.
 */
object EngineTokens {
    // The names are the engine's old semantic roles; the values are generated from
    // the site's Review edition. Ink is still the page, Cream is still the words.
    val Ink = EngineEdition.Page
    val Panel = EngineEdition.Card
    val PanelRaised = EngineEdition.Panel
    val Rule = EngineEdition.Rule
    val Cream = EngineEdition.Ink
    val Dim = EngineEdition.Dim
    val Mint = EngineEdition.Plum
    val Lime = EngineEdition.PlumHover
    val Clay = EngineEdition.Warn
    val Artboard = EngineEdition.BandInk
    val ArtboardInk = EngineEdition.Ink

    val Forest = EngineEdition.Card
    val Paper = EngineEdition.BandInk
    val PaperEdge = EngineEdition.Panel
    val MintWash = EngineEdition.Card
    val StudioOutline = EngineEdition.Ink

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
