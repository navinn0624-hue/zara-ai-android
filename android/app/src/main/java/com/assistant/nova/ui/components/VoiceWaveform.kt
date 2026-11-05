package com.assistant.nova.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonPurple
import kotlin.math.sin

@Composable
fun VoiceWaveform(
    isActive: Boolean,
    rmsAmplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    maxBarHeight: Dp = 48.dp,
    minBarHeight: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Row(
        modifier = modifier.height(maxBarHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val gradient = Brush.verticalGradient(
            colors = listOf(NeonCyan, NeonPurple)
        )

        for (i in 0 until barCount) {
            val barFactor = if (isActive) {
                // Calculate dynamic height based on RMS amplitude + phase wave offset
                val waveOffset = sin(phase + (i.toFloat() / barCount) * Math.PI * 2).toFloat()
                val normalizedWave = (waveOffset + 1f) / 2f
                val dynamicHeight = (rmsAmplitude * 0.7f + normalizedWave * 0.3f).coerceIn(0.12f, 1.0f)
                dynamicHeight
            } else {
                0.1f
            }

            val currentHeight = minBarHeight + (maxBarHeight - minBarHeight) * barFactor

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(gradient)
            )
        }
    }
}
