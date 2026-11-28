package com.awd.teledrive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.toArgb

@Composable
fun TeledriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    primaryColor: Color = Color(0xFF000000),
    content: @Composable () -> Unit
) {
    val isPureBlackPrimary = primaryColor.toArgb() == Color(0xFF000000).toArgb()

    val colorScheme = remember(darkTheme, primaryColor) {
        if (darkTheme) {
            val effectivePrimary = if (isPureBlackPrimary) Color(0xFFFFFFFF) else primaryColor
            val effectiveOnPrimary = if (isPureBlackPrimary) Color(0xFF000000) else Color.White
            val effectivePrimaryContainer = if (isPureBlackPrimary) Color(0xFF273957) else primaryColor.copy(alpha = 0.3f)
            val effectiveOnPrimaryContainer = if (isPureBlackPrimary) Color(0xFFE5EDFF) else Color.White
            val effectiveSecondary = if (isPureBlackPrimary) Color(0xFFE4E4E7) else primaryColor.copy(alpha = 0.7f)

            darkColorScheme(
                primary = effectivePrimary,
                onPrimary = effectiveOnPrimary,
                primaryContainer = effectivePrimaryContainer,
                onPrimaryContainer = effectiveOnPrimaryContainer,
                secondary = effectiveSecondary,
                onSecondary = if (isPureBlackPrimary) Color(0xFF000000) else Color.White,
                tertiary = Color(0xFFFFC107), // Amber for Virtual Folders
                onTertiary = Color.Black,
                background = Color(0xFF10141B),
                onBackground = Color(0xFFE5EBF5),
                surface = Color(0xFF171D27),
                onSurface = Color(0xFFE5EBF5),
                surfaceVariant = Color(0xFF2C3545),
                onSurfaceVariant = Color(0xFFB7C4D8),
                surfaceContainerLowest = Color(0xFF0D1118),
                surfaceContainerLow = Color(0xFF191F29),
                surfaceContainer = Color(0xFF222A37),
                surfaceContainerHigh = Color(0xFF2C3545),
                surfaceContainerHighest = Color(0xFF364154)
            )
        } else {
            val effectivePrimaryContainer = if (isPureBlackPrimary) Color(0xFFDCE8FF) else primaryColor.copy(alpha = 0.12f)
            val effectiveOnPrimaryContainer = if (isPureBlackPrimary) Color(0xFF1A365D) else primaryColor
            val effectiveSecondary = if (isPureBlackPrimary) Color(0xFF262626) else primaryColor.copy(alpha = 0.8f)

            lightColorScheme(
                primary = primaryColor,
                onPrimary = Color.White,
                primaryContainer = effectivePrimaryContainer,
                onPrimaryContainer = effectiveOnPrimaryContainer,
                secondary = effectiveSecondary,
                onSecondary = Color.White,
                tertiary = Color(0xFFFFA000), // Darker Amber for light theme
                onTertiary = Color.White,
                background = Color(0xFFF8FAFF),
                onBackground = Color(0xFF171D27),
                surface = Color(0xFFF8FAFF),
                onSurface = Color(0xFF171D27),
                surfaceVariant = Color(0xFFE3EAF5),
                onSurfaceVariant = Color(0xFF46546B),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF0F4FC),
                surfaceContainer = Color(0xFFEAF0F9),
                surfaceContainerHigh = Color(0xFFE3EBF8),
                surfaceContainerHighest = Color(0xFFDCE6F5)
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
