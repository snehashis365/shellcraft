package com.snehashis.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.core.security.SecretCipher
import com.snehashis.data.local.SecuritySettings
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.GitHubSyncPolicy
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelDiscoveryState
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.ui.components.ModelPickerDialog
import com.snehashis.ui.components.SectionHeader
import com.snehashis.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    llmConfig: LlmBackendConfig,
    githubConfig: GitHubRepoConfig,
    securitySettings: SecuritySettings,
    onDeviceStatus: OnDeviceModelStatus = OnDeviceModelStatus.Checking,
    onRefreshOnDeviceStatus: () -> Unit = {},
    onDownloadOnDeviceModel: () -> Unit = {},
    onSaveLlmConfig: (
        LlmProviderType,
        ByokProviderPreset,
        String,
        String,
        String,
        String?,
        Int,
        Boolean,
        LlmProviderType,
        Boolean,
        Float
    ) -> Unit,
    onTestLlmConfig: suspend (LlmBackendConfig) -> Result<EndpointTestResult>,
    modelDiscoveryState: ModelDiscoveryState = ModelDiscoveryState.Idle,
    onClearModelDiscovery: () -> Unit = {},
    onSaveGitHubConfig: (Boolean, String, String, String, String?, GitHubSyncPolicy) -> Unit,
    onSyncNow: () -> Unit,
    onUpdateSecurity: (SecuritySettings) -> Unit,
    onOpenSchemaDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var providerType by remember(llmConfig) { mutableStateOf(llmConfig.providerType) }
    var byokPreset by remember(llmConfig) { mutableStateOf(llmConfig.byokPreset) }
    var geminiModel by remember(llmConfig) { mutableStateOf(llmConfig.geminiModel) }
    var customEndpoint by remember(llmConfig) { mutableStateOf(llmConfig.customEndpointUrl) }
    var customModel by remember(llmConfig) { mutableStateOf(llmConfig.customModelName) }
    var customApiKey by remember { mutableStateOf("") }
    var timeoutSeconds by remember(llmConfig) { mutableStateOf(llmConfig.timeoutSeconds) }
    var onDeviceFallbackEnabled by remember(llmConfig) { mutableStateOf(llmConfig.onDeviceFallbackEnabled) }
    var onDeviceFallbackProvider by remember(llmConfig) { mutableStateOf(llmConfig.onDeviceFallbackProvider) }
    var onDeviceCondenseDocs by remember(llmConfig) { mutableStateOf(llmConfig.onDeviceCondenseDocs) }
    var onDeviceTemperature by remember(llmConfig) { mutableStateOf(llmConfig.onDeviceTemperature) }
    var llmTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingLlm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    var ghEnabled by remember(githubConfig) { mutableStateOf(githubConfig.enabled) }
    var ghOwnerRepo by remember(githubConfig) { mutableStateOf(githubConfig.ownerRepo) }
    var ghBranch by remember(githubConfig) { mutableStateOf(githubConfig.branch) }
    var ghDir by remember(githubConfig) { mutableStateOf(githubConfig.pluginsDirectory) }
    var ghPat by remember { mutableStateOf("") }
    var ghPolicy by remember(githubConfig) { mutableStateOf(githubConfig.syncPolicy) }

    LaunchedEffect(llmConfig) {
        providerType = llmConfig.providerType
        byokPreset = llmConfig.byokPreset
        geminiModel = llmConfig.geminiModel
        customEndpoint = llmConfig.customEndpointUrl
        customModel = llmConfig.customModelName
        timeoutSeconds = llmConfig.timeoutSeconds
        onDeviceFallbackEnabled = llmConfig.onDeviceFallbackEnabled
        onDeviceFallbackProvider = llmConfig.onDeviceFallbackProvider
        onDeviceCondenseDocs = llmConfig.onDeviceCondenseDocs
        onDeviceTemperature = llmConfig.onDeviceTemperature
    }

    LaunchedEffect(providerType) {
        if (providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL) {
            onRefreshOnDeviceStatus()
        }
    }

    val resolvedEncryptedKeyForPreset = remember(customApiKey, byokPreset, llmConfig) {
        when {
            customApiKey.isNotBlank() -> SecretCipher.encrypt(customApiKey)
            else -> llmConfig.presetSlots[byokPreset]?.encryptedApiKey?.takeIf { it.isNotBlank() }
                ?: if (byokPreset == llmConfig.byokPreset) llmConfig.encryptedApiKey else ""
        }
    }

    val hasSavedKeyForPreset = remember(byokPreset, llmConfig) {
        llmConfig.presetSlots[byokPreset]?.encryptedApiKey?.isNotBlank() == true ||
            (byokPreset == llmConfig.byokPreset && llmConfig.encryptedApiKey.isNotBlank())
    }

    val discoveredModels = (modelDiscoveryState as? ModelDiscoveryState.Ready)?.models.orEmpty()

    val runTestAndDiscover: () -> Unit = {
        isTestingLlm = true
        llmTestResult = null
        val candidate = LlmBackendConfig(
            providerType = providerType,
            byokPreset = byokPreset,
            geminiModel = geminiModel,
            customEndpointUrl = customEndpoint,
            customModelName = customModel,
            encryptedApiKey = if (providerType == LlmProviderType.OPENAI_COMPATIBLE) {
                resolvedEncryptedKeyForPreset
            } else {
                ""
            },
            presetSlots = llmConfig.presetSlots,
            timeoutSeconds = timeoutSeconds,
            onDeviceFallbackEnabled = onDeviceFallbackEnabled,
            onDeviceFallbackProvider = onDeviceFallbackProvider,
            onDeviceCondenseDocs = onDeviceCondenseDocs,
            onDeviceTemperature = onDeviceTemperature
        )
        scope.launch {
            val res = onTestLlmConfig(candidate)
            res.onSuccess { result ->
                llmTestResult = result.statusMessage
                if (result.discoveredModels.isNotEmpty() &&
                    (customModel.isBlank() || result.discoveredModels.none { it.id == customModel })
                ) {
                    customModel = result.discoveredModels.first().id
                }
            }.onFailure { err ->
                llmTestResult = "Test failed: ${err.localizedMessage}"
            }
            isTestingLlm = false
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(
                    title = "Configuration",
                    subtitle = "LLM backends, GitHub repository sync, and biometric safety policy"
                )
            }

            // 1. Configurable LLM Backends
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("LLM Inference Backend", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = providerType == LlmProviderType.FIREBASE_GEMINI,
                                onClick = { providerType = LlmProviderType.FIREBASE_GEMINI },
                                label = { Text("Cloud Gemini") },
                                modifier = Modifier.testTag("llm_gemini_chip")
                            )
                            FilterChip(
                                selected = providerType == LlmProviderType.OLLAMA_LOCAL,
                                onClick = {
                                    if (providerType != LlmProviderType.OLLAMA_LOCAL) {
                                        providerType = LlmProviderType.OLLAMA_LOCAL
                                        if (llmConfig.providerType == LlmProviderType.OLLAMA_LOCAL &&
                                            llmConfig.customEndpointUrl.isNotBlank()
                                        ) {
                                            customEndpoint = llmConfig.customEndpointUrl
                                            customModel = llmConfig.customModelName
                                        } else if (customEndpoint.startsWith("https://") ||
                                            ByokProviderPreset.entries.any { customEndpoint == it.defaultBaseUrl }
                                        ) {
                                            customEndpoint = "http://192.168.1.100:11434"
                                            customModel = "qwen2.5-coder:7b"
                                        }
                                        llmTestResult = null
                                        onClearModelDiscovery()
                                    }
                                },
                                label = { Text("Local Ollama") },
                                modifier = Modifier.testTag("llm_ollama_chip")
                            )
                            FilterChip(
                                selected = providerType == LlmProviderType.OPENAI_COMPATIBLE,
                                onClick = {
                                    if (providerType != LlmProviderType.OPENAI_COMPATIBLE) {
                                        providerType = LlmProviderType.OPENAI_COMPATIBLE
                                        val slot = llmConfig.presetSlots[byokPreset]
                                        customEndpoint = slot?.endpointUrl?.takeIf { it.isNotBlank() }
                                            ?: if (llmConfig.providerType == LlmProviderType.OPENAI_COMPATIBLE &&
                                                llmConfig.customEndpointUrl.isNotBlank()
                                            ) llmConfig.customEndpointUrl else byokPreset.defaultBaseUrl
                                        customModel = slot?.modelName?.takeIf { it.isNotBlank() }
                                            ?: if (llmConfig.providerType == LlmProviderType.OPENAI_COMPATIBLE &&
                                                llmConfig.customModelName.isNotBlank()
                                            ) llmConfig.customModelName else byokPreset.defaultModel
                                        customApiKey = ""
                                        llmTestResult = null
                                        onClearModelDiscovery()
                                    }
                                },
                                label = { Text("BYOK Cloud (API)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("llm_openai_chip")
                            )
                            FilterChip(
                                selected = providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL,
                                onClick = { providerType = LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL },
                                label = { Text("On-Device Nano (Exp.)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("llm_ondevice_chip")
                            )
                        }

                        when (providerType) {
                            LlmProviderType.FIREBASE_GEMINI -> {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon").forEach { m ->
                                        FilterChip(
                                            selected = geminiModel == m,
                                            onClick = { geminiModel = m },
                                            label = { Text(m) }
                                        )
                                    }
                                }
                                Text(
                                    text = "Uses Firebase AI Logic with App Check attestation (no API key). To use your own free API key quota instead, choose BYOK Cloud (API) → Gemini AI Studio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> {
                                OnDeviceNanoConfigSection(
                                    status = onDeviceStatus,
                                    condenseDocs = onDeviceCondenseDocs,
                                    onCondenseDocsChange = { onDeviceCondenseDocs = it },
                                    fallbackEnabled = onDeviceFallbackEnabled,
                                    onFallbackEnabledChange = { onDeviceFallbackEnabled = it },
                                    fallbackProvider = onDeviceFallbackProvider,
                                    onFallbackProviderChange = { onDeviceFallbackProvider = it },
                                    temperature = onDeviceTemperature,
                                    onTemperatureChange = { onDeviceTemperature = it },
                                    onCheckStatus = onRefreshOnDeviceStatus,
                                    onDownloadModel = onDownloadOnDeviceModel
                                )
                            }

                            LlmProviderType.OLLAMA_LOCAL, LlmProviderType.OPENAI_COMPATIBLE -> {
                                if (providerType == LlmProviderType.OPENAI_COMPATIBLE) {
                                    Text(
                                        text = "Provider Preset (Free Key Tiers & OpenAI-Compatible Endpoints)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ByokProviderPreset.entries.forEach { preset ->
                                            FilterChip(
                                                selected = byokPreset == preset,
                                                onClick = {
                                                    byokPreset = preset
                                                    val slot = llmConfig.presetSlots[preset]
                                                    customEndpoint = slot?.endpointUrl?.takeIf { it.isNotBlank() }
                                                        ?: preset.defaultBaseUrl
                                                    customModel = slot?.modelName?.takeIf { it.isNotBlank() }
                                                        ?: preset.defaultModel
                                                    customApiKey = ""
                                                    llmTestResult = null
                                                    onClearModelDiscovery()
                                                },
                                                label = { Text(preset.displayName) },
                                                modifier = Modifier.testTag("byok_preset_${preset.name.lowercase()}")
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("byok_preset_info_card")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = byokPreset.freeTierSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (byokPreset.apiKeyPortalUrl.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        runCatching { uriHandler.openUri(byokPreset.apiKeyPortalUrl) }
                                                    },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("byok_get_key_button")
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.OpenInNew,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Get Free Key")
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = customEndpoint,
                                    onValueChange = { customEndpoint = it },
                                    label = { Text("Endpoint Base URL") },
                                    placeholder = {
                                        Text(
                                            if (providerType == LlmProviderType.OLLAMA_LOCAL) "http://192.168.1.100:11434"
                                            else byokPreset.defaultBaseUrl
                                        )
                                    },
                                    trailingIcon = {
                                        if (providerType == LlmProviderType.OPENAI_COMPATIBLE &&
                                            customEndpoint != byokPreset.defaultBaseUrl
                                        ) {
                                            IconButton(
                                                onClick = { customEndpoint = byokPreset.defaultBaseUrl }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Refresh,
                                                    contentDescription = "Reset URL to preset default"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("llm_endpoint_input")
                                )

                                if (providerType == LlmProviderType.OPENAI_COMPATIBLE) {
                                    OutlinedTextField(
                                        value = customApiKey,
                                        onValueChange = { customApiKey = it },
                                        label = {
                                            Text(
                                                if (hasSavedKeyForPreset) {
                                                    "Saved for ${byokPreset.displayName} — leave blank to keep"
                                                } else {
                                                    "API Key (${byokPreset.keyHint})"
                                                }
                                            )
                                        },
                                        placeholder = { Text(byokPreset.keyHint) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("llm_apikey_input")
                                    )
                                }

                                // Dynamic Model Selector Card + Quick-Pick Chips
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { showModelPicker = true }
                                        .testTag("model_selector_card")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Active Model",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = customModel.ifBlank { "Tap to browse or type model ID" },
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("active_model_value")
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Tune,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (discoveredModels.isNotEmpty()) {
                                                        "Browse (${discoveredModels.size})"
                                                    } else {
                                                        "Pick / Type"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (modelDiscoveryState is ModelDiscoveryState.Discovering) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                if (discoveredModels.isNotEmpty()) {
                                    Text(
                                        text = "Quick Pick (${discoveredModels.size} models live from endpoint)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        discoveredModels.take(8).forEach { model ->
                                            FilterChip(
                                                selected = customModel == model.id,
                                                onClick = { customModel = model.id },
                                                label = {
                                                    Text(
                                                        text = model.id,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontFamily = JetBrainsMonoFontFamily
                                                        ),
                                                        maxLines = 1
                                                    )
                                                },
                                                modifier = Modifier.testTag("quick_model_${model.id}")
                                            )
                                        }
                                        if (discoveredModels.size > 8) {
                                            FilterChip(
                                                selected = false,
                                                onClick = { showModelPicker = true },
                                                label = { Text("All ${discoveredModels.size}...") },
                                                modifier = Modifier.testTag("quick_model_more")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text("Request Timeout (for reasoning/large local models)", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 180, 300, 480).forEach { sec ->
                                FilterChip(
                                    selected = timeoutSeconds == sec,
                                    onClick = { timeoutSeconds = sec },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier.testTag("timeout_chip_$sec")
                                )
                            }
                        }

                        if (!llmTestResult.isNullOrBlank()) {
                            Text(
                                text = llmTestResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (llmTestResult!!.startsWith("Test failed", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.testTag("llm_test_result_text")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = runTestAndDiscover,
                                enabled = !isTestingLlm,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("test_llm_button")
                            ) {
                                if (isTestingLlm) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (providerType) {
                                        LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> "Warm Up & Test"
                                        LlmProviderType.FIREBASE_GEMINI -> "Test"
                                        LlmProviderType.OLLAMA_LOCAL, LlmProviderType.OPENAI_COMPATIBLE -> "Test & Fetch Models"
                                    }
                                )
                            }
                            Button(
                                onClick = {
                                    onSaveLlmConfig(
                                        providerType,
                                        byokPreset,
                                        geminiModel,
                                        customEndpoint,
                                        customModel,
                                        customApiKey.takeIf { it.isNotBlank() },
                                        timeoutSeconds,
                                        onDeviceFallbackEnabled,
                                        onDeviceFallbackProvider,
                                        onDeviceCondenseDocs,
                                        onDeviceTemperature
                                    )
                                    customApiKey = ""
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_llm_button")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Backend")
                            }
                        }
                    }
                }
            }

            // 2. Opt-in GitHub Repository Sync
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("GitHub Plugin Sync (Opt-In)", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (ghEnabled) "Checks repo before generating plugins" else "Defaulting to local Room storage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = ghEnabled,
                                onCheckedChange = { ghEnabled = it },
                                modifier = Modifier.testTag("github_enable_switch")
                            )
                        }

                        if (ghEnabled) {
                            OutlinedTextField(
                                value = ghOwnerRepo,
                                onValueChange = { ghOwnerRepo = it },
                                label = { Text("Repository (owner/repo)") },
                                placeholder = { Text("acme-ops/shellcraft-plugins") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("github_repo_input")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ghBranch,
                                    onValueChange = { ghBranch = it },
                                    label = { Text("Branch") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("github_branch_input")
                                )
                                OutlinedTextField(
                                    value = ghDir,
                                    onValueChange = { ghDir = it },
                                    label = { Text("Directory") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("github_dir_input")
                                )
                            }
                            OutlinedTextField(
                                value = ghPat,
                                onValueChange = { ghPat = it },
                                label = {
                                    Text(
                                        if (githubConfig.encryptedPat.isBlank()) "GitHub PAT (for private repos & publishing)"
                                        else "New PAT (leave blank to keep stored token)"
                                    )
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("github_pat_input")
                            )

                            Text("Sync Trigger Policy", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GitHubSyncPolicy.entries.forEach { policy ->
                                    FilterChip(
                                        selected = ghPolicy == policy,
                                        onClick = { ghPolicy = policy },
                                        label = { Text(policy.name.replace("_", " ")) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ghEnabled && githubConfig.isConfigured) {
                                OutlinedButton(
                                    onClick = onSyncNow,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("settings_sync_now_button")
                                ) {
                                    Text("Sync Now")
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveGitHubConfig(
                                        ghEnabled,
                                        ghOwnerRepo,
                                        ghBranch,
                                        ghDir,
                                        ghPat.takeIf { it.isNotBlank() },
                                        ghPolicy
                                    )
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_github_button")
                            ) {
                                Text("Save Sync Policy")
                            }
                        }
                    }
                }
            }

            // 3. Security & Tactile Experience
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Security & Tactile UX", style = MaterialTheme.typography.titleMedium)

                        SettingToggleRow(
                            title = "Biometric Superuser Authorization",
                            subtitle = "Require fingerprint/face/PIN before sudo elevation",
                            checked = securitySettings.requireBiometricForSudo,
                            onCheckedChange = {
                                onUpdateSecurity(securitySettings.copy(requireBiometricForSudo = it))
                            },
                            testTag = "toggle_biometric_sudo"
                        )
                        SettingToggleRow(
                            title = "Confirm Moderate-Risk Actions",
                            subtitle = "Prompt before mutating services or files (High/Critical always prompt)",
                            checked = securitySettings.confirmModerateCommands,
                            onCheckedChange = {
                                onUpdateSecurity(securitySettings.copy(confirmModerateCommands = it))
                            },
                            testTag = "toggle_confirm_moderate"
                        )
                        SettingToggleRow(
                            title = "Tactile Haptic Feedback",
                            subtitle = "Vibration pulses for switches, execution, and safety warnings",
                            checked = securitySettings.hapticsEnabled,
                            onCheckedChange = {
                                onUpdateSecurity(securitySettings.copy(hapticsEnabled = it))
                            },
                            testTag = "toggle_haptics"
                        )
                        SettingToggleRow(
                            title = "Dark Carbon Workstation Theme",
                            subtitle = "Switch between Dark Carbon and Light Studio palettes",
                            checked = securitySettings.darkTheme,
                            onCheckedChange = {
                                onUpdateSecurity(securitySettings.copy(darkTheme = it))
                            },
                            testTag = "toggle_dark_theme"
                        )
                    }
                }
            }

            // 4. Plugin Architecture Docs
            item {
                OutlinedButton(
                    onClick = onOpenSchemaDocs,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("settings_plugin_docs_button")
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Plugin Architecture Docs & Agent JSON Importer")
                }
            }
        }
    }

    if (showModelPicker) {
        val providerTitle = if (providerType == LlmProviderType.OLLAMA_LOCAL) {
            "Local Ollama"
        } else {
            byokPreset.displayName
        }
        ModelPickerDialog(
            providerTitle = providerTitle,
            currentModel = customModel,
            discoveredModels = discoveredModels,
            isRefreshing = isTestingLlm || modelDiscoveryState is ModelDiscoveryState.Discovering,
            onRefreshModels = runTestAndDiscover,
            onSelectModel = { chosen -> customModel = chosen },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun OnDeviceNanoConfigSection(
    status: OnDeviceModelStatus,
    condenseDocs: Boolean,
    onCondenseDocsChange: (Boolean) -> Unit,
    fallbackEnabled: Boolean,
    onFallbackEnabledChange: (Boolean) -> Unit,
    fallbackProvider: LlmProviderType,
    onFallbackProviderChange: (LlmProviderType) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    onCheckStatus: () -> Unit,
    onDownloadModel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ondevice_diagnostics_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                    Icon(
                        Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Gemini Nano · Android AICore",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "BETA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val (statusLabel, statusTint) = when (status) {
                OnDeviceModelStatus.Checking -> "Checking AICore availability..." to MaterialTheme.colorScheme.onSurfaceVariant
                OnDeviceModelStatus.Available -> "Ready on-device (NPU / GPU / CPU managed by AICore)" to MaterialTheme.colorScheme.secondary
                OnDeviceModelStatus.Downloadable -> "Model supported · ready to download via AICore" to MaterialTheme.colorScheme.primary
                is OnDeviceModelStatus.Downloading -> {
                    val mb = status.bytesDownloaded / (1024.0 * 1024.0)
                    val dlText = if (status.bytesDownloaded > 0L) {
                        "Downloading Gemini Nano via AICore (%.1f MB)...".format(mb)
                    } else {
                        "Downloading Gemini Nano via AICore..."
                    }
                    dlText to MaterialTheme.colorScheme.primary
                }
                is OnDeviceModelStatus.Unavailable -> "Unavailable: ${status.reason}" to MaterialTheme.colorScheme.error
                is OnDeviceModelStatus.Error -> "Error: ${status.message}" to MaterialTheme.colorScheme.error
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = statusTint,
                modifier = Modifier.testTag("ondevice_status_text")
            )

            if (status is OnDeviceModelStatus.Downloading || status is OnDeviceModelStatus.Checking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheckStatus,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ondevice_check_status_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check Status")
                }
                if (status is OnDeviceModelStatus.Downloadable || status is OnDeviceModelStatus.Error) {
                    Button(
                        onClick = onDownloadModel,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ondevice_download_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download via AICore")
                    }
                }
            }

            Text(
                text = "Runs 100% offline via Android Private Compute Services (zero bundled weights). Keep ShellCraft in the foreground while generating.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingToggleRow(
                title = "Condense CLI Docs (4k-token Guard)",
                subtitle = "Extract synopsis, subcommands, and flags before prompting Gemini Nano",
                checked = condenseDocs,
                onCheckedChange = onCondenseDocsChange,
                testTag = "ondevice_condense_switch"
            )

            SettingToggleRow(
                title = "Auto-Fallback to Remote Backend",
                subtitle = "Retry on Cloud Gemini or LAN LLM if AICore is unsupported, downloading, or over-quota",
                checked = fallbackEnabled,
                onCheckedChange = onFallbackEnabledChange,
                testTag = "ondevice_fallback_switch"
            )

            AnimatedVisibility(
                visible = fallbackEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Fallback Backend (uses its saved endpoint/model settings)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            LlmProviderType.FIREBASE_GEMINI to "Cloud Gemini",
                            LlmProviderType.OLLAMA_LOCAL to "Local Ollama",
                            LlmProviderType.OPENAI_COMPATIBLE to "BYOK Cloud (API)"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = fallbackProvider == type,
                                onClick = { onFallbackProviderChange(type) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("ondevice_fallback_${type.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            Text("Sampling Temperature", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.1f to "0.1 (Strict)", 0.2f to "0.2 (Balanced)", 0.4f to "0.4 (Creative)").forEach { (temp, label) ->
                    FilterChip(
                        selected = kotlin.math.abs(temperature - temp) < 0.05f,
                        onClick = { onTemperatureChange(temp) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("ondevice_temp_${(temp * 10).toInt()}")
                    )
                }
            }
        }
    }
}

