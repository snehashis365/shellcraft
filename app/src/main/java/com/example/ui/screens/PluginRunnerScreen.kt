package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.CliPlugin
import com.example.domain.model.GitHubRepoConfig
import com.example.domain.model.ParameterType
import com.example.domain.model.PluginAction
import com.example.domain.model.PluginCommandBuilder
import com.example.domain.model.PluginParameter
import com.example.domain.model.PluginSource
import com.example.domain.model.RiskLevel
import com.example.ui.components.CommandPreviewBox
import com.example.ui.components.RiskBadge
import com.example.ui.components.SourceChip
import com.example.ui.components.riskColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRunnerScreen(
    plugin: CliPlugin,
    githubConfig: GitHubRepoConfig,
    isSshConnected: Boolean,
    onBack: () -> Unit,
    onExecute: (command: String, action: PluginAction, elevateSudo: Boolean) -> Unit,
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
                            clipboard.setText(AnnotatedString(onExportJson(plugin)))
                            onNotify("Copied ${plugin.commandName}.json manifest")
                        },
                        modifier = Modifier.testTag("export_plugin_json_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy plugin JSON")
                    }
                    if (githubConfig.isConfigured) {
                        IconButton(
                            onClick = { onPublishToGitHub(plugin) },
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

                item {
                    Text("Action", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plugin.actions.forEach { action ->
                            FilterChip(
                                selected = action.id == selectedAction?.id,
                                onClick = {
                                    onHapticTick()
                                    selectedActionId = action.id
                                },
                                label = { Text(action.label) },
                                modifier = Modifier.testTag("action_chip_${action.id}")
                            )
                        }
                    }
                }

                if (selectedAction != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RiskBadge(
                                    risk = selectedAction.riskLevel,
                                    requiresSudo = elevateSudo || selectedAction.requiresSudo
                                )
                                if (selectedAction.description.isNotBlank()) {
                                    Text(
                                        text = selectedAction.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        CommandPreviewBox(
                            command = resolvedCommand,
                            onCopy = {
                                clipboard.setText(AnnotatedString(resolvedCommand))
                                onNotify("Copied command to clipboard")
                            }
                        )
                    }

                    if (selectedAction.parameters.isNotEmpty()) {
                        item {
                            Text("Parameters", style = MaterialTheme.typography.titleMedium)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text("Elevate with sudo", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Requires manual biometric/password authorization",
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

        ParameterType.NUMBER, ParameterType.PORT -> {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(Char::isDigit)) },
                label = { Text(if (param.required) "${param.label} *" else param.label) },
                placeholder = { Text(param.placeholder.ifBlank { param.flag.orEmpty() }) },
                supportingText = param.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("param_number_${param.id}")
            )
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
