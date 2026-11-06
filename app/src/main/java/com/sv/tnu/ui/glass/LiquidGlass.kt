package com.sv.tnu.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
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
    val alpha: Float = 0.55f,
    val blurRadius: Dp = 20.dp,
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
                alpha = if (isDark) 0.55f else 0.70f,
                blurRadius = 24.dp,
            )
        }

    val regular: Glass
        @Composable @ReadOnlyComposable
        get() {
            val isDark = LocalGlassConfig.current.dark
            return Glass(
                alpha = if (isDark) 0.45f else 0.65f,
                blurRadius = 16.dp,
            )
        }

    @Composable @ReadOnlyComposable
    fun prominent(color: Color = MaterialTheme.colorScheme.primary): Glass =
        regular.copy(tint = color, alpha = 0.85f)
}

/**
 * Modifier vẽ hiệu ứng Liquid Glass:
 * - Kính mờ với nền bán trong suốt và blur (RenderEffect trên API 31+)
 * - Đường viền viền sáng (rim lighting / specular border) phản chiếu ánh sáng từ trên xuống
 * - Hiệu ứng nhấn mượt mà khi interactive = true
 */
fun Modifier.glassEffect(
    glass: Glass = Glass(),
    shape: Shape = CapsuleShape,
): Modifier = composed {
    val config = LocalGlassConfig.current
    val isDark = config.dark
    val density = LocalDensity.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (glass.isInteractive && isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassPressScale",
    )

    val surfaceColor = if (glass.tint != Color.Unspecified) {
        glass.tint.copy(alpha = glass.alpha)
    } else if (isDark) {
        Color(0xFF1E222A).copy(alpha = glass.alpha)
    } else {
        Color(0xFFFFFFFF).copy(alpha = glass.alpha)
    }

    val rimBrush = Brush.verticalGradient(
        0.0f to if (isDark) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.65f),
        0.5f to if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f),
        1.0f to if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.08f),
    )

    this
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            clip = true
            this.shape = shape
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glass.blurRadius > 0.dp) {
                val blurPx = with(density) { glass.blurRadius.toPx() }
                renderEffect = RenderEffect.createBlurEffect(
                    blurPx,
                    blurPx,
                    Shader.TileMode.CLAMP,
                ).asComposeRenderEffect()
            }
        }
        .background(surfaceColor, shape)
        .border(BorderStroke(1.dp, rimBrush), shape)
}

/**
 * Thanh điều hướng Liquid Glass dạng viên thuốc lơ lửng (Floating Glass Tab Bar)
 * Tái hiện chuẩn thiết kế iOS Liquid Glass trong ảnh mẫu:
 * - Khối thanh kính lơ lửng với viền specular
 * - Lớp kính lúp "Liquid Lens" di chuyển mềm mại giữa các tab khi bấm
 * - Tự động đổi màu icon và text khi lens lướt qua
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
                elevation = 18.dp,
                shape = CapsuleShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.6f) else Color(0x33000000),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0x22000000),
            )
            .glassEffect(
                glass = Glass(
                    alpha = if (isDark) 0.62f else 0.75f,
                    blurRadius = 24.dp,
                ),
                shape = CapsuleShape,
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / itemCount
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // Vị trí lens trượt theo tab được chọn
        val lensOffsetAnim = remember { Animatable(selectedIndex * tabWidthPx) }

        LaunchedEffect(selectedIndex, tabWidthPx) {
            lensOffsetAnim.animateTo(
                targetValue = selectedIndex * tabWidthPx,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 550f,
                ),
            )
        }

        // Lớp Liquid Lens (viên ngọc kính trượt nổi bật)
        Box(
            modifier = Modifier
                .offset { IntOffset(lensOffsetAnim.value.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CapsuleShape,
                    spotColor = if (isDark) Color(0x400A84FF) else Color(0x200A84FF),
                )
                .clip(CapsuleShape)
                .background(
                    if (isDark) Color(0xFF2C313C).copy(alpha = 0.75f)
                    else Color(0xFFE2E7F0).copy(alpha = 0.85f),
                    CapsuleShape,
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.35f else 0.7f),
                                Color.White.copy(alpha = if (isDark) 0.08f else 0.15f),
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
