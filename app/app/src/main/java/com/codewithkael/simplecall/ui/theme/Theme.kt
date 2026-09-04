package com.codewithkael.simplecall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * antAI theme. Dynamic color is OFF on purpose: the brand identity (calm teal
 * guardian) must be consistent on every device instead of adopting the user's
 * wallpaper colors. Light-first, with a dark scheme for system dark mode.
 */

private val LightColors = lightColorScheme(
    primary = AntaiTeal,
    onPrimary = Color.White,
    primaryContainer = AntaiTealContainer,
    onPrimaryContainer = AntaiOnTealContainer,
    secondary = AntaiGreen,
    onSecondary = Color.White,
    secondaryContainer = AntaiGreenContainer,
    onSecondaryContainer = AntaiOnGreenContainer,
    tertiary = AntaiTealDark,
    onTertiary = Color.White,
    background = AntaiBackground,
    onBackground = AntaiInk,
    surface = AntaiSurface,
    onSurface = AntaiInk,
    surfaceVariant = AntaiSurfaceVariant,
    onSurfaceVariant = AntaiMuted,
    outline = AntaiHairline,
    outlineVariant = AntaiHairline,
    error = RiskCritical,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = AntaiTealLight,
    onPrimary = Color(0xFF00201F),
    primaryContainer = AntaiTealDark,
    onPrimaryContainer = AntaiTealContainer,
    secondary = AntaiGreen,
    onSecondary = Color.White,
    secondaryContainer = AntaiOnGreenContainer,
    onSecondaryContainer = AntaiGreenContainer,
    tertiary = AntaiTealLight,
    onTertiary = Color(0xFF00201F),
    background = AntaiInkDark,
    onBackground = AntaiOnDark,
    surface = AntaiSurfaceDark,
    onSurface = AntaiOnDark,
    surfaceVariant = AntaiSurfaceVariantDark,
    onSurfaceVariant = AntaiMutedDark,
    outline = AntaiHairlineDark,
    outlineVariant = AntaiHairlineDark,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0A08),
)

// Rounded, friendly geometry (WhatsApp-ish) shared across cards, sheets, chips.
private val AntaiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SimpleCallTheme(
    darkTheme: Boolean = false,           // light-first; call screen supplies its own dark overlays
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AntaiShapes,
        content = content
    )
}
