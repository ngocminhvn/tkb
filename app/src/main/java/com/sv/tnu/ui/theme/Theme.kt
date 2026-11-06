package com.sv.tnu.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sv.tnu.ui.glass.LiquidGlassTheme

private val DarkBackground = Color(0xFF0F1115)
private val DarkSurface = Color(0xFF16191F)
private val DarkSurfaceVariant = Color(0xFF1F242D)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentGreen = Color(0xFF30D158)

private val LightBackground = Color(0xFFF2F4F8)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE5E9F0)

@Composable
fun TkbTheme(
    darkTheme: Boolean = false, // Mặc định Light mode
    dynamicColor: Boolean = false, // Tắt hoàn toàn monet, giữ màu gốc của ứng dụng
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = AccentBlue,
            secondary = AccentGreen,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onBackground = Color(0xFFF0F2F5),
            onSurface = Color(0xFFF0F2F5),
            outline = Color(0xFF2C323E),
            errorContainer = Color(0xFF5C1B1B),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = AccentBlue,
            secondary = AccentGreen,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = Color(0xFF111418),
            onSurface = Color(0xFF111418),
            outline = Color(0xFFD0D7E2),
            errorContainer = Color(0xFFFFEBEE),
            onErrorContainer = Color(0xFFC62828),
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
    ) {
        LiquidGlassTheme(
            dark = darkTheme,
            background = colorScheme.background,
            content = content,
        )
    }
}
