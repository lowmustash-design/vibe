package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HapticVisualizerBadge(
    isHapticsEnabled: Boolean,
    isHardwareActive: Boolean,
    lastHapticTime: Long,
    modifier: Modifier = Modifier
) {
    var isPulsing by remember { mutableStateOf(false) }

    LaunchedEffect(lastHapticTime) {
        if (lastHapticTime > 0L) {
            isPulsing = true
            delay(120)
            isPulsing = false
        }
    }

    val scaleAnimate by animateFloatAsState(
        targetValue = if (isPulsing) 1.25f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "hapticPulseScale"
    )

    val badgeBg = if (isHapticsEnabled) {
        Color(0xFF2B2930).copy(alpha = 0.9f)
    } else {
        Color(0xFF1C1B1F).copy(alpha = 0.85f)
    }

    val activeColor = Color(0xFFD0BCFF)
    val inactiveColor = Color(0xFFCAC4D0)

    Box(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(14.dp)
                    .scale(if (isHapticsEnabled) scaleAnimate else 1.0f)
            ) {
                if (isHapticsEnabled) {
                    drawCircle(
                        color = activeColor.copy(alpha = if (isPulsing) 0.9f else 0.4f),
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = activeColor,
                        radius = size.minDimension / 3.5f
                    )
                    if (isPulsing) {
                        drawCircle(
                            color = activeColor,
                            radius = size.minDimension / 1.8f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                } else {
                    drawCircle(
                        color = Color(0xFF49454F),
                        radius = size.minDimension / 3f
                    )
                }
            }

            Text(
                text = when {
                    !isHapticsEnabled -> "HAPTICS OFF"
                    isHardwareActive -> "LRA ENGINE • HW SYNC"
                    else -> "LRA ENGINE • 30MS COMP"
                },
                color = if (isHapticsEnabled) activeColor else inactiveColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
