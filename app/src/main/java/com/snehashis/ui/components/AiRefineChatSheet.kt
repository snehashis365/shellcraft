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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snehashis.domain.model.ChatRole
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PluginSource
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AiRefineChatDialog(
    plugin: CliPlugin,
    isBuiltInOrCustomized: Boolean,
    onRequestRefinement: suspend (
        currentPlugin: CliPlugin,
        history: List<PluginChatTurn>,
        instruction: String
    ) -> Result<Pair<CliPlugin, String>>,
    onApplyPlugin: (CliPlugin) -> Unit,
    onResetBuiltin: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val revisionStack = remember(plugin.id) { mutableStateListOf(plugin) }
    val currentRevision = revisionStack.lastOrNull() ?: plugin

    val chatHistory = remember(plugin.id) {
        mutableStateListOf(
            PluginChatTurn(
                id = "welcome",
                role = ChatRole.ASSISTANT,
                message = "Describe what to include, exclude, or redesign for `${plugin.displayName}` — e.g. sliders, presets, inline row buttons, or switching between Modular Dashboard and Custom HTML/Canvas."
            )
        )
    }

    var inputPrompt by remember { mutableStateOf("") }
    var isRefining by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val sendPrompt: (String) -> Unit = { rawText ->
        val trimmed = rawText.trim()
        if (trimmed.isNotBlank() && !isRefining) {
            inputPrompt = ""
            errorText = null
            val userTurn = PluginChatTurn(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                message = trimmed
            )
            chatHistory.add(userTurn)
            isRefining = true
            scope.launch {
                val res = onRequestRefinement(currentRevision, chatHistory.toList(), trimmed)
                res.onSuccess { (updated, summary) ->
                    val normalized = if (plugin.source == PluginSource.BUILT_IN || plugin.customFromBuiltinId != null) {
                        updated.copy(
                            id = plugin.id,
                            source = PluginSource.AI_GENERATED,
                            customFromBuiltinId = plugin.customFromBuiltinId ?: plugin.id
                        )
                    } else {
                        updated
                    }
                    revisionStack.add(normalized)
                    chatHistory.add(
                        PluginChatTurn(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            message = summary,
                            pluginSnapshot = normalized
                        )
                    )
                    onApplyPlugin(normalized)
                }.onFailure { err ->
                    errorText = err.localizedMessage ?: "Failed to refine GUI plugin"
                }
                isRefining = false
            }
        }
    }

    val suggestions = remember(currentRevision.uiMode) {
        listOf(
            "Add 2 quick presets for common tasks",
            "Convert numeric inputs into range sliders",
            "Add inline Logs & Restart row buttons on output tables",
            "Hide uncommon flags inside Advanced",
            if (currentRevision.uiMode == PluginGuiStyle.MODULAR_DASHBOARD) {
                "Convert this plugin into a Custom HTML/Canvas GUI"
            } else {
                "Convert back to a native Modular Tool Dashboard"
            }
        )
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
                .heightIn(min = 430.dp, max = 680.dp)
                .testTag("ai_refine_chat_sheet")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("AI GUI Refinement Studio", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${currentRevision.displayName} · v${currentRevision.version} · ${currentRevision.uiMode.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close AI Refine dialog")
                    }
                }

                // Undo / Reset bar
                if (revisionStack.size > 1 || (isBuiltInOrCustomized && onResetBuiltin != null)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (revisionStack.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    revisionStack.removeAt(revisionStack.lastIndex)
                                    val prev = revisionStack.last()
                                    onApplyPlugin(prev)
                                    chatHistory.add(
                                        PluginChatTurn(
                                            id = UUID.randomUUID().toString(),
                                            role = ChatRole.ASSISTANT,
                                            message = "Reverted to v${prev.version}."
                                        )
                                    )
                                },
                                modifier = Modifier.heightIn(min = 42.dp).testTag("ai_refine_undo_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Undo Change")
                            }
                        }
                        if (isBuiltInOrCustomized && onResetBuiltin != null) {
                            TextButton(
                                onClick = {
                                    onResetBuiltin()
                                    onDismiss()
                                },
                                modifier = Modifier.heightIn(min = 42.dp).testTag("ai_refine_reset_builtin_button")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Built-In Default")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatHistory, key = { it.id }) { turn ->
                        val isUser = turn.role == ChatRole.USER
                        Surface(
                            color = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                1.dp,
                                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isUser) "YOU" else "SHELLCRAFT AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Text(text = turn.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (isRefining) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Recompiling GUI controls...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { chipText ->
                        FilterChip(
                            selected = false,
                            onClick = { sendPrompt(chipText) },
                            label = { Text(chipText) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = { Text("Tell AI what to add, remove, or redesign...") },
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_refine_prompt_input")
                    )
                    Button(
                        onClick = { sendPrompt(inputPrompt) },
                        enabled = inputPrompt.isNotBlank() && !isRefining,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("ai_refine_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send AI refinement prompt")
                    }
                }
            }
        }
    }
}
