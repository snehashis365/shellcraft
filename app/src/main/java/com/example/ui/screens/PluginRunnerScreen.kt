package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CliPlugin
import com.example.domain.model.GitHubRepoConfig
import com.example.domain.model.ParameterType
import com.example.domain.model.PluginAction
import com.example.domain.model.PluginCommandBuilder
import com.example.domain.model.PluginParameter
import com.example.domain.model.PluginSource
import com.example.domain.model.RiskLevel
import com.example.ui.TerminalSessionState
import com.example.ui.components.CommandPreviewBox
import com.example.ui.components.GlowSurfaceCard
import com.example.ui.components.InteractiveStdinPanel
import com.example.ui.components.RichCommandOutputView
import com.example.ui.components.RiskBadge
import com.example.ui.components.SourceChip
import com.example.ui.components.riskColor
import com.example.ui.components.springPressScale
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRunnerScreen(
    plugin: CliPlugin,
    githubConfig: GitHubRepoConfig,
    isSshConnected: Boolean,
    terminalState: TerminalSessionState,
    onBack: () -> Unit,
    onExecute: (command: String, action: PluginAction, elevateSudo: Boolean) -> Unit,
    onSendInput: (text: String, isSecret: Boolean) -> Unit,
    onSendControl: (ctrlByte: Int, label: String) -> Unit,
    onExpandTerminal: () -> Unit,
    onPublishToGitHub: (CliPlugin) -> Unit,
    onExportJson: (CliPlugin) -> String,
    onDeletePlugin: (String) -> Unit,
    onNotify: (String) -> Unit,
    onHapticTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var selectedActionId by remember(plugin.id) {
        mutableStateOf(plugin.actions.firstOrNull()?.id.orEmpty())
    }
    val selectedAction = plugin.actions.find { it.id == selectedActionId } ?: plugin.actions.firstOrNull()

    val paramValues = remember(plugin.id, selectedAction?.id) {
        mutableStateMapOf<String, String>().apply {
            selectedAction?.parameters?.forEach { p ->
                put(p.id, p.defaultValue)
            }
        }
    }

    var elevateSudo by remember(plugin.id, selectedAction?.id) {
        mutableStateOf(selectedAction?.requiresSudo == true)
    }

    val resolvedCommand = remember(plugin, selectedAction, paramValues.toMap(), elevateSudo) {
        if (selectedAction == null) plugin.commandName
        else PluginCommandBuilder.buildCommand(plugin, selectedAction, paramValues, elevateSudo)
    }

    val missingRequired = remember(selectedAction, paramValues.toMap()) {
        selectedAction?.parameters?.filter { it.required && paramValues[it.id].isNullOrBlank() }.orEmpty()
    }

    val showInlineOutput = terminalState.pluginId == plugin.id &&
        (terminalState.isRunning || terminalState.output.isNotBlank() || !terminalState.errorMessage.isNullOrBlank())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(plugin.displayName, style = MaterialTheme.typography.titleLarge)
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
                    if (plugin.source != PluginSource.BUILT_IN) {
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
                    .widthIn(max = 840.dp),
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

                // Rich Interactive Action Cards Carousel
                item {
                    Text("Select Command Mode", style = MaterialTheme.typography.titleMedium)
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

                    if (selectedAction.parameters.isNotEmpty()) {
                        item {
                            Text("Configure Parameters", style = MaterialTheme.typography.titleMedium)
                        }

                        items(selectedAction.parameters, key = { "${selectedAction.id}_${it.id}" }) { param ->
                            DynamicParameterControl(
                                param = param,
                                value = paramValues[param.id] ?: param.defaultValue,
                                onValueChange = { newValue ->
                                    paramValues[param.id] = newValue
                                },
                                onHapticTick = onHapticTick
                            )
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
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
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

                    // Inline Rich Formatted Output View
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicParameterControl(
    param: PluginParameter,
    value: String,
    onValueChange: (String) -> Unit,
    onHapticTick: () -> Unit
) {
    when (param.type) {
        ParameterType.BOOLEAN_FLAG -> {
            val checked = value.equals("true", ignoreCase = true)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(param.label, style = MaterialTheme.typography.titleSmall)
                        val helper = listOfNotNull(
                            param.flag?.let { "Flag: $it" },
                            param.description.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (helper.isNotBlank()) {
                            Text(
                                text = helper,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            onHapticTick()
                            onValueChange(it.toString())
                        },
                        modifier = Modifier.testTag("param_switch_${param.id}")
                    )
                }
            }
        }

        ParameterType.SELECT_ENUM -> {
            if (param.options.size in 1..4) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(param.label, style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        param.options.forEach { option ->
                            FilterChip(
                                selected = value == option,
                                onClick = {
                                    onHapticTick()
                                    onValueChange(option)
                                },
                                label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            } else {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(param.label) },
                        supportingText = param.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("param_select_${param.id}")
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        param.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onHapticTick()
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        ParameterType.NUMBER, ParameterType.PORT -> {
            val currentInt = value.toIntOrNull() ?: 0
            val step = if (param.type == ParameterType.PORT) 1 else if (currentInt >= 50) 10 else 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        onHapticTick()
                        onValueChange((currentInt - step).coerceAtLeast(0).toString())
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease ${param.label}")
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(it.filter(Char::isDigit)) },
                    label = { Text(if (param.required) "${param.label} *" else param.label) },
                    placeholder = { Text(param.placeholder.ifBlank { param.flag.orEmpty() }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("param_number_${param.id}")
                )
                FilledTonalIconButton(
                    onClick = {
                        onHapticTick()
                        onValueChange((currentInt + step).toString())
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase ${param.label}")
                }
            }
        }

        ParameterType.PATH -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(if (param.required) "${param.label} *" else param.label) },
                    placeholder = { Text(param.placeholder.ifBlank { "/var/log/" }) },
                    supportingText = param.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("param_text_${param.id}")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("~/", "/var/log/", "/etc/", ".").forEach { quickPath ->
                        FilterChip(
                            selected = value == quickPath,
                            onClick = {
                                onHapticTick()
                                onValueChange(quickPath)
                            },
                            label = {
                                Text(
                                    quickPath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        )
                    }
                }
            }
        }

        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(if (param.required) "${param.label} *" else param.label) },
                placeholder = { Text(param.placeholder.ifBlank { param.flag.orEmpty() }) },
                supportingText = param.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("param_text_${param.id}")
            )
        }
    }
}
