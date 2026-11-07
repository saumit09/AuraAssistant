package com.aura.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aura.assistant.ui.theme.AuraBlue
import com.aura.assistant.ui.theme.AuraCyan
import com.aura.assistant.ui.theme.AuraMagenta
import com.aura.assistant.ui.theme.AuraViolet
import kotlin.math.sin

/**
 * Real-time Sine Wave Visualizer driven by SpeechRecognizer RMS dB level.
 * Features 3 layered, phase-shifted trigonometric harmonic waves that expand with vocal energy.
 */
@Composable
fun SineWaveVisualizer(
    rmsDb: Float,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseTransition")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth normalized amplitude between 0.15 (idle hum) and 1.0 (loud speech)
    val normalizedRms = (rmsDb.coerceIn(-2f, 10f) + 2f) / 12f
    val animatedAmplitudeScale by animateFloatAsState(
        targetValue = if (isActive) (0.20f + normalizedRms * 0.80f) else 0.05f,
        animationSpec = tween(durationMillis = 100),
        label = "amplitudeScale"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val maxWaveHeight = (height * 0.42f) * animatedAmplitudeScale

        if (width <= 0 || height <= 0) return@Canvas

        // Wave 1: Cyan / Blue primary harmonic
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = maxWaveHeight,
            frequency = 2.2f,
            phase = phase,
            strokeWidth = 3.5.dp.toPx(),
            brush = Brush.horizontalGradient(
                listOf(AuraCyan.copy(alpha = 0.2f), AuraCyan, AuraBlue, AuraCyan.copy(alpha = 0.2f))
            )
        )

        // Wave 2: Magenta secondary harmonic (higher frequency, shifted phase)
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = maxWaveHeight * 0.75f,
            frequency = 3.4f,
            phase = phase + 1.6f,
            strokeWidth = 2.5.dp.toPx(),
            brush = Brush.horizontalGradient(
                listOf(AuraMagenta.copy(alpha = 0.15f), AuraMagenta, AuraViolet, AuraMagenta.copy(alpha = 0.15f))
            )
        )

        // Wave 3: Violet tertiary harmonic (subtle background glow)
        drawSineWave(
            width = width,
            centerY = centerY,
            amplitude = maxWaveHeight * 0.5f,
            frequency = 1.6f,
            phase = phase - 1.2f,
            strokeWidth = 2.0.dp.toPx(),
            brush = Brush.horizontalGradient(
                listOf(AuraViolet.copy(alpha = 0.1f), AuraViolet, AuraCyan, AuraViolet.copy(alpha = 0.1f))
            )
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSineWave(
    width: Float,
    centerY: Float,
    amplitude: Float,
    frequency: Float,
    phase: Float,
    strokeWidth: Float,
    brush: Brush
) {
    val path = Path()
    val step = 4f
    var x = 0f
    
    // Tapering envelope at edges so wave blends seamlessly
    while (x <= width) {
        val normalizedX = x / width
        // Hann-style window envelope: sin(pi * x)^2
        val envelope = sin(Math.PI * normalizedX).toFloat()
        val y = centerY + amplitude * envelope * sin((2 * Math.PI * frequency * normalizedX).toFloat() + phase)
        
        if (x == 0f) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        x += step
    }

    drawPath(
        path = path,
        brush = brush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
