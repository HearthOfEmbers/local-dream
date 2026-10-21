package io.github.xororz.localdream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R

/** "Add to queue" and "X/Y plot" buttons shown under Generate. */
@Composable
internal fun GenerationExtrasRow(onAddToQueue: () -> Unit, onXyPlot: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onAddToQueue,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.queue_add), modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(
            onClick = onXyPlot,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.xy_plot), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Pending jobs, each removable; "Start" runs them when nothing is generating. */
@Composable
internal fun GenerationQueueCard(
    jobs: List<GenerationJob>,
    canStart: Boolean,
    onStart: () -> Unit,
    onRemove: (GenerationJob) -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.queue_title, jobs.sumOf { it.count }),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.queue_clear)) }
                if (canStart) {
                    FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.queue_start)) }
                }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(jobs, key = { it.id }) { job ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                job.prompt.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                job.summary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onRemove(job) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Picks one or two parameters to sweep. Confirm hands back the parsed axes;
 * the caller turns them into queued jobs.
 */
@Composable
internal fun XyPlotDialog(
    useImg2img: Boolean,
    initialX: XyAxisType,
    initialXValues: String,
    initialY: XyAxisType,
    initialYValues: String,
    onStart: (XyAxis, XyAxis, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var xType by remember { mutableStateOf(initialX) }
    var yType by remember { mutableStateOf(initialY) }
    var xValues by remember { mutableStateOf(initialXValues) }
    var yValues by remember { mutableStateOf(initialYValues) }
    val available = XyAxisType.entries.filter { useImg2img || it != XyAxisType.DENOISE }

    val xAxis = parseXyAxis(xType, xValues)
    val yAxis = parseXyAxis(yType, yValues)
    val cells = if (xAxis != null && yAxis != null) {
        xAxis.values.size * maxOf(1, yAxis.values.size)
    } else {
        0
    }
    val valid = xAxis != null && yAxis != null && xType != XyAxisType.NONE && xType != yType &&
        cells in 1..XY_MAX_CELLS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xy_plot)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AxisEditor(
                    title = stringResource(R.string.xy_x_axis),
                    type = xType,
                    options = available.filter { it != XyAxisType.NONE },
                    values = xValues,
                    isError = xValues.isNotBlank() && xAxis == null,
                    onTypeChange = { xType = it },
                    onValuesChange = { xValues = it },
                )
                AxisEditor(
                    title = stringResource(R.string.xy_y_axis),
                    type = yType,
                    options = available,
                    values = yValues,
                    isError = yType != XyAxisType.NONE && yValues.isNotBlank() && yAxis == null,
                    onTypeChange = { yType = it },
                    onValuesChange = { yValues = it },
                )
                Text(
                    if (valid) {
                        stringResource(R.string.xy_cells, cells)
                    } else {
                        stringResource(R.string.xy_invalid, XY_MAX_CELLS)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    stringResource(R.string.xy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (xAxis != null && yAxis != null) onStart(xAxis, yAxis, xValues, yValues) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.xy_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AxisEditor(
    title: String,
    type: XyAxisType,
    options: List<XyAxisType>,
    values: String,
    isError: Boolean,
    onTypeChange: (XyAxisType) -> Unit,
    onValuesChange: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(type.title)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.title) },
                            onClick = {
                                onTypeChange(option)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
        if (type != XyAxisType.NONE) {
            OutlinedTextField(
                value = values,
                onValueChange = onValuesChange,
                singleLine = true,
                isError = isError,
                label = { Text(stringResource(R.string.xy_values)) },
                supportingText = { Text(type.hint) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
