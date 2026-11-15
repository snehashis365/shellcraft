package com.snehashis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snehashis.domain.model.DiscoveredLlmModel
import com.snehashis.domain.model.ModelCapabilityTag
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ModelPickerDialog(
    providerTitle: String,
    currentModel: String,
    discoveredModels: List<DiscoveredLlmModel>,
    isRefreshing: Boolean,
    onRefreshModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeTagFilter by remember { mutableStateOf<ModelCapabilityTag?>(null) }
    var showCustomInput by remember(discoveredModels) { mutableStateOf(discoveredModels.isEmpty()) }
    var customModelDraft by remember(currentModel) { mutableStateOf(currentModel) }

    val availableTags by remember(discoveredModels) {
        derivedStateOf {
            ModelCapabilityTag.entries.filter { tag ->
                discoveredModels.any { tag in it.tags }
            }
        }
    }

    val filteredModels by remember(discoveredModels, searchQuery, activeTagFilter) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            discoveredModels.filter { model ->
                val matchesTag = activeTagFilter == null || activeTagFilter in model.tags
                val matchesQuery = q.isBlank() ||
                    model.id.lowercase().contains(q) ||
                    model.ownedBy.lowercase().contains(q)
                matchesTag && matchesQuery
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 620.dp)
                .heightIn(max = 660.dp)
                .testTag("model_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Model",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "$providerTitle · ${discoveredModels.size} discovered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshModels,
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("model_picker_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Fetch models from endpoint")
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("model_picker_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close model picker")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name (e.g. coder, flash, r1, llama)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_picker_search")
                )

                if (availableTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeTagFilter == null,
                            onClick = { activeTagFilter = null },
                            label = { Text("All (${discoveredModels.size})") },
                            modifier = Modifier.testTag("model_filter_all")
                        )
                        availableTags.forEach { tag ->
                            val count = discoveredModels.count { tag in it.tags }
                            FilterChip(
                                selected = activeTagFilter == tag,
                                onClick = {
                                    activeTagFilter = if (activeTagFilter == tag) null else tag
                                },
                                label = { Text("${tag.label} ($count)") },
                                modifier = Modifier.testTag("model_filter_${tag.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filteredModels.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(min = 120.dp, max = 340.dp)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredModels, key = { it.id }) { model ->
                            val isSelected = model.id == currentModel
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onSelectModel(model.id)
                                        onDismiss()
                                    }
                                    .testTag("model_option_${model.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = model.id,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (model.ownedBy.isNotBlank() || model.tags.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (model.ownedBy.isNotBlank()) {
                                                    Text(
                                                        text = model.ownedBy,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                model.tags.forEach { tag ->
                                                    CapabilityBadge(tag)
                                                }
                                            }
                                        }
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (discoveredModels.isEmpty()) {
                                    "No models loaded yet"
                                } else {
                                    "No matching models"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (discoveredModels.isEmpty()) {
                                    "Tap Refresh above or 'Test & Fetch Models' in Settings after entering your endpoint & API key, or enter a custom model ID below."
                                } else {
                                    "Try clearing your search filter or enter a custom model ID manually."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showCustomInput = !showCustomInput },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("model_picker_custom_toggle")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showCustomInput) "Hide Custom ID" else "Enter Custom Model ID")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                }

                AnimatedVisibility(visible = showCustomInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModelDraft,
                            onValueChange = { customModelDraft = it },
                            label = { Text("Custom Model ID") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("model_picker_custom_input")
                        )
                        Button(
                            onClick = {
                                if (customModelDraft.isNotBlank()) {
                                    onSelectModel(customModelDraft.trim())
                                    onDismiss()
                                }
                            },
                            enabled = customModelDraft.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("model_picker_custom_apply")
                        ) {
                            Text("Use")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(tag: ModelCapabilityTag) {
    val (bg, fg) = when (tag) {
        ModelCapabilityTag.FREE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        ModelCapabilityTag.CODER -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        ModelCapabilityTag.REASONING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        ModelCapabilityTag.FAST -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = tag.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
