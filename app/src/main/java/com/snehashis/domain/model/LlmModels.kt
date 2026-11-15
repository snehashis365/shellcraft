package com.snehashis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LlmProviderType {
    FIREBASE_GEMINI,
    OLLAMA_LOCAL,
    OPENAI_COMPATIBLE,
    ON_DEVICE_NANO_EXPERIMENTAL
}

data class LlmBackendConfig(
    val providerType: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
    val geminiModel: String = "gemini-3.8-flash",
    val customEndpointUrl: String = "http://192.168.1.100:11434",
    val customModelName: String = "qwen2.5-coder:7b",
    val encryptedApiKey: String = "",
    val timeoutSeconds: Int = 240,
    val onDeviceFallbackEnabled: Boolean = false,
    val onDeviceFallbackProvider: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
    val onDeviceCondenseDocs: Boolean = true,
    val onDeviceTemperature: Float = 0.2f
)

sealed interface OnDeviceModelStatus {
    data object Checking : OnDeviceModelStatus
    data object Available : OnDeviceModelStatus
    data object Downloadable : OnDeviceModelStatus
    data class Downloading(val bytesDownloaded: Long = 0L) : OnDeviceModelStatus
    data class Unavailable(val reason: String) : OnDeviceModelStatus
    data class Error(val message: String) : OnDeviceModelStatus
}

enum class PluginAnalysisSource {
    SSH_PROBE,
    MANUAL_DOCS
}

sealed interface ForgeState {
    data object Idle : ForgeState
    data class CheckingGitHub(val commandName: String) : ForgeState
    data class FoundExistingRemote(
        val commandName: String,
        val plugin: CliPlugin,
        val sourcePath: String
    ) : ForgeState
    data class ProbingSshDocs(val commandName: String) : ForgeState
    data class AnalyzingWithLlm(val statusMessage: String, val rawDocsPreview: String = "") : ForgeState
    data class Ready(val plugin: CliPlugin, val rawDocsUsed: String) : ForgeState
    data class Error(val message: String) : ForgeState
}
