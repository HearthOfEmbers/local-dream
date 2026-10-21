package io.github.xororz.localdream.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A labelled slider for a generation parameter, with − / + buttons that nudge
 * the value by [step] and a tappable label that opens a dialog to type an exact
 * value. Every value it emits is snapped to a multiple of [step] (offset from the
 * range start) and clamped to [valueRange], so dragging, the buttons and typing
 * all land on the same grid.
 *
 * [label] is the full caption, usually including the formatted value.
 */
@Composable
fun ParamSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    var showInput by remember { mutableStateOf(false) }
    val decimals = decimalsFor(step)
    val snap = { raw: Float -> snapToStep(raw, valueRange, step) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = labelStyle.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { showInput = true },
            )
            IconButton(
                onClick = { onValueChange(snap(value - step)) },
                enabled = enabled && value > valueRange.start,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.param_decrease),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { onValueChange(snap(value + step)) },
                enabled = enabled && value < valueRange.endInclusive,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.param_increase),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = { raw ->
                val snapped = snap(raw)
                if (snapped != value) onValueChange(snapped)
            },
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showInput) {
        var text by remember { mutableStateOf(formatValue(value, decimals)) }
        val parsed = text.replace(',', '.').toFloatOrNull()
        val confirm = {
            if (parsed != null) onValueChange(snap(parsed))
            showInput = false
        }
        AlertDialog(
            onDismissRequest = { showInput = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = parsed == null,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.param_value_range,
                                formatValue(valueRange.start, decimals),
                                formatValue(valueRange.endInclusive, decimals),
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = confirm, enabled = parsed != null) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInput = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Snaps [raw] to the [step] grid starting at the range start, then clamps it. */
internal fun snapToStep(raw: Float, valueRange: ClosedFloatingPointRange<Float>, step: Float): Float {
    if (step <= 0f) return raw.coerceIn(valueRange)
    val start = valueRange.start
    val steps = ((raw - start) / step).roundToLong()
    // Round away float noise (7.299999 -> 7.3) at the step's precision.
    val decimals = decimalsFor(step)
    val snapped = formatValue(start + steps * step, decimals).toFloat()
    return snapped.coerceIn(valueRange)
}

private fun decimalsFor(step: Float): Int {
    var d = 0
    var s = step
    while (d < 4 && abs(s - s.roundToLong()) > 1e-4f) {
        s *= 10f
        d++
    }
    return d
}

private fun formatValue(value: Float, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
