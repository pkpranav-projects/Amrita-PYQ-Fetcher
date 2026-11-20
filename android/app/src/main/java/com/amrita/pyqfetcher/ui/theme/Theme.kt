package com.amrita.pyqfetcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amrita.pyqfetcher.data.prefs.ThemeMode

// Palette ported from the CLI (configs.go): mint primary, emerald success, amber warning.
val CyberMint = Color(0xFF01FAC6)
val Emerald = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFFF6B6B)

private val DarkScheme = darkColorScheme(
    primary = CyberMint,
    onPrimary = Color(0xFF00241C),
    primaryContainer = Color(0xFF0D3B32),
    onPrimaryContainer = Color(0xFFB8FFEB),
    secondary = Emerald,
    onSecondary = Color(0xFF00261A),
    secondaryContainer = Color(0xFF123A2E),
    onSecondaryContainer = Color(0xFFB5F2D6),
    tertiary = Amber,
    onTertiary = Color(0xFF2E1D00),
    background = Color(0xFF080B12),
    onBackground = Color(0xFFE7ECF2),
    surface = Color(0xFF080B12),
    onSurface = Color(0xFFE7ECF2),
    surfaceVariant = Color(0xFF161D29),
    onSurfaceVariant = Color(0xFF98A4B3),
    surfaceContainerLowest = Color(0xFF05070C),
    surfaceContainerLow = Color(0xFF0E131C),
    surfaceContainer = Color(0xFF111824),
    surfaceContainerHigh = Color(0xFF151D2A),
    surfaceContainerHighest = Color(0xFF1C2635),
    outline = Color(0xFF2E3A4B),
    outlineVariant = Color(0xFF1E2836),
    error = ErrorRed,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00806A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F7EA),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF0B7A55),
    secondaryContainer = Color(0xFFD3F5E5),
    tertiary = Color(0xFF9A5B00),
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFF6F8FB),
    surfaceVariant = Color(0xFFE6EBF1),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEDF1F5),
    outline = Color(0xFFCBD3DD),
    outlineVariant = Color(0xFFE2E7ED),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Monospace-ish style for course codes so they line up and read like codes. */
val CodeStyle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)

@Composable
fun AmritaTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides if (dark) DarkGlass else LightGlass) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
