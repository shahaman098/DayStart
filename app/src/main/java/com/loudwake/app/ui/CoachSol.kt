package com.loudwake.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * DayStart's identity mark: a sun-faced "tough coach," not a cozy mascot — stern and
 * determined by default, only pleased once a morning is actually earned. Hand-drawn in
 * Canvas (same technique as [WakeClockFace]) rather than an illustration/animation
 * dependency: the shape vocabulary is fully parametric, so light/dark/mission just swap
 * [Theme.dsAtmosphere] color tokens instead of needing re-exported art.
 */
enum class SolExpression { RESTING, ALERT, STERN, PROUD }

@Composable
fun CoachSol(
    expression: SolExpression,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val atmosphere = MaterialTheme.dsAtmosphere
    val discBrush = atmosphere.solDiscGradient
    val rayColor = atmosphere.solRayColor
    val faceColor = atmosphere.solFaceColor

    val rayLengthFrac by animateFloatAsState(
        targetValue = when (expression) {
            SolExpression.RESTING -> 0.05f
            SolExpression.ALERT -> 0.18f
            SolExpression.STERN -> 0.16f
            SolExpression.PROUD -> 0.34f
        },
        animationSpec = tween(if (expression == SolExpression.PROUD) 900 else 500),
        label = "solRayLength"
    )
    val browAngleDeg by animateFloatAsState(
        targetValue = when (expression) {
            SolExpression.RESTING -> 2f
            SolExpression.ALERT -> 14f
            SolExpression.STERN -> 22f
            SolExpression.PROUD -> 6f
        },
        animationSpec = tween(400),
        label = "solBrowAngle"
    )
    val mouthCurve by animateFloatAsState(
        targetValue = when (expression) {
            SolExpression.RESTING -> 0f
            SolExpression.ALERT -> -0.05f
            SolExpression.STERN -> -0.22f
            SolExpression.PROUD -> 0.38f
        },
        animationSpec = tween(400),
        label = "solMouthCurve"
    )
    val eyeOpenFrac by animateFloatAsState(
        targetValue = if (expression == SolExpression.RESTING) 0f else 1f,
        animationSpec = tween(300),
        label = "solEyeOpen"
    )

    Canvas(modifier.size(size)) {
        val r = size.toPx() / 2f
        val c = Offset(r, r)
        val discR = r * 0.6f

        // Rays: fixed count, animated length only, so "bursting" reads as one clear gesture.
        val rayCount = 12
        val rayInner = discR * 1.08f
        val rayOuter = discR * (1.08f + rayLengthFrac)
        val rayStroke = discR * 0.09f
        for (i in 0 until rayCount) {
            val a = Math.toRadians((i * (360f / rayCount)).toDouble())
            drawLine(
                color = rayColor,
                start = Offset(c.x + (cos(a) * rayInner).toFloat(), c.y + (sin(a) * rayInner).toFloat()),
                end = Offset(c.x + (cos(a) * rayOuter).toFloat(), c.y + (sin(a) * rayOuter).toFloat()),
                strokeWidth = rayStroke,
                cap = StrokeCap.Round
            )
        }

        drawCircle(brush = discBrush, radius = discR, center = c)

        // Face sits in the lower-center of the disc, coach-like (stern brows dominate the read).
        val eyeY = c.y - discR * 0.05f
        val eyeDx = discR * 0.32f
        val browY = eyeY - discR * 0.34f
        val browHalfWidth = discR * 0.22f
        val browStroke = discR * 0.1f
        val eyeRadius = discR * (0.1f * eyeOpenFrac).coerceAtLeast(discR * 0.02f)

        // Brows: rotate each about its own midpoint, mirrored, so they converge downward
        // toward the center as sternness increases — the single biggest "tough coach" cue.
        rotate(degrees = browAngleDeg, pivot = Offset(c.x - eyeDx, browY)) {
            drawLine(
                color = faceColor,
                start = Offset(c.x - eyeDx - browHalfWidth, browY),
                end = Offset(c.x - eyeDx + browHalfWidth, browY),
                strokeWidth = browStroke,
                cap = StrokeCap.Round
            )
        }
        rotate(degrees = -browAngleDeg, pivot = Offset(c.x + eyeDx, browY)) {
            drawLine(
                color = faceColor,
                start = Offset(c.x + eyeDx - browHalfWidth, browY),
                end = Offset(c.x + eyeDx + browHalfWidth, browY),
                strokeWidth = browStroke,
                cap = StrokeCap.Round
            )
        }

        if (expression == SolExpression.RESTING) {
            val closedHalfWidth = discR * 0.14f
            drawLine(
                color = faceColor,
                start = Offset(c.x - eyeDx - closedHalfWidth, eyeY),
                end = Offset(c.x - eyeDx + closedHalfWidth, eyeY),
                strokeWidth = browStroke * 0.7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = faceColor,
                start = Offset(c.x + eyeDx - closedHalfWidth, eyeY),
                end = Offset(c.x + eyeDx + closedHalfWidth, eyeY),
                strokeWidth = browStroke * 0.7f,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(color = faceColor, radius = eyeRadius, center = Offset(c.x - eyeDx, eyeY))
            drawCircle(color = faceColor, radius = eyeRadius, center = Offset(c.x + eyeDx, eyeY))
        }

        // Mouth: negative curve reads as a firm/stern line, positive as an earned smile.
        val mouthY = c.y + discR * 0.4f
        val mouthHalfWidth = discR * 0.3f
        val mouthPath = Path().apply {
            moveTo(c.x - mouthHalfWidth, mouthY)
            quadraticTo(
                c.x, mouthY + mouthCurve * discR,
                c.x + mouthHalfWidth, mouthY
            )
        }
        drawPath(
            path = mouthPath,
            color = faceColor,
            style = Stroke(width = discR * 0.09f, cap = StrokeCap.Round)
        )
    }
}
