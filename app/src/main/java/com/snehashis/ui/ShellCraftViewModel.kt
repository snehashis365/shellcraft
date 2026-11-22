package com.snehashis.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snehashis.core.AppContainer
import com.snehashis.core.security.SafetyAssessment
import com.snehashis.core.security.SafetyGuard
import com.snehashis.core.security.SecretCipher
import com.snehashis.data.local.ExecutionLogEntity
import com.snehashis.data.local.SecuritySettings
import com.snehashis.data.ssh.RemoteFileEntry
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.ForgeState
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.GitHubSyncPolicy
import com.snehashis.domain.model.InteractivePrompt
import com.snehashis.domain.model.InteractivePromptDetector
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelDiscoveryState
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.domain.model.SshAuthType
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.domain.model.SshExecutionResult
import com.snehashis.domain.model.SshHostProfile
import com.snehashis.domain.model.SyncStatus
import com.snehashis.domain.model.TargetOs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    val pluginId: String? = null,
    val command: String = "",
    val output: String = "",
    val detectedPrompt: InteractivePrompt? = null,
    val lastResult: SshExecutionResult? = null,
    val errorMessage: String? = null
)

class ShellCraftViewModel(application: Application) : AndroidViewModel(application) {

    init {
        AppContainer.ensureInitialized(application)
    }

    private val repository = AppContainer.repository
    private val sshEngine = AppContainer.sshEngine
    private val llmAnalyzer = AppContainer.llmAnalyzer
    private val gitHubClient = AppContainer.gitHubClient
    val haptics = AppContainer.haptics

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

    val forgeState: StateFlow<ForgeState> = AppContainer.forgeState
    val onDeviceStatus: StateFlow<OnDeviceModelStatus> = AppContainer.onDeviceStatus

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.LocalOnly)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _pendingExecution = MutableStateFlow<PendingExecutionRequest?>(null)
    val pendingExecution: StateFlow<PendingExecutionRequest?> = _pendingExecution.asStateFlow()

    private val _terminalState = MutableStateFlow(TerminalSessionState())
    val terminalState: StateFlow<TerminalSessionState> = _terminalState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _modelDiscoveryState = MutableStateFlow<ModelDiscoveryState>(ModelDiscoveryState.Idle)
    val modelDiscoveryState: StateFlow<ModelDiscoveryState> = _modelDiscoveryState.asStateFlow()

    private val _sftpUploadStatus = MutableStateFlow<Pair<String, Float>?>(null)
    val sftpUploadStatus: StateFlow<Pair<String, Float>?> = _sftpUploadStatus.asStateFlow()

    private var activeExecutionJob: Job? = null
    private var promptWatchJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastAttemptedHost: SshHostProfile? = null

    init {
        viewModelScope.launch {
            repository.ensureSeedPlugins()
            repository.settingsDataStore.securitySettingsFlow.collect { sec ->
                haptics.enabled = sec.hapticsEnabled
            }
        }
        AppContainer.refreshOnDeviceStatus(application)
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

    // --- SSH Host Management & Periodic Heartbeat ---

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
                heartbeatJob?.cancel()
                sshEngine.disconnect()
                _connectionState.value = SshConnectionState.Disconnected
            }
            repository.deleteHost(id)
            haptics.tick()
        }
    }

    fun connectHost(profile: SshHostProfile) {
        lastAttemptedHost = profile
        heartbeatJob?.cancel()
        viewModelScope.launch {
            _connectionState.value = SshConnectionState.Connecting(profile.label)
            haptics.tick()
            try {
                val startMs = System.currentTimeMillis()
                val env = sshEngine.connect(profile)
                val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                repository.markHostConnected(profile.id)
                _connectionState.value = SshConnectionState.Connected(
                    host = profile,
                    env = env,
                    latencyMs = rtt,
                    lastHeartbeatAtMs = System.currentTimeMillis(),
                    isHealthy = true
                )
                haptics.success()
                startHeartbeatMonitor()
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

    fun reconnectLastHost() {
        val target = lastAttemptedHost ?: hosts.value.firstOrNull()
        if (target != null) {
            connectHost(target)
        } else {
            _bannerMessage.value = "Add an SSH host to connect."
        }
    }

    fun checkSshHealthNow() {
        viewModelScope.launch {
            val current = _connectionState.value as? SshConnectionState.Connected ?: return@launch
            try {
                val latency = sshEngine.checkHealth()
                _connectionState.value = current.copy(
                    latencyMs = latency,
                    lastHeartbeatAtMs = System.currentTimeMillis(),
                    isHealthy = true
                )
                haptics.tick()
            } catch (e: Exception) {
                heartbeatJob?.cancel()
                _connectionState.value = SshConnectionState.Error(
                    "SSH keepalive failed: ${e.localizedMessage ?: "Tunnel dropped"}"
                )
                haptics.warning()
            }
        }
    }

    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val current = _connectionState.value as? SshConnectionState.Connected ?: break
                try {
                    val latency = sshEngine.checkHealth()
                    _connectionState.value = current.copy(
                        latencyMs = latency,
                        lastHeartbeatAtMs = System.currentTimeMillis(),
                        isHealthy = true
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic SSH heartbeat failed", e)
                    _connectionState.value = SshConnectionState.Error(
                        "SSH connection lost (${e.localizedMessage ?: "timeout"})."
                    )
                    haptics.warning()
                    break
                }
            }
        }
    }

    fun disconnectHost() {
        heartbeatJob?.cancel()
        viewModelScope.launch {
            sshEngine.disconnect()
            _connectionState.value = SshConnectionState.Disconnected
            haptics.tick()
        }
    }

    // --- Safe Execution & Biometric Sudo ---

    fun requestCommandExecution(
        pluginId: String?,
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
            if (assessment.effectiveRisk >= RiskLevel.HIGH) {
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
            _terminalState.value = _terminalState.value.copy(pluginId = pluginId)
        } else {
            haptics.tick()
            launchSshExecution(pluginId, pluginName, command, sudoPassword = null)
        }
    }

    fun markBiometricVerified() {
        val current = _pendingExecution.value ?: return
        haptics.success()
        _pendingExecution.value = current.copy(biometricVerified = true)
    }

    fun confirmPendingExecution(sudoPassword: String?) {
        val pending = _pendingExecution.value ?: return
        val pluginId = _terminalState.value.pluginId
        _pendingExecution.value = null
        launchSshExecution(
            pluginId = pluginId,
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
        promptWatchJob?.cancel()
        activeExecutionJob?.cancel()
        sshEngine.cancelActiveCommand()
        _terminalState.value = _terminalState.value.copy(
            isRunning = false,
            detectedPrompt = null,
            errorMessage = "Execution cancelled by user."
        )
        haptics.warning()
    }

    fun sendTerminalInput(text: String, isSecret: Boolean = false) {
        viewModelScope.launch {
            val sent = sshEngine.sendInteractiveInput(text, appendNewline = true)
            if (sent) {
                haptics.tick()
                val marker = if (isSecret) "[secret sent]" else text
                val currentOut = _terminalState.value.output
                val suffix = if (currentOut.endsWith("\n") || currentOut.isEmpty()) "" else "\n"
                _terminalState.value = _terminalState.value.copy(
                    output = "$currentOut$suffix> $marker\n",
                    detectedPrompt = null
                )
            } else {
                haptics.warning()
                _bannerMessage.value = "Process stdin is no longer open."
            }
        }
    }

    fun sendTerminalControl(ctrlByte: Int, label: String) {
        viewModelScope.launch {
            val sent = sshEngine.sendControlByte(ctrlByte)
            if (sent) {
                haptics.tick()
                val currentOut = _terminalState.value.output
                _terminalState.value = _terminalState.value.copy(
                    output = "$currentOut\n^$label\n",
                    detectedPrompt = null
                )
            }
        }
    }

    private fun launchSshExecution(
        pluginId: String?,
        pluginName: String,
        command: String,
        sudoPassword: String?
    ) {
        promptWatchJob?.cancel()
        activeExecutionJob?.cancel()
        activeExecutionJob = viewModelScope.launch {
            val hostLabel = (_connectionState.value as? SshConnectionState.Connected)?.host?.label ?: "SSH Host"
            val outputBuilder = StringBuilder()
            _terminalState.value = TerminalSessionState(
                isVisible = true,
                isRunning = true,
                pluginId = pluginId,
                command = command,
                output = "",
                detectedPrompt = null,
                lastResult = null,
                errorMessage = null
            )

            val schedulePromptCheck: () -> Unit = {
                promptWatchJob?.cancel()
                val snapshot = outputBuilder.toString()
                val immediate = InteractivePromptDetector.detect(snapshot, stalledOnPartialLine = false)
                if (immediate != null && _terminalState.value.detectedPrompt == null) {
                    haptics.warning()
                }
                _terminalState.value = _terminalState.value.copy(
                    output = snapshot,
                    detectedPrompt = immediate
                )
                promptWatchJob = viewModelScope.launch {
                    delay(450L)
                    if (_terminalState.value.isRunning) {
                        val delayed = InteractivePromptDetector.detect(
                            outputBuilder.toString(),
                            stalledOnPartialLine = true
                        )
                        if (delayed != null && _terminalState.value.detectedPrompt == null) {
                            haptics.warning()
                            _terminalState.value = _terminalState.value.copy(detectedPrompt = delayed)
                        }
                    }
                }
            }

            try {
                val result = sshEngine.executeCommand(
                    command = command,
                    sudoPassword = sudoPassword,
                    allocatePty = true,
                    onStdoutChunk = { chunk ->
                        outputBuilder.append(chunk)
                        schedulePromptCheck()
                    },
                    onStderrChunk = { chunk ->
                        outputBuilder.append(chunk)
                        schedulePromptCheck()
                    }
                )
                promptWatchJob?.cancel()
                val finalOutput = outputBuilder.toString().ifBlank {
                    listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n")
                }
                _terminalState.value = _terminalState.value.copy(
                    isRunning = false,
                    output = finalOutput.ifBlank { "(Command completed with no output)" },
                    detectedPrompt = null,
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
                promptWatchJob?.cancel()
                throw e
            } catch (e: Exception) {
                promptWatchJob?.cancel()
                Log.e(TAG, "Command execution error", e)
                _terminalState.value = _terminalState.value.copy(
                    isRunning = false,
                    detectedPrompt = null,
                    errorMessage = e.localizedMessage ?: "SSH command execution failed."
                )
                haptics.danger()
            }
        }
    }

    // --- Sleep-Resilient AI Forge Service & Refinement Chat ---

    fun resetForge() {
        AppContainer.updateForgeState(ForgeState.Idle)
    }

    fun startForgeAnalysis(
        commandName: String,
        sourceMode: PluginAnalysisSource,
        manualDocsText: String,
        bypassExistingCheck: Boolean = false,
        guiStyle: PluginGuiStyle? = null,
        customInstructions: String = ""
    ) {
        val cleanCmd = commandName.trim()
        if (cleanCmd.isBlank()) {
            AppContainer.updateForgeState(ForgeState.Error("Enter a command name (e.g. ufw, docker, nginx, ffmpeg)."))
            return
        }
        val osHint = (_connectionState.value as? SshConnectionState.Connected)?.env?.osName ?: "POSIX"
        val resolvedStyle = guiStyle ?: securitySettings.value.defaultForgeGuiStyle
        haptics.tick()
        AppContainer.startForgePipeline(
            context = getApplication(),
            commandName = cleanCmd,
            sourceMode = sourceMode,
            manualDocs = manualDocsText,
            osHint = osHint,
            bypassCheck = bypassExistingCheck,
            guiStyle = resolvedStyle,
            customInstructions = customInstructions
        )
    }

    fun cancelForgeAnalysis() {
        AppContainer.cancelForgePipeline()
    }

    fun updateReadyForgePlugin(plugin: CliPlugin) {
        val current = forgeState.value as? ForgeState.Ready ?: return
        AppContainer.updateForgeState(current.copy(plugin = plugin))
    }

    suspend fun refinePluginWithChat(
        currentPlugin: CliPlugin,
        history: List<PluginChatTurn>,
        instruction: String,
        rawDocs: String = ""
    ): Result<Pair<CliPlugin, String>> {
        return runCatching {
            val activeLlm = repository.settingsDataStore.llmConfigFlow.first()
            llmAnalyzer.refinePluginWithChat(
                currentPlugin = currentPlugin,
                conversationHistory = history,
                userInstruction = instruction,
                rawDocs = rawDocs,
                config = activeLlm
            )
        }.onSuccess {
            haptics.success()
        }.onFailure {
            haptics.warning()
        }
    }

    fun resetBuiltinPlugin(pluginId: String) {
        viewModelScope.launch {
            val restored = repository.resetBuiltinPlugin(pluginId)
            if (restored != null) {
                haptics.success()
                _bannerMessage.value = "Restored ${restored.displayName} to factory built-in default"
            }
        }
    }

    suspend fun listRemoteDirectory(path: String): Result<Pair<String, List<RemoteFileEntry>>> {
        return runCatching { sshEngine.listRemoteDirectory(path) }
    }

    suspend fun uploadPhoneFileToRemote(uri: Uri): Result<String> {
        val app = getApplication<Application>()
        return runCatching {
            var displayName = "uploaded_file"
            var totalBytes = -1L
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        totalBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
            _sftpUploadStatus.value = displayName to 0f
            val remotePath = app.contentResolver.openInputStream(uri)?.use { stream ->
                sshEngine.uploadLocalStream(
                    inputStream = stream,
                    fileName = displayName,
                    totalBytes = totalBytes
                ) { transferred ->
                    val pct = if (totalBytes > 0L) {
                        (transferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0.5f
                    _sftpUploadStatus.value = displayName to pct
                }
            } ?: throw IllegalStateException("Unable to open selected phone file.")
            haptics.success()
            _bannerMessage.value = "Uploaded to $remotePath"
            remotePath
        }.also {
            _sftpUploadStatus.value = null
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

    fun refreshOnDeviceStatus() {
        haptics.tick()
        AppContainer.refreshOnDeviceStatus(getApplication())
    }

    fun downloadOnDeviceModel() {
        haptics.tick()
        AppContainer.downloadOnDeviceModel(getApplication())
    }

    fun clearModelDiscovery() {
        _modelDiscoveryState.value = ModelDiscoveryState.Idle
    }

    fun saveLlmBackend(
        providerType: LlmProviderType,
        byokPreset: ByokProviderPreset = ByokProviderPreset.CUSTOM,
        geminiModel: String,
        customEndpointUrl: String,
        customModelName: String,
        plainApiKey: String?,
        timeoutSeconds: Int,
        onDeviceFallbackEnabled: Boolean = false,
        onDeviceFallbackProvider: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
        onDeviceCondenseDocs: Boolean = true,
        onDeviceTemperature: Float = 0.2f
    ) {
        viewModelScope.launch {
            repository.settingsDataStore.saveLlmConfig(
                providerType = providerType,
                byokPreset = byokPreset,
                geminiModel = geminiModel,
                customEndpointUrl = customEndpointUrl,
                customModelName = customModelName,
                plainApiKey = plainApiKey,
                timeoutSeconds = timeoutSeconds,
                onDeviceFallbackEnabled = onDeviceFallbackEnabled,
                onDeviceFallbackProvider = onDeviceFallbackProvider,
                onDeviceCondenseDocs = onDeviceCondenseDocs,
                onDeviceTemperature = onDeviceTemperature
            )
            haptics.success()
            _bannerMessage.value = "Saved LLM backend configuration"
        }
    }

    suspend fun testLlmBackend(config: LlmBackendConfig): Result<String> {
        return testAndDiscoverModels(config).map { it.statusMessage }
    }

    suspend fun testAndDiscoverModels(config: LlmBackendConfig): Result<EndpointTestResult> {
        _modelDiscoveryState.value = ModelDiscoveryState.Discovering
        return runCatching {
            llmAnalyzer.testAndDiscoverModels(config)
        }.onSuccess { res ->
            _modelDiscoveryState.value = if (res.discoveredModels.isNotEmpty()) {
                ModelDiscoveryState.Ready(res.discoveredModels, res.statusMessage)
            } else {
                ModelDiscoveryState.Idle
            }
            haptics.success()
        }.onFailure { err ->
            _modelDiscoveryState.value = ModelDiscoveryState.Error(
                err.localizedMessage ?: "Failed to query endpoint."
            )
            haptics.warning()
        }
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
        heartbeatJob?.cancel()
        super.onCleared()
    }
}
