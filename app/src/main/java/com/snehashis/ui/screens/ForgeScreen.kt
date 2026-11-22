@file:Suppress("DEPRECATION")

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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.snehashis.domain.model.CliDocCondenser
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.ForgeState
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.ui.components.AiRefineChatDialog
import com.snehashis.ui.components.GlowSurfaceCard
import com.snehashis.ui.components.HeroSceneMode
import com.snehashis.ui.components.RiskBadge
import com.snehashis.ui.components.SectionHeader
import com.snehashis.ui.components.ThreeJsHeroCanvas
import com.snehashis.ui.theme.JetBrainsMonoFontFamily
import com.snehashis.ui.theme.LocalExpressiveAccents

@Composable
fun ForgeScreen(
    forgeState: ForgeState,
    llmConfig: LlmBackendConfig,
    connectionState: SshConnectionState,
    defaultGuiStyle: PluginGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD,
    onDeviceStatus: OnDeviceModelStatus = OnDeviceModelStatus.Checking,
    onAnalyze: (
        commandName: String,
        sourceMode: PluginAnalysisSource,
        manualDocs: String,
        bypassCheck: Boolean,
        guiStyle: PluginGuiStyle,
        customInstructions: String
    ) -> Unit,
    onCancelAnalysis: () -> Unit,
    onSaveAndOpenPlugin: (CliPlugin) -> Unit,
    onUpdateReadyPlugin: (CliPlugin) -> Unit = {},
    onRequestAiRefinement: suspend (
        currentPlugin: CliPlugin,
        history: List<PluginChatTurn>,
        instruction: String,
        rawDocs: String
    ) -> Result<Pair<CliPlugin, String>> = { p, _, _, _ -> Result.success(p to "No changes") },
    onResetForge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accents = LocalExpressiveAccents.current

    var commandName by remember { mutableStateOf("") }
    var sourceMode by remember {
        mutableStateOf(
            if (connectionState is SshConnectionState.Connected) PluginAnalysisSource.SSH_PROBE
            else PluginAnalysisSource.MANUAL_DOCS
        )
    }
    var manualDocs by remember { mutableStateOf("") }
    var pendingStyleBypass by remember { mutableStateOf<Boolean?>(null) }
    var showRefineDialog by remember { mutableStateOf(false) }

    val isBusy = forgeState is ForgeState.CheckingGitHub ||
        forgeState is ForgeState.ProbingSshDocs ||
        forgeState is ForgeState.AnalyzingWithLlm

    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

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

    val activeBackendLabel = when (llmConfig.providerType) {
        LlmProviderType.FIREBASE_GEMINI -> "Gemini (${llmConfig.geminiModel})"
        LlmProviderType.OLLAMA_LOCAL -> "Ollama (${llmConfig.customModelName})"
        LlmProviderType.OPENAI_COMPATIBLE -> "${llmConfig.byokPreset.displayName} (${llmConfig.customModelName})"
        LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> "On-Device Gemini Nano (Exp.)"
    }

    val heroPrimary = when (forgeState) {
        is ForgeState.Error -> MaterialTheme.colorScheme.error
        is ForgeState.Ready -> MaterialTheme.colorScheme.secondary
        is ForgeState.AnalyzingWithLlm, is ForgeState.ProbingSshDocs, is ForgeState.CheckingGitHub -> MaterialTheme.colorScheme.primary
        else -> accents.violet
    }
    val heroSecondary = when (forgeState) {
        is ForgeState.Ready -> accents.violet
        is ForgeState.Error -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val heroStateTag = when {
        isBusy -> "busy"
        forgeState is ForgeState.Error -> "error"
        forgeState is ForgeState.Ready -> "ready"
        else -> "idle"
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
                GlowSurfaceCard(
                    accentColor = heroPrimary,
                    isPulsing = isBusy,
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                    ) {
                        ThreeJsHeroCanvas(
                            mode = HeroSceneMode.FORGE_NEURAL,
                            stateName = heroStateTag,
                            primaryColor = heroPrimary,
                            secondaryColor = heroSecondary,
                            speed = if (isBusy) 1.85f else 0.9f,
                            pulse = if (isBusy) 0.85f else 0.35f,
                            modifier = Modifier
                                .matchParentSize()
                                .testTag("forge_hero_canvas")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = accents.violetContainer.copy(alpha = 0.85f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, accents.violet.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "DEFAULT STUDIO MODE · ${defaultGuiStyle.displayName.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accents.onVioletContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "CLI-to-GUI AI Forge Studio",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Build interactive tool dashboards or custom HTML/Canvas decks and iterate with conversational AI refinement.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forge_command_input")
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forge_manual_docs_input")
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
                        onClick = { pendingStyleBypass = false },
                        enabled = commandName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("forge_analyze_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Interactive GUI Plugin")
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
                            onOpenRefineChat = { showRefineDialog = true },
                            onRegenerateAnyway = { pendingStyleBypass = true },
                            onReset = onResetForge
                        )
                    }
                }
            }
        }
    }

    pendingStyleBypass?.let { bypassFlag ->
        ForgeStylePickerDialog(
            commandName = commandName.trim(),
            defaultStyle = defaultGuiStyle,
            onConfirm = { chosenStyle, instructions ->
                pendingStyleBypass = null
                onAnalyze(commandName, sourceMode, manualDocs, bypassFlag, chosenStyle, instructions)
            },
            onDismiss = { pendingStyleBypass = null }
        )
    }

    val readyState = forgeState as? ForgeState.Ready
    if (showRefineDialog && readyState != null) {
        AiRefineChatDialog(
            plugin = readyState.plugin,
            isBuiltInOrCustomized = false,
            onRequestRefinement = { currentPlugin, history, instruction ->
                onRequestAiRefinement(currentPlugin, history, instruction, readyState.rawDocsUsed)
            },
            onApplyPlugin = { updated -> onUpdateReadyPlugin(updated) },
            onDismiss = { showRefineDialog = false }
        )
    }
}

@Composable
private fun ForgeStylePickerDialog(
    commandName: String,
    defaultStyle: PluginGuiStyle,
    onConfirm: (PluginGuiStyle, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStyle by remember(defaultStyle) { mutableStateOf(defaultStyle) }
    var customInstructions by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forge_style_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Choose GUI Studio Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Target: `$commandName`",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ForgeStyleOptionCard(
                    title = "Modular Tool Dashboard",
                    badge = "Recommended",
                    subtitle = "Native widgets, presets, range sliders, file pickers & inline row action buttons.",
                    icon = Icons.Filled.Dashboard,
                    selected = selectedStyle == PluginGuiStyle.MODULAR_DASHBOARD,
                    onClick = { selectedStyle = PluginGuiStyle.MODULAR_DASHBOARD },
                    tag = "forge_style_modular"
                )

                ForgeStyleOptionCard(
                    title = "Custom HTML/Canvas GUI",
                    badge = "Custom Deck",
                    subtitle = "Sandboxed interactive HTML5/Canvas UI wired through the safe POSIX bridge.",
                    icon = Icons.Filled.Code,
                    selected = selectedStyle == PluginGuiStyle.HTML_CANVAS_CUSTOM,
                    onClick = { selectedStyle = PluginGuiStyle.HTML_CANVAS_CUSTOM },
                    tag = "forge_style_html"
                )

                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = { customInstructions = it },
                    label = { Text("Custom Instructions (Optional)") },
                    placeholder = { Text("e.g. Include --dry-run toggle preset, hide verbose TLS flags, add Kill button") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forge_custom_instructions_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(selectedStyle, customInstructions) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("forge_confirm_generate_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Generation")
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgeStyleOptionCard(
    title: String,
    badge: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        color = if (badge == "Recommended") MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (badge == "Recommended") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    onOpenRefineChat: () -> Unit,
    onRegenerateAnyway: () -> Unit,
    onReset: () -> Unit
) {
    val accents = LocalExpressiveAccents.current
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forge_existing_found_card")
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("use_existing_plugin_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Existing GUI")
                        }
                        OutlinedButton(
                            onClick = onRegenerateAnyway,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("regenerate_anyway_button")
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badges = buildList {
                            add(plugin.uiMode.displayName)
                            add("${plugin.actions.size} Actions")
                            if (plugin.presets.isNotEmpty()) add("${plugin.presets.size} Presets")
                            add(plugin.category)
                            add(plugin.targetOs.name)
                            if (plugin.author.contains("On-Device", ignoreCase = true)) {
                                add("EXP · ON-DEVICE")
                            }
                        }
                        badges.forEach { badge ->
                            val isExp = badge.startsWith("EXP")
                            val isStyle = badge == plugin.uiMode.displayName
                            Surface(
                                color = when {
                                    isExp -> MaterialTheme.colorScheme.tertiaryContainer
                                    isStyle -> accents.violetContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isExp -> MaterialTheme.colorScheme.tertiary
                                        isStyle -> accents.violet
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        isExp -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isStyle -> accents.onVioletContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
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
                                    text = "${action.parameters.size} widget(s) · ${action.rowActions.size} row button(s) · subcommand: `${action.subcommand.ifBlank { "(root)" }}`",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenRefineChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("forge_refine_ai_button")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refine GUI with AI Chat")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveAndOpen(plugin) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_generated_plugin_button")
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
