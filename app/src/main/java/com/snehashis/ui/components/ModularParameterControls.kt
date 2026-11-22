package com.snehashis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.ParameterWidget
import com.snehashis.domain.model.PluginParameter
import com.snehashis.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModularParameterControl(
    param: PluginParameter,
    value: String,
    isSshConnected: Boolean,
    isUploadingLocal: Boolean,
    onValueChange: (String) -> Unit,
    onBrowseRemote: () -> Unit,
    onUploadLocal: () -> Unit,
    onHapticTick: () -> Unit
) {
    val effectiveWidget = remember(param) {
        when {
            param.widget != ParameterWidget.AUTO -> param.widget
            param.type == ParameterType.PATH -> ParameterWidget.FILE_PATH
            param.type == ParameterType.BOOLEAN_FLAG -> ParameterWidget.TOGGLE_CARD
            param.type == ParameterType.SELECT_ENUM && param.options.size in 1..6 -> ParameterWidget.SEGMENTED_CHOICE
            param.type == ParameterType.NUMBER && param.minValue != null && param.maxValue != null -> ParameterWidget.SLIDER
            else -> ParameterWidget.AUTO
        }
    }

    when {
        param.type == ParameterType.BOOLEAN_FLAG || effectiveWidget == ParameterWidget.TOGGLE_CARD -> {
            val checked = value.equals("true", ignoreCase = true)
            Surface(
                color = if (checked) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
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

        param.type == ParameterType.SELECT_ENUM -> {
            if (param.options.size in 1..6 || effectiveWidget == ParameterWidget.SEGMENTED_CHOICE) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(param.label, style = MaterialTheme.typography.titleSmall)
                    if (param.description.isNotBlank()) {
                        Text(
                            text = param.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                                label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("param_choice_${param.id}_$option")
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

        effectiveWidget == ParameterWidget.SLIDER && param.type == ParameterType.NUMBER -> {
            val min = param.minValue ?: 1f
            val max = (param.maxValue ?: 100f).coerceAtLeast(min + 1f)
            val step = (param.stepValue ?: 1f).coerceAtLeast(1f)
            val numericVal = value.toFloatOrNull()?.coerceIn(min, max) ?: min

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(param.label, style = MaterialTheme.typography.titleSmall)
                            if (param.description.isNotBlank()) {
                                Text(
                                    text = param.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            val badgeText = buildString {
                                append(numericVal.roundToInt())
                                if (param.unitLabel.isNotBlank()) append(" ${param.unitLabel}")
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                onHapticTick()
                                val next = (numericVal - step).coerceIn(min, max)
                                onValueChange(next.roundToInt().toString())
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease ${param.label}")
                        }
                        Slider(
                            value = numericVal,
                            onValueChange = { raw ->
                                val snapped = (((raw - min) / step).roundToInt() * step + min).coerceIn(min, max)
                                onValueChange(snapped.roundToInt().toString())
                            },
                            valueRange = min..max,
                            modifier = Modifier.weight(1f).testTag("param_slider_${param.id}")
                        )
                        FilledTonalIconButton(
                            onClick = {
                                onHapticTick()
                                val next = (numericVal + step).coerceIn(min, max)
                                onValueChange(next.roundToInt().toString())
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase ${param.label}")
                        }
                    }
                }
            }
        }

        param.type == ParameterType.NUMBER || param.type == ParameterType.PORT -> {
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

        param.type == ParameterType.PATH || effectiveWidget == ParameterWidget.FILE_PATH -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            onHapticTick()
                            onBrowseRemote()
                        },
                        enabled = isSshConnected,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("browse_remote_${param.id}")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Host", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            onHapticTick()
                            onUploadLocal()
                        },
                        enabled = isSshConnected && !isUploadingLocal,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("upload_local_${param.id}")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Local", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
