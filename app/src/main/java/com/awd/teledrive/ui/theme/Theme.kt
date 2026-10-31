package com.awd.teledrive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

    val colorScheme = if (darkTheme) {
        val effectivePrimary = if (isPureBlackPrimary) Color(0xFFFFFFFF) else primaryColor
        val effectiveOnPrimary = if (isPureBlackPrimary) Color(0xFF000000) else Color.White
        val effectivePrimaryContainer = if (isPureBlackPrimary) Color(0xFF2C2C30) else primaryColor.copy(alpha = 0.3f)
        val effectiveOnPrimaryContainer = Color.White
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
            background = Color(0xFF121214),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF2B2930),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F26),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B)
        )
    } else {
        val effectivePrimaryContainer = if (isPureBlackPrimary) Color(0xFFF0F0F2) else primaryColor.copy(alpha = 0.12f)
        val effectiveOnPrimaryContainer = if (isPureBlackPrimary) Color(0xFF000000) else primaryColor
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
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainer = Color(0xFFF3EDF7),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerHighest = Color(0xFFE6E0E9)
        )
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
