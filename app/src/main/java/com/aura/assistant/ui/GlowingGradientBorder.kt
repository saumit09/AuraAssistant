package com.aura.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aura.assistant.ui.theme.AuraBlue
import com.aura.assistant.ui.theme.AuraCyan
import com.aura.assistant.ui.theme.AuraMagenta
import com.aura.assistant.ui.theme.AuraViolet

/**
 * Animated glowing aurora edge lighting along the bottom of the device screen,
 * echoing Samsung One UI's Edge Lighting & Siri's multi-hue glow.
 */
@Composable
fun GlowingGradientBorder(
    modifier: Modifier = Modifier,
    heightDp: Int = 8
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BorderTransition")
    
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientShift"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val width = size.width
        val height = size.height

        val colors = listOf(
            AuraCyan,
            AuraBlue,
            AuraViolet,
            AuraMagenta,
            AuraCyan
        )

        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(shift % width, 0f),
            end = Offset((shift % width) + width, height)
        )

        // Soft blurred glow layer
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, AuraCyan.copy(alpha = 0.6f))
            ),
            size = size
        )

        // Sharp vibrant top line
        drawRect(
            brush = brush,
            size = size.copy(height = 3.dp.toPx())
        )
    }
}
