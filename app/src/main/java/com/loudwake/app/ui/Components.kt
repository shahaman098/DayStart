package com.loudwake.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.cos
import kotlin.math.sin

/**
 * Frosted glass surface backed by Haze when placed inside an atmosphere [SurfaceBackground]
 * (real blur + translucency on API 31+, a tinted scrim fallback below that). Falls back to a
 * flat surface-colored card when [frosted] is false or no Haze source is available upstream —
 * this is the one shared surface primitive; it isn't a misnomer, it's actually glass now.
 */
@Composable
fun GlassCard(
    mod: Modifier = Modifier,
    frosted: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    FrostedSurface(
        mod = mod,
        frosted = frosted,
        containerColor = MaterialTheme.colorScheme.surface,
        padding = 16.dp,
        content = content
    )
}

/** Same treatment as [GlassCard], distinct padding/tone for wake/relapse mission surfaces. */
@Composable
fun MissionCard(
    mod: Modifier = Modifier,
    frosted: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    FrostedSurface(
        mod = mod,
        frosted = frosted,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        padding = 18.dp,
        content = content
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun FrostedSurface(
    mod: Modifier,
    frosted: Boolean,
    containerColor: Color,
    padding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val atmosphere = MaterialTheme.dsAtmosphere
    val hazeState = LocalHazeState.current
    val glass = frosted && hazeState != null
    val shape = if (frosted) atmosphere.heroCardShape else RoundedCornerShape(8.dp)
    val glassStyle = HazeMaterials.thin(containerColor)
    Box(
        mod
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (glass) {
                    Modifier.hazeEffect(state = hazeState, style = glassStyle)
                } else {
                    Modifier.background(containerColor, shape)
                }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        if (glass) {
            // Faint top-edge highlight to fake glass edge lighting without elevation/shadow.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(atmosphere.glassBorderTop)
            )
        }
        Column(
            Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * Analog clock ring showing [hour]:[minute] — used as the home “next wake” motif.
 */
@Composable
fun WakeClockFace(
    hour: Int,
    minute: Int,
    size: Dp = 132.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.outline,
    hand: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(Modifier.size(size)) {
        val r = size.toPx() / 2f
        val c = Offset(r, r)
        drawCircle(color = track, radius = r - 2f, style = Stroke(width = 3f))
        drawCircle(color = accent.copy(alpha = 0.14f), radius = r - 10f)
        // Hour ticks
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30 - 90).toDouble())
            val outer = r - 8f
            val inner = r - 16f
            drawLine(
                color = hand.copy(alpha = 0.45f),
                start = Offset(
                    c.x + (cos(a) * inner).toFloat(),
                    c.y + (sin(a) * inner).toFloat()
                ),
                end = Offset(
                    c.x + (cos(a) * outer).toFloat(),
                    c.y + (sin(a) * outer).toFloat()
                ),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }
        val hourAngle = Math.toRadians((((hour % 12) + minute / 60f) * 30f - 90f).toDouble())
        val minuteAngle = Math.toRadians((minute * 6f - 90f).toDouble())
        drawLine(
            color = hand,
            start = c,
            end = Offset(
                c.x + (cos(hourAngle) * (r * 0.42f)).toFloat(),
                c.y + (sin(hourAngle) * (r * 0.42f)).toFloat()
            ),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accent,
            start = c,
            end = Offset(
                c.x + (cos(minuteAngle) * (r * 0.62f)).toFloat(),
                c.y + (sin(minuteAngle) * (r * 0.62f)).toFloat()
            ),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
        drawCircle(color = accent, radius = 5f)
    }
}

/**
 * The app's one primary-action button — Wake Gate submits, emergency-stop confirm, "I'm
 * Awake" all go through here, so a shared press-scale + haptic tap lands everywhere that
 * matters without needing to wire it up at each call site individually.
 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    mod: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "primaryButtonPress"
    )
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = mod
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    mod: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = mod.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
fun DangerTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun DsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mod: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = mod.fillMaxWidth(),
        label = { Text(placeholder) },
        singleLine = true
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
fun DsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun TimeBig(hour: Int, minute: Int, enabled: Boolean = true) {
    Text(
        "%02d:%02d".format(hour, minute),
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 48.sp,
            letterSpacing = (-1).sp,
            fontFeatureSettings = "tnum, lnum"
        ),
        color = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        }
    )
}

/** AOSP Clock–style alarm row: big time, labels, switch. */
@Composable
fun ClockAlarmRow(
    hour: Int,
    minute: Int,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TimeBig(hour, minute, enabled = enabled)
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** Preference row like Settings / Clock alarm detail. */
@Composable
fun PrefRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}

@Composable
fun ModeBadge(text: String, strict: Boolean = false) {
    val bg = if (strict) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val fg = if (strict) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    // Tag, not a pill — mode badges are secondary info and should recede, not compete.
    val tagShape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .background(bg, tagShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), tagShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GateProgress(current: Int, total: Int, label: String? = null) {
    val t = total.coerceAtLeast(1)
    val c = current.coerceIn(0, t)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label ?: "Challenge $c of $t",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${(c * 100 / t)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val animatedProgress by animateFloatAsState(
            targetValue = c / t.toFloat(),
            label = "gateProgress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Groups PrefRows in the Alarm Editor instead of one flat 14-row scrolling list. */
@Composable
fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}
