package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.security.SecretCipher
import com.example.data.local.SecuritySettings
import com.example.domain.model.GitHubRepoConfig
import com.example.domain.model.GitHubSyncPolicy
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.LlmProviderType
import com.example.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    llmConfig: LlmBackendConfig,
    githubConfig: GitHubRepoConfig,
    securitySettings: SecuritySettings,
    onSaveLlmConfig: (LlmProviderType, String, String, String, String?, Int) -> Unit,
    onTestLlmConfig: suspend (LlmBackendConfig) -> Result<String>,
    onSaveGitHubConfig: (Boolean, String, String, String, String?, GitHubSyncPolicy) -> Unit,
    onSyncNow: () -> Unit,
    onUpdateSecurity: (SecuritySettings) -> Unit,
    onOpenSchemaDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var providerType by remember(llmConfig) { mutableStateOf(llmConfig.providerType) }
    var geminiModel by remember(llmConfig) { mutableStateOf(llmConfig.geminiModel) }
    var customEndpoint by remember(llmConfig) { mutableStateOf(llmConfig.customEndpointUrl) }
    var customModel by remember(llmConfig) { mutableStateOf(llmConfig.customModelName) }
    var customApiKey by remember { mutableStateOf("") }
    var llmTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingLlm by remember { mutableStateOf(false) }

    var ghEnabled by remember(githubConfig) { mutableStateOf(githubConfig.enabled) }
    var ghOwnerRepo by remember(githubConfig) { mutableStateOf(githubConfig.ownerRepo) }
    var ghBranch by remember(githubConfig) { mutableStateOf(githubConfig.branch) }
    var ghDir by remember(githubConfig) { mutableStateOf(githubConfig.pluginsDirectory) }
    var ghPat by remember { mutableStateOf("") }
    var ghPolicy by remember(githubConfig) { mutableStateOf(githubConfig.syncPolicy) }

    LaunchedEffect(llmConfig) {
        providerType = llmConfig.providerType
        geminiModel = llmConfig.geminiModel
        customEndpoint = llmConfig.customEndpointUrl
        customModel = llmConfig.customModelName
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
                                onClick = { providerType = LlmProviderType.OLLAMA_LOCAL },
                                label = { Text("Local Ollama") },
                                modifier = Modifier.testTag("llm_ollama_chip")
                            )
                            FilterChip(
                                selected = providerType == LlmProviderType.OPENAI_COMPATIBLE,
                                onClick = { providerType = LlmProviderType.OPENAI_COMPATIBLE },
                                label = { Text("OpenAI-Compatible") },
                                modifier = Modifier.testTag("llm_openai_chip")
                            )
                        }

                        if (providerType == LlmProviderType.FIREBASE_GEMINI) {
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
                                text = "Powered by Firebase AI Logic with App Check attestation (no API key required).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = { customEndpoint = it },
                                label = { Text("Endpoint Base URL") },
                                placeholder = {
                                    Text(
                                        if (providerType == LlmProviderType.OLLAMA_LOCAL) "http://192.168.1.100:11434"
                                        else "https://api.openai.com"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("llm_endpoint_input")
                            )
                            OutlinedTextField(
                                value = customModel,
                                onValueChange = { customModel = it },
                                label = { Text("Model Name") },
                                placeholder = { Text("qwen2.5-coder:7b") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("llm_model_input")
                            )
                            if (providerType == LlmProviderType.OPENAI_COMPATIBLE) {
                                OutlinedTextField(
                                    value = customApiKey,
                                    onValueChange = { customApiKey = it },
                                    label = {
                                        Text(
                                            if (llmConfig.encryptedApiKey.isBlank()) "API Key (optional for local vLLM)"
                                            else "New API Key (leave blank to keep existing)"
                                        )
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("llm_apikey_input")
                                )
                            }
                        }

                        if (!llmTestResult.isNullOrBlank()) {
                            Text(
                                text = llmTestResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isTestingLlm = true
                                    llmTestResult = null
                                    val candidate = LlmBackendConfig(
                                        providerType = providerType,
                                        geminiModel = geminiModel,
                                        customEndpointUrl = customEndpoint,
                                        customModelName = customModel,
                                        encryptedApiKey = if (customApiKey.isNotBlank()) {
                                            SecretCipher.encrypt(customApiKey)
                                        } else {
                                            llmConfig.encryptedApiKey
                                        }
                                    )
                                    scope.launch {
                                        val res = onTestLlmConfig(candidate)
                                        llmTestResult = res.fold(
                                            onSuccess = { it },
                                            onFailure = { "Test failed: ${it.localizedMessage}" }
                                        )
                                        isTestingLlm = false
                                    }
                                },
                                enabled = !isTestingLlm,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("test_llm_button")
                            ) {
                                if (isTestingLlm) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test")
                            }
                            Button(
                                onClick = {
                                    onSaveLlmConfig(
                                        providerType,
                                        geminiModel,
                                        customEndpoint,
                                        customModel,
                                        customApiKey.takeIf { it.isNotBlank() },
                                        60
                                    )
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
