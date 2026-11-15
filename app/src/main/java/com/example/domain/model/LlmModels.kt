package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LlmProviderType {
    FIREBASE_GEMINI,
    OLLAMA_LOCAL,
    OPENAI_COMPATIBLE
}

data class LlmBackendConfig(
    val providerType: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
    val geminiModel: String = "gemini-3.8-flash",
    val customEndpointUrl: String = "http://192.168.1.100:11434",
    val customModelName: String = "qwen2.5-coder:7b",
    val encryptedApiKey: String = "",
    val timeoutSeconds: Int = 60
)

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
