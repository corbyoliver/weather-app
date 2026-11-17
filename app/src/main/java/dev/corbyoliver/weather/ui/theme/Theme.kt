package dev.corbyoliver.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.corbyoliver.weather.R

private fun dmSans(weight: Int) = Font(
    R.font.dm_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** DM Sans (bundled variable font, SIL OFL) at the three weights the spec uses. */
val DmSans = FontFamily(dmSans(400), dmSans(500), dmSans(700))

private val Base = TextStyle(fontFamily = DmSans, color = Tokens.Text)

val AppTypography = Typography(
    displayLarge = Base.copy(fontSize = 80.sp, lineHeight = 84.sp, fontWeight = FontWeight.W500, letterSpacing = (-2).sp),
    titleLarge = Base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.W500),
    titleMedium = Base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.W500),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.W500),
    labelMedium = Base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.W500),
    labelSmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

private val Scheme = darkColorScheme(
    primary = Tokens.Accent,
    onPrimary = Tokens.Background,
    background = Tokens.Background,
    onBackground = Tokens.Text,
    surface = Tokens.Card,
    onSurface = Tokens.Text,
    surfaceVariant = Tokens.Tile,
    onSurfaceVariant = Tokens.TextMuted,
    outline = Tokens.Divider,
    tertiary = Tokens.Amber,
    onTertiary = Tokens.OnAmber,
)

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
