@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sv.tnu.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.glassEffect
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.IconButton as MaterialIconButton

/**
 * Primary-based soft gradient with a blurred visual falloff, avoiding an
 * expensive, full-screen RenderEffect in the scrolling timetable.
 */
@Composable
fun GlassGradientBackground(modifier: Modifier = Modifier) {
    val ground = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val dark = LocalGlassConfig.current.dark
    Box(modifier.drawBehind {
        drawRect(ground)
        drawRect(
            brush = Brush.linearGradient(
                listOf(primary.copy(alpha = if (dark) .07f else .05f),
                    Color.Transparent, primary.copy(alpha = if (dark) .06f else .035f)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
        )
        val top = Offset(size.width * .17f, size.height * .08f)
        val r1 = size.width.coerceAtLeast(1f) * 1.2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = if (dark) .19f else .12f),
                    primary.copy(alpha = if (dark) .07f else .045f), Color.Transparent),
                center = top, radius = r1,
            ),
            radius = r1, center = top,
        )
        val bottom = Offset(size.width * .91f, size.height * .88f)
        val r2 = size.width.coerceAtLeast(1f) * 1.06f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = if (dark) .12f else .065f), Color.Transparent),
                center = bottom, radius = r2,
            ),
            radius = r2, center = bottom,
        )
    })
}

private fun buttonGlass(color: Color, enabled: Boolean): Glass {
    val glass = if (color == Color.Unspecified || color.alpha < .05f) Glass.Regular
        else Glass.Regular.tint(color.copy(alpha = if (enabled) .68f else .3f))
    return glass.interactive(enabled)
}

private fun ButtonColors.clearContainer(): ButtonColors =
    copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)

/**
 * Opt-in optical glass for summary/settings panels; retain scrolling lesson,
 * exam, transcript and question cards as lightweight Material 3.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val panel = colors.containerColor
    val glass = if (panel == Color.Unspecified || panel.alpha < .05f) Glass.Regular
        else Glass.Regular.tint(panel.copy(alpha = if (LocalGlassConfig.current.dark) .58f else .64f))
    MaterialCard(
        modifier = modifier.glassEffect(glass, shape),
        shape = shape,
        colors = colors.copy(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
        content = content,
    )
}

/** A compact glass body, centred inside any existing full-width button slot. */
private fun Modifier.compactGlassWidth(): Modifier =
    wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 272.dp)

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = buttonGlass(if (enabled) colors.containerColor else colors.disabledContainerColor, enabled)
    MaterialButton(onClick = onClick, modifier = modifier.compactGlassWidth().glassEffect(glass, shape),
        enabled = enabled, shape = shape, colors = colors.clearContainer(), elevation = null,
        border = border, contentPadding = contentPadding, interactionSource = interactionSource,
        content = content)
}

/** Preserve expressive shapes and pressedShape motion when using genuine glass. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = buttonGlass(if (enabled) colors.containerColor else colors.disabledContainerColor, enabled)
    MaterialButton(onClick = onClick, shapes = shapes,
        modifier = modifier.compactGlassWidth().glassEffect(glass, shapes.shape),
        enabled = enabled, colors = colors.clearContainer(), elevation = null,
        border = border, contentPadding = contentPadding, interactionSource = interactionSource,
        content = content)
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButton(onClick = onClick, shapes = shapes, modifier = modifier,
    enabled = enabled, colors = colors, elevation = elevation, border = border,
    contentPadding = contentPadding, interactionSource = interactionSource, content = content)

@Composable
fun GlassFilledTonalButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButton(onClick = onClick, shapes = shapes, modifier = modifier,
    enabled = enabled, colors = colors, elevation = elevation, border = border,
    contentPadding = contentPadding, interactionSource = interactionSource, content = content)

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    MaterialIconButton(onClick = onClick,
        modifier = modifier.glassEffect(Glass.Regular.interactive(enabled), CircleShape),
        enabled = enabled, colors = colors, interactionSource = interactionSource, content = content)
}
