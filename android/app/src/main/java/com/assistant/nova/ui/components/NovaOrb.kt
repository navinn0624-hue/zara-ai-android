package com.assistant.nova.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonMagenta
import com.assistant.nova.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun NovaOrb(
    state: OrbState,
    rmsAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")

    // Idle breathing scale animation
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdlePulse"
    )

    // Thinking rotation animation
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ThinkingRotation"
    )

    // Speaking wave frequency
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpeakingWave"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val baseRadius = (size.toPx() / 2f) * 0.65f

            when (state) {
                OrbState.IDLE -> {
                    val currentRadius = baseRadius * idleScale
                    drawAmbientHalo(center, currentRadius * 1.4f, NeonPurple.copy(alpha = 0.25f))
                    drawOrbBody(center, currentRadius, rotationDegrees = 0f)
                }
                OrbState.LISTENING -> {
                    // Responsive expansion driven by microphone RMS amplitude
                    val audioBoost = (rmsAmplitude * 0.35f)
                    val activeRadius = baseRadius * (1.05f + audioBoost)
                    drawAmbientHalo(center, activeRadius * 1.55f, NeonCyan.copy(alpha = 0.4f + audioBoost * 0.4f))
                    drawListeningRings(center, activeRadius, audioBoost)
                    drawOrbBody(center, activeRadius, rotationDegrees = 0f)
                }
                OrbState.THINKING -> {
                    drawAmbientHalo(center, baseRadius * 1.5f, NeonMagenta.copy(alpha = 0.35f))
                    rotate(rotationDegrees, pivot = center) {
                        drawOrbBody(center, baseRadius * 1.02f, rotationDegrees = rotationDegrees)
                    }
                }
                OrbState.SPEAKING -> {
                    val speakingPulse = (1f + 0.08f * sin(wavePhase))
                    val speakingRadius = baseRadius * speakingPulse
                    drawAmbientHalo(center, speakingRadius * 1.45f, NeonCyan.copy(alpha = 0.35f))
                    drawHarmonicWaves(center, speakingRadius, wavePhase)
                    drawOrbBody(center, speakingRadius, rotationDegrees = 0f)
                }
            }
        }
    }
}

private fun DrawScope.drawAmbientHalo(center: Offset, radius: Float, haloColor: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(haloColor, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawOrbBody(center: Offset, radius: Float, rotationDegrees: Float) {
    // Outer atmospheric glow
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.White.copy(alpha = 0.95f),
            0.25f to NeonCyan.copy(alpha = 0.9f),
            0.65f to NeonPurple.copy(alpha = 0.8f),
            1.0f to Color(0xFF0F172A).copy(alpha = 0.1f),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // Inner core highlight
    val coreOffset = Offset(center.x - radius * 0.2f, center.y - radius * 0.25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.8f),
                NeonCyan.copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = coreOffset,
            radius = radius * 0.5f
        ),
        radius = radius * 0.5f,
        center = coreOffset
    )
}

private fun DrawScope.drawListeningRings(center: Offset, radius: Float, audioBoost: Float) {
    val ringRadius = radius * (1.2f + audioBoost * 0.4f)
    drawCircle(
        color = NeonCyan.copy(alpha = (0.5f - audioBoost * 0.2f).coerceAtLeast(0.1f)),
        radius = ringRadius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
    )
}

private fun DrawScope.drawHarmonicWaves(center: Offset, radius: Float, phase: Float) {
    for (i in 1..3) {
        val waveRadius = radius + (i * 14.dp.toPx() * (0.5f + 0.5f * sin(phase + i)))
        drawCircle(
            color = NeonCyan.copy(alpha = (0.4f / i)),
            radius = waveRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}
