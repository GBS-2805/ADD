package app.add.engine

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// Playful Studio: dark green risograph ink, warm paper cards and a deep studio
// backdrop. The artboard and painter colours above stay unchanged.
val Forest = Color(0xFF101C16)
val Paper = Color(0xFFF6EEDF)
val PaperEdge = Color(0xFFE4D7C1)
val MintWash = Color(0xFFE4F5EC)
val StudioOutline = Color(0xFF17241C)
val PebbleShape = RoundedCornerShape(18.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val CardShape = RoundedCornerShape(16.dp)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
)

private val SpikeTypography = Typography(
    defaultFontFamily = Poppins,
    h1 = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    h2 = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    h3 = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    body1 = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    body2 = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    caption = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.4.sp),
    button = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

@Composable
fun EngineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = Mint,
            primaryVariant = Lime,
            secondary = Clay,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onSecondary = Ink,
            onBackground = Cream,
            onSurface = Cream,
        ),
        typography = SpikeTypography,
        content = content,
    )
}

/** Shared timing for the chrome: quick press, springy expansion, gentle sheet. */
object StudioMotion {
    const val PressScale = 0.96f
    const val SheetFraction = 0.66f
    const val StaggerMillis = 25L
}
