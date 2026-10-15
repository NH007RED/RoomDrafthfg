package com.nhdeveloper.roomdraft.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val RoomGreen = Color(0xFF0E9F4F)

private val LightColors = lightColorScheme(
    primary = RoomGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F5EA),
    onPrimaryContainer = Color(0xFF064D26),
    secondary = Color(0xFF4A6355),
    background = Color(0xFFF6F9F7),
    onBackground = Color(0xFF161C18),
    surface = Color.White,
    onSurface = Color(0xFF161C18),
    surfaceVariant = Color(0xFFEDF2EE),
    onSurfaceVariant = Color(0xFF59655E),
    outline = Color(0xFFB9C4BD),
    outlineVariant = Color(0xFFDDE5E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE90),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF0D3F26),
    onPrimaryContainer = Color(0xFFBDF2D3),
    secondary = Color(0xFFB1CCBB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE1E8E3),
    surface = Color(0xFF151B17),
    onSurface = Color(0xFFE1E8E3),
    surfaceVariant = Color(0xFF222B25),
    onSurfaceVariant = Color(0xFFA9B5AD),
    outline = Color(0xFF5B675F),
    outlineVariant = Color(0xFF2E3932)
)

@Composable
fun RoomDraftTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
