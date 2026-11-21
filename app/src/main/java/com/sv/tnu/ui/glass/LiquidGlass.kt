package com.sv.tnu.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.GlassTabBarStyle

data class GlassConfig(val dark: Boolean = true, val background: Color = Color.Black)
val LocalGlassConfig = compositionLocalOf { GlassConfig() }

/** Preserve TKB theme preferences while delegating AGSL refraction to the upstream renderer. */
@Composable
fun LiquidGlassTheme(
    dark: Boolean = isSystemInDarkTheme(),
    background: Color = if (dark) Color(0xFF0F1115) else Color(0xFFF2F4F8),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlassConfig provides GlassConfig(dark, background)) {
        com.wexpa.liquidglass.LiquidGlassTheme(
            dark = dark, background = background, content = content,
        )
    }
}

/** Keep the upstream Liquid Glass motion; suppress only tab-bar haptic feedback. */
@Composable
fun GlassTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalGlassConfig.current.dark,
    height: Dp = 64.dp,
    item: @Composable (index: Int, isSelected: Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalHapticFeedback provides SilentHapticFeedback) {
        com.wexpa.liquidglass.GlassTabBar(
            itemCount = itemCount, selectedIndex = selectedIndex,
            onSelected = onSelected, modifier = modifier,
            style = GlassTabBarStyle.Calm(isDark).copy(height = height),
            item = item,
        )
    }
}

private object SilentHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
