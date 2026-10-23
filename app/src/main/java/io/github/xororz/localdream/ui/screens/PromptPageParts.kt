package io.github.xororz.localdream.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R
import io.github.xororz.localdream.ui.components.SmoothLinearWavyProgressIndicator
import io.github.xororz.localdream.ui.theme.Motion
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A column where child [fillIndex] takes whatever height the others leave of
 * [viewportHeight] (never less than [minFillHeight]); the rest keep their own
 * height. Meant to sit inside a vertical scroll, so a screen too short for
 * everything scrolls instead of squashing the prompt field to nothing.
 * Zero-height children (collapsed sections) take no spacing.
 */
@Composable
internal fun FillColumn(
    fillIndex: Int,
    viewportHeight: Dp,
    minFillHeight: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val loose = Constraints(minWidth = 0, maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        measurables.forEachIndexed { i, m -> if (i != fillIndex) placeables[i] = m.measure(loose) }
        val others = placeables.filterNotNull()
        val othersHeight = others.sumOf { it.height } + gap * others.count { it.height > 0 }
        val fillHeight = max(minFillHeight.roundToPx(), viewportHeight.roundToPx() - othersHeight)
        if (fillIndex in measurables.indices) {
            placeables[fillIndex] = measurables[fillIndex].measure(
                Constraints.fixed(width, fillHeight),
            )
        }
        val visible = placeables.filterNotNull().filter { it.height > 0 }
        val total = visible.sumOf { it.height } + gap * (visible.size - 1).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            placeables.forEach { p ->
                if (p == null || p.height == 0) return@forEach
                p.placeRelative(0, y)
                y += p.height + gap
            }
        }
    }
}

/**
 * Expand-and-fade visibility for sections of the prompt page. Callable where
 * an outer Column's scoped AnimatedVisibility would otherwise be picked up.
 */
@Composable
internal fun ExpandingVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = content,
    )
}

/** One tap-to-change parameter in the prompt page's chip row. */
@Composable
internal fun ParamChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    contentDescription: String? = null,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            if (label.isNotEmpty()) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    it,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
        },
        colors = if (highlighted) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        modifier = modifier.height(34.dp),
    )
}

/**
 * Wraps the chip row: chips are 34dp tall, so the 48dp minimum touch size
 * would add a band of empty space around every row of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParamChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

/** A small dialog editing one parameter from the chip row. */
@Composable
internal fun QuickParamDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

/**
 * The bar pinned to the bottom of the prompt page: queue and X/Y on the left,
 * Generate filling the rest. Queue and X/Y stay usable while a run is going,
 * so more work can be lined up behind it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GenerationDock(
    generateEnabled: Boolean,
    extrasEnabled: Boolean,
    isLoading: Boolean,
    onGenerate: () -> Unit,
    onAddToQueue: () -> Unit,
    onXyPlot: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val compact = PaddingValues(horizontal = 12.dp)
            OutlinedButton(
                onClick = onAddToQueue,
                enabled = extrasEnabled,
                contentPadding = compact,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = stringResource(R.string.queue_add),
                    modifier = Modifier.size(20.dp),
                )
            }
            OutlinedButton(
                onClick = onXyPlot,
                enabled = extrasEnabled,
                contentPadding = compact,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("X/Y", modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = onGenerate,
                enabled = generateEnabled,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = tween(Motion.DurationShort)) +
                                scaleIn(initialScale = 0.8f, animationSpec = tween(Motion.DurationShort))
                            ).togetherWith(
                            fadeOut(animationSpec = tween(Motion.DurationShort)) +
                                scaleOut(targetScale = 0.8f, animationSpec = tween(Motion.DurationShort)),
                        )
                    },
                    label = "GenerateButtonContent",
                ) { loading ->
                    if (loading) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.generate_image))
                    }
                }
            }
        }
    }
}

/**
 * The fixed-height progress slot: live preview on the left (tap to enlarge),
 * status, progress and a stop button on the right. Its height never changes
 * between "no preview yet" and "preview showing", so nothing around it moves.
 */
@Composable
internal fun GenerationProgressSlot(
    progress: () -> Float,
    preview: Bitmap?,
    batchIndex: Int,
    batchTotal: Int,
    queuedCount: Int,
    startTime: Long?,
    onPreviewClick: () -> Unit,
    onStop: () -> Unit,
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProgressSlotHeight)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPreviewClick,
                enabled = preview != null,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(ProgressSlotHeight - 16.dp),
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.live_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append(stringResource(R.string.generating))
                            if (batchIndex > 0 && batchTotal > 1) append(" ($batchIndex/$batchTotal)")
                            append("…")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(stringResource(R.string.stop))
                    }
                }
                val p = progress()
                SmoothLinearWavyProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth())
                // Rough time left for this image, from the elapsed time and
                // progress so far (skipped until there is a signal).
                val eta = startTime?.let { start ->
                    val elapsed = System.currentTimeMillis() - start
                    if (p in 0.05f..0.999f) (elapsed * (1f - p) / p / 1000f).roundToInt() else null
                }
                val status = listOfNotNull(
                    "${(p * 100).toInt()}%",
                    eta?.let { stringResource(R.string.time_left, formatSeconds(it)) },
                    queuedCount.takeIf { it > 0 }?.let { stringResource(R.string.queue_pending, it) },
                ).joinToString(" · ")
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ProgressSlotHeight = 104.dp

/** "42s" / "3m 05s" for the progress slot's time-left estimate. */
internal fun formatSeconds(seconds: Int): String = if (seconds < 60) {
    "${seconds}s"
} else {
    String.format(Locale.US, "%dm %02ds", seconds / 60, seconds % 60)
}
