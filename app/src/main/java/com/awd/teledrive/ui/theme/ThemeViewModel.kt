package com.awd.teledrive.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val secureSettings: SecureSettings,
) : ViewModel() {
    val isDarkMode: StateFlow<Boolean?> = secureSettings.themeModeFlow
    val themeColor: StateFlow<Color> = secureSettings.themeColorFlow

    fun setDarkMode(enabled: Boolean?) {
        secureSettings.setThemeMode(enabled)
    }

    fun setThemeColor(color: Color) {
        secureSettings.setThemeColor(color)
    }
}
