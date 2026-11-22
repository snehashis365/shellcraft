package com.snehashis.ui.components

import androidx.compose.foundation.BorderStroke
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.snehashis.data.ssh.RemoteFileEntry
import com.snehashis.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

@Composable
fun SftpBrowserDialog(
    initialPath: String,
    parameterLabel: String,
    onListDirectory: suspend (String) -> Result<Pair<String, List<RemoteFileEntry>>>,
    onSelectPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(initialPath.ifBlank { "~" }) }
    var entries by remember { mutableStateOf<List<RemoteFileEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }

    fun loadDir(path: String) {
        scope.launch {
            isLoading = true
            errorMsg = null
            onListDirectory(path)
                .onSuccess { (resolved, items) ->
                    currentPath = resolved
                    entries = items
                }
                .onFailure { err ->
                    errorMsg = err.localizedMessage ?: "Failed to read remote directory"
                }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadDir(initialPath.ifBlank { "~" })
    }

    val filtered = remember(entries, searchQuery, showHidden) {
        entries.filter { entry ->
            val hiddenOk = showHidden || !entry.name.startsWith(".")
            val searchOk = searchQuery.isBlank() || entry.name.contains(searchQuery, ignoreCase = true)
            hiddenOk && searchOk
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(min = 400.dp, max = 660.dp)
                .testTag("remote_sftp_browser")
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
                            text = "Remote Host Files",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Pick path for: $parameterLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(
                            imageVector = if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle hidden dotfiles"
                        )
                    }
                    IconButton(onClick = { loadDir(currentPath) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh directory")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close browser")
                    }
                }

                // Bookmarks & Up
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPath != "/") {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val parent = currentPath.substringBeforeLast('/').ifEmpty { "/" }
                                loadDir(parent)
                            },
                            label = { Text("Up") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            modifier = Modifier.testTag("sftp_up_button")
                        )
                    }
                    listOf("~", "/", "/etc", "/var/log", "/tmp").forEach { bookmark ->
                        FilterChip(
                            selected = currentPath == bookmark,
                            onClick = { loadDir(bookmark) },
                            label = {
                                Text(
                                    bookmark,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        )
                    }
                }

                // Current resolved PWD bar + use directory button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSelectPath(currentPath)
                                onDismiss()
                            },
                            modifier = Modifier.heightIn(min = 40.dp).testTag("sftp_select_folder_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use Dir")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter files in current folder...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Listing $currentPath over SFTP...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        !errorMsg.isNullOrBlank() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = errorMsg!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(onClick = { loadDir("~") }) {
                                    Text("Return Home (~)")
                                }
                            }
                        }
                        filtered.isEmpty() -> {
                            Text(
                                text = "Directory is empty or filtered out.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filtered, key = { it.path }) { entry ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                if (entry.isDirectory) {
                                                    searchQuery = ""
                                                    loadDir(entry.path)
                                                } else {
                                                    onSelectPath(entry.path)
                                                    onDismiss()
                                                }
                                            }
                                            .testTag("sftp_entry_${entry.name}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                                    contentDescription = null,
                                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = entry.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontFamily = JetBrainsMonoFontFamily,
                                                            fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (entry.isDirectory) {
                                                            "DIR · ${entry.permissions}"
                                                        } else {
                                                            "${formatFileSize(entry.sizeBytes)} · ${entry.permissions}"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (entry.isDirectory) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onSelectPath(entry.path)
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier.heightIn(min = 36.dp)
                                                ) {
                                                    Text("Pick")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
    else -> String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
}
