package com.snehashis.core

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.snehashis.core.security.HapticFeedbackController
import com.snehashis.data.github.GitHubPluginSyncClient
import com.snehashis.data.llm.LlmPluginAnalyzer
import com.snehashis.data.llm.OnDeviceNanoEngine
import com.snehashis.data.repository.ShellCraftRepository
import com.snehashis.data.ssh.SshEngine
import com.snehashis.domain.model.ForgeState
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.service.ForgeGenerationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "AppContainer"

object AppContainer {
    @Volatile
    private var initialized = false

    lateinit var repository: ShellCraftRepository
        private set
    lateinit var sshEngine: SshEngine
        private set
    lateinit var onDeviceNanoEngine: OnDeviceNanoEngine
        private set
    lateinit var llmAnalyzer: LlmPluginAnalyzer
        private set
    lateinit var gitHubClient: GitHubPluginSyncClient
        private set
    lateinit var haptics: HapticFeedbackController
        private set

    private val forgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeForgeJob: Job? = null
    private var onDeviceDownloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val _forgeState = MutableStateFlow<ForgeState>(ForgeState.Idle)
    val forgeState: StateFlow<ForgeState> = _forgeState.asStateFlow()

    private val _onDeviceStatus = MutableStateFlow<OnDeviceModelStatus>(OnDeviceModelStatus.Checking)
    val onDeviceStatus: StateFlow<OnDeviceModelStatus> = _onDeviceStatus.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            repository = ShellCraftRepository(appCtx)
            sshEngine = SshEngine()
            onDeviceNanoEngine = OnDeviceNanoEngine()
            llmAnalyzer = LlmPluginAnalyzer(onDeviceNanoEngine)
            gitHubClient = GitHubPluginSyncClient()
            haptics = HapticFeedbackController(appCtx)
            initialized = true
        }
    }

    fun refreshOnDeviceStatus(context: Context) {
        ensureInitialized(context.applicationContext)
        forgeScope.launch {
            _onDeviceStatus.value = OnDeviceModelStatus.Checking
            _onDeviceStatus.value = onDeviceNanoEngine.checkStatus()
        }
    }

    fun downloadOnDeviceModel(context: Context) {
        ensureInitialized(context.applicationContext)
        if (onDeviceDownloadJob?.isActive == true) return
        onDeviceDownloadJob = forgeScope.launch {
            val result = onDeviceNanoEngine.downloadModel { progress ->
                _onDeviceStatus.value = progress
            }
            _onDeviceStatus.value = result
            if (result is OnDeviceModelStatus.Available) {
                haptics.success()
            } else if (result is OnDeviceModelStatus.Error || result is OnDeviceModelStatus.Unavailable) {
                haptics.warning()
            }
        }
    }

    fun updateForgeState(state: ForgeState) {
        _forgeState.value = state
    }

    fun startForgePipeline(
        context: Context,
        commandName: String,
        sourceMode: PluginAnalysisSource,
        manualDocs: String,
        osHint: String,
        bypassCheck: Boolean
    ) {
        val appCtx = context.applicationContext
        ensureInitialized(appCtx)

        activeForgeJob?.cancel()
        _forgeState.value = ForgeState.CheckingGitHub(commandName)

        // Best-effort foreground service companion for sleep-resilient background execution;
        // never fatal if an OEM/OS policy restricts foreground service promotion.
        ForgeGenerationService.startCompanion(appCtx, commandName)

        activeForgeJob = forgeScope.launch {
            var acquiredLocks = false
            try {
                val activeLlm = repository.settingsDataStore.llmConfigFlow.first()
                val lockTimeoutMs = ((activeLlm.timeoutSeconds.coerceIn(30, 600) + 60) * 1000L)
                acquireLocks(appCtx, lockTimeoutMs)
                acquiredLocks = true

                if (!bypassCheck) {
                    val gh = repository.settingsDataStore.githubConfigFlow.first()
                    if (gh.isConfigured) {
                        _forgeState.value = ForgeState.CheckingGitHub(commandName)
                        val remoteMatch = runCatching {
                            gitHubClient.findExistingPlugin(commandName, gh)
                        }.getOrNull()
                        if (remoteMatch != null) {
                            _forgeState.value = ForgeState.FoundExistingRemote(
                                commandName = commandName,
                                plugin = remoteMatch.first,
                                sourcePath = "${gh.ownerRepo}/${remoteMatch.second}"
                            )
                            haptics.success()
                            return@launch
                        }
                    }

                    val localMatch = repository.findLocalPluginByCommand(commandName)
                    if (localMatch != null) {
                        _forgeState.value = ForgeState.FoundExistingRemote(
                            commandName = commandName,
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
                                "No active SSH host connected. Connect a host in Workspace or paste docs manually."
                            )
                            haptics.warning()
                            return@launch
                        }
                        _forgeState.value = ForgeState.ProbingSshDocs(commandName)
                        sshEngine.probeCommandDocumentation(commandName)
                    }
                    PluginAnalysisSource.MANUAL_DOCS -> {
                        if (manualDocs.isBlank()) {
                            _forgeState.value = ForgeState.Error(
                                "Paste `--help` or `man` documentation text to analyze."
                            )
                            haptics.warning()
                            return@launch
                        }
                        manualDocs.trim()
                    }
                }

                val providerLabel = when (activeLlm.providerType) {
                    LlmProviderType.FIREBASE_GEMINI -> "Gemini (${activeLlm.geminiModel})"
                    LlmProviderType.OLLAMA_LOCAL -> "Ollama (${activeLlm.customModelName})"
                    LlmProviderType.OPENAI_COMPATIBLE -> "Custom LLM (${activeLlm.customModelName})"
                    LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> "Gemini Nano (On-Device Exp.)"
                }

                _forgeState.value = ForgeState.AnalyzingWithLlm(
                    statusMessage = "Synthesizing `$commandName` GUI via $providerLabel...",
                    rawDocsPreview = docsToAnalyze.take(600)
                )

                val generated = llmAnalyzer.analyzeCliDocumentation(
                    commandName = commandName,
                    rawDocs = docsToAnalyze,
                    remoteOsHint = osHint,
                    config = activeLlm,
                    onProgress = { progress ->
                        _forgeState.value = ForgeState.AnalyzingWithLlm(
                            statusMessage = "$providerLabel: $progress",
                            rawDocsPreview = docsToAnalyze.take(600)
                        )
                    }
                )

                _forgeState.value = ForgeState.Ready(generated, docsToAnalyze)
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Forge pipeline failed", t)
                _forgeState.value = ForgeState.Error(
                    t.localizedMessage ?: "Plugin generation failed: ${t.javaClass.simpleName}"
                )
                haptics.warning()
            } finally {
                if (acquiredLocks) {
                    releaseLocks()
                }
            }
        }
    }

    fun cancelForgePipeline() {
        activeForgeJob?.cancel()
        activeForgeJob = null
        releaseLocks()
        _forgeState.value = ForgeState.Error("Generation cancelled by user.")
        if (initialized) {
            haptics.warning()
        }
    }

    private fun acquireLocks(appCtx: Context, timeoutMs: Long) {
        try {
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShellCraft::ForgeWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
            val wm = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm?.createWifiLock(wifiMode, "ShellCraft::ForgeWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not acquire wake/wifi lock", t)
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }
}
