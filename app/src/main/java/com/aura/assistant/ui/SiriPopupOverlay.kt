package com.aura.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.assistant.ui.theme.AuraBlue
import com.aura.assistant.ui.theme.AuraCardBorder
import com.aura.assistant.ui.theme.AuraCyan
import com.aura.assistant.ui.theme.AuraDarkBackground
import com.aura.assistant.ui.theme.AuraDarkSurface
import com.aura.assistant.ui.theme.AuraGlassGradient
import com.aura.assistant.ui.theme.AuraGreen
import com.aura.assistant.ui.theme.AuraMagenta
import com.aura.assistant.ui.theme.AuraTextMuted
import com.aura.assistant.ui.theme.AuraTextPrimary
import com.aura.assistant.ui.theme.AuraTextSecondary
import com.aura.assistant.ui.theme.AuraViolet
import com.aura.assistant.voice.SpeechState

/**
 * Siri-style floating frosted-glass bottom popup overlay.
 * Renders an animated aurora bottom glow, dynamic sine wave audio visualizer,
 * live streaming transcription, and assistant response card.
 */
@Composable
fun SiriPopupOverlay(
    state: SpeechState,
    onDismiss: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Outer scrim: transparent touch listener to dismiss when tapping outside pill
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { /* Intercept click on pill so it doesn't dismiss */ }
                )
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Frosted Glass Capsule
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(bottom = 12.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = AuraCyan.copy(alpha = 0.35f),
                        ambientColor = AuraMagenta.copy(alpha = 0.25f)
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                AuraCyan.copy(alpha = 0.7f),
                                AuraViolet.copy(alpha = 0.4f),
                                AuraMagenta.copy(alpha = 0.6f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = AuraDarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.verticalGradient(AuraGlassGradient))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Status Bar: Pill Indicator + Mode Label + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StateIndicatorBadge(state)

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = AuraTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Audio Visualizer Waveform (Dynamic sine waves modulated by live mic RMS dB)
                    val rmsDb = (state as? SpeechState.Listening)?.rmsDb ?: 0f
                    val isListening = state is SpeechState.Listening
                    SineWaveVisualizer(
                        rmsDb = rmsDb,
                        isActive = isListening,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Content Area: Live Streaming Transcript -> Tool Execution -> Assistant Reply
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 4 }) togetherWith
                                    (fadeOut() + slideOutVertically { -it / 4 })
                        },
                        label = "StateContentTransition"
                    ) { targetState ->
                        when (targetState) {
                            is SpeechState.Idle -> {
                                Text(
                                    text = "Tap microphone to speak",
                                    color = AuraTextMuted,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }

                            is SpeechState.Listening -> {
                                val transcript = targetState.partialTranscript
                                if (transcript.isBlank()) {
                                    Text(
                                        text = "Listening… \"Call Mom\" or \"Turn on flashlight\"",
                                        color = AuraTextSecondary,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    Text(
                                        text = transcript,
                                        color = AuraTextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 24.sp,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }

                            is SpeechState.Thinking -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "\"${targetState.userPrompt}\"",
                                        color = AuraTextSecondary,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Processing with Gemini…",
                                        color = AuraCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            is SpeechState.ExecutingTool -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = targetState.statusMessage,
                                        color = AuraCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ActionChip(
                                        label = targetState.toolName,
                                        icon = Icons.Default.Bolt
                                    )
                                }
                            }

                            is SpeechState.Speaking -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    if (!targetState.toolExecuted.isNullOrBlank()) {
                                        ActionChip(
                                            label = targetState.toolExecuted,
                                            icon = Icons.Default.Bolt
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    Text(
                                        text = targetState.assistantReply,
                                        color = AuraTextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 24.sp
                                    )
                                }
                            }

                            is SpeechState.Error -> {
                                Text(
                                    text = targetState.message,
                                    color = AuraMagenta,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Mic / Interaction Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (isListening) AuraCyan else AuraViolet,
                                            AuraBlue
                                        )
                                    )
                                )
                                .clickable(onClick = onMicClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Voice Control",
                                tint = AuraDarkBackground,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Samsung Edge Lighting / Siri Aurora Glowing Edge
            GlowingGradientBorder(heightDp = 6)
        }
    }
}

@Composable
private fun StateIndicatorBadge(state: SpeechState) {
    val (color, text) = when (state) {
        is SpeechState.Idle -> Pair(AuraTextMuted, "Standby")
        is SpeechState.Listening -> Pair(AuraCyan, "Listening")
        is SpeechState.Thinking -> Pair(AuraViolet, "Thinking")
        is SpeechState.ExecutingTool -> Pair(AuraGreen, "Executing")
        is SpeechState.Speaking -> Pair(AuraMagenta, "Speaking")
        is SpeechState.Error -> Pair(AuraMagenta, "Notice")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x3300F5FF))
            .border(0.8.dp, AuraCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AuraCyan,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = AuraCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
