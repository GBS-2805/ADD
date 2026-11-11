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

// Colour and shape tokens live in EngineTokens.kt; these aliases keep the painter,
// inspector and chrome reading one palette while older call sites stay source-compatible.

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
