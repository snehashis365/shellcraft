package com.snehashis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.SyncStatus
import com.snehashis.ui.components.SectionHeader
import com.snehashis.ui.components.SourceChip
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun PluginsScreen(
    plugins: List<CliPlugin>,
    githubConfig: GitHubRepoConfig,
    syncStatus: SyncStatus,
    onSyncNow: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    onOpenSchemaDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(plugins) {
        listOf("All") + plugins.map { it.category }.distinct().sorted()
    }

    val filteredPlugins = remember(plugins, searchQuery, selectedCategory) {
        plugins.filter { plugin ->
            val matchesCategory = selectedCategory == "All" ||
                plugin.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                plugin.commandName.contains(searchQuery, ignoreCase = true) ||
                plugin.displayName.contains(searchQuery, ignoreCase = true) ||
                plugin.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = "Plugin Ecosystem",
                    subtitle = "${plugins.size} graphical CLI interfaces ready",
                    trailing = {
                        OutlinedButton(
                            onClick = onOpenSchemaDocs,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("open_schema_docs_button")
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Spec / Import")
                        }
                    }
                )
            }

            item {
                SyncStatusBanner(
                    githubConfig = githubConfig,
                    syncStatus = syncStatus,
                    onSyncNow = onSyncNow
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search command, tool, or description...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plugin_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                        )
                    }
                }
            }

            items(filteredPlugins, key = { it.id }) { plugin ->
                PluginCatalogCard(
                    plugin = plugin,
                    onClick = { onOpenPlugin(plugin.id) }
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBanner(
    githubConfig: GitHubRepoConfig,
    syncStatus: SyncStatus,
    onSyncNow: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (githubConfig.isConfigured) Icons.Filled.CloudSync else Icons.Filled.Storage,
                    contentDescription = null,
                    tint = if (githubConfig.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text(
                        text = if (githubConfig.isConfigured) {
                            "GitHub Repo: ${githubConfig.ownerRepo}"
                        } else {
                            "Local Storage Mode"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when (syncStatus) {
                            SyncStatus.LocalOnly -> "No repo configured; plugins saved locally in Room"
                            SyncStatus.Syncing -> "Synchronizing plugin manifests..."
                            is SyncStatus.Success -> "Synced ${syncStatus.syncedCount} plugins (${githubConfig.syncPolicy.name})"
                            is SyncStatus.Error -> syncStatus.message
                            SyncStatus.Idle -> "Sync policy: ${githubConfig.syncPolicy.name}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus is SyncStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (githubConfig.isConfigured) {
                if (syncStatus is SyncStatus.Syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(
                        onClick = onSyncNow,
                        modifier = Modifier.heightIn(min = 44.dp).testTag("sync_github_button")
                    ) {
                        Text("Sync")
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCatalogCard(
    plugin: CliPlugin,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("plugin_card_${plugin.commandName}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = plugin.commandName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = plugin.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SourceChip(source = plugin.source)
            }

            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${plugin.category} · ${plugin.targetOs.name} · ${plugin.actions.size} actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open ${plugin.displayName}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
