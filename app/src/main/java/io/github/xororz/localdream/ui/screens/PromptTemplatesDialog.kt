package io.github.xororz.localdream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.xororz.localdream.data.PromptTemplate

/**
 * Saved starting prompts. Each template can replace the prompt fields ("Use")
 * or be appended to them; the current fields can be saved as a new template.
 * Pure UI: the caller owns the list and persistence.
 */
@Composable
internal fun PromptTemplatesDialog(
    templates: List<PromptTemplate>,
    canSaveCurrent: Boolean,
    onApply: (PromptTemplate) -> Unit,
    onAppend: (PromptTemplate) -> Unit,
    onDelete: (PromptTemplate) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PromptTemplate?>(null) }
    val nameTaken = templates.any { it.name == newName.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompt_templates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (templates.isEmpty()) {
                    Text(
                        stringResource(R.string.prompt_templates_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(templates, key = { it.name }) { template ->
                            TemplateRow(
                                template = template,
                                onApply = { onApply(template) },
                                onAppend = { onAppend(template) },
                                onDelete = { pendingDelete = template },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.prompt_template_name)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (nameTaken) {
                                    R.string.prompt_template_overwrite
                                } else {
                                    R.string.prompt_template_save_hint
                                },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        onSaveCurrent(newName.trim())
                        newName = ""
                    },
                    enabled = canSaveCurrent && newName.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.prompt_template_save))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )

    pendingDelete?.let { template ->
        ModelRunConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.prompt_template_delete_confirm, template.name),
            destructiveConfirm = true,
            onConfirm = {
                onDelete(template)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TemplateRow(template: PromptTemplate, onApply: () -> Unit, onAppend: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                template.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        if (template.prompt.isNotBlank()) {
            Text(
                template.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (template.negativePrompt.isNotBlank()) {
            Text(
                template.negativePrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onAppend) { Text(stringResource(R.string.prompt_template_append)) }
            TextButton(onClick = onApply) { Text(stringResource(R.string.prompt_template_use)) }
        }
    }
}
