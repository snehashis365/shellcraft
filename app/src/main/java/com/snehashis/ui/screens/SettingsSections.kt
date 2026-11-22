@file:Suppress("DEPRECATION")

package com.snehashis.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.snehashis.data.local.SecuritySettings
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.GitHubSyncPolicy
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.ThemeMode

@Composable
internal fun AppearanceAndForgeSettingsCard(
    securitySettings: SecuritySettings,
    onUpdateSecurity: (SecuritySettings) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appearance_forge_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Theme, Forge Studio & Animations", style = MaterialTheme.typography.titleMedium)
            }

            Text("Color Personality", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = securitySettings.themeMode == mode,
                        onClick = {
                            onUpdateSecurity(
                                securitySettings.copy(
                                    themeMode = mode,
                                    darkTheme = mode != ThemeMode.DAYLIGHT_LIGHT
                                )
                            )
                        },
                        label = { Text(mode.displayName) },
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                    )
                }
            }
            Text(
                text = securitySettings.themeMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Default Forge GUI Generation Style", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PluginGuiStyle.entries.forEach { style ->
                    val labelText = if (style == PluginGuiStyle.MODULAR_DASHBOARD) {
                        "Modular Dashboard (Rec.)"
                    } else {
                        "Custom HTML/Canvas"
                    }
                    FilterChip(
                        selected = securitySettings.defaultForgeGuiStyle == style,
                        onClick = {
                            onUpdateSecurity(securitySettings.copy(defaultForgeGuiStyle = style))
                        },
                        label = { Text(labelText) },
                        modifier = Modifier.testTag("default_gui_${style.name.lowercase()}")
                    )
                }
            }

            SettingToggleRow(
                title = "Disable Hardware-Demanding Animations",
                subtitle = "Turn off interactive 3D Three.js WebGL hero visuals, orbital particles, and continuous glow pulses",
                checked = securitySettings.disableHeavyAnimations,
                onCheckedChange = {
                    onUpdateSecurity(securitySettings.copy(disableHeavyAnimations = it))
                },
                testTag = "toggle_disable_heavy_animations"
            )
        }
    }
}

@Composable
internal fun GitHubSyncSettingsCard(
    githubConfig: GitHubRepoConfig,
    onSaveGitHubConfig: (Boolean, String, String, String, String?, GitHubSyncPolicy) -> Unit,
    onSyncNow: () -> Unit
) {
    var ghEnabled by remember(githubConfig) { mutableStateOf(githubConfig.enabled) }
    var ghOwnerRepo by remember(githubConfig) { mutableStateOf(githubConfig.ownerRepo) }
    var ghBranch by remember(githubConfig) { mutableStateOf(githubConfig.branch) }
    var ghDir by remember(githubConfig) { mutableStateOf(githubConfig.pluginsDirectory) }
    var ghPat by remember { mutableStateOf("") }
    var ghPolicy by remember(githubConfig) { mutableStateOf(githubConfig.syncPolicy) }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ghBranch,
                        onValueChange = { ghBranch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_branch_input")
                    )
                    OutlinedTextField(
                        value = ghDir,
                        onValueChange = { ghDir = it },
                        label = { Text("Directory") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_dir_input")
                    )
                }
                OutlinedTextField(
                    value = ghPat,
                    onValueChange = { ghPat = it },
                    label = {
                        Text(
                            if (githubConfig.encryptedPat.isBlank()) "GitHub PAT (optional for read-only public sync)"
                            else "New PAT (leave blank to keep stored token)"
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_pat_input")
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
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_sync_now_button")
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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("save_github_button")
                ) {
                    Text("Save Sync Policy")
                }
            }
        }
    }
}

@Composable
internal fun SecuritySettingsCard(
    securitySettings: SecuritySettings,
    onUpdateSecurity: (SecuritySettings) -> Unit
) {
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
        }
    }
}

@Composable
internal fun SettingToggleRow(
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
internal fun OnDeviceNanoConfigSection(
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
                subtitle = "Retry on Cloud Gemini or BYOK / Local API if AICore is unsupported, downloading, or over-quota",
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
                            LlmProviderType.OPENAI_COMPATIBLE to "BYOK / Local API"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = fallbackProvider == type ||
                                    (type == LlmProviderType.OPENAI_COMPATIBLE && fallbackProvider == LlmProviderType.OLLAMA_LOCAL),
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
