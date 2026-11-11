package com.focusstudy.poc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Focus Study mark: a shield (blocking / discipline) holding a bolt (energy / study).
 * Drawn in code so it stays sharp at any size and can be reused in the UI.
 */
@Composable
fun FocusLogo(modifier: Modifier = Modifier, size: Dp = 56.dp, showTile: Boolean = true) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        if (showTile) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(FocusColors.BlueDeep, FocusColors.Violet)),
                cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
                size = Size(w, h)
            )
        }

        val inset = if (showTile) 0.20f else 0.06f
        val scale = 1f - inset * 2

        fun x(value: Float) = (inset + value * scale) * w
        fun y(value: Float) = (inset + value * scale) * h

        val shield = Path().apply {
            moveTo(x(0.5f), y(0.04f))
            lineTo(x(0.9f), y(0.18f))
            lineTo(x(0.9f), y(0.52f))
            cubicTo(x(0.9f), y(0.74f), x(0.72f), y(0.89f), x(0.5f), y(0.96f))
            cubicTo(x(0.28f), y(0.89f), x(0.1f), y(0.74f), x(0.1f), y(0.52f))
            lineTo(x(0.1f), y(0.18f))
            close()
        }

        drawPath(
            path = shield,
            color = Color.White.copy(alpha = 0.95f),
            style = Stroke(width = w * 0.062f, join = StrokeJoin.Round)
        )

        val bolt = Path().apply {
            moveTo(x(0.58f), y(0.26f))
            lineTo(x(0.38f), y(0.55f))
            lineTo(x(0.49f), y(0.55f))
            lineTo(x(0.44f), y(0.76f))
            lineTo(x(0.64f), y(0.46f))
            lineTo(x(0.53f), y(0.46f))
            close()
        }
        drawPath(path = bolt, color = Color.White)
    }
}

/** Deep gradient background with two slowly drifting colour orbs. */
@Composable
fun BrandBackground(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "background")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )

    Box(modifier = Modifier.fillMaxSize().background(FocusColors.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.9f
            val blueCenter = Offset(size.width * (0.12f + 0.18f * drift), -radius * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FocusColors.Blue.copy(alpha = 0.30f), Color.Transparent),
                    center = blueCenter,
                    radius = radius
                ),
                radius = radius,
                center = blueCenter
            )
            val violetCenter = Offset(size.width * (0.95f - 0.2f * drift), size.height * 0.78f)
            val violetRadius = size.minDimension * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FocusColors.Violet.copy(alpha = 0.24f), Color.Transparent),
                    center = violetCenter,
                    radius = violetRadius
                ),
                radius = violetRadius,
                center = violetCenter
            )
        }
        content()
    }
}
