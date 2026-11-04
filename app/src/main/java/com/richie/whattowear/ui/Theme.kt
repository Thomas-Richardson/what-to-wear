package com.richie.whattowear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richie.whattowear.R
import com.richie.whattowear.model.Feeling
import java.util.Locale

/**
 * Richie's own branding (see the richie-branding skill on his Mac): minimal 1920s–30s Art Deco in navy,
 * burgundy, forest green, ivory and gold, set in Jost, a free font modelled on Futura. Gold is only
 * for lines and ornament on ivory, never for text, because it's too pale to read there.
 */
object Brand {
    val Navy = Color(0xFF1C2541)
    val Burgundy = Color(0xFF6D1A36)
    val Forest = Color(0xFF2E5339)
    val Ivory = Color(0xFFF6F1E3)
    val Gold = Color(0xFFB8963E)

    // Helper shades that go with the five above.
    val Slate = Color(0xFF4A5A7A)
    val Paper = Color(0xFFFFFDF7)
    val RaisedNavy = Color(0xFF26304F)
    val LightGold = Color(0xFFD9BE73)
    val LightGreen = Color(0xFFA9C9AE)
    val Mist = Color(0xFFC9CFDB)
    val LightBurgundy = Color(0xFFE3A0B4)
    val Champagne = Color(0xFFE8DDBF)
    val DarkChampagne = Color(0xFF514732)
}

val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Navy, onPrimary = Brand.Ivory,
    primaryContainer = Brand.Champagne, onPrimaryContainer = Brand.Navy,
    secondary = Brand.Burgundy, onSecondary = Brand.Ivory,
    secondaryContainer = Brand.Champagne, onSecondaryContainer = Brand.Navy,
    tertiary = Brand.Forest, onTertiary = Brand.Ivory,
    tertiaryContainer = Color(0xFFD5DFD3), onTertiaryContainer = Brand.Forest,
    background = Brand.Ivory, onBackground = Brand.Navy,
    surface = Brand.Ivory, onSurface = Brand.Navy,
    surfaceVariant = Color(0xFFECE4CF), onSurfaceVariant = Brand.Slate,
    surfaceTint = Brand.Paper,
    // Cards are paper on an ivory page; the bottom bar is a shade darker than the page.
    surfaceContainerLowest = Brand.Paper, surfaceContainerLow = Brand.Paper,
    surfaceContainer = Color(0xFFEFE8D5), surfaceContainerHigh = Brand.Paper, surfaceContainerHighest = Brand.Paper,
    outline = Brand.Gold, outlineVariant = Color(0xFFDCCB9C),
    error = Brand.Burgundy, onError = Brand.Ivory,
    errorContainer = Color(0xFFF1D9DF), onErrorContainer = Brand.Burgundy,
    inverseSurface = Brand.Navy, inverseOnSurface = Brand.Ivory, inversePrimary = Brand.LightGold,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.LightGold, onPrimary = Brand.Navy,
    primaryContainer = Brand.DarkChampagne, onPrimaryContainer = Brand.Ivory,
    secondary = Brand.LightBurgundy, onSecondary = Brand.Navy,
    secondaryContainer = Brand.DarkChampagne, onSecondaryContainer = Brand.Ivory,
    tertiary = Brand.LightGreen, onTertiary = Brand.Navy,
    tertiaryContainer = Brand.Forest, onTertiaryContainer = Brand.Ivory,
    background = Brand.Navy, onBackground = Brand.Ivory,
    surface = Brand.Navy, onSurface = Brand.Ivory,
    surfaceVariant = Color(0xFF2C3655), onSurfaceVariant = Brand.Mist,
    surfaceTint = Brand.RaisedNavy,
    surfaceContainerLowest = Color(0xFF18203A), surfaceContainerLow = Brand.RaisedNavy,
    surfaceContainer = Color(0xFF212A47), surfaceContainerHigh = Brand.RaisedNavy, surfaceContainerHighest = Brand.RaisedNavy,
    outline = Brand.Gold, outlineVariant = Brand.DarkChampagne,
    error = Brand.LightBurgundy, onError = Brand.Navy,
    errorContainer = Brand.Burgundy, onErrorContainer = Brand.Ivory,
    inverseSurface = Brand.Ivory, inverseOnSurface = Brand.Navy, inversePrimary = Brand.Navy,
)

/** Jost, bundled with the app (SIL Open Font License, in third_party/Jost-OFL.txt). Two weights only. */
val Jost = FontFamily(
    Font(R.font.jost_regular, FontWeight.Normal),
    Font(R.font.jost_medium, FontWeight.Medium),
)

private val BrandTypography: Typography = Typography().let { t ->
    fun TextStyle.jost() = copy(fontFamily = Jost, fontWeight = if ((fontWeight ?: FontWeight.Normal) > FontWeight.Normal) FontWeight.Medium else FontWeight.Normal)
    Typography(
        displayLarge = t.displayLarge.jost(), displayMedium = t.displayMedium.jost(), displaySmall = t.displaySmall.jost(),
        headlineLarge = t.headlineLarge.jost(), headlineMedium = t.headlineMedium.jost(), headlineSmall = t.headlineSmall.jost(),
        titleLarge = t.titleLarge.jost(), titleMedium = t.titleMedium.jost(), titleSmall = t.titleSmall.jost(),
        bodyLarge = t.bodyLarge.jost(), bodyMedium = t.bodyMedium.jost(), bodySmall = t.bodySmall.jost(),
        labelLarge = t.labelLarge.jost(), labelMedium = t.labelMedium.jost(), labelSmall = t.labelSmall.jost(),
    )
}

/** Small, crisp corners: Art Deco, not pills. */
private val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/** Richie's branding, light or dark to match the phone. (Before 0.11 it took the wallpaper's colours.) */
@Composable
fun WhatToWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BrandTypography,
        shapes = BrandShapes,
        content = content,
    )
}

/** The 1–5 scale runs cold to hot through the brand colours: navy, slate, forest green, gold, burgundy. */
@Composable
fun feelingColor(f: Feeling): Color = feelingDayNight(f).let { if (isSystemInDarkTheme()) it.second else it.first }

/** The same colours for light and dark (dark ones are a touch lighter, to stand out on navy). The widget uses both. */
fun feelingDayNight(f: Feeling): Pair<Color, Color> = when (f) {
    Feeling.WAY_TOO_COLD -> Brand.Navy to Color(0xFF3A4C78)
    Feeling.TOO_COLD -> Brand.Slate to Color(0xFF5E6F93)
    Feeling.JUST_RIGHT -> Brand.Forest to Brand.Forest
    Feeling.TOO_WARM -> Brand.Gold to Brand.Gold
    Feeling.TOO_HOT -> Brand.Burgundy to Color(0xFF8A2546)
}

/** Text on a [feelingColor]: navy on gold (ivory would be too faint), ivory on the rest. */
fun onFeelingColor(f: Feeling): Color = if (f == Feeling.TOO_WARM) Brand.Navy else Brand.Ivory

/** The signature divider: a thin gold line with a small gold diamond in the middle. Once or twice a screen. */
@Composable
fun DecoDivider(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.outline
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(gold))
        Box(Modifier.padding(horizontal = 6.dp).size(7.dp).rotate(45f).background(gold))
        Box(Modifier.weight(1f).height(1.dp).background(gold))
    }
}

/** A section heading in small spaced-out capitals: burgundy on ivory, light burgundy on navy. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.UK),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.9.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}
