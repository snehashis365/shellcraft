package com.snehashis.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.snehashis.R
import com.snehashis.domain.model.CliDocCondenser
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.ForgeState
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.ui.components.GlowSurfaceCard
import com.snehashis.ui.components.RiskBadge
import com.snehashis.ui.components.SectionHeader
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ForgeScreen(
    forgeState: ForgeState,
    llmConfig: LlmBackendConfig,
    connectionState: SshConnectionState,
    onDeviceStatus: OnDeviceModelStatus = OnDeviceModelStatus.Checking,
    onAnalyze: (commandName: String, sourceMode: PluginAnalysisSource, manualDocs: String, bypassCheck: Boolean) -> Unit,
    onCancelAnalysis: () -> Unit,
    onSaveAndOpenPlugin: (CliPlugin) -> Unit,
    onResetForge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var commandName by remember { mutableStateOf("") }
    var sourceMode by remember {
        mutableStateOf(
            if (connectionState is SshConnectionState.Connected) PluginAnalysisSource.SSH_PROBE
            else PluginAnalysisSource.MANUAL_DOCS
        )
    }
    var manualDocs by remember { mutableStateOf("") }

    val isBusy = forgeState is ForgeState.CheckingGitHub ||
        forgeState is ForgeState.ProbingSshDocs ||
        forgeState is ForgeState.AnalyzingWithLlm

    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    // Keep screen awake cleanly while in foreground AND generation is active (WakeLock also protects screen-off)
    DisposableEffect(isBusy) {
        val activity = context.findActivity()
        if (isBusy) {
            runCatching { activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        onDispose {
            runCatching { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (!askedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotificationPermission = true
            runCatching { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    val launchForge: (Boolean) -> Unit = { bypass ->
        onAnalyze(commandName, sourceMode, manualDocs, bypass)
    }

    val activeBackendLabel = when (llmConfig.providerType) {
        LlmProviderType.FIREBASE_GEMINI -> "Gemini (${llmConfig.geminiModel})"
        LlmProviderType.OLLAMA_LOCAL -> "Ollama (${llmConfig.customModelName})"
        LlmProviderType.OPENAI_COMPATIBLE -> "Custom LLM (${llmConfig.customModelName})"
        LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> "On-Device Gemini Nano (Exp.)"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("forge_lazy_column"),
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
                                text = "Runs in a sleep-resilient background service so large local models won't drop if your screen locks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Target Command",
                    subtitle = "Backend: $activeBackendLabel · Timeout: ${llmConfig.timeoutSeconds}s"
                )
            }

            if (llmConfig.providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL) {
                item {
                    val statusSummary = when (onDeviceStatus) {
                        OnDeviceModelStatus.Checking -> "AICore: Checking device support..."
                        OnDeviceModelStatus.Available -> "AICore: Gemini Nano ready on-device"
                        OnDeviceModelStatus.Downloadable -> "AICore: Weights downloadable in Settings"
                        is OnDeviceModelStatus.Downloading -> "AICore: Downloading Gemini Nano..."
                        is OnDeviceModelStatus.Unavailable -> "AICore unavailable (${if (llmConfig.onDeviceFallbackEnabled) "Auto-Fallback ON" else "Enable Fallback in Settings"})"
                        is OnDeviceModelStatus.Error -> "AICore error: ${onDeviceStatus.message}"
                    }
                    val tokenEstimateNote = if (sourceMode == PluginAnalysisSource.MANUAL_DOCS && manualDocs.isNotBlank()) {
                        val effectiveDocs = if (llmConfig.onDeviceCondenseDocs) {
                            CliDocCondenser.condense(commandName.ifBlank { "cli" }, manualDocs)
                        } else {
                            manualDocs
                        }
                        val docsTokens = CliDocCondenser.estimateTokens(effectiveDocs)
                        " · Docs ~${docsTokens}t (+prompt <4k limit)"
                    } else {
                        " · Keep app in foreground during Nano inference"
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forge_ondevice_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$statusSummary$tokenEstimateNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = commandName,
                    onValueChange = { commandName = it },
                    label = { Text("CLI Command Name") },
                    placeholder = { Text("e.g. ping, ufw, nginx, git, ffmpeg") },
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
                if (isBusy) {
                    Button(
                        onClick = onCancelAnalysis,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("forge_stop_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Background Generation")
                    }
                } else {
                    Button(
                        onClick = { launchForge(false) },
                        enabled = commandName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("forge_analyze_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Repo & Generate GUI Plugin")
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = forgeState !is ForgeState.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PipelineStepper(forgeState = forgeState)
                        ForgeResultSection(
                            forgeState = forgeState,
                            isOnDeviceBackend = llmConfig.providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL,
                            onSaveAndOpen = onSaveAndOpenPlugin,
                            onRegenerateAnyway = { launchForge(true) },
                            onReset = onResetForge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStepper(forgeState: ForgeState) {
    val activeStep = when (forgeState) {
        is ForgeState.CheckingGitHub -> 1
        is ForgeState.ProbingSshDocs -> 2
        is ForgeState.AnalyzingWithLlm -> 3
        is ForgeState.Ready, is ForgeState.FoundExistingRemote -> 4
        else -> 0
    }
    if (activeStep == 0) return

    val steps = listOf("Repo", "Docs", "AI Synth", "GUI Ready")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { idx, label ->
            val stepNum = idx + 1
            val done = activeStep > stepNum || (activeStep == 4 && stepNum == 4)
            val current = activeStep == stepNum && activeStep < 4
            Surface(
                color = when {
                    done -> MaterialTheme.colorScheme.secondaryContainer
                    current -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = when {
                    done -> MaterialTheme.colorScheme.secondary
                    current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (done) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (current) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ForgeResultSection(
    forgeState: ForgeState,
    isOnDeviceBackend: Boolean,
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
            GlowSurfaceCard(
                accentColor = MaterialTheme.colorScheme.primary,
                isPulsing = true,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(forgeState.statusMessage, style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = if (isOnDeviceBackend) {
                            "Screen kept awake for AICore — keep ShellCraft in the foreground until Gemini Nano finishes."
                        } else {
                            "Foreground Service + WakeLock active — safe if phone screen turns off."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (forgeState.rawDocsPreview.isNotBlank()) {
                        Text(
                            text = forgeState.rawDocsPreview,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4
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
            GlowSurfaceCard(
                accentColor = MaterialTheme.colorScheme.secondary,
                isPulsing = false,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("forge_ready_card")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badges = buildList {
                            add(plugin.category)
                            add("${plugin.actions.size} Actions")
                            add(plugin.targetOs.name)
                            if (plugin.author.contains("On-Device", ignoreCase = true)) {
                                add("EXP · ON-DEVICE")
                            }
                        }
                        badges.forEach { badge ->
                            val isExp = badge.startsWith("EXP")
                            Surface(
                                color = if (isExp) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    1.dp,
                                    if (isExp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isExp) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
