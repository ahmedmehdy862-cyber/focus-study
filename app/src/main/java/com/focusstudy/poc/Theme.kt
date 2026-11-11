package com.focusstudy.poc

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Brand palette used across the app and the launcher icon. */
object FocusColors {
    val Ink = Color(0xFF070B16)
    val Navy = Color(0xFF0B1222)
    val Surface = Color(0xFF131C31)
    val SurfaceHigh = Color(0xFF1B2540)
    val Outline = Color(0xFF334268)

    /** Body text: about 15:1 against [Ink], well past WCAG AAA. */
    val TextPrimary = Color(0xFFF6F9FF)

    /** Secondary text: about 10:1 against [Ink]; readable at small sizes. */
    val TextSecondary = Color(0xFFC3D0EA)

    /** Lowest emphasis, still about 7:1 against [Ink]. */
    val TextMuted = Color(0xFFA3B4D6)

    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF1D4ED8)
    val Violet = Color(0xFF8B5CF6)
    val Teal = Color(0xFF14B8A6)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF22C55E)
    val Rose = Color(0xFFF43F5E)

    val accents = listOf(Blue, Violet, Teal, Amber, Green)

    val background = Brush.verticalGradient(listOf(Ink, Navy, Color(0xFF0E1730)))
    val hero = Brush.linearGradient(listOf(Color(0xFF1E3FA8), Color(0xFF5B21B6)))

    /**
     * Accent colours are tuned for fills, not for small text. Blending them towards white keeps
     * the identity while lifting contrast on dark and tinted surfaces.
     */
    fun onTint(accent: Color): Color = lerp(accent, Color.White, 0.45f)
}

private fun focusScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFE6EDFF),
    secondary = FocusColors.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B1F7A),
    onSecondaryContainer = Color(0xFFF0E8FF),
    tertiary = FocusColors.Teal,
    onTertiary = Color(0xFF04211E),
    background = FocusColors.Ink,
    onBackground = FocusColors.TextPrimary,
    surface = FocusColors.Surface,
    onSurface = FocusColors.TextPrimary,
    surfaceVariant = FocusColors.SurfaceHigh,
    onSurfaceVariant = FocusColors.TextSecondary,
    outline = FocusColors.Outline,
    outlineVariant = FocusColors.Outline,
    error = FocusColors.Rose,
    onError = Color.White
)

private val FocusTypography = Typography(
    headlineSmall = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
)

private val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FocusTheme(accent: Color = FocusColors.Blue, textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(
            colorScheme = focusScheme(accent),
            typography = FocusTypography,
            shapes = FocusShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides FocusColors.TextPrimary) {
                content()
            }
        }
    }
}
