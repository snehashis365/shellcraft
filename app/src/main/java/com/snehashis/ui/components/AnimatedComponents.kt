package com.snehashis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.snehashis.ui.theme.LocalDisableHeavyAnimations

fun Modifier.springPressScale(
    enabled: Boolean = true,
    onHapticTick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val disableHeavy = LocalDisableHeavyAnimations.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !disableHeavy) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "springPressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onClick = {
                onHapticTick?.invoke()
                onClick()
            }
        )
}

@Composable
fun GlowSurfaceCard(
    accentColor: Color,
    isPulsing: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val disableHeavy = LocalDisableHeavyAnimations.current
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(if (disableHeavy) 120 else 450),
        label = "glowAccent"
    )

    val drift = if (isPulsing && !disableHeavy) {
        val infinite = rememberInfiniteTransition(label = "meshGlow")
        val value by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowDrift"
        )
        value
    } else {
        0f
    }

    val baseSurface = MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, animatedAccent.copy(alpha = if (isPulsing) 0.62f else 0.36f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(color = baseSurface)
                    if (size.width > 1f && size.height > 1f) {
                        val activePulse = isPulsing && !disableHeavy
                        val radius = (size.maxDimension * (0.72f + (if (activePulse) drift * 0.2f else 0f)))
                            .coerceAtLeast(1f)
                        val centerX = size.width * (0.86f - (if (activePulse) drift * 0.14f else 0f))
                        val centerY = size.height * 0.16f
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animatedAccent.copy(alpha = if (isPulsing) 0.24f else 0.13f),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = radius
                            )
                        )
                    }
                },
            content = content
        )
    }
}
