package io.github.xororz.localdream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.FuzzyMatcher
import io.github.xororz.localdream.data.TagMatchType
import io.github.xororz.localdream.data.TagSuggestion
import io.github.xororz.localdream.data.tagUnderscoresToSpaces

// Fixed heights of the docked suggestion strip, so the strip doesn't grow or
// shrink (and push the prompt field around) as suggestions come and go.
private val SuggestionStripHeight = 44.dp
private val SuggestionToolbarHeight = 44.dp

/**
 * Outlined prompt input that greys out the part past the token limit.
 *
 * With [fill] it takes whatever height its modifier gives it and scrolls
 * inside; otherwise it shows [lines] lines. Tag suggestions are not drawn
 * here: [PromptSuggestionDock] shows them in a strip above the keyboard, so
 * they never cover the words being typed.
 */
@Composable
fun PromptTagTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = false,
    lines: Int = 2,
    onFocusChanged: (Boolean) -> Unit = {},
    overflowOffset: Int = -1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val outlinedColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    val inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)

    // Grey out the portion of the prompt past the CLIP token limit. The offset
    // is a UTF-16 index from the backend; it may lag the latest keystroke, so
    // the transformation clamps it to the current text length.
    val overflowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val overflowTransformation = remember(overflowOffset, overflowColor) {
        VisualTransformation { text ->
            if (overflowOffset in 0 until text.length) {
                val styled = buildAnnotatedString {
                    append(text.subSequence(0, overflowOffset))
                    withStyle(SpanStyle(color = overflowColor)) {
                        append(text.subSequence(overflowOffset, text.length))
                    }
                }
                TransformedText(styled, OffsetMapping.Identity)
            } else {
                TransformedText(text, OffsetMapping.Identity)
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) },
        enabled = enabled,
        textStyle = inputTextStyle,
        visualTransformation = overflowTransformation,
        // Prompts are tag lists: autocorrect and auto-capitals only get in
        // the way (the docked strip offers real tag completions instead).
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        maxLines = if (fill) Int.MAX_VALUE else lines,
        minLines = if (fill) 1 else lines,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = false,
                visualTransformation = overflowTransformation,
                interactionSource = interactionSource,
                label = label,
                colors = outlinedColors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 14.dp, bottom = 12.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = outlinedColors,
                        shape = MaterialTheme.shapes.medium,
                    )
                },
            )
        },
    )
}

/**
 * The editing strip docked above the keyboard while a prompt is being typed:
 * a fixed-height row of tag suggestions (when the dictionary is available)
 * and the tag toolbar. [progress] draws a thin bar while a generation runs;
 * [trailing] sits at the end of the toolbar (the Generate button).
 */
@Composable
fun PromptSuggestionDock(
    suggestions: List<TagSuggestion>,
    highlightQuery: String?,
    showSuggestions: Boolean,
    onSuggestionClick: (TagSuggestion) -> Unit,
    onAddTag: () -> Unit,
    onClearTag: () -> Unit,
    onIncreaseWeight: () -> Unit,
    onDecreaseWeight: () -> Unit,
    onInsertBreak: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (showSuggestions) {
                // Reset the scroll to the start whenever the list changes: keyed
                // items keep the previously anchored chip in place, so a newly
                // promoted best match would otherwise sit off screen.
                val listState = rememberLazyListState()
                LaunchedEffect(suggestions) {
                    listState.scrollToItem(0)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SuggestionStripHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (suggestions.isEmpty()) {
                        Text(
                            stringResource(R.string.tag_suggestions_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(items = suggestions, key = { it.replacementTag }) { suggestion ->
                            SuggestionPill(
                                suggestion = suggestion,
                                highlightQuery = highlightQuery,
                                onClick = { onSuggestionClick(suggestion) },
                            )
                        }
                    }
                }
            }
            TagActionToolbar(
                onAddTag = onAddTag,
                onClearTag = onClearTag,
                onIncreaseWeight = onIncreaseWeight,
                onDecreaseWeight = onDecreaseWeight,
                onInsertBreak = onInsertBreak,
                onUndo = onUndo,
                onRedo = onRedo,
                undoEnabled = undoEnabled,
                redoEnabled = redoEnabled,
                trailing = trailing,
            )
        }
    }
}

@Composable
private fun TagActionToolbar(
    onAddTag: () -> Unit,
    onClearTag: () -> Unit,
    onIncreaseWeight: () -> Unit,
    onDecreaseWeight: () -> Unit,
    onInsertBreak: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    trailing: @Composable RowScope.() -> Unit,
) {
    // Tonal groups read apart at a glance: tag edits (secondary), weight steps
    // and BREAK (tertiary), undo/redo history (primary).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionToolbarHeight)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.tag_add),
            container = MaterialTheme.colorScheme.secondaryContainer,
            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAddTag,
        )
        ToolbarAction(
            icon = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.tag_clear),
            container = MaterialTheme.colorScheme.secondaryContainer,
            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onClearTag,
        )
        ToolbarAction(
            icon = Icons.Default.ArrowUpward,
            contentDescription = stringResource(R.string.tag_weight_up),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onIncreaseWeight,
        )
        ToolbarAction(
            icon = Icons.Default.ArrowDownward,
            contentDescription = stringResource(R.string.tag_weight_down),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onDecreaseWeight,
        )
        ToolbarAction(
            text = "BREAK",
            contentDescription = stringResource(R.string.tag_insert_break),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onInsertBreak,
            weight = 1.6f,
        )
        ToolbarAction(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = stringResource(R.string.undo),
            container = MaterialTheme.colorScheme.primaryContainer,
            onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onUndo,
            enabled = undoEnabled,
        )
        ToolbarAction(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = stringResource(R.string.redo),
            container = MaterialTheme.colorScheme.primaryContainer,
            onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onRedo,
            enabled = redoEnabled,
        )
        trailing()
    }
}

@Composable
private fun RowScope.ToolbarAction(
    contentDescription: String,
    container: Color,
    onColor: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String? = null,
    enabled: Boolean = true,
    weight: Float = 1f,
) {
    val disabledAlpha = 0.38f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
        color = if (enabled) container else container.copy(alpha = disabledAlpha),
        contentColor = if (enabled) onColor else onColor.copy(alpha = disabledAlpha),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                )
            } else if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SuggestionPill(suggestion: TagSuggestion, highlightQuery: String?, onClick: () -> Unit) {
    // Embedding names round-trip into the prompt verbatim; spaces would break
    // the lookup in PromptProcessor (it keys on the lowercase filename stem).
    val isEmbedding = suggestion.matchType == TagMatchType.Embedding
    val displayPrimary = if (isEmbedding) {
        suggestion.primaryText
    } else {
        tagUnderscoresToSpaces(suggestion.primaryText)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isEmbedding) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isEmbedding) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            categoryColor(suggestion.category)
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = highlightMatches(displayPrimary, highlightQuery, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            suggestion.secondaryText?.takeIf { it.isNotBlank() }?.let { secondary ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = highlightMatches(secondary, highlightQuery, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
            if (suggestion.postCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatPostCount(suggestion.postCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MatchTypeBadge(suggestion.matchType)
        }
    }
}

@Composable
private fun MatchTypeBadge(matchType: TagMatchType) {
    val label = when (matchType) {
        TagMatchType.Alias -> stringResource(R.string.tag_alias_label)
        TagMatchType.Correction -> stringResource(R.string.tag_correction_label)
        TagMatchType.Embedding -> stringResource(R.string.tag_embedding_label)
        else -> return
    }
    val container = when (matchType) {
        TagMatchType.Correction -> MaterialTheme.colorScheme.errorContainer
        TagMatchType.Embedding -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = when (matchType) {
        TagMatchType.Correction -> MaterialTheme.colorScheme.onErrorContainer
        TagMatchType.Embedding -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Spacer(Modifier.width(8.dp))
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun categoryColor(category: Int): Color = when (category) {
    1 -> Color(0xFFE53935)

    // artist
    3 -> Color(0xFFAB47BC)

    // copyright
    4 -> Color(0xFF43A047)

    // character
    5 -> Color(0xFFFB8C00)

    // meta
    else -> MaterialTheme.colorScheme.outline // general / unknown
}

private fun formatPostCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1_000}k"
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun normalizeForHighlight(value: String): String = value.lowercase().replace(' ', '_').replace('-', '_')

private fun highlightMatches(text: String, query: String?, highlightColor: Color): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val normQuery = normalizeForHighlight(query.trim())
    if (normQuery.isEmpty()) return AnnotatedString(text)
    // normalizeForHighlight only swaps single chars (no trimming or collapsing),
    // so positions into normText line up one-to-one with text.
    val normText = normalizeForHighlight(text)
    val positions = FuzzyMatcher.positions(normQuery.toCharArray(), normText)
    if (positions == null || positions.isEmpty()) return AnnotatedString(text)
    val matchStyle = SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)
    return buildAnnotatedString {
        var idx = 0
        var p = 0
        while (idx < text.length) {
            if (p < positions.size && positions[p] == idx) {
                val start = idx
                while (p < positions.size && positions[p] == idx) {
                    p++
                    idx++
                }
                withStyle(matchStyle) {
                    append(text.substring(start, idx))
                }
            } else {
                val start = idx
                while (idx < text.length && (p >= positions.size || positions[p] != idx)) idx++
                append(text.substring(start, idx))
            }
        }
    }
}
