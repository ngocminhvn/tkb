package com.sv.tnu.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val CapsuleShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

data class GlassConfig(
    val dark: Boolean = true,
    val background: Color = Color.Black,
)

val LocalGlassConfig = compositionLocalOf { GlassConfig() }

@Composable
fun LiquidGlassTheme(
    dark: Boolean = isSystemInDarkTheme(),
    background: Color = if (dark) Color(0xFF0F1115) else Color(0xFFF2F4F8),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassConfig provides GlassConfig(dark = dark, background = background),
        content = content,
    )
}

@Stable
data class Glass(
    val tint: Color = Color.Unspecified,
    val alpha: Float = 0.82f,
    val isInteractive: Boolean = false,
) {
    fun tint(color: Color): Glass = copy(tint = color)
    fun interactive(): Glass = copy(isInteractive = true)
}

object GlassMaterialDefaults {
    val panel: Glass
        @Composable @ReadOnlyComposable
        get() {
            val isDark = LocalGlassConfig.current.dark
            return Glass(
                alpha = if (isDark) 0.85f else 0.88f,
            )
        }

    val regular: Glass
        @Composable @ReadOnlyComposable
        get() {
            val isDark = LocalGlassConfig.current.dark
            return Glass(
                alpha = if (isDark) 0.78f else 0.82f,
            )
        }

    @Composable @ReadOnlyComposable
    fun prominent(color: Color = MaterialTheme.colorScheme.primary): Glass =
        regular.copy(tint = color, alpha = 0.92f)
}

/**
 * Modifier vẽ hiệu ứng Liquid Glass với bề mặt kính mờ trong suốt,
 * viền phản chiếu ánh sáng specular đỉnh cao, không làm mờ nội dung văn bản bên trong.
 */
fun Modifier.glassEffect(
    glass: Glass = Glass(),
    shape: Shape = CapsuleShape,
): Modifier = composed {
    val config = LocalGlassConfig.current
    val isDark = config.dark

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (glass.isInteractive && isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassPressScale",
    )

    val surfaceColor = if (glass.tint != Color.Unspecified) {
        glass.tint.copy(alpha = glass.alpha)
    } else if (isDark) {
        Color(0xFF1B2028).copy(alpha = glass.alpha)
    } else {
        Color(0xFFFFFFFF).copy(alpha = glass.alpha)
    }

    val rimBrush = Brush.verticalGradient(
        0.0f to if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.80f),
        0.4f to if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.35f),
        1.0f to if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.15f),
    )

    this
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .shadow(
            elevation = if (isDark) 6.dp else 4.dp,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x18000000),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0x25000000),
        )
        .background(surfaceColor, shape)
        .border(BorderStroke(1.dp, rimBrush), shape)
        .clip(shape)
}

/**
 * Thanh điều hướng Liquid Glass dạng viên thuốc lơ lửng (Floating Glass Tab Bar)
 * Hiệu ứng lăng kính chất lỏng Liquid Lens mượt mà, chữ và icon sắc nét 100%.
 */
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
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = 20.dp,
                shape = CapsuleShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.7f) else Color(0x35000000),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0x20000000),
            )
            .background(
                color = if (isDark) Color(0xE81A1F28) else Color(0xF2F5F8FC),
                shape = CapsuleShape,
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.28f) else Color.White,
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.4f),
                        ),
                    ),
                ),
                CapsuleShape,
            )
            .clip(CapsuleShape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / itemCount
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // Vị trí lens trượt theo tab được chọn
        val lensOffsetAnim = remember { Animatable(selectedIndex * tabWidthPx) }

        LaunchedEffect(selectedIndex, tabWidthPx) {
            if (tabWidthPx > 0f) {
                lensOffsetAnim.animateTo(
                    targetValue = selectedIndex * tabWidthPx,
                    animationSpec = spring(
                        dampingRatio = 0.74f,
                        stiffness = 500f,
                    ),
                )
            }
        }

        // Lớp Liquid Lens (viên ngọc kính trượt nổi bật)
        Box(
            modifier = Modifier
                .offset {
                    val x = lensOffsetAnim.value
                    IntOffset(if (x.isFinite()) x.roundToInt() else 0, 0)
                }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CapsuleShape,
                    spotColor = if (isDark) Color(0x400A84FF) else Color(0x250A84FF),
                )
                .background(
                    if (isDark) Color(0xFF2E3544).copy(alpha = 0.90f)
                    else Color(0xFFFFFFFF).copy(alpha = 0.95f),
                    CapsuleShape,
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.40f else 0.85f),
                                Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                            ),
                        ),
                    ),
                    CapsuleShape,
                ),
        )

        // Các nút tab phân bổ đều
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (index in 0 until itemCount) {
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CapsuleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelected(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    item(index, isSelected)
                }
            }
        }
    }
}
