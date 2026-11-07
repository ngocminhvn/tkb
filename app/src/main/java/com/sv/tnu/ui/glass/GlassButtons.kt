package com.sv.tnu.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reuse the app's own Liquid Glass material, rather than drawing opaque
 * Material3 icon-button surfaces on top of glass panels.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    loading: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val dark = LocalGlassConfig.current.dark
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .glassEffect(
                glass = Glass(alpha = if (dark) 0.60f else 0.78f),
                shape = CircleShape,
            )
            .clickable(
                enabled = enabled && !loading,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint.copy(alpha = if (enabled) 1f else 0.35f),
            )
        }
    }
}

/** Glass action for dialog confirmation / cancellation. */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val dark = LocalGlassConfig.current.dark
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .glassEffect(
                glass = Glass(alpha = if (dark) 0.60f else 0.78f),
                shape = CapsuleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = tint.copy(alpha = if (enabled) 1f else 0.35f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
