package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CliPlugin
import com.example.domain.model.ForgeState
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.LlmProviderType
import com.example.domain.model.PluginAnalysisSource
import com.example.domain.model.SshConnectionState
import com.example.ui.components.RiskBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ForgeScreen(
    forgeState: ForgeState,
    llmConfig: LlmBackendConfig,
    connectionState: SshConnectionState,
    onAnalyze: (commandName: String, sourceMode: PluginAnalysisSource, manualDocs: String, bypassCheck: Boolean) -> Unit,
    onSaveAndOpenPlugin: (CliPlugin) -> Unit,
    onResetForge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandName by remember { mutableStateOf("") }
    var sourceMode by remember {
        mutableStateOf(
            if (connectionState is SshConnectionState.Connected) PluginAnalysisSource.SSH_PROBE
            else PluginAnalysisSource.MANUAL_DOCS
        )
    }
    var manualDocs by remember { mutableStateOf("") }

    val activeBackendLabel = when (llmConfig.providerType) {
        LlmProviderType.FIREBASE_GEMINI -> "Gemini (${llmConfig.geminiModel})"
        LlmProviderType.OLLAMA_LOCAL -> "Ollama (${llmConfig.customModelName})"
        LlmProviderType.OPENAI_COMPATIBLE -> "Custom LLM (${llmConfig.customModelName})"
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(146.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_forge_banner),
                        contentDescription = "AI Forge banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "CLI-to-GUI AI Forge",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Checks GitHub & local cache first, then synthesizes typed GUI plugins from man/--help.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "Target Command",
                        subtitle = "Active Backend: $activeBackendLabel"
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = commandName,
                    onValueChange = { commandName = it },
                    label = { Text("CLI Command Name") },
                    placeholder = { Text("e.g. ufw, nginx, git, ffmpeg, kubectl") },
                    leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("forge_command_input")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sourceMode == PluginAnalysisSource.SSH_PROBE,
                        onClick = { sourceMode = PluginAnalysisSource.SSH_PROBE },
                        label = { Text("Probe Active SSH") },
                        leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("forge_source_ssh_chip")
                    )
                    FilterChip(
                        selected = sourceMode == PluginAnalysisSource.MANUAL_DOCS,
                        onClick = { sourceMode = PluginAnalysisSource.MANUAL_DOCS },
                        label = { Text("Paste man / --help") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("forge_source_docs_chip")
                    )
                }
            }

            if (sourceMode == PluginAnalysisSource.MANUAL_DOCS) {
                item {
                    OutlinedTextField(
                        value = manualDocs,
                        onValueChange = { manualDocs = it },
                        label = { Text("CLI Help / Man Page Output") },
                        placeholder = { Text("Paste `--help` or `man` text here to generate a plugin offline or without SSH...") },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth().testTag("forge_manual_docs_input")
                    )
                }
            } else {
                item {
                    val connected = connectionState as? SshConnectionState.Connected
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (connected != null) {
                                "Will probe `man $commandName` and `$commandName --help` on ${connected.host.label} (${connected.env.osName})."
                            } else {
                                "No SSH session active. Connect a host in Workspace or switch to Paste man / --help."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                val isBusy = forgeState is ForgeState.CheckingGitHub ||
                    forgeState is ForgeState.ProbingSshDocs ||
                    forgeState is ForgeState.AnalyzingWithLlm

                Button(
                    onClick = { onAnalyze(commandName, sourceMode, manualDocs, false) },
                    enabled = !isBusy && commandName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("forge_analyze_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isBusy) "Synthesizing Plugin..." else "Check Repo & Generate GUI Plugin")
                }
            }

            item {
                ForgeResultSection(
                    forgeState = forgeState,
                    onSaveAndOpen = onSaveAndOpenPlugin,
                    onRegenerateAnyway = {
                        onAnalyze(commandName, sourceMode, manualDocs, true)
                    },
                    onReset = onResetForge
                )
            }
        }
    }
}

@Composable
private fun ForgeResultSection(
    forgeState: ForgeState,
    onSaveAndOpen: (CliPlugin) -> Unit,
    onRegenerateAnyway: () -> Unit,
    onReset: () -> Unit
) {
    when (forgeState) {
        ForgeState.Idle -> Unit

        is ForgeState.CheckingGitHub -> {
            StatusCard("Checking GitHub & local cache for `${forgeState.commandName}.json`...")
        }

        is ForgeState.ProbingSshDocs -> {
            StatusCard("Probing `man` and `--help` over SSH for `${forgeState.commandName}`...")
        }

        is ForgeState.AnalyzingWithLlm -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(forgeState.statusMessage, style = MaterialTheme.typography.titleSmall)
                    }
                    if (forgeState.rawDocsPreview.isNotBlank()) {
                        Text(
                            text = forgeState.rawDocsPreview,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5
                        )
                    }
                }
            }
        }

        is ForgeState.FoundExistingRemote -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().testTag("forge_existing_found_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "Existing Plugin Found (Zero LLM Cost)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Found `${forgeState.plugin.displayName}` (${forgeState.plugin.actions.size} actions) in ${forgeState.sourcePath}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveAndOpen(forgeState.plugin) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("use_existing_plugin_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Existing GUI")
                        }
                        OutlinedButton(
                            onClick = onRegenerateAnyway,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("regenerate_anyway_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                    }
                }
            }
        }

        is ForgeState.Ready -> {
            val plugin = forgeState.plugin
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().testTag("forge_ready_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Generated: ${plugin.displayName} (${plugin.commandName})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            plugin.category,
                            "${plugin.actions.size} Actions",
                            plugin.targetOs.name
                        ).forEach { badge ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    plugin.actions.forEach { action ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.titleSmall)
                                    RiskBadge(risk = action.riskLevel, requiresSudo = action.requiresSudo)
                                }
                                Text(
                                    text = "${action.parameters.size} parameter(s) · subcommand: `${action.subcommand.ifBlank { "(root)" }}`",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveAndOpen(plugin) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_generated_plugin_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Launch GUI")
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        is ForgeState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = forgeState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
