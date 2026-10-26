package com.expensemanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expensemanager.app.data.ThemeMode

/** Semantic colours not covered by Material's scheme. */
data class ExtraColors(val income: Color, val expense: Color, val card: Color, val subtle: Color)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF4ADE80), Color(0xFFFF6B6B), Color(0xFF1A1D24), Color(0xFF8A8F9C))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B7CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E2754),
    onPrimaryContainer = Color(0xFFE2DDFF),
    secondary = Color(0xFF4ADE80),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1F232C),
    onSurfaceVariant = Color(0xFF9BA1AE),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1F232C),
    surfaceContainerHighest = Color(0xFF262A34),
    surfaceContainerLow = Color(0xFF14171D),
    outline = Color(0xFF353A46),
    outlineVariant = Color(0xFF2A2E38),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF16A34A),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceVariant = Color(0xFFEDEEF4),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF0F5),
    surfaceContainerHighest = Color(0xFFE6E7EE),
    surfaceContainerLow = Color(0xFFFFFFFF),
    outline = Color(0xFFCDD0DA),
    outlineVariant = Color(0xFFE2E4EB),
    error = Color(0xFFDC2626),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val extra = if (dark) {
        ExtraColors(Color(0xFF4ADE80), Color(0xFFFF6B6B), Color(0xFF171A21), Color(0xFF8A8F9C))
    } else {
        ExtraColors(Color(0xFF16A34A), Color(0xFFDC2626), Color(0xFFFFFFFF), Color(0xFF6B7280))
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}

val extraColors: ExtraColors
    @Composable get() = LocalExtraColors.current
