package com.snehashis.data.llm

import android.util.Log
import com.snehashis.core.security.SecretCipher
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.CliDocCondenser
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.DiscoveredLlmModel
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelCapabilityTag
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginParameter
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.domain.model.TargetOs
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val pluginResponseSchema = Schema.obj(
        properties = mapOf(
            "commandName" to Schema.string("Base executable CLI name, e.g. docker, ufw, rsync"),
            "displayName" to Schema.string("Human-readable application title"),
            "description" to Schema.string("Concise 1-sentence summary of what the CLI tool does"),
            "category" to Schema.string("Category such as System, Network, Containers, Files, Git, Security, Diagnostics"),
            "targetOs" to Schema.enumeration(listOf("POSIX", "LINUX", "MACOS", "WINDOWS_POSIX")),
            "actions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string("Snake_case action identifier"),
                        "label" to Schema.string("Short button/tab label"),
                        "description" to Schema.string("What this action does"),
                        "subcommand" to Schema.string("Subcommand appended after base command, or empty string"),
                        "riskLevel" to Schema.enumeration(listOf("SAFE", "MODERATE", "HIGH", "CRITICAL")),
                        "requiresSudo" to Schema.boolean("True if root/superuser privileges are typically required"),
                        "parameters" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "id" to Schema.string("Snake_case parameter id"),
                                    "label" to Schema.string("UI field label"),
                                    "description" to Schema.string("Tooltip/helper text"),
                                    "type" to Schema.enumeration(
                                        listOf("TEXT", "NUMBER", "BOOLEAN_FLAG", "SELECT_ENUM", "PATH", "PORT", "KEY_VALUE")
                                    ),
                                    "flag" to Schema.string("CLI flag like -n or --force, or null if positional", nullable = true),
                                    "positionalIndex" to Schema.integer("0-based positional order if flag is null, or null", nullable = true),
                                    "required" to Schema.boolean("True if required"),
                                    "defaultValue" to Schema.string("Sensible default value or empty string"),
                                    "placeholder" to Schema.string("Input placeholder hint"),
                                    "options" to Schema.array(Schema.string(), "Allowed enum choices for SELECT_ENUM")
                                ),
                                optionalProperties = listOf("flag", "positionalIndex", "options")
                            )
                        )
                    )
                )
            )
        )
    )

    suspend fun analyzeCliDocumentation(
        commandName: String,
        rawDocs: String,
        remoteOsHint: String,
        config: LlmBackendConfig,
        onProgress: ((String) -> Unit)? = null
    ): CliPlugin {
        val timeoutSec = config.timeoutSeconds.coerceIn(30, 600)
        val timeoutMs = timeoutSec * 1000L
        val prompt = buildPrompt(commandName, rawDocs, remoteOsHint)
        return when (config.providerType) {
            LlmProviderType.FIREBASE_GEMINI -> analyzeWithFirebaseGemini(
                commandName, prompt, config.geminiModel, timeoutMs
            )
            LlmProviderType.OLLAMA_LOCAL -> analyzeWithOllamaStream(
                commandName, prompt, config, timeoutSec, timeoutMs, onProgress
            )
            LlmProviderType.OPENAI_COMPATIBLE -> analyzeWithOpenAiCompatibleStream(
                commandName, prompt, config, timeoutSec, timeoutMs, onProgress
            )
            LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> analyzeWithOnDeviceNano(
                commandName, rawDocs, remoteOsHint, config, timeoutMs, onProgress
            )
        }
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
            LlmProviderType.OLLAMA_LOCAL -> {
                val baseUrl = config.customEndpointUrl.trim().trimEnd('/')
                require(baseUrl.startsWith("http")) { "Ollama endpoint must start with http:// or https://" }
                val client = buildHttpClient(20)
                val req = Request.Builder().url("$baseUrl/api/tags").get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
                    val body = resp.body?.string().orEmpty()
                    val discovered = parseOllamaTagsResponse(body)
                    EndpointTestResult(
                        statusMessage = "Ollama reachable (${discovered.size} local models found)",
                        discoveredModels = discovered
                    )
                }
            }
            LlmProviderType.OPENAI_COMPATIBLE -> {
                val baseUrl = config.customEndpointUrl.trim().trimEnd('/')
                require(baseUrl.startsWith("http")) { "Endpoint URL must start with http:// or https://" }
                val modelsUrl = if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/openai")) {
                    "$baseUrl/models"
                } else {
                    "$baseUrl/v1/models"
                }
                val client = buildHttpClient(20)
                val apiKey = SecretCipher.decrypt(config.encryptedApiKey)
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
                            401, 403 -> "Invalid or missing API key for ${config.byokPreset.displayName}."
                            429 -> "Rate limit reached (HTTP 429). Retry shortly."
                            else -> resp.message
                        }
                        throw IllegalStateException("HTTP ${resp.code}: $hint")
                    }
                    val discovered = parseOpenAiModelsResponse(body, baseUrl)
                    val presetName = if (config.byokPreset == ByokProviderPreset.CUSTOM) {
                        "Endpoint"
                    } else {
                        config.byokPreset.displayName
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

    private suspend fun analyzeWithFirebaseGemini(
        commandName: String,
        prompt: String,
        modelName: String,
        timeoutMs: Long
    ): CliPlugin {
        val geminiConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = pluginResponseSchema
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
            return parseGeneratedPluginJson(commandName, rawJson)
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
                " (Firebase App Check rejected the phone's unregistered debug token. Use the AI Studio emulator, register your debug token in Firebase Console, or switch to an OpenAI-compatible/Ollama endpoint in Settings.)"
            } else {
                ""
            }
            throw IllegalStateException("$detail$hint", e)
        }
    }

    private suspend fun analyzeWithOllamaStream(
        commandName: String,
        prompt: String,
        config: LlmBackendConfig,
        timeoutSec: Int,
        timeoutMs: Long,
        onProgress: ((String) -> Unit)?
    ): CliPlugin = withContext(Dispatchers.IO) {
        val baseUrl = config.customEndpointUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("http")) { "Configure a valid Ollama HTTP endpoint in Settings." }
        val client = buildHttpClient(timeoutSec)
        val payload = OllamaChatRequest(
            model = config.customModelName.ifBlank { "qwen2.5-coder:7b" },
            stream = true,
            format = "json",
            messages = listOf(
                ChatMsg("system", SYSTEM_JSON_INSTRUCTIONS),
                ChatMsg("user", prompt)
            )
        )
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/chat").post(body).build()

        val contentBuilder = StringBuilder()
        var reasoningChars = 0

        try {
            withTimeout(timeoutMs) {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Ollama error HTTP ${resp.code}: ${resp.body?.string()?.take(240)}")
                    }
                    val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty Ollama response stream")
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            val chunk = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                            val msg = chunk["message"]?.jsonObject
                            val token = runCatching { msg?.get("content")?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                            val thinking = runCatching { msg?.get("thinking")?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                            if (thinking.isNotEmpty()) {
                                reasoningChars += thinking.length
                                onProgress?.invoke("Reasoning locally ($reasoningChars chars)...")
                            }
                            if (token.isNotEmpty()) {
                                contentBuilder.append(token)
                                onProgress?.invoke("Generating plugin JSON (${contentBuilder.length} chars)...")
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Local Ollama generation timed out after ${timeoutSec}s. You can raise the timeout in Settings.")
        }

        val finalContent = contentBuilder.toString()
        if (finalContent.isBlank()) {
            throw IllegalStateException("Ollama finished without emitting JSON content.")
        }
        parseGeneratedPluginJson(commandName, extractJsonObject(finalContent))
    }

    private suspend fun analyzeWithOpenAiCompatibleStream(
        commandName: String,
        prompt: String,
        config: LlmBackendConfig,
        timeoutSec: Int,
        timeoutMs: Long,
        onProgress: ((String) -> Unit)?
    ): CliPlugin = withContext(Dispatchers.IO) {
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
            temperature = 0.1,
            stream = true,
            messages = listOf(
                ChatMsg("system", SYSTEM_JSON_INSTRUCTIONS),
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
                                onProgress?.invoke("Streaming JSON (${contentBuilder.length} chars)...")
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Local/Custom LLM timed out after ${timeoutSec}s (reasoning reached $reasoningChars chars). Increase Timeout in Settings or pick a faster model.")
        }

        val finalContent = contentBuilder.toString()
        if (finalContent.isBlank()) {
            throw IllegalStateException("LLM finished reasoning ($reasoningChars chars) without emitting JSON content.")
        }
        parseGeneratedPluginJson(commandName, extractJsonObject(finalContent))
    }

    private suspend fun analyzeWithOnDeviceNano(
        commandName: String,
        rawDocs: String,
        remoteOsHint: String,
        config: LlmBackendConfig,
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

        val nanoPrompt = buildOnDeviceNanoPrompt(commandName, effectiveDocs, remoteOsHint)
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
            parseGeneratedPluginJson(
                commandName = commandName,
                rawJson = repairedJson,
                authorOverride = "Gemini Nano (On-Device Exp.)"
            )
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
                    onProgress = onProgress
                )
            }
            throw IllegalStateException(
                t.localizedMessage ?: "On-device Gemini Nano plugin synthesis failed.",
                t
            )
        }
    }

    private fun parseGeneratedPluginJson(
        commandName: String,
        rawJson: String,
        authorOverride: String = "ShellCraft AI"
    ): CliPlugin {
        val dto = json.decodeFromString<GeneratedPluginDto>(rawJson)
        val cleanCmd = dto.commandName.ifBlank { commandName }.trim()
        val actions = dto.actions.mapIndexed { index, actionDto ->
            val rawSub = actionDto.subcommand.trim()
            val cleanedSub = when {
                rawSub.equals(cleanCmd, ignoreCase = true) -> ""
                rawSub.startsWith("$cleanCmd ", ignoreCase = true) -> rawSub.drop(cleanCmd.length).trim()
                else -> rawSub
            }
            PluginAction(
                id = actionDto.id.ifBlank { "action_$index" },
                label = actionDto.label.ifBlank { "Run" },
                description = actionDto.description,
                subcommand = cleanedSub,
                riskLevel = runCatching { RiskLevel.valueOf(actionDto.riskLevel.uppercase()) }.getOrDefault(RiskLevel.MODERATE),
                requiresSudo = actionDto.requiresSudo,
                parameters = actionDto.parameters.mapIndexed { pIdx, p ->
                    val mappedType = when (p.type.uppercase()) {
                        "STRING", "HOST", "TEXT" -> ParameterType.TEXT
                        "INT", "INTEGER", "FLOAT", "NUMBER" -> ParameterType.NUMBER
                        "BOOL", "BOOLEAN", "FLAG", "BOOLEAN_FLAG" -> ParameterType.BOOLEAN_FLAG
                        "ENUM", "SELECT", "SELECT_ENUM" -> ParameterType.SELECT_ENUM
                        "PATH", "FILE", "DIR" -> ParameterType.PATH
                        "PORT" -> ParameterType.PORT
                        "KEY_VALUE", "KV" -> ParameterType.KEY_VALUE
                        else -> ParameterType.TEXT
                    }
                    PluginParameter(
                        id = p.id.ifBlank { "param_$pIdx" },
                        label = p.label.ifBlank { p.id },
                        description = p.description,
                        type = mappedType,
                        flag = p.flag?.takeIf { it.isNotBlank() },
                        positionalIndex = p.positionalIndex,
                        required = p.required,
                        defaultValue = p.defaultValue,
                        placeholder = p.placeholder,
                        options = p.options
                    )
                }
            )
        }.ifEmpty {
            listOf(
                PluginAction(
                    id = "run",
                    label = "Execute",
                    description = "Run $cleanCmd with custom arguments",
                    riskLevel = RiskLevel.MODERATE,
                    parameters = listOf(
                        PluginParameter(
                            id = "args",
                            label = "Arguments",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            placeholder = "--help"
                        )
                    )
                )
            )
        }

        val mappedOs = when (dto.targetOs.uppercase()) {
            "DARWIN", "MAC", "MACOS" -> TargetOs.MACOS
            "LINUX" -> TargetOs.LINUX
            "WINDOWS", "WINDOWS_POSIX" -> TargetOs.WINDOWS_POSIX
            else -> TargetOs.POSIX
        }

        return CliPlugin(
            schemaVersion = "shellcraft.plugin.v1",
            id = "ai-${cleanCmd.lowercase().replace(Regex("[^a-z0-9_-]"), "-")}-${System.currentTimeMillis()}",
            commandName = cleanCmd,
            displayName = dto.displayName.ifBlank { cleanCmd },
            description = dto.description.ifBlank { "AI-generated GUI plugin for $cleanCmd" },
            category = dto.category.ifBlank { "System" },
            targetOs = mappedOs,
            version = "1.0.0",
            author = authorOverride,
            source = PluginSource.AI_GENERATED,
            updatedAtEpochMs = System.currentTimeMillis(),
            actions = actions
        )
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

    private fun buildPrompt(commandName: String, rawDocs: String, remoteOsHint: String): String = """
Analyze the following CLI documentation (man page / --help / usage output) for the command `$commandName` on target environment `$remoteOsHint`.
Generate a structured, ergonomic GUI plugin specification (`shellcraft.plugin.v1`) with 2 to 5 practical actions ordered from safest/most common read-only inspection actions to mutating/administrative actions.

Rules:
- `subcommand` must ONLY contain subcommands (or `""` if the command takes flags directly). Never repeat `$commandName` inside `subcommand`.
- Parameter `type` MUST be strictly one of: `TEXT`, `NUMBER`, `BOOLEAN_FLAG`, `SELECT_ENUM`, `PATH`, `PORT`, `KEY_VALUE`.
- Map boolean flags (e.g. `-a`, `--verbose`, `--dry-run`, `--json`) to `BOOLEAN_FLAG` with `"true"` or `"false"` defaultValue.
- Map enumerated options to `SELECT_ENUM` with explicit `options` list.
- Map numeric counts/lines/timeouts to `NUMBER`, ports to `PORT`, file/directory paths to `PATH`.
- Set `riskLevel` accurately: `SAFE`, `MODERATE`, `HIGH`, or `CRITICAL`.
- Set `requiresSudo = true` ONLY when root privileges are genuinely required.
- Keep your thinking concise and output valid JSON immediately.

CLI Documentation:
${rawDocs.take(12_000)}
""".trimIndent()

    private fun buildOnDeviceNanoPrompt(commandName: String, condensedDocs: String, remoteOsHint: String): String = """
## Task
Generate a `shellcraft.plugin.v1` JSON object for CLI command `$commandName` on `$remoteOsHint`.

## Rules
- Output ONLY valid JSON. No markdown, no intro, no prose.
- Provide 2 to 3 most useful actions ordered safest to administrative.
- `subcommand`: subcommand word(s) only or "" (never repeat `$commandName`).
- Parameter `type`: TEXT, NUMBER, BOOLEAN_FLAG, SELECT_ENUM, PATH, PORT, or KEY_VALUE.
- `riskLevel`: SAFE, MODERATE, HIGH, or CRITICAL.

## Example Output
{"commandName":"ping","displayName":"Ping","description":"Test host reachability","category":"Network","targetOs":"POSIX","actions":[{"id":"ping_host","label":"Ping Host","description":"Send ICMP echo packets","subcommand":"","riskLevel":"SAFE","requiresSudo":false,"parameters":[{"id":"count","label":"Count","type":"NUMBER","flag":"-c","defaultValue":"4"},{"id":"host","label":"Host","type":"TEXT","positionalIndex":0,"required":true,"placeholder":"8.8.8.8"}]}]}

## CLI Documentation
<cli_docs>
$condensedDocs
</cli_docs>
""".trimIndent()

    companion object {
        private const val SYSTEM_JSON_INSTRUCTIONS =
            "You are a POSIX CLI-to-GUI compiler. Return ONLY a valid JSON object matching {commandName, displayName, description, category, targetOs, actions:[{id,label,description,subcommand,riskLevel,requiresSudo,parameters:[{id,label,description,type,flag,positionalIndex,required,defaultValue,placeholder,options}]}]}."

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

                // Skip explicitly inactive entries (e.g. Groq deactivated weights)
                val activeFlag = obj["active"]?.jsonPrimitive?.contentOrNull
                if (activeFlag == "false") return@mapNotNull null

                val cleanId = if (isGoogleAiStudio && rawId.startsWith("models/")) {
                    rawId.removePrefix("models/")
                } else {
                    rawId
                }

                // Filter out pure embedding/rerank/audio/image generation models from text completion list
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
    private data class OllamaChatRequest(
        val model: String,
        val stream: Boolean,
        val format: String,
        val messages: List<ChatMsg>
    )

    @Serializable
    private data class OpenAiChatRequest(
        val model: String,
        val temperature: Double,
        val stream: Boolean,
        val messages: List<ChatMsg>
    )

    @Serializable
    private data class GeneratedPluginDto(
        val commandName: String = "",
        val displayName: String = "",
        val description: String = "",
        val category: String = "System",
        val targetOs: String = "POSIX",
        val actions: List<GeneratedActionDto> = emptyList()
    )

    @Serializable
    private data class GeneratedActionDto(
        val id: String = "",
        val label: String = "",
        val description: String = "",
        val subcommand: String = "",
        val riskLevel: String = "MODERATE",
        val requiresSudo: Boolean = false,
        val parameters: List<GeneratedParamDto> = emptyList()
    )

    @Serializable
    private data class GeneratedParamDto(
        val id: String = "",
        val label: String = "",
        val description: String = "",
        val type: String = "TEXT",
        val flag: String? = null,
        val positionalIndex: Int? = null,
        val required: Boolean = false,
        val defaultValue: String = "",
        val placeholder: String = "",
        val options: List<String> = emptyList()
    )
}
