package io.github.xororz.localdream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R
import io.github.xororz.localdream.ui.components.ParamSlider
import io.github.xororz.localdream.ui.components.snapToStep
import io.github.xororz.localdream.utils.schedulerDisplayName
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pending jobs, folded to a one-line header so a queue doesn't push the
 * prompt page around; tap the header to see and remove jobs. "Start" runs
 * them when nothing is generating.
 */
@Composable
internal fun GenerationQueueCard(
    jobs: List<GenerationJob>,
    canStart: Boolean,
    onStart: () -> Unit,
    onRemove: (GenerationJob) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.queue_title, jobs.sumOf { it.count }),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.queue_clear)) }
                if (canStart) {
                    FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.queue_start)) }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                ) {
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
}

/** Prompt-page values the X/Y pickers start from. */
@Immutable
data class XyPickerDefaults(
    val steps: Float,
    val cfg: Float,
    val denoise: Float,
    val cfgRescale: Float,
    // Share of final steps without the negative prompt (the slider's reading).
    val skipShare: Float,
    // Fixed seed from the prompt page, else the last image's seed.
    val currentSeed: Long?,
) {
    fun valueFor(type: XyAxisType): Float = when (type) {
        XyAxisType.STEPS -> steps
        XyAxisType.CFG -> cfg
        XyAxisType.DENOISE -> denoise
        XyAxisType.CFG_RESCALE -> cfgRescale
        XyAxisType.CFG_CUTOFF -> skipShare
        else -> 0f
    }
}

/**
 * Picks one or two parameters to sweep. Confirm hands back the parsed axes;
 * the caller turns them into queued jobs.
 */
@Composable
internal fun XyPlotDialog(
    useImg2img: Boolean,
    baseCfg: Float,
    defaults: XyPickerDefaults,
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
    // Values typed for other axis types, so switching the type and back
    // doesn't lose them.
    val stash = remember { mutableStateMapOf<Pair<Int, XyAxisType>, String>() }
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
    // Skip negative and CFG Rescale only act on the negative-prompt pass, which
    // CFG 1 never runs, so such a sweep would give identical images.
    val guidanceOnly = setOf(XyAxisType.CFG_CUTOFF, XyAxisType.CFG_RESCALE)
    val noEffectAtCfg1 = baseCfg <= 1f && XyAxisType.CFG !in setOf(xType, yType) &&
        (xType in guidanceOnly || yType in guidanceOnly)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xy_plot)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AxisEditor(
                    title = stringResource(R.string.xy_x_axis),
                    type = xType,
                    options = available.filter { it != XyAxisType.NONE },
                    values = xValues,
                    isError = xValues.isNotBlank() && xAxis == null,
                    defaults = defaults,
                    onTypeChange = {
                        stash[0 to xType] = xValues
                        xType = it
                        xValues = stash[0 to it].orEmpty()
                    },
                    onValuesChange = { xValues = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AxisEditor(
                    title = stringResource(R.string.xy_y_axis),
                    type = yType,
                    options = available,
                    values = yValues,
                    isError = yType != XyAxisType.NONE && yValues.isNotBlank() && yAxis == null,
                    defaults = defaults,
                    onTypeChange = {
                        stash[1 to yType] = yValues
                        yType = it
                        yValues = stash[1 to it].orEmpty()
                    },
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
                if (noEffectAtCfg1) {
                    Text(
                        stringResource(R.string.xy_needs_cfg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
    defaults: XyPickerDefaults,
    onTypeChange: (XyAxisType) -> Unit,
    onValuesChange: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                if (option != type) onTypeChange(option)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
        if (type == XyAxisType.NONE) return@Column
        OutlinedTextField(
            value = values,
            onValueChange = onValuesChange,
            singleLine = true,
            isError = isError,
            label = { Text(stringResource(R.string.xy_values)) },
            supportingText = { Text(type.hint) },
            // Values are ids and numbers: no autocorrect or capitalisation
            // fighting the input.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (type == XyAxisType.PROMPT_SR) KeyboardType.Text else KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = if (values.isNotEmpty()) {
                {
                    IconButton(onClick = { onValuesChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.xy_clear_values))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val slider = type.slider
        when {
            slider != null -> NumberAxisPicker(
                type = type,
                slider = slider,
                start = defaults.valueFor(type),
                onAdd = { onValuesChange(appendXyValue(values, it)) },
                onFill = { onValuesChange(it.joinToString(", ")) },
            )

            type == XyAxisType.SCHEDULER -> SamplerAxisPicker(
                selected = xyValueList(values),
                onToggle = { onValuesChange(toggleXyValue(values, it)) },
            )

            type == XyAxisType.SEED -> SeedAxisPicker(
                currentSeed = defaults.currentSeed,
                onAdd = { onValuesChange(appendXyValue(values, it.toString())) },
            )
        }
    }
}

/** Slider with an Add button, plus a from/to/step helper that fills the list. */
@Composable
private fun NumberAxisPicker(
    type: XyAxisType,
    slider: XyAxisSlider,
    start: Float,
    onAdd: (String) -> Unit,
    onFill: (List<String>) -> Unit,
) {
    fun format(v: Float) = if (slider.integer) v.roundToInt().toString() else formatAxisNumber(v)
    var value by remember(type) {
        mutableFloatStateOf(snapToStep(start.coerceIn(slider.range), slider.range, slider.step))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        ParamSlider(
            label = "${type.title}: ${format(value)}",
            value = value,
            onValueChange = { value = it },
            valueRange = slider.range,
            step = slider.step,
            labelStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = { onAdd(format(value)) },
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.xy_add), modifier = Modifier.padding(start = 4.dp))
        }
    }

    var from by remember(type) { mutableStateOf(format(value)) }
    var to by remember(type) {
        mutableStateOf(format((value + slider.fillStep * 4).coerceAtMost(slider.range.endInclusive)))
    }
    var step by remember(type) { mutableStateOf(format(slider.fillStep)) }
    fun parse(text: String) = text.replace(',', '.').trim().toFloatOrNull()
    val filled = run {
        val a = parse(from) ?: return@run null
        val b = parse(to) ?: return@run null
        val st = parse(step) ?: return@run null
        fillXyRange(a, b, st, slider.integer)?.takeIf { parseXyAxis(type, it.joinToString(",")) != null }
    }
    Text(
        stringResource(R.string.xy_fill_range),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RangeField(from, { from = it }, stringResource(R.string.xy_from), slider.integer, Modifier.weight(1f))
        RangeField(to, { to = it }, stringResource(R.string.xy_to), slider.integer, Modifier.weight(1f))
        RangeField(step, { step = it }, stringResource(R.string.xy_step), slider.integer, Modifier.weight(1f))
        TextButton(
            onClick = { filled?.let(onFill) },
            enabled = filled != null,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.xy_fill))
        }
    }
}

@Composable
private fun RangeField(value: String, onValueChange: (String) -> Unit, label: String, integer: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, maxLines = 1) },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier,
    )
}

/** One chip per sampler; tapping adds it to the list, tapping again removes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SamplerAxisPicker(selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        XY_SCHEDULER_IDS.forEach { id ->
            val on = id in selected
            FilterChip(
                selected = on,
                onClick = { onToggle(id) },
                label = { Text(schedulerDisplayName(id)) },
                leadingIcon = if (on) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedAxisPicker(currentSeed: Long?, onAdd: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (currentSeed != null) {
            AssistChip(
                onClick = { onAdd(currentSeed) },
                label = { Text(stringResource(R.string.xy_add_current_seed, currentSeed)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        AssistChip(
            onClick = { onAdd(Random.nextLong(0, UInt.MAX_VALUE.toLong())) },
            label = { Text(stringResource(R.string.xy_add_random_seed)) },
            leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}
