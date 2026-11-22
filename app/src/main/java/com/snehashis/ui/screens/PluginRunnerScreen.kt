package com.snehashis.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.data.ssh.RemoteFileEntry
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginCommandBuilder
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.ui.TerminalSessionState
import com.snehashis.ui.components.AiRefineChatDialog
import com.snehashis.ui.components.CommandPreviewBox
import com.snehashis.ui.components.CustomHtmlPluginView
import com.snehashis.ui.components.GlowSurfaceCard
import com.snehashis.ui.components.InteractiveStdinPanel
import com.snehashis.ui.components.ModularParameterControl
import com.snehashis.ui.components.RichCommandOutputView
import com.snehashis.ui.components.RiskBadge
import com.snehashis.ui.components.SftpBrowserDialog
import com.snehashis.ui.components.SourceChip
import com.snehashis.ui.components.riskColor
import com.snehashis.ui.components.springPressScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRunnerScreen(
    plugin: CliPlugin,
    githubConfig: GitHubRepoConfig,
    isSshConnected: Boolean,
    terminalState: TerminalSessionState,
    sftpUploadStatus: Pair<String, Float>? = null,
    onBack: () -> Unit,
    onExecute: (command: String, action: PluginAction, elevateSudo: Boolean) -> Unit,
    onSendInput: (text: String, isSecret: Boolean) -> Unit,
    onSendControl: (ctrlByte: Int, label: String) -> Unit,
    onExpandTerminal: () -> Unit,
    onListRemoteDirectory: suspend (String) -> Result<Pair<String, List<RemoteFileEntry>>> = {
        Result.failure(IllegalStateException("SFTP unavailable"))
    },
    onUploadPhoneFile: suspend (Uri) -> Result<String> = {
        Result.failure(IllegalStateException("Upload unavailable"))
    },
    onRequestAiRefinement: suspend (CliPlugin, List<PluginChatTurn>, String) -> Result<Pair<CliPlugin, String>> = { p, _, _ ->
        Result.success(p to "No changes")
    },
    onSaveRefinedPlugin: (CliPlugin) -> Unit = {},
    onResetBuiltinPlugin: (String) -> Unit = {},
    onPublishToGitHub: (CliPlugin) -> Unit,
    onExportJson: (CliPlugin) -> String,
    onDeletePlugin: (String) -> Unit,
    onNotify: (String) -> Unit,
    onHapticTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedActionId by remember(plugin.id) {
        mutableStateOf(plugin.actions.firstOrNull()?.id.orEmpty())
    }
    val selectedAction = plugin.actions.find { it.id == selectedActionId } ?: plugin.actions.firstOrNull()

    val paramValues = remember(plugin.id, selectedAction?.id, plugin.updatedAtEpochMs) {
        mutableStateMapOf<String, String>().apply {
            selectedAction?.parameters?.forEach { p ->
                put(p.id, p.defaultValue)
            }
        }
    }

    var elevateSudo by remember(plugin.id, selectedAction?.id) {
        mutableStateOf(selectedAction?.requiresSudo == true)
    }

    var activeViewMode by remember(plugin.id, plugin.uiMode) {
        mutableStateOf(
            if (plugin.uiMode == PluginGuiStyle.HTML_CANVAS_CUSTOM && !plugin.customHtmlTemplate.isNullOrBlank()) {
                PluginGuiStyle.HTML_CANVAS_CUSTOM
            } else {
                PluginGuiStyle.MODULAR_DASHBOARD
            }
        )
    }

    var showAdvancedParams by remember(plugin.id, selectedAction?.id) { mutableStateOf(false) }
    var showAiRefineDialog by remember { mutableStateOf(false) }
    var browsingParamTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // paramId to label
    var pendingLocalPickParamId by remember { mutableStateOf<String?>(null) }
    var lastPickedForHtml by remember { mutableStateOf<Pair<String, String>?>(null) }

    val localFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetParam = pendingLocalPickParamId
        pendingLocalPickParamId = null
        if (uri == null || targetParam == null) return@rememberLauncherForActivityResult
        scope.launch {
            onUploadPhoneFile(uri)
                .onSuccess { remotePath ->
                    paramValues[targetParam] = remotePath
                    lastPickedForHtml = targetParam to remotePath
                }
                .onFailure { err ->
                    onNotify(err.localizedMessage ?: "Failed to upload phone file")
                }
        }
    }

    val resolvedCommand = remember(plugin, selectedAction, paramValues.toMap(), elevateSudo) {
        if (selectedAction == null) plugin.commandName
        else PluginCommandBuilder.buildCommand(plugin, selectedAction, paramValues, elevateSudo)
    }

    val missingRequired = remember(selectedAction, paramValues.toMap()) {
        selectedAction?.parameters?.filter { it.required && paramValues[it.id].isNullOrBlank() }.orEmpty()
    }

    val primaryParams = remember(selectedAction) {
        selectedAction?.parameters?.filter { !it.group.equals("Advanced", ignoreCase = true) }.orEmpty()
    }
    val advancedParams = remember(selectedAction) {
        selectedAction?.parameters?.filter { it.group.equals("Advanced", ignoreCase = true) }.orEmpty()
    }

    val showInlineOutput = terminalState.pluginId == plugin.id &&
        (terminalState.isRunning || terminalState.output.isNotBlank() || !terminalState.errorMessage.isNullOrBlank())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = plugin.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${plugin.commandName} · v${plugin.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("runner_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to plugins")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onHapticTick()
                            showAiRefineDialog = true
                        },
                        modifier = Modifier.testTag("runner_ai_refine_button")
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "Refine GUI with AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            onHapticTick()
                            clipboard.setText(AnnotatedString(onExportJson(plugin)))
                            onNotify("Copied ${plugin.commandName}.json manifest")
                        },
                        modifier = Modifier.testTag("export_plugin_json_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy plugin JSON")
                    }
                    if (githubConfig.isConfigured) {
                        IconButton(
                            onClick = {
                                onHapticTick()
                                onPublishToGitHub(plugin)
                            },
                            modifier = Modifier.testTag("publish_github_button")
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Publish plugin to GitHub")
                        }
                    }
                    if (plugin.source != PluginSource.BUILT_IN && plugin.customFromBuiltinId == null) {
                        IconButton(
                            onClick = {
                                onDeletePlugin(plugin.id)
                                onBack()
                            },
                            modifier = Modifier.testTag("delete_plugin_button")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete plugin",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("runner_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plugin.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SourceChip(source = plugin.source)
                    }
                }

                if (sftpUploadStatus != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Uploading ${sftpUploadStatus.first} to remote SSH host...",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                LinearProgressIndicator(
                                    progress = { sftpUploadStatus.second },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Hybrid GUI Switcher when Custom HTML/Canvas template is available
                if (!plugin.customHtmlTemplate.isNullOrBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = activeViewMode == PluginGuiStyle.MODULAR_DASHBOARD,
                                onClick = { activeViewMode = PluginGuiStyle.MODULAR_DASHBOARD },
                                label = { Text("Modular Dashboard") },
                                leadingIcon = { Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("runner_mode_modular")
                            )
                            FilterChip(
                                selected = activeViewMode == PluginGuiStyle.HTML_CANVAS_CUSTOM,
                                onClick = { activeViewMode = PluginGuiStyle.HTML_CANVAS_CUSTOM },
                                label = { Text("HTML/Canvas GUI") },
                                leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("runner_mode_html")
                            )
                        }
                    }
                }

                if (activeViewMode == PluginGuiStyle.HTML_CANVAS_CUSTOM && !plugin.customHtmlTemplate.isNullOrBlank()) {
                    item {
                        CustomHtmlPluginView(
                            plugin = plugin,
                            htmlTemplate = plugin.customHtmlTemplate,
                            terminalState = terminalState,
                            pickedFileUpdate = lastPickedForHtml,
                            onExecute = onExecute,
                            onPickRemoteFile = { pId -> browsingParamTarget = pId to pId },
                            onPickLocalFile = { pId ->
                                pendingLocalPickParamId = pId
                                runCatching { localFileLauncher.launch(arrayOf("*/*")) }
                            },
                            onNotify = onNotify
                        )
                    }
                } else {
                    // Quick One-Tap Workflow Presets
                    if (plugin.presets.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick Presets", style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    plugin.presets.forEach { preset ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                                            shape = MaterialTheme.shapes.medium,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .width(186.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .springPressScale(onHapticTick = onHapticTick) {
                                                    selectedActionId = preset.actionId
                                                    val targetAct = plugin.actions.find { it.id == preset.actionId }
                                                    if (targetAct != null) {
                                                        paramValues.clear()
                                                        targetAct.parameters.forEach { p ->
                                                            paramValues[p.id] = preset.values[p.id] ?: p.defaultValue
                                                        }
                                                        elevateSudo = targetAct.requiresSudo
                                                    }
                                                }
                                                .testTag("preset_card_${preset.id}")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Bolt,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = preset.label,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (preset.description.isNotBlank()) {
                                                    Text(
                                                        text = preset.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rich Interactive Action Cards Carousel
                    item {
                        Text("Command Dashboard Mode", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            plugin.actions.forEach { action ->
                                val selected = action.id == selectedAction?.id
                                val accent = if (selected) riskColor(action.riskLevel) else MaterialTheme.colorScheme.outline
                                Surface(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, accent),
                                    modifier = Modifier
                                        .width(196.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .springPressScale(onHapticTick = onHapticTick) {
                                            selectedActionId = action.id
                                        }
                                        .testTag("action_chip_${action.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = action.description.ifBlank { action.subcommand.ifBlank { plugin.commandName } },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        RiskBadge(
                                            risk = action.riskLevel,
                                            requiresSudo = action.requiresSudo
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedAction != null) {
                        item {
                            AnimatedContent(
                                targetState = selectedAction,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "actionDetailTransition"
                            ) { act ->
                                GlowSurfaceCard(
                                    accentColor = riskColor(act.riskLevel),
                                    isPulsing = false,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(act.label, style = MaterialTheme.typography.titleMedium)
                                            RiskBadge(
                                                risk = act.riskLevel,
                                                requiresSudo = elevateSudo || act.requiresSudo
                                            )
                                        }
                                        if (act.description.isNotBlank()) {
                                            Text(
                                                text = act.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        CommandPreviewBox(
                                            command = resolvedCommand,
                                            onCopy = {
                                                onHapticTick()
                                                clipboard.setText(AnnotatedString(resolvedCommand))
                                                onNotify("Copied command to clipboard")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (primaryParams.isNotEmpty()) {
                            item {
                                Text("Primary Controls", style = MaterialTheme.typography.titleMedium)
                            }
                            items(primaryParams, key = { "${selectedAction.id}_${it.id}" }) { param ->
                                ModularParameterControl(
                                    param = param,
                                    value = paramValues[param.id] ?: param.defaultValue,
                                    isSshConnected = isSshConnected,
                                    isUploadingLocal = sftpUploadStatus != null,
                                    onValueChange = { paramValues[param.id] = it },
                                    onBrowseRemote = { browsingParamTarget = param.id to param.label },
                                    onUploadLocal = {
                                        pendingLocalPickParamId = param.id
                                        runCatching { localFileLauncher.launch(arrayOf("*/*")) }
                                    },
                                    onHapticTick = onHapticTick
                                )
                            }
                        }

                        if (advancedParams.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showAdvancedParams = !showAdvancedParams },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("toggle_advanced_params")
                                    ) {
                                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Advanced Options (${advancedParams.size})")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            if (showAdvancedParams) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null
                                        )
                                    }
                                    AnimatedVisibility(visible = showAdvancedParams) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            advancedParams.forEach { param ->
                                                ModularParameterControl(
                                                    param = param,
                                                    value = paramValues[param.id] ?: param.defaultValue,
                                                    isSshConnected = isSshConnected,
                                                    isUploadingLocal = sftpUploadStatus != null,
                                                    onValueChange = { paramValues[param.id] = it },
                                                    onBrowseRemote = { browsingParamTarget = param.id to param.label },
                                                    onUploadLocal = {
                                                        pendingLocalPickParamId = param.id
                                                        runCatching { localFileLauncher.launch(arrayOf("*/*")) }
                                                    },
                                                    onHapticTick = onHapticTick
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        Column {
                                            Text("Elevate with sudo", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "Requires biometric/password verification",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = elevateSudo,
                                        onCheckedChange = {
                                            onHapticTick()
                                            elevateSudo = it
                                        },
                                        modifier = Modifier.testTag("sudo_toggle_switch")
                                    )
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(visible = missingRequired.isNotEmpty()) {
                                Text(
                                    text = "Required: ${missingRequired.joinToString { it.label }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            val isDangerous = selectedAction.riskLevel >= RiskLevel.HIGH || elevateSudo
                            Button(
                                onClick = { onExecute(resolvedCommand, selectedAction, elevateSudo) },
                                enabled = missingRequired.isEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDangerous) riskColor(selectedAction.riskLevel) else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("execute_plugin_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        !isSshConnected -> "Connect SSH Host to Execute"
                                        elevateSudo -> "Authorize & Execute with sudo"
                                        selectedAction.riskLevel >= RiskLevel.HIGH -> "Review & Execute High-Risk Action"
                                        else -> "Execute on Remote Host"
                                    }
                                )
                            }
                        }
                    }
                }

                // Inline Interactive Output View (works for both modes!)
                if (selectedAction != null) {
                    item {
                        AnimatedVisibility(
                            visible = showInlineOutput,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            if (terminalState.isRunning) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            }
                                            Text("Interactive Output", style = MaterialTheme.typography.titleMedium)
                                        }
                                        TextButton(onClick = onExpandTerminal) {
                                            Icon(Icons.Filled.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Expand")
                                        }
                                    }

                                    if (!terminalState.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = terminalState.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        RichCommandOutputView(
                                            command = terminalState.command,
                                            rawOutput = terminalState.output.ifBlank { "Running..." },
                                            exitCode = terminalState.lastResult?.exitCode,
                                            durationMs = terminalState.lastResult?.durationMs,
                                            rowActions = selectedAction.rowActions,
                                            onInvokeRowAction = { rowAction, rowTokens ->
                                                onHapticTick()
                                                val resolved = PluginCommandBuilder.resolveRowActionCommand(
                                                    plugin = plugin,
                                                    rowAction = rowAction,
                                                    contextTokens = rowTokens
                                                )
                                                if (resolved != null) {
                                                    val (targetAct, cmd) = resolved
                                                    selectedActionId = targetAct.id
                                                    onExecute(cmd, targetAct, rowAction.requiresSudo || targetAct.requiresSudo)
                                                } else {
                                                    onNotify("Target action ${rowAction.targetActionId} missing in plugin")
                                                }
                                            },
                                            onCopyRaw = {
                                                clipboard.setText(AnnotatedString(terminalState.output))
                                                onNotify("Copied raw output")
                                            }
                                        )
                                    }

                                    if (terminalState.isRunning) {
                                        InteractiveStdinPanel(
                                            detectedPrompt = terminalState.detectedPrompt,
                                            onSendInput = onSendInput,
                                            onSendControl = onSendControl
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    browsingParamTarget?.let { (paramId, paramLabel) ->
        SftpBrowserDialog(
            initialPath = paramValues[paramId].orEmpty(),
            parameterLabel = paramLabel,
            onListDirectory = onListRemoteDirectory,
            onSelectPath = { chosenPath ->
                paramValues[paramId] = chosenPath
                lastPickedForHtml = paramId to chosenPath
            },
            onDismiss = { browsingParamTarget = null }
        )
    }

    if (showAiRefineDialog) {
        AiRefineChatDialog(
            plugin = plugin,
            isBuiltInOrCustomized = plugin.source == PluginSource.BUILT_IN || plugin.customFromBuiltinId != null,
            onRequestRefinement = onRequestAiRefinement,
            onApplyPlugin = onSaveRefinedPlugin,
            onResetBuiltin = { onResetBuiltinPlugin(plugin.customFromBuiltinId ?: plugin.id) },
            onDismiss = { showAiRefineDialog = false }
        )
    }
}
