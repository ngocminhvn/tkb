package com.sv.tnu.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sv.tnu.ui.glass.LocalGlassConfig

/**
 * Shared visual language for ordinary app cards.
 * Adjust dark/light surfaces and ink here, not separately in each screen.
 * Special semantic cards (warnings, grades, answer feedback, gradients)
 * intentionally keep their own styling.
 */
object AppCardDefaults {
    enum class Tone { Standard, Muted, Raised, Selected }

    val shape = RoundedCornerShape(18.dp)
    val compactShape = RoundedCornerShape(16.dp)
    val featureShape = RoundedCornerShape(22.dp)

    @Composable
    fun colors(
        tone: Tone = Tone.Standard,
        lightContainer: Color? = null,
    ): CardColors {
        val dark = LocalGlassConfig.current.dark
        val scheme = MaterialTheme.colorScheme
        val color = if (dark) {
            when (tone) {
                Tone.Standard -> scheme.surface
                Tone.Muted -> scheme.surfaceContainerLow
                Tone.Raised -> scheme.surfaceContainerHigh
                Tone.Selected -> scheme.surfaceContainerHighest
            }
        } else {
            lightContainer ?: when (tone) {
                Tone.Standard, Tone.Raised -> Color.White
                Tone.Muted -> Color(0xFFF1F5F9)
                Tone.Selected -> Color(0xFFEAF1FF)
            }
        }
        return CardDefaults.cardColors(
            containerColor = color,
            contentColor = scheme.onSurface,
        )
    }

    @Composable
    fun border(subtle: Boolean = false): BorderStroke {
        val dark = LocalGlassConfig.current.dark
        return BorderStroke(
            1.dp,
            if (dark) Color(0xFF8DA9CA).copy(alpha = if (subtle) 0.10f else 0.16f)
            else Color.Black.copy(alpha = if (subtle) 0.06f else 0.08f),
        )
    }

    @Composable
    fun elevation(
        darkDp: Dp = 3.dp,
        lightDp: Dp = 2.dp,
    ): CardElevation =
        CardDefaults.cardElevation(
            defaultElevation = if (LocalGlassConfig.current.dark) darkDp else lightDp,
        )
}
