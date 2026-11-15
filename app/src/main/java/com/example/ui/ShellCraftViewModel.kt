package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.security.HapticFeedbackController
import com.example.core.security.SafetyAssessment
import com.example.core.security.SafetyGuard
import com.example.core.security.SecretCipher
import com.example.data.github.GitHubPluginSyncClient
import com.example.data.llm.LlmPluginAnalyzer
import com.example.data.local.ExecutionLogEntity
import com.example.data.local.SecuritySettings
import com.example.data.repository.ShellCraftRepository
import com.example.data.ssh.SshEngine
import com.example.domain.model.CliPlugin
import com.example.domain.model.ForgeState
import com.example.domain.model.GitHubRepoConfig
import com.example.domain.model.GitHubSyncPolicy
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.LlmProviderType
import com.example.domain.model.PluginAction
import com.example.domain.model.PluginAnalysisSource
import com.example.domain.model.PluginSource
import com.example.domain.model.SshAuthType
import com.example.domain.model.SshConnectionState
import com.example.domain.model.SshExecutionResult
import com.example.domain.model.SshHostProfile
import com.example.domain.model.SyncStatus
import com.example.domain.model.TargetOs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ShellCraftViewModel"

data class PendingExecutionRequest(
    val pluginName: String,
    val command: String,
    val action: PluginAction?,
    val assessment: SafetyAssessment,
    val biometricVerified: Boolean = false
)

data class TerminalSessionState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val command: String = "",
    val output: String = "",
    val lastResult: SshExecutionResult? = null,
    val errorMessage: String? = null
)

class ShellCraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShellCraftRepository(application)
    private val sshEngine = SshEngine()
    private val llmAnalyzer = LlmPluginAnalyzer()
    private val gitHubClient = GitHubPluginSyncClient()
    val haptics = HapticFeedbackController(application)

    val hosts: StateFlow<List<SshHostProfile>> = repository.hostsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plugins: StateFlow<List<CliPlugin>> = repository.pluginsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.recentLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val llmConfig: StateFlow<LlmBackendConfig> = repository.settingsDataStore.llmConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LlmBackendConfig())

    val githubConfig: StateFlow<GitHubRepoConfig> = repository.settingsDataStore.githubConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GitHubRepoConfig())

    val securitySettings: StateFlow<SecuritySettings> = repository.settingsDataStore.securitySettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecuritySettings())

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _forgeState = MutableStateFlow<ForgeState>(ForgeState.Idle)
    val forgeState: StateFlow<ForgeState> = _forgeState.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.LocalOnly)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _pendingExecution = MutableStateFlow<PendingExecutionRequest?>(null)
    val pendingExecution: StateFlow<PendingExecutionRequest?> = _pendingExecution.asStateFlow()

    private val _terminalState = MutableStateFlow(TerminalSessionState())
    val terminalState: StateFlow<TerminalSessionState> = _terminalState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var activeExecutionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeedPlugins()
            repository.settingsDataStore.securitySettingsFlow.collect { sec ->
                haptics.enabled = sec.hapticsEnabled
            }
        }
        viewModelScope.launch {
            val gh = repository.settingsDataStore.githubConfigFlow.first()
            if (gh.isConfigured) {
                val shouldAutoSync = when (gh.syncPolicy) {
                    GitHubSyncPolicy.AUTOMATIC -> true
                    GitHubSyncPolicy.INTERVAL_HOURLY -> {
                        val last = gh.lastSyncedAtMs ?: 0L
                        System.currentTimeMillis() - last > 3_600_000L
                    }
                    GitHubSyncPolicy.INTERVAL_DAILY -> {
                        val last = gh.lastSyncedAtMs ?: 0L
                        System.currentTimeMillis() - last > 86_400_000L
                    }
                    else -> false
                }
                if (shouldAutoSync) {
                    syncGitHubPlugins(silent = true)
                } else {
                    _syncStatus.value = SyncStatus.Idle
                }
            } else {
                _syncStatus.value = SyncStatus.LocalOnly
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showMessage(message: String) {
        _bannerMessage.value = message
    }

    // --- SSH Host Management & Connection ---

    fun saveHost(
        existingId: String?,
        label: String,
        host: String,
        port: Int,
        username: String,
        authType: SshAuthType,
        plainPassword: String,
        plainPrivateKey: String,
        plainPassphrase: String,
        targetOsHint: TargetOs,
        existingHost: SshHostProfile?
    ) {
        viewModelScope.launch {
            val profile = SshHostProfile(
                id = existingId ?: UUID.randomUUID().toString(),
                label = label.trim().ifBlank { "$username@$host" },
                host = host.trim(),
                port = port.coerceIn(1, 65535),
                username = username.trim(),
                authType = authType,
                encryptedPassword = if (plainPassword.isNotBlank()) {
                    SecretCipher.encrypt(plainPassword)
                } else {
                    existingHost?.encryptedPassword.orEmpty()
                },
                encryptedPrivateKey = if (plainPrivateKey.isNotBlank()) {
                    SecretCipher.encrypt(plainPrivateKey)
                } else {
                    existingHost?.encryptedPrivateKey.orEmpty()
                },
                encryptedKeyPassphrase = if (plainPassphrase.isNotBlank()) {
                    SecretCipher.encrypt(plainPassphrase)
                } else {
                    existingHost?.encryptedKeyPassphrase.orEmpty()
                },
                targetOsHint = targetOsHint,
                lastConnectedAtMs = existingHost?.lastConnectedAtMs
            )
            repository.saveHost(profile)
            haptics.success()
            _bannerMessage.value = "Saved host ${profile.label}"
        }
    }

    fun deleteHost(id: String) {
        viewModelScope.launch {
            val current = _connectionState.value
            if (current is SshConnectionState.Connected && current.host.id == id) {
                sshEngine.disconnect()
                _connectionState.value = SshConnectionState.Disconnected
            }
            repository.deleteHost(id)
            haptics.tick()
        }
    }

    fun connectHost(profile: SshHostProfile) {
        viewModelScope.launch {
            _connectionState.value = SshConnectionState.Connecting(profile.label)
            haptics.tick()
            try {
                val env = sshEngine.connect(profile)
                repository.markHostConnected(profile.id)
                _connectionState.value = SshConnectionState.Connected(profile, env)
                haptics.success()
                val gh = githubConfig.value
                if (gh.isConfigured && gh.syncPolicy == GitHubSyncPolicy.ON_SSH_CONNECT) {
                    syncGitHubPlugins(silent = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SSH connect failed", e)
                _connectionState.value = SshConnectionState.Error(
                    e.localizedMessage ?: "SSH connection failed."
                )
                haptics.warning()
            }
        }
    }

    fun disconnectHost() {
        viewModelScope.launch {
            sshEngine.disconnect()
            _connectionState.value = SshConnectionState.Disconnected
            haptics.tick()
        }
    }

    // --- Safe Execution & Biometric Sudo ---

    fun requestCommandExecution(
        pluginName: String,
        command: String,
        action: PluginAction?,
        elevateWithSudo: Boolean
    ) {
        if (!sshEngine.isConnected()) {
            haptics.warning()
            _bannerMessage.value = "Connect to an SSH host in Workspace before executing commands."
            return
        }
        val sec = securitySettings.value
        val assessment = SafetyGuard.assess(
            command = command,
            action = action,
            elevateWithSudo = elevateWithSudo,
            confirmModerateCommands = sec.confirmModerateCommands
        )

        if (assessment.requiresConfirmation || assessment.requiresSuperuserAuth) {
            if (assessment.effectiveRisk >= com.example.domain.model.RiskLevel.HIGH) {
                haptics.danger()
            } else {
                haptics.warning()
            }
            _pendingExecution.value = PendingExecutionRequest(
                pluginName = pluginName,
                command = command,
                action = action,
                assessment = assessment,
                biometricVerified = !sec.requireBiometricForSudo
            )
        } else {
            haptics.tick()
            launchSshExecution(pluginName, command, sudoPassword = null)
        }
    }

    fun markBiometricVerified() {
        val current = _pendingExecution.value ?: return
        haptics.success()
        _pendingExecution.value = current.copy(biometricVerified = true)
    }

    fun confirmPendingExecution(sudoPassword: String?) {
        val pending = _pendingExecution.value ?: return
        _pendingExecution.value = null
        launchSshExecution(
            pluginName = pending.pluginName,
            command = pending.command,
            sudoPassword = sudoPassword?.takeIf { pending.assessment.requiresSuperuserAuth }
        )
    }

    fun cancelPendingExecution() {
        _pendingExecution.value = null
        haptics.tick()
    }

    fun dismissTerminal() {
        _terminalState.value = _terminalState.value.copy(isVisible = false)
    }

    fun reopenTerminal() {
        if (_terminalState.value.command.isNotBlank()) {
            haptics.tick()
            _terminalState.value = _terminalState.value.copy(isVisible = true)
        }
    }

    fun cancelRunningExecution() {
        activeExecutionJob?.cancel()
        _terminalState.value = _terminalState.value.copy(
            isRunning = false,
            errorMessage = "Execution cancelled by user."
        )
        haptics.warning()
    }

    private fun launchSshExecution(
        pluginName: String,
        command: String,
        sudoPassword: String?
    ) {
        activeExecutionJob?.cancel()
        activeExecutionJob = viewModelScope.launch {
            val hostLabel = (_connectionState.value as? SshConnectionState.Connected)?.host?.label ?: "SSH Host"
            val outputBuilder = StringBuilder()
            _terminalState.value = TerminalSessionState(
                isVisible = true,
                isRunning = true,
                command = command,
                output = "",
                lastResult = null,
                errorMessage = null
            )
            try {
                val result = sshEngine.executeCommand(
                    command = command,
                    sudoPassword = sudoPassword,
                    onStdoutChunk = { chunk ->
                        outputBuilder.append(chunk)
                        _terminalState.value = _terminalState.value.copy(output = outputBuilder.toString())
                    },
                    onStderrChunk = { chunk ->
                        outputBuilder.append(chunk)
                        _terminalState.value = _terminalState.value.copy(output = outputBuilder.toString())
                    }
                )
                val finalOutput = outputBuilder.toString().ifBlank {
                    listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n")
                }
                _terminalState.value = _terminalState.value.copy(
                    isRunning = false,
                    output = finalOutput.ifBlank { "(Command completed with no output)" },
                    lastResult = result
                )
                repository.recordExecution(
                    hostLabel = hostLabel,
                    pluginName = pluginName,
                    command = result.command,
                    exitCode = result.exitCode,
                    outputPreview = finalOutput,
                    elevatedWithSudo = result.elevatedWithSudo,
                    durationMs = result.durationMs
                )
                if (result.exitCode == 0) haptics.success() else haptics.warning()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Command execution error", e)
                _terminalState.value = _terminalState.value.copy(
                    isRunning = false,
                    errorMessage = e.localizedMessage ?: "SSH command execution failed."
                )
                haptics.danger()
            }
        }
    }

    // --- AI Forge & Check-Before-Generate ---

    fun resetForge() {
        _forgeState.value = ForgeState.Idle
    }

    fun startForgeAnalysis(
        commandName: String,
        sourceMode: PluginAnalysisSource,
        manualDocsText: String,
        bypassExistingCheck: Boolean = false
    ) {
        val cleanCmd = commandName.trim()
        if (cleanCmd.isBlank()) {
            _forgeState.value = ForgeState.Error("Enter a command name (e.g. ufw, docker, nginx, ffmpeg).")
            return
        }

        viewModelScope.launch {
            haptics.tick()
            try {
                if (!bypassExistingCheck) {
                    val gh = githubConfig.value
                    if (gh.isConfigured) {
                        _forgeState.value = ForgeState.CheckingGitHub(cleanCmd)
                        val remoteMatch = runCatching {
                            gitHubClient.findExistingPlugin(cleanCmd, gh)
                        }.getOrNull()
                        if (remoteMatch != null) {
                            _forgeState.value = ForgeState.FoundExistingRemote(
                                commandName = cleanCmd,
                                plugin = remoteMatch.first,
                                sourcePath = "${gh.ownerRepo}/${remoteMatch.second}"
                            )
                            haptics.success()
                            return@launch
                        }
                    }

                    val localMatch = repository.findLocalPluginByCommand(cleanCmd)
                    if (localMatch != null) {
                        _forgeState.value = ForgeState.FoundExistingRemote(
                            commandName = cleanCmd,
                            plugin = localMatch,
                            sourcePath = "Local Plugin Store"
                        )
                        haptics.success()
                        return@launch
                    }
                }

                val docsToAnalyze = when (sourceMode) {
                    PluginAnalysisSource.SSH_PROBE -> {
                        if (!sshEngine.isConnected()) {
                            _forgeState.value = ForgeState.Error(
                                "No active SSH host connected. Connect a host in Workspace or switch source to Manual Docs."
                            )
                            haptics.warning()
                            return@launch
                        }
                        _forgeState.value = ForgeState.ProbingSshDocs(cleanCmd)
                        sshEngine.probeCommandDocumentation(cleanCmd)
                    }
                    PluginAnalysisSource.MANUAL_DOCS -> {
                        if (manualDocsText.isBlank()) {
                            _forgeState.value = ForgeState.Error(
                                "Paste `--help` or `man` documentation text to analyze."
                            )
                            return@launch
                        }
                        manualDocsText.trim()
                    }
                }

                val activeLlm = llmConfig.value
                val osHint = (_connectionState.value as? SshConnectionState.Connected)?.env?.osName ?: "POSIX"
                val providerLabel = when (activeLlm.providerType) {
                    LlmProviderType.FIREBASE_GEMINI -> "Gemini (${activeLlm.geminiModel})"
                    LlmProviderType.OLLAMA_LOCAL -> "Ollama (${activeLlm.customModelName})"
                    LlmProviderType.OPENAI_COMPATIBLE -> "Custom LLM (${activeLlm.customModelName})"
                }
                _forgeState.value = ForgeState.AnalyzingWithLlm(
                    statusMessage = "Synthesizing GUI schema with $providerLabel...",
                    rawDocsPreview = docsToAnalyze.take(600)
                )

                val generated = llmAnalyzer.analyzeCliDocumentation(
                    commandName = cleanCmd,
                    rawDocs = docsToAnalyze,
                    remoteOsHint = osHint,
                    config = activeLlm
                )
                _forgeState.value = ForgeState.Ready(generated, docsToAnalyze)
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Forge analysis failed", e)
                _forgeState.value = ForgeState.Error(e.localizedMessage ?: "Plugin analysis failed.")
                haptics.warning()
            }
        }
    }

    fun savePlugin(plugin: CliPlugin, pushToGitHubIfEnabled: Boolean = true) {
        viewModelScope.launch {
            repository.savePlugin(plugin)
            haptics.success()
            val gh = githubConfig.value
            if (pushToGitHubIfEnabled && gh.isConfigured && gh.syncPolicy == GitHubSyncPolicy.AUTOMATIC &&
                SecretCipher.decrypt(gh.encryptedPat).isNotBlank()
            ) {
                runCatching { gitHubClient.publishPlugin(plugin, gh) }
                    .onSuccess { path ->
                        _bannerMessage.value = "Saved ${plugin.displayName} & synced to $path"
                    }
                    .onFailure { err ->
                        _bannerMessage.value = "Saved locally; GitHub push skipped: ${err.localizedMessage}"
                    }
            } else {
                _bannerMessage.value = "Saved plugin ${plugin.displayName} to local storage"
            }
        }
    }

    fun deletePlugin(id: String) {
        viewModelScope.launch {
            repository.deletePlugin(id)
            haptics.tick()
        }
    }

    fun importPluginJson(rawJson: String): Result<CliPlugin> {
        return runCatching {
            val decoded = repository.decodePluginJson(rawJson).copy(
                source = PluginSource.USER_IMPORTED,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            viewModelScope.launch {
                repository.savePlugin(decoded)
                haptics.success()
                _bannerMessage.value = "Imported plugin ${decoded.displayName}"
            }
            decoded
        }
    }

    fun exportPluginJson(plugin: CliPlugin): String = repository.encodePluginJson(plugin)

    // --- GitHub Sync & Settings ---

    fun syncGitHubPlugins(silent: Boolean = false) {
        viewModelScope.launch {
            val gh = githubConfig.value
            if (!gh.isConfigured) {
                _syncStatus.value = SyncStatus.LocalOnly
                if (!silent) _bannerMessage.value = "No GitHub repo configured; using local storage."
                return@launch
            }
            _syncStatus.value = SyncStatus.Syncing
            try {
                val remotePlugins = gitHubClient.syncAllPlugins(gh)
                if (remotePlugins.isNotEmpty()) {
                    repository.savePlugins(remotePlugins)
                }
                val now = System.currentTimeMillis()
                repository.settingsDataStore.updateLastSynced(now)
                _syncStatus.value = SyncStatus.Success(remotePlugins.size, now)
                if (!silent) {
                    haptics.success()
                    _bannerMessage.value = "Synced ${remotePlugins.size} plugin(s) from ${gh.ownerRepo}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "GitHub sync error", e)
                _syncStatus.value = SyncStatus.Error(e.localizedMessage ?: "Sync failed")
                if (!silent) {
                    haptics.warning()
                    _bannerMessage.value = "GitHub sync failed: ${e.localizedMessage}"
                }
            }
        }
    }

    fun publishPluginToGitHub(plugin: CliPlugin) {
        viewModelScope.launch {
            val gh = githubConfig.value
            try {
                val path = gitHubClient.publishPlugin(plugin, gh)
                haptics.success()
                _bannerMessage.value = "Published to ${gh.ownerRepo}/$path"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptics.warning()
                _bannerMessage.value = e.localizedMessage ?: "Failed to publish plugin to GitHub"
            }
        }
    }

    fun saveLlmBackend(
        providerType: LlmProviderType,
        geminiModel: String,
        customEndpointUrl: String,
        customModelName: String,
        plainApiKey: String?,
        timeoutSeconds: Int
    ) {
        viewModelScope.launch {
            repository.settingsDataStore.saveLlmConfig(
                providerType, geminiModel, customEndpointUrl, customModelName, plainApiKey, timeoutSeconds
            )
            haptics.success()
            _bannerMessage.value = "Saved LLM backend configuration"
        }
    }

    suspend fun testLlmBackend(config: LlmBackendConfig): Result<String> {
        return runCatching { llmAnalyzer.testEndpoint(config) }
    }

    fun saveGitHubSettings(
        enabled: Boolean,
        ownerRepo: String,
        branch: String,
        pluginsDir: String,
        plainPat: String?,
        syncPolicy: GitHubSyncPolicy
    ) {
        viewModelScope.launch {
            repository.settingsDataStore.saveGitHubConfig(
                enabled, ownerRepo, branch, pluginsDir, plainPat, syncPolicy
            )
            haptics.success()
            if (!enabled || ownerRepo.isBlank()) {
                _syncStatus.value = SyncStatus.LocalOnly
                _bannerMessage.value = "Using local plugin storage"
            } else {
                _syncStatus.value = SyncStatus.Idle
                _bannerMessage.value = "Saved GitHub repository settings"
            }
        }
    }

    fun updateSecuritySettings(settings: SecuritySettings) {
        viewModelScope.launch {
            repository.settingsDataStore.saveSecuritySettings(settings)
            haptics.tick()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            haptics.tick()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            sshEngine.disconnect()
        }
    }
}
