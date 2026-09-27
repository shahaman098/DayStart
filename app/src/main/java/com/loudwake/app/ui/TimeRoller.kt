package com.loudwake.app.ui

import android.graphics.drawable.ColorDrawable
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

// NOTE: NumberPicker is a View, not Compose — its colors are read once per recomposition in
// styleNumberPicker() below via imperative setTextColor/reflection, not via MaterialTheme.
// Whenever Theme.kt's onSurface/primary/surfaceVariant/primaryContainer semantics change,
// double-check this file renders correctly; it won't pick up new *tokens* automatically,
// only new *values* of the same tokens it already reads.
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TimeRoller(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    mod: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Column(
        mod.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum, lnum"
            ),
            color = onSurface
        )
        Text(
            "24-hour · scroll to set",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val hazeState = LocalHazeState.current
        val panelShape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin(surfaceVariant))
                    } else {
                        Modifier.background(surfaceVariant)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Selection highlight stays solid (not frosted) — it's a functional indicator
            // for the scrolling digit under it and needs to stay crisp, not atmospheric.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryContainer)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NumberRoller(
                    value = hour,
                    min = 0,
                    max = 23,
                    label = "Hour",
                    onChange = onHourChange,
                    textColor = onSurface.toArgb(),
                    dividerColor = primary.copy(alpha = 0.35f).toArgb(),
                    mod = Modifier.weight(1f)
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                NumberRoller(
                    value = minute,
                    min = 0,
                    max = 59,
                    label = "Min",
                    onChange = onMinuteChange,
                    textColor = onSurface.toArgb(),
                    dividerColor = primary.copy(alpha = 0.35f).toArgb(),
                    mod = Modifier.weight(1f)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("Hour", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(24.dp))
            Text("Minute", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NumberRoller(
    value: Int,
    min: Int,
    max: Int,
    label: String,
    onChange: (Int) -> Unit,
    textColor: Int,
    dividerColor: Int,
    mod: Modifier = Modifier,
    pad: Boolean = true
) {
    AndroidView(
        modifier = mod.height(168.dp),
        factory = { context ->
            NumberPicker(context).apply {
                contentDescription = label
                minValue = min
                maxValue = max
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                if (pad) {
                    setFormatter { v -> "%02d".format(v) }
                }
                this.value = value.coerceIn(min, max)
                setOnValueChangedListener { _, _, newVal -> onChange(newVal) }
                styleNumberPicker(this, textColor, dividerColor)
            }
        },
        update = { picker ->
            if (picker.value != value.coerceIn(min, max)) {
                picker.value = value.coerceIn(min, max)
            }
            styleNumberPicker(picker, textColor, dividerColor)
        }
    )
}

private fun styleNumberPicker(picker: NumberPicker, textColor: Int, dividerColor: Int) {
    try {
        picker.setTextColor(textColor)
    } catch (_: Throwable) {
    }
    runCatching {
        val field = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
        field.isAccessible = true
        field.set(picker, ColorDrawable(dividerColor))
    }
    runCatching {
        val edit = NumberPicker::class.java.getDeclaredField("mInputText")
        edit.isAccessible = true
        val tv = edit.get(picker) as? android.widget.EditText
        tv?.filters = emptyArray()
        tv?.setTextColor(textColor)
    }
}
