package com.snehashis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val displayName: String, val subtitle: String) {
    SYSTEM("System Default", "Follow Android light/dark mode with vibrant ShellCraft palettes"),
    NEON_DARK("Neon Midnight", "High-contrast obsidian surfaces with glowing cyan & emerald accents"),
    DAYLIGHT_LIGHT("Vivid Daylight", "Crisp frosted daylight canvas with electric cobalt & teal accents")
}

@Serializable
enum class PluginGuiStyle(val displayName: String, val subtitle: String) {
    MODULAR_DASHBOARD(
        displayName = "Modular Tool Dashboard",
        subtitle = "Native widgets, sliders, quick presets, and row action buttons"
    ),
    HTML_CANVAS_CUSTOM(
        displayName = "Custom HTML/Canvas GUI",
        subtitle = "Sandboxed HTML5/Canvas interactive control deck with live bridge"
    )
}

@Serializable
enum class LlmProviderType {
    FIREBASE_GEMINI,
    @Deprecated("Consolidated into OPENAI_COMPATIBLE with ByokProviderPreset.LOCAL_OLLAMA")
    OLLAMA_LOCAL,
    OPENAI_COMPATIBLE,
    ON_DEVICE_NANO_EXPERIMENTAL
}

@Serializable
enum class ByokProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val apiKeyPortalUrl: String,
    val keyHint: String,
    val defaultModel: String,
    val freeTierSummary: String
) {
    GEMINI_AI_STUDIO(
        displayName = "Gemini AI Studio",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        apiKeyPortalUrl = "https://aistudio.google.com/apikey",
        keyHint = "AIza...",
        defaultModel = "gemini-2.5-flash",
        freeTierSummary = "Free Google AI Studio API key tier (Gemini Flash / Pro, zero Firebase App Check needed)"
    ),
    NVIDIA_NIM(
        displayName = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        apiKeyPortalUrl = "https://build.nvidia.com/",
        keyHint = "nvapi-...",
        defaultModel = "meta/llama-3.3-70b-instruct",
        freeTierSummary = "Free NVIDIA NIM developer credits for DeepSeek-R1, Qwen, Llama & Nemotron"
    ),
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        apiKeyPortalUrl = "https://console.groq.com/keys",
        keyHint = "gsk_...",
        defaultModel = "llama-3.3-70b-versatile",
        freeTierSummary = "Fast LPU free tier (~30 RPM, no credit card required)"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        apiKeyPortalUrl = "https://openrouter.ai/keys",
        keyHint = "sk-or-...",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        freeTierSummary = "Single gateway with zero-cost ':free' community models"
    ),
    LOCAL_OLLAMA(
        displayName = "Local Ollama",
        defaultBaseUrl = "http://192.168.1.100:11434/v1",
        apiKeyPortalUrl = "",
        keyHint = "Optional key (leave blank for LAN Ollama)",
        defaultModel = "qwen2.5-coder:7b",
        freeTierSummary = "Run local models via Ollama's OpenAI-compatible /v1 API (Zero cloud cost)"
    ),
    CUSTOM(
        displayName = "Custom URL",
        defaultBaseUrl = "http://192.168.1.100:1234/v1",
        apiKeyPortalUrl = "",
        keyHint = "Optional Bearer API key",
        defaultModel = "qwen/qwen3.6-35b-a3b",
        freeTierSummary = "Self-hosted LM Studio, vLLM, llama.cpp, or any OpenAI-compatible proxy"
    )
}

data class PresetCredentialSlot(
    val endpointUrl: String,
    val modelName: String,
    val encryptedApiKey: String
)

enum class ModelCapabilityTag(val label: String) {
    FREE("FREE"),
    CODER("CODER"),
    REASONING("THINK"),
    FAST("FAST")
}

data class DiscoveredLlmModel(
    val id: String,
    val ownedBy: String = "",
    val tags: Set<ModelCapabilityTag> = emptySet()
)

data class EndpointTestResult(
    val statusMessage: String,
    val discoveredModels: List<DiscoveredLlmModel> = emptyList()
)

sealed interface ModelDiscoveryState {
    data object Idle : ModelDiscoveryState
    data object Discovering : ModelDiscoveryState
    data class Ready(
        val models: List<DiscoveredLlmModel>,
        val statusMessage: String
    ) : ModelDiscoveryState
    data class Error(val message: String) : ModelDiscoveryState
}

data class LlmBackendConfig(
    val providerType: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
    val byokPreset: ByokProviderPreset = ByokProviderPreset.CUSTOM,
    val geminiModel: String = "gemini-3.8-flash",
    val customEndpointUrl: String = "http://192.168.1.100:11434/v1",
    val customModelName: String = "qwen2.5-coder:7b",
    val encryptedApiKey: String = "",
    val presetSlots: Map<ByokProviderPreset, PresetCredentialSlot> = emptyMap(),
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

enum class ChatRole {
    USER,
    ASSISTANT
}

data class PluginChatTurn(
    val id: String,
    val role: ChatRole,
    val message: String,
    val pluginSnapshot: CliPlugin? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

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
