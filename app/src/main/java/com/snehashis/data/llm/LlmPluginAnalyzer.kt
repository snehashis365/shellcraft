@file:Suppress("DEPRECATION")

package com.snehashis.data.llm

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.snehashis.core.security.SecretCipher
import com.snehashis.data.local.LlmSettingsMigration
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.CliDocCondenser
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.DiscoveredLlmModel
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelCapabilityTag
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginGuiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val TAG = "LlmPluginAnalyzer"

class LlmPluginAnalyzer(
    private val onDeviceEngine: OnDeviceNanoEngine = OnDeviceNanoEngine()
) {

    private val json = LlmPluginPromptBuilder.json

    suspend fun analyzeCliDocumentation(
        commandName: String,
        rawDocs: String,
        remoteOsHint: String,
        config: LlmBackendConfig,
        guiStyle: PluginGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD,
        customInstructions: String = "",
        onProgress: ((String) -> Unit)? = null
    ): CliPlugin {
        val timeoutSec = config.timeoutSeconds.coerceIn(30, 600)
        val timeoutMs = timeoutSec * 1000L
        val prompt = LlmPluginPromptBuilder.buildGeneratePrompt(
            commandName = commandName,
            rawDocs = rawDocs,
            remoteOsHint = remoteOsHint,
            guiStyle = guiStyle,
            customInstructions = customInstructions
        )
        return when (config.providerType) {
            LlmProviderType.FIREBASE_GEMINI -> {
                val rawJson = runFirebaseGeminiJson(prompt, config.geminiModel, timeoutMs)
                LlmPluginPromptBuilder.parseGeneratedPluginJson(
                    commandName = commandName,
                    rawJson = rawJson,
                    fallbackGuiStyle = guiStyle
                ).first
            }
            LlmProviderType.OLLAMA_LOCAL, LlmProviderType.OPENAI_COMPATIBLE -> {
                val effectiveConfig = normalizeIfLegacyOllama(config)
                val rawJson = runOpenAiCompatibleJsonStream(
                    prompt = prompt,
                    config = effectiveConfig,
                    timeoutSec = timeoutSec,
                    timeoutMs = timeoutMs,
                    onProgress = onProgress
                )
                LlmPluginPromptBuilder.parseGeneratedPluginJson(
                    commandName = commandName,
                    rawJson = extractJsonObject(rawJson),
                    fallbackGuiStyle = guiStyle
                ).first
            }
            LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> analyzeWithOnDeviceNano(
                commandName = commandName,
                rawDocs = rawDocs,
                remoteOsHint = remoteOsHint,
                config = config,
                guiStyle = guiStyle,
                customInstructions = customInstructions,
                timeoutMs = timeoutMs,
                onProgress = onProgress
            )
        }
    }

    suspend fun refinePluginWithChat(
        currentPlugin: CliPlugin,
        conversationHistory: List<PluginChatTurn>,
        userInstruction: String,
        rawDocs: String,
        config: LlmBackendConfig,
        onProgress: ((String) -> Unit)? = null
    ): Pair<CliPlugin, String> {
        val timeoutSec = config.timeoutSeconds.coerceIn(30, 600)
        val timeoutMs = timeoutSec * 1000L
        val prompt = LlmPluginPromptBuilder.buildRefinePrompt(
            currentPlugin = currentPlugin,
            history = conversationHistory,
            userInstruction = userInstruction,
            rawDocs = rawDocs
        )
        val effectiveConfig = when {
            config.providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL &&
                config.onDeviceFallbackEnabled &&
                config.onDeviceFallbackProvider != LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL ->
                config.copy(providerType = config.onDeviceFallbackProvider)
            config.providerType == LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL ->
                throw IllegalStateException("Iterative HTML/GUI refinement needs Cloud Gemini or a BYOK/Local Ollama API backend selected in Settings (or enable Auto-Fallback).")
            else -> config
        }

        val rawJson = when (effectiveConfig.providerType) {
            LlmProviderType.FIREBASE_GEMINI ->
                runFirebaseGeminiJson(prompt, effectiveConfig.geminiModel, timeoutMs)
            LlmProviderType.OLLAMA_LOCAL, LlmProviderType.OPENAI_COMPATIBLE ->
                extractJsonObject(
                    runOpenAiCompatibleJsonStream(
                        prompt = prompt,
                        config = normalizeIfLegacyOllama(effectiveConfig),
                        timeoutSec = timeoutSec,
                        timeoutMs = timeoutMs,
                        onProgress = onProgress
                    )
                )
            LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL ->
                throw IllegalStateException("On-Device Nano is not supported for full GUI chat edits.")
        }

        return LlmPluginPromptBuilder.parseGeneratedPluginJson(
            commandName = currentPlugin.commandName,
            rawJson = rawJson,
            fallbackGuiStyle = currentPlugin.uiMode,
            preserveExisting = currentPlugin
        )
    }

    suspend fun testEndpoint(config: LlmBackendConfig): String =
        testAndDiscoverModels(config).statusMessage

    suspend fun testAndDiscoverModels(config: LlmBackendConfig): EndpointTestResult = withContext(Dispatchers.IO) {
        when (config.providerType) {
            LlmProviderType.FIREBASE_GEMINI -> {
                val model by lazy { Firebase.ai.generativeModel(config.geminiModel) }
                val response = withTimeout(30_000L) {
                    model.generateContent("Reply with exactly: OK")
                }
                val curated = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon").map { id ->
                    DiscoveredLlmModel(
                        id = id,
                        ownedBy = "firebase-ai",
                        tags = classifyModelTags(id, "firebase")
                    )
                }
                EndpointTestResult(
                    statusMessage = "Firebase AI Logic (${config.geminiModel}) ready: ${response.text?.trim() ?: "OK"}",
                    discoveredModels = curated
                )
            }
            LlmProviderType.OLLAMA_LOCAL, LlmProviderType.OPENAI_COMPATIBLE -> {
                val effective = normalizeIfLegacyOllama(config)
                val baseUrl = effective.customEndpointUrl.trim().trimEnd('/')
                require(baseUrl.startsWith("http")) { "Endpoint URL must start with http:// or https://" }
                val modelsUrl = if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/openai")) {
                    "$baseUrl/models"
                } else {
                    "$baseUrl/v1/models"
                }
                val client = buildHttpClient(20)
                val apiKey = SecretCipher.decrypt(effective.encryptedApiKey)
                val reqBuilder = Request.Builder().url(modelsUrl).get()
                if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
                if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                    reqBuilder.header("HTTP-Referer", "https://shellcraft.local")
                    reqBuilder.header("X-Title", "ShellCraft")
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val hint = when (resp.code) {
                            401, 403 -> "Invalid or missing API key for ${effective.byokPreset.displayName}."
                            429 -> "Rate limit reached (HTTP 429). Retry shortly."
                            else -> resp.message
                        }
                        throw IllegalStateException("HTTP ${resp.code}: $hint")
                    }
                    val discovered = parseOpenAiModelsResponse(body, baseUrl)
                    val presetName = if (effective.byokPreset == ByokProviderPreset.CUSTOM) {
                        "Endpoint"
                    } else {
                        effective.byokPreset.displayName
                    }
                    EndpointTestResult(
                        statusMessage = "$presetName connected · ${discovered.size} models discovered",
                        discoveredModels = discovered
                    )
                }
            }
            LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> {
                EndpointTestResult(statusMessage = onDeviceEngine.warmupAndTest())
            }
        }
    }

    private fun normalizeIfLegacyOllama(config: LlmBackendConfig): LlmBackendConfig {
        return if (config.providerType == LlmProviderType.OLLAMA_LOCAL ||
            config.byokPreset == ByokProviderPreset.LOCAL_OLLAMA
        ) {
            config.copy(
                providerType = LlmProviderType.OPENAI_COMPATIBLE,
                byokPreset = ByokProviderPreset.LOCAL_OLLAMA,
                customEndpointUrl = LlmSettingsMigration.normalizeOllamaV1Url(config.customEndpointUrl)
            )
        } else {
            config
        }
    }

    private suspend fun runFirebaseGeminiJson(
        prompt: String,
        modelName: String,
        timeoutMs: Long
    ): String {
        val geminiConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = LlmPluginPromptBuilder.pluginResponseSchema
        }
        val model by lazy {
            Firebase.ai.generativeModel(
                modelName = modelName.ifBlank { "gemini-3.8-flash" },
                generationConfig = geminiConfig
            )
        }
        try {
            val response = withTimeout(timeoutMs) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
            return rawJson
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out", e)
            throw IllegalStateException("Gemini analysis timed out after ${timeoutMs / 1000}s.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            val detail = e.localizedMessage ?: "Firebase AI Logic generation failed."
            val hint = if (detail.contains("403") || detail.contains("App Check", ignoreCase = true) ||
                detail.contains("attestation", ignoreCase = true)
            ) {
                " (Firebase App Check rejected the debug token. Switch to BYOK / Local API in Settings for zero App Check dependency.)"
            } else {
                ""
            }
            throw IllegalStateException("$detail$hint", e)
        }
    }

    private suspend fun runOpenAiCompatibleJsonStream(
        prompt: String,
        config: LlmBackendConfig,
        timeoutSec: Int,
        timeoutMs: Long,
        onProgress: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = config.customEndpointUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("http")) { "Configure a valid OpenAI-compatible HTTP endpoint in Settings." }
        val completionsUrl = if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/openai")) {
            "$baseUrl/chat/completions"
        } else {
            "$baseUrl/v1/chat/completions"
        }
        val client = buildHttpClient(timeoutSec)
        val apiKey = SecretCipher.decrypt(config.encryptedApiKey)
        val payload = OpenAiChatRequest(
            model = config.customModelName.ifBlank { "gpt-4o-mini" },
            temperature = 0.15,
            stream = true,
            messages = listOf(
                ChatMsg("system", LlmPluginPromptBuilder.SYSTEM_JSON_INSTRUCTIONS),
                ChatMsg("user", prompt)
            )
        )
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder().url(completionsUrl).post(body)
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }
        if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
            reqBuilder.header("HTTP-Referer", "https://shellcraft.local")
            reqBuilder.header("X-Title", "ShellCraft")
        }

        val contentBuilder = StringBuilder()
        var reasoningChars = 0

        try {
            withTimeout(timeoutMs) {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errSnippet = resp.body?.string()?.take(240).orEmpty()
                        val hint = when (resp.code) {
                            401, 403 -> "Authentication failed — check your ${config.byokPreset.displayName} API key in Settings."
                            429 -> "Free-tier rate limit hit (HTTP 429). Wait briefly or pick another model."
                            else -> errSnippet
                        }
                        throw IllegalStateException("Provider error HTTP ${resp.code}: $hint")
                    }
                    val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty provider stream")
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        while (true) {
                            val rawLine = reader.readLine() ?: break
                            val line = rawLine.trim()
                            if (line.isEmpty() || !line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                            val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("delta")?.jsonObject ?: continue
                            val reasoning = runCatching { delta["reasoning_content"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                            val content = runCatching { delta["content"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                            if (reasoning.isNotEmpty()) {
                                reasoningChars += reasoning.length
                                onProgress?.invoke("Model reasoning ($reasoningChars chars)...")
                            }
                            if (content.isNotEmpty()) {
                                contentBuilder.append(content)
                                onProgress?.invoke("Streaming GUI JSON (${contentBuilder.length} chars)...")
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("LLM timed out after ${timeoutSec}s (reasoning reached $reasoningChars chars). Increase Timeout in Settings or pick a faster model.")
        }

        val finalContent = contentBuilder.toString()
        if (finalContent.isBlank()) {
            throw IllegalStateException("LLM finished reasoning ($reasoningChars chars) without emitting JSON content.")
        }
        finalContent
    }

    private suspend fun analyzeWithOnDeviceNano(
        commandName: String,
        rawDocs: String,
        remoteOsHint: String,
        config: LlmBackendConfig,
        guiStyle: PluginGuiStyle,
        customInstructions: String,
        timeoutMs: Long,
        onProgress: ((String) -> Unit)?
    ): CliPlugin {
        val status = onDeviceEngine.checkStatus()
        if (status !is OnDeviceModelStatus.Available) {
            val statusLabel = when (status) {
                is OnDeviceModelStatus.Downloadable -> "model not downloaded yet"
                is OnDeviceModelStatus.Downloading -> "model still downloading"
                is OnDeviceModelStatus.Unavailable -> "device unsupported"
                is OnDeviceModelStatus.Error -> status.message
                OnDeviceModelStatus.Checking -> "checking status"
                OnDeviceModelStatus.Available -> "available"
            }
            if (config.onDeviceFallbackEnabled && config.onDeviceFallbackProvider != LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL) {
                onProgress?.invoke("AICore ($statusLabel) — falling back to ${config.onDeviceFallbackProvider.name}...")
                return analyzeCliDocumentation(
                    commandName = commandName,
                    rawDocs = rawDocs,
                    remoteOsHint = remoteOsHint,
                    config = config.copy(providerType = config.onDeviceFallbackProvider),
                    guiStyle = guiStyle,
                    customInstructions = customInstructions,
                    onProgress = onProgress
                )
            }
            val actionHint = when (status) {
                is OnDeviceModelStatus.Downloadable ->
                    "Gemini Nano is downloadable on this phone. Open Settings → On-Device Nano (Exp.) and tap 'Download via AICore' (or enable Auto-Fallback)."
                is OnDeviceModelStatus.Unavailable ->
                    "On-device Gemini Nano (AICore) is unavailable on this device: ${status.reason}. Enable Auto-Fallback in Settings or choose another backend."
                else -> "On-device Gemini Nano is not ready ($statusLabel)."
            }
            throw IllegalStateException(actionHint)
        }

        val effectiveDocs = if (config.onDeviceCondenseDocs) {
            onProgress?.invoke("Condensing CLI docs for Gemini Nano token ceiling...")
            CliDocCondenser.condense(commandName, rawDocs, maxChars = 6_000)
        } else {
            rawDocs.trim().take(6_000)
        }

        val nanoPrompt = buildOnDeviceNanoPrompt(commandName, effectiveDocs, remoteOsHint, customInstructions)
        return try {
            val rawOutput = withTimeout(timeoutMs) {
                onDeviceEngine.generatePluginJsonStream(
                    systemPrompt = ON_DEVICE_NANO_SYSTEM_PROMPT,
                    userPrompt = nanoPrompt,
                    temperatureValue = config.onDeviceTemperature,
                    maxOutputTokensValue = 1024,
                    onProgress = onProgress
                )
            }
            val repairedJson = CliDocCondenser.repairJson(extractJsonObject(rawOutput))
            LlmPluginPromptBuilder.parseGeneratedPluginJson(
                commandName = commandName,
                rawJson = repairedJson,
                authorOverride = "Gemini Nano (On-Device Exp.)",
                fallbackGuiStyle = guiStyle
            ).first
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (config.onDeviceFallbackEnabled && config.onDeviceFallbackProvider != LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL) {
                onProgress?.invoke("On-device generation hit limit (${t.localizedMessage?.take(48)}); falling back...")
                return analyzeCliDocumentation(
                    commandName = commandName,
                    rawDocs = rawDocs,
                    remoteOsHint = remoteOsHint,
                    config = config.copy(providerType = config.onDeviceFallbackProvider),
                    guiStyle = guiStyle,
                    customInstructions = customInstructions,
                    onProgress = onProgress
                )
            }
            throw IllegalStateException(
                t.localizedMessage ?: "On-device Gemini Nano plugin synthesis failed.",
                t
            )
        }
    }

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return trimmed
    }

    private fun buildHttpClient(timeoutSeconds: Int): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    private fun buildOnDeviceNanoPrompt(
        commandName: String,
        condensedDocs: String,
        remoteOsHint: String,
        customInstructions: String
    ): String {
        val note = if (customInstructions.isNotBlank()) "\n- User preference: ${customInstructions.take(240)}" else ""
        return """
## Task
Generate a `shellcraft.plugin.v2` JSON object for CLI command `$commandName` on `$remoteOsHint`.

## Rules
- Output ONLY valid JSON. No markdown, no intro, no prose.
- Provide 2 to 3 useful actions ordered safest to administrative, plus 1-2 quick presets.$note
- Parameter `type`: TEXT, NUMBER, BOOLEAN_FLAG, SELECT_ENUM, PATH, PORT, or KEY_VALUE.
- Set `widget` (`SLIDER` with `minValue`/`maxValue`, `SEGMENTED_CHOICE`, or `FILE_PATH`).

## CLI Documentation
<cli_docs>
$condensedDocs
</cli_docs>
""".trimIndent()
    }

    companion object {
        private const val ON_DEVICE_NANO_SYSTEM_PROMPT =
            "You are a POSIX CLI-to-GUI compiler. Output ONLY a single compact valid JSON object matching the schema. Do not include markdown fences or explanations."

        private val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun classifyModelTags(modelId: String, baseUrl: String = ""): Set<ModelCapabilityTag> {
            val lower = modelId.lowercase()
            val tags = mutableSetOf<ModelCapabilityTag>()
            if (lower.endsWith(":free") ||
                baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true) ||
                baseUrl.contains("api.groq.com", ignoreCase = true)
            ) {
                tags.add(ModelCapabilityTag.FREE)
            }
            if (lower.contains("coder") || lower.contains("codestral") ||
                lower.contains("devstral") || lower.contains("deepseek-coder") ||
                lower.contains("qwen2.5-coder") || lower.contains("qwen3-coder")
            ) {
                tags.add(ModelCapabilityTag.CODER)
            }
            if (lower.contains("r1") || lower.contains("thinking") ||
                lower.contains("reason") || lower.contains("qwq") ||
                lower.contains("nemotron-ultra") || lower.contains("argon")
            ) {
                tags.add(ModelCapabilityTag.REASONING)
            }
            if (lower.contains("flash") || lower.contains("lite") ||
                lower.contains("mini") || lower.contains("instant") ||
                lower.contains("-8b") || lower.contains(":8b") || lower.contains("3b")
            ) {
                tags.add(ModelCapabilityTag.FAST)
            }
            return tags
        }

        fun parseOpenAiModelsResponse(rawJson: String, baseUrl: String = ""): List<DiscoveredLlmModel> {
            val root = runCatching { parserJson.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val items = root["data"]?.jsonArray ?: root["models"]?.jsonArray ?: return emptyList()
            val isGoogleAiStudio = baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)

            val parsed = items.mapNotNull { el ->
                val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val rawId = obj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (rawId.isBlank()) return@mapNotNull null

                val activeFlag = obj["active"]?.jsonPrimitive?.contentOrNull
                if (activeFlag == "false") return@mapNotNull null

                val cleanId = if (isGoogleAiStudio && rawId.startsWith("models/")) {
                    rawId.removePrefix("models/")
                } else {
                    rawId
                }

                val lower = cleanId.lowercase()
                if (lower.contains("embedding") || lower.contains("rerank") ||
                    lower.contains("imagen") || lower.contains("veo") ||
                    lower.contains("whisper") || lower.contains("tts") ||
                    lower == "models/aqa" || lower == "aqa"
                ) {
                    return@mapNotNull null
                }

                val ownedBy = obj["owned_by"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val tags = classifyModelTags(cleanId, baseUrl).toMutableSet()
                val pricingPrompt = runCatching {
                    obj["pricing"]?.jsonObject?.get("prompt")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (pricingPrompt == "0" || pricingPrompt == "0.0") {
                    tags.add(ModelCapabilityTag.FREE)
                }

                DiscoveredLlmModel(id = cleanId, ownedBy = ownedBy, tags = tags)
            }.distinctBy { it.id }

            return parsed.sortedWith(
                compareByDescending<DiscoveredLlmModel> { ModelCapabilityTag.FREE in it.tags }
                    .thenByDescending { ModelCapabilityTag.CODER in it.tags }
                    .thenByDescending { ModelCapabilityTag.FAST in it.tags }
                    .thenBy { it.id.lowercase() }
            )
        }

        fun parseOllamaTagsResponse(rawJson: String): List<DiscoveredLlmModel> {
            val root = runCatching { parserJson.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val models = root["models"]?.jsonArray ?: return emptyList()
            return models.mapNotNull { el ->
                val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val family = runCatching {
                    obj["details"]?.jsonObject?.get("family")?.jsonPrimitive?.contentOrNull
                }.getOrNull().orEmpty()
                DiscoveredLlmModel(
                    id = name,
                    ownedBy = family.ifBlank { "ollama" },
                    tags = classifyModelTags(name, "ollama")
                )
            }.distinctBy { it.id }
                .sortedWith(
                    compareByDescending<DiscoveredLlmModel> { ModelCapabilityTag.CODER in it.tags }
                        .thenBy { it.id.lowercase() }
                )
        }
    }

    @Serializable
    private data class ChatMsg(val role: String, val content: String)

    @Serializable
    private data class OpenAiChatRequest(
        val model: String,
        val temperature: Double,
        val stream: Boolean,
        val messages: List<ChatMsg>
    )
}
