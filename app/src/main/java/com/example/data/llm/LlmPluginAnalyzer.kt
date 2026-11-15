package com.example.data.llm

import android.util.Log
import com.example.core.security.SecretCipher
import com.example.domain.model.CliPlugin
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.LlmProviderType
import com.example.domain.model.ParameterType
import com.example.domain.model.PluginAction
import com.example.domain.model.PluginParameter
import com.example.domain.model.PluginSource
import com.example.domain.model.RiskLevel
import com.example.domain.model.TargetOs
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "LlmPluginAnalyzer"

class LlmPluginAnalyzer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
        config: LlmBackendConfig
    ): CliPlugin {
        val timeoutMs = (config.timeoutSeconds.coerceIn(15, 180)) * 1000L
        val prompt = buildPrompt(commandName, rawDocs, remoteOsHint)
        return when (config.providerType) {
            LlmProviderType.FIREBASE_GEMINI -> analyzeWithFirebaseGemini(commandName, prompt, config.geminiModel, timeoutMs)
            LlmProviderType.OLLAMA_LOCAL -> analyzeWithOllama(commandName, prompt, config, timeoutMs)
            LlmProviderType.OPENAI_COMPATIBLE -> analyzeWithOpenAiCompatible(commandName, prompt, config, timeoutMs)
        }
    }

    suspend fun testEndpoint(config: LlmBackendConfig): String = withContext(Dispatchers.IO) {
        when (config.providerType) {
            LlmProviderType.FIREBASE_GEMINI -> {
                val model by lazy { Firebase.ai.generativeModel(config.geminiModel) }
                val response = withTimeout(30_000L) {
                    model.generateContent("Reply with exactly: OK")
                }
                "Firebase AI Logic (${config.geminiModel}) ready: ${response.text?.trim() ?: "OK"}"
            }
            LlmProviderType.OLLAMA_LOCAL -> {
                val baseUrl = config.customEndpointUrl.trimEnd('/')
                require(baseUrl.startsWith("http")) { "Ollama endpoint must start with http:// or https://" }
                val client = buildHttpClient(20)
                val req = Request.Builder().url("$baseUrl/api/tags").get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
                    val body = resp.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val count = root["models"]?.jsonArray?.size ?: 0
                    "Ollama reachable ($count local models found)"
                }
            }
            LlmProviderType.OPENAI_COMPATIBLE -> {
                val baseUrl = config.customEndpointUrl.trimEnd('/')
                require(baseUrl.startsWith("http")) { "Endpoint URL must start with http:// or https://" }
                val client = buildHttpClient(20)
                val apiKey = SecretCipher.decrypt(config.encryptedApiKey)
                val reqBuilder = Request.Builder().url("$baseUrl/v1/models").get()
                if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
                    "OpenAI-compatible endpoint reachable (HTTP ${resp.code})"
                }
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
            throw IllegalStateException("LLM analysis timed out after ${timeoutMs / 1000}s. Try a smaller doc snippet or faster model.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Firebase AI Logic generation failed.", e)
        }
    }

    private suspend fun analyzeWithOllama(
        commandName: String,
        prompt: String,
        config: LlmBackendConfig,
        timeoutMs: Long
    ): CliPlugin = withContext(Dispatchers.IO) {
        val baseUrl = config.customEndpointUrl.trimEnd('/')
        require(baseUrl.startsWith("http")) { "Configure a valid Ollama HTTP endpoint in Settings." }
        val client = buildHttpClient((timeoutMs / 1000).toInt())
        val payload = OllamaChatRequest(
            model = config.customModelName.ifBlank { "qwen2.5-coder:7b" },
            stream = false,
            format = "json",
            messages = listOf(
                ChatMsg("system", SYSTEM_JSON_INSTRUCTIONS),
                ChatMsg("user", prompt)
            )
        )
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/chat").post(body).build()
        val respText = withTimeout(timeoutMs) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Ollama error HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                resp.body?.string() ?: throw IllegalStateException("Empty Ollama response")
            }
        }
        val root = json.parseToJsonElement(respText).jsonObject
        val content = root["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Malformed Ollama response JSON")
        parseGeneratedPluginJson(commandName, extractJsonObject(content))
    }

    private suspend fun analyzeWithOpenAiCompatible(
        commandName: String,
        prompt: String,
        config: LlmBackendConfig,
        timeoutMs: Long
    ): CliPlugin = withContext(Dispatchers.IO) {
        val baseUrl = config.customEndpointUrl.trimEnd('/')
        require(baseUrl.startsWith("http")) { "Configure a valid OpenAI-compatible HTTP endpoint in Settings." }
        val client = buildHttpClient((timeoutMs / 1000).toInt())
        val apiKey = SecretCipher.decrypt(config.encryptedApiKey)
        val payload = OpenAiChatRequest(
            model = config.customModelName.ifBlank { "gpt-4o-mini" },
            temperature = 0.1,
            messages = listOf(
                ChatMsg("system", SYSTEM_JSON_INSTRUCTIONS),
                ChatMsg("user", prompt)
            )
        )
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder().url("$baseUrl/v1/chat/completions").post(body)
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }
        val respText = withTimeout(timeoutMs) {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Provider error HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                resp.body?.string() ?: throw IllegalStateException("Empty provider response")
            }
        }
        val root = json.parseToJsonElement(respText).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Malformed OpenAI-compatible response JSON")
        parseGeneratedPluginJson(commandName, extractJsonObject(content))
    }

    private fun parseGeneratedPluginJson(commandName: String, rawJson: String): CliPlugin {
        val dto = json.decodeFromString<GeneratedPluginDto>(rawJson)
        val cleanCmd = dto.commandName.ifBlank { commandName }.trim()
        val actions = dto.actions.mapIndexed { index, actionDto ->
            PluginAction(
                id = actionDto.id.ifBlank { "action_$index" },
                label = actionDto.label.ifBlank { "Run" },
                description = actionDto.description,
                subcommand = actionDto.subcommand,
                riskLevel = runCatching { RiskLevel.valueOf(actionDto.riskLevel.uppercase()) }.getOrDefault(RiskLevel.MODERATE),
                requiresSudo = actionDto.requiresSudo,
                parameters = actionDto.parameters.mapIndexed { pIdx, p ->
                    PluginParameter(
                        id = p.id.ifBlank { "param_$pIdx" },
                        label = p.label.ifBlank { p.id },
                        description = p.description,
                        type = runCatching { ParameterType.valueOf(p.type.uppercase()) }.getOrDefault(ParameterType.TEXT),
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

        return CliPlugin(
            schemaVersion = "shellcraft.plugin.v1",
            id = "ai-${cleanCmd.lowercase().replace(Regex("[^a-z0-9_-]"), "-")}-${System.currentTimeMillis()}",
            commandName = cleanCmd,
            displayName = dto.displayName.ifBlank { cleanCmd },
            description = dto.description.ifBlank { "AI-generated GUI plugin for $cleanCmd" },
            category = dto.category.ifBlank { "System" },
            targetOs = runCatching { TargetOs.valueOf(dto.targetOs.uppercase()) }.getOrDefault(TargetOs.POSIX),
            version = "1.0.0",
            author = "ShellCraft AI",
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
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    private fun buildPrompt(commandName: String, rawDocs: String, remoteOsHint: String): String = """
Analyze the following CLI documentation (man page / --help / usage output) for the command `$commandName` on target environment `$remoteOsHint`.
Generate a structured, ergonomic GUI plugin specification (`shellcraft.plugin.v1`) with 2 to 6 practical actions ordered from safest/most common read-only inspection actions to mutating/administrative actions.

Rules:
- Map boolean flags (e.g. `-a`, `--verbose`, `--dry-run`, `--json`) to `BOOLEAN_FLAG` with `"true"` or `"false"` defaultValue.
- Map enumerated options to `SELECT_ENUM` with explicit `options` list.
- Map numeric counts/lines/timeouts to `NUMBER`, ports to `PORT`, file/directory paths to `PATH`.
- Set `riskLevel` accurately: `SAFE` for read-only inspection/status/list; `MODERATE` for reversible writes/restarts/sync; `HIGH` for destructive delete/prune/stop/kill/firewall changes; `CRITICAL` for disk format/poweroff/kernel/system-wide wipe.
- Set `requiresSudo = true` ONLY when the command genuinely needs root privileges on POSIX systems.

CLI Documentation:
${rawDocs.take(12_000)}
""".trimIndent()

    companion object {
        private const val SYSTEM_JSON_INSTRUCTIONS =
            "You are a POSIX CLI-to-GUI compiler. Return ONLY a valid JSON object matching {commandName, displayName, description, category, targetOs, actions:[{id,label,description,subcommand,riskLevel,requiresSudo,parameters:[{id,label,description,type,flag,positionalIndex,required,defaultValue,placeholder,options}]}]}."
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
