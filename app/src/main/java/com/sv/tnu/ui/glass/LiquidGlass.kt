package com.sv.tnu.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Hỗ trợ cử chỉ bấm giữ và kéo vuốt lăng kính (hold & drag liquid lens) mượt mà,
 * đàn hồi nảy spring theo đúng thiết kế của thư viện liquidglass.
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
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop
    val coroutineScope = rememberCoroutineScope()

    val currentItemCount by rememberUpdatedState(itemCount)
    val currentOnSelected by rememberUpdatedState(onSelected)

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
                color = if (isDark) Color(0xFF1E222B) else Color(0xFFFFFFFF),
                shape = CapsuleShape,
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF333A48) else Color(0xFFE2E8F0),
                ),
                CapsuleShape,
            )
            .clip(CapsuleShape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / itemCount
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val currentTabWidthPx by rememberUpdatedState(tabWidthPx)

        // Vị trí lens trượt theo tab
        val lensOffsetAnim = remember { Animatable(selectedIndex * tabWidthPx) }

        // Trạng thái bấm giữ / kéo vuốt
        var isHolding by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var activePreviewIndex by remember { mutableIntStateOf(selectedIndex) }

        // Hiệu ứng phình to / dãn nở đàn hồi khi bấm giữ (liquid swelling)
        val lensScaleX by animateFloatAsState(
            targetValue = if (isHolding) 1.15f else 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "lensScaleX",
        )
        val lensScaleY by animateFloatAsState(
            targetValue = if (isHolding) 1.08f else 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "lensScaleY",
        )
        val lensElevation by animateFloatAsState(
            targetValue = if (isHolding) 8f else 4f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            label = "lensElevation",
        )

        // Cập nhật vị trí khi selectedIndex thay đổi từ bên ngoài (khi không trong gesture)
        LaunchedEffect(selectedIndex, tabWidthPx) {
            if (!isDragging && !isHolding && tabWidthPx > 0f) {
                lensOffsetAnim.animateTo(
                    targetValue = selectedIndex * tabWidthPx,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = 450f,
                    ),
                )
                activePreviewIndex = selectedIndex
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val widthPx = currentTabWidthPx.takeIf { it > 0f } ?: return@awaitEachGesture
                            val count = currentItemCount
                            val downX = down.position.x
                            var currentFingerX = downX
                            var hasMoved = false

                            val downTab = (downX / widthPx).toInt().coerceIn(0, count - 1)
                            // Phản hồi thị giác đổi màu tab ngay khi chạm xuống
                            activePreviewIndex = downTab

                            // Bấm giữ (hold threshold) làm phình to viên lens sau 200ms
                            val holdJob = launch {
                                delay(200)
                                isHolding = true
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        break
                                    }

                                    val newX = change.position.x
                                    if (!hasMoved && abs(newX - downX) > touchSlop) {
                                        hasMoved = true
                                        isDragging = true
                                        isHolding = true
                                        holdJob.cancel()
                                    }

                                    if (hasMoved) {
                                        change.consume()
                                        currentFingerX = newX
                                        val maxOffset = ((count - 1) * widthPx).coerceAtLeast(0f)
                                        val targetLensX = (currentFingerX - widthPx / 2f).coerceIn(0f, maxOffset)

                                        launch {
                                            lensOffsetAnim.snapTo(targetLensX)
                                        }

                                        val currentTab = (currentFingerX / widthPx)
                                            .toInt()
                                            .coerceIn(0, count - 1)
                                        activePreviewIndex = currentTab
                                    }
                                }
                            } finally {
                                holdJob.cancel()
                                isDragging = false
                                isHolding = false

                                // Tính tab đích khi thả ngón tay (Tap nhạy ngay lập tức bằng downTab)
                                val finalTab = if (hasMoved) {
                                    val centreX = lensOffsetAnim.value + widthPx / 2f
                                    (centreX / widthPx)
                                        .roundToInt()
                                        .coerceIn(0, count - 1)
                                } else {
                                    downTab
                                }

                                activePreviewIndex = finalTab
                                currentOnSelected(finalTab)

                                launch {
                                    lensOffsetAnim.animateTo(
                                        targetValue = finalTab * widthPx,
                                        animationSpec = spring(
                                            dampingRatio = 0.65f,
                                            stiffness = 420f,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
        ) {
            // Lớp Liquid Lens (viên ngọc kính trượt nổi bật)
            Box(
                modifier = Modifier
                    .offset {
                        val x = lensOffsetAnim.value
                        IntOffset(if (x.isFinite()) x.roundToInt() else 0, 0)
                    }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = lensScaleX
                        scaleY = lensScaleY
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .shadow(
                        elevation = lensElevation.dp,
                        shape = CapsuleShape,
                        spotColor = if (isDark) Color.Black.copy(alpha = if (isHolding) 0.6f else 0.4f) else Color(0x25000000),
                    )
                    .background(
                        if (isDark) {
                            if (isHolding) Color(0xFF384357) else Color(0xFF2E3646)
                        } else {
                            if (isHolding) Color(0xFFDCE6F5) else Color(0xFFE8EEF8)
                        },
                        CapsuleShape,
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isDark) {
                                if (isHolding) Color(0xFF5A6987) else Color(0xFF434E64)
                            } else {
                                if (isHolding) Color(0xFFBACCE4) else Color(0xFFD0DBEC)
                            },
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
                    val isSelected = if (isDragging || isHolding) {
                        index == activePreviewIndex
                    } else {
                        index == selectedIndex
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CapsuleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        item(index, isSelected)
                    }
                }
            }
        }
    }
}
