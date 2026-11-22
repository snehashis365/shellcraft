package com.snehashis.data.llm

import com.google.firebase.ai.type.Schema
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.OutputRowAction
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.ParameterWidget
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginChatTurn
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PluginParameter
import com.snehashis.domain.model.PluginPreset
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.domain.model.TargetOs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object LlmPluginPromptBuilder {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val pluginResponseSchema = Schema.obj(
        properties = mapOf(
            "assistantSummary" to Schema.string("Short 1-sentence summary of the generated or refined GUI"),
            "commandName" to Schema.string("Base executable CLI name, e.g. docker, ufw, rsync"),
            "displayName" to Schema.string("Human-readable application title"),
            "description" to Schema.string("Concise 1-sentence summary of what the CLI tool does"),
            "category" to Schema.string("Category such as System, Network, Containers, Files, Git, Security, Diagnostics"),
            "targetOs" to Schema.enumeration(listOf("POSIX", "LINUX", "MACOS", "WINDOWS_POSIX")),
            "uiMode" to Schema.enumeration(listOf("MODULAR_DASHBOARD", "HTML_CANVAS_CUSTOM")),
            "presets" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string("Snake_case preset id"),
                        "label" to Schema.string("Short preset chip title"),
                        "description" to Schema.string("What this preset configures"),
                        "actionId" to Schema.string("Target action id"),
                        "iconName" to Schema.string("bolt, warning, shield, speed, layers, or lock"),
                        "valuePairs" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "key" to Schema.string("Parameter id"),
                                    "value" to Schema.string("Preset value string")
                                )
                            )
                        )
                    )
                )
            ),
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
                                    "widget" to Schema.enumeration(
                                        listOf("AUTO", "SLIDER", "SEGMENTED_CHOICE", "TOGGLE_CARD", "FILE_PATH", "STEPPER")
                                    ),
                                    "group" to Schema.enumeration(listOf("Primary", "Advanced")),
                                    "flag" to Schema.string("CLI flag like -n or --force, or null if positional", nullable = true),
                                    "positionalIndex" to Schema.integer("0-based positional order if flag is null, or null", nullable = true),
                                    "required" to Schema.boolean("True if required"),
                                    "defaultValue" to Schema.string("Sensible default value or empty string"),
                                    "placeholder" to Schema.string("Input placeholder hint"),
                                    "options" to Schema.array(Schema.string(), "Allowed enum choices for SELECT_ENUM"),
                                    "minValue" to Schema.float("Slider minimum when widget is SLIDER", nullable = true),
                                    "maxValue" to Schema.float("Slider maximum when widget is SLIDER", nullable = true),
                                    "stepValue" to Schema.float("Slider increment step", nullable = true),
                                    "unitLabel" to Schema.string("Short unit suffix like lines, ms, or %")
                                ),
                                optionalProperties = listOf(
                                    "flag", "positionalIndex", "options",
                                    "widget", "group", "minValue", "maxValue", "stepValue", "unitLabel"
                                )
                            )
                        ),
                        "rowActions" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "id" to Schema.string("Row action id"),
                                    "label" to Schema.string("Short button label like Logs or Restart"),
                                    "targetActionId" to Schema.string("Action id in this plugin to invoke"),
                                    "iconName" to Schema.string("play, refresh, stop, description, or info"),
                                    "riskLevel" to Schema.enumeration(listOf("SAFE", "MODERATE", "HIGH", "CRITICAL")),
                                    "requiresSudo" to Schema.boolean("Whether elevation is required"),
                                    "mappingPairs" to Schema.array(
                                        items = Schema.obj(
                                            properties = mapOf(
                                                "key" to Schema.string("Target parameter id"),
                                                "value" to Schema.string("Template value like {col:0} or {unit}")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    optionalProperties = listOf("rowActions")
                )
            ),
            "customHtmlTemplate" to Schema.string(
                "Self-contained responsive HTML5/CSS/JS control surface calling window.ShellCraftBridge when uiMode is HTML_CANVAS_CUSTOM, or null",
                nullable = true
            )
        ),
        optionalProperties = listOf("assistantSummary", "presets", "customHtmlTemplate")
    )

    fun buildGeneratePrompt(
        commandName: String,
        rawDocs: String,
        remoteOsHint: String,
        guiStyle: PluginGuiStyle,
        customInstructions: String
    ): String {
        val styleGuidance = when (guiStyle) {
            PluginGuiStyle.MODULAR_DASHBOARD -> """
Preferred GUI Style: MODULAR_DASHBOARD (`"uiMode": "MODULAR_DASHBOARD"`, `"customHtmlTemplate": null`).
- Design a desktop-grade control panel, NOT a plain form!
- Add 2–3 useful `presets` (1-tap workflow configurations) with `valuePairs`.
- Use `"widget": "SLIDER"` with `minValue`, `maxValue`, `stepValue`, `unitLabel` for bounded counts/depths/timeouts.
- Use `"widget": "SEGMENTED_CHOICE"` for 2–6 enum options, and `"widget": "FILE_PATH"` for files/directories (`type: "PATH"`).
- Put everyday controls in `"group": "Primary"` and secondary flags in `"group": "Advanced"`.
- For actions producing lists/tables (status, ls, ps, jobs, connections), attach `rowActions` using `{col:0}` or `{unit}` so output cards show inline action buttons like `Inspect`, `Logs`, `Restart`, or `Kill`.
""".trimIndent()
            PluginGuiStyle.HTML_CANVAS_CUSTOM -> """
Preferred GUI Style: HTML_CANVAS_CUSTOM (`"uiMode": "HTML_CANVAS_CUSTOM"`).
- Still populate complete structured `actions`, `parameters`, `presets`, and `rowActions` so command assembly and SafetyGuard work deterministically.
- ALSO populate `"customHtmlTemplate"` with a clean, self-contained HTML5 document (CSS + inline SVG/Canvas + vanilla JS) styled with CSS variables `var(--sc-bg)`, `var(--sc-surface)`, `var(--sc-surface-elevated)`, `var(--sc-primary)`, `var(--sc-secondary)`, `var(--sc-accent)`, `var(--sc-text)`, `var(--sc-muted)`, `var(--sc-border)`.
- Inside `"customHtmlTemplate"`, NEVER construct shell strings directly; invoke `window.ShellCraftBridge.runAction(actionId, JSON.stringify(valuesObj), elevateSudoBool)` for actions, `window.ShellCraftBridge.pickRemoteFile(paramId)` / `window.ShellCraftBridge.pickLocalFile(paramId)` for paths, and implement `window.onShellCraftOutput = function(jsonStr){...}` & `window.onShellCraftFilePicked = function(paramId, path){...}`.
""".trimIndent()
        }

        val userNote = if (customInstructions.isNotBlank()) {
            "\nUser Custom Instructions (MUST honor):\n${customInstructions.trim()}\n"
        } else {
            ""
        }

        return """
Analyze the CLI documentation for `$commandName` targeting `$remoteOsHint` and generate a rich `shellcraft.plugin.v2` GUI specification with 2 to 5 practical actions ordered from safe read-only inspection to mutating operations.

$styleGuidance$userNote
Strict Rules:
- `subcommand`: subcommand tokens only (or `""`). Never repeat `$commandName` inside `subcommand`.
- Parameter `type`: strictly `TEXT`, `NUMBER`, `BOOLEAN_FLAG`, `SELECT_ENUM`, `PATH`, `PORT`, or `KEY_VALUE`.
- Map file/folder/directory operands to `type: "PATH"` + `widget: "FILE_PATH"` so users get dual Remote SFTP Browse + Phone Upload pickers.
- Set `riskLevel` accurately (`SAFE`, `MODERATE`, `HIGH`, `CRITICAL`) and `requiresSudo` only when needed.

CLI Documentation:
${rawDocs.take(12_000)}
""".trimIndent()
    }

    fun buildRefinePrompt(
        currentPlugin: CliPlugin,
        history: List<PluginChatTurn>,
        userInstruction: String,
        rawDocs: String
    ): String {
        val currentJson = json.encodeToString(currentPlugin)
        val historyBlock = history.takeLast(6).joinToString("\n") { turn ->
            "${turn.role.name}: ${turn.message}"
        }
        val docsSnippet = if (rawDocs.isNotBlank()) "\nReference CLI Docs Excerpt:\n${rawDocs.take(4500)}\n" else ""

        return """
You are refining an existing ShellCraft GUI plugin JSON specification according to the user's request.
Preserve the command semantics, safety accuracy, and untouched actions unless the user asks to change, add, or remove them.
Return the COMPLETE updated `shellcraft.plugin.v2` JSON object including a brief 1-sentence `assistantSummary` describing what changed.

Current Plugin JSON:
$currentJson
${if (historyBlock.isNotBlank()) "\nRecent Conversation:\n$historyBlock\n" else ""}$docsSnippet
Latest User Request:
${userInstruction.trim()}
""".trimIndent()
    }

    fun parseGeneratedPluginJson(
        commandName: String,
        rawJson: String,
        authorOverride: String = "ShellCraft AI",
        fallbackGuiStyle: PluginGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD,
        preserveExisting: CliPlugin? = null
    ): Pair<CliPlugin, String> {
        val dto = json.decodeFromString<GeneratedPluginDto>(rawJson)
        val cleanCmd = dto.commandName.ifBlank { preserveExisting?.commandName ?: commandName }.trim()
        val parsedUiMode = runCatching {
            dto.uiMode?.let { PluginGuiStyle.valueOf(it.uppercase()) }
        }.getOrNull() ?: preserveExisting?.uiMode ?: fallbackGuiStyle

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
                    val mappedWidget = runCatching {
                        p.widget?.uppercase()?.let { ParameterWidget.valueOf(it) }
                    }.getOrNull() ?: when {
                        mappedType == ParameterType.PATH -> ParameterWidget.FILE_PATH
                        p.minValue != null && p.maxValue != null -> ParameterWidget.SLIDER
                        mappedType == ParameterType.SELECT_ENUM && p.options.size in 2..6 -> ParameterWidget.SEGMENTED_CHOICE
                        else -> ParameterWidget.AUTO
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
                        options = p.options,
                        widget = mappedWidget,
                        group = if (p.group.equals("Advanced", ignoreCase = true)) "Advanced" else "Primary",
                        minValue = p.minValue,
                        maxValue = p.maxValue,
                        stepValue = p.stepValue,
                        unitLabel = p.unitLabel
                    )
                },
                rowActions = actionDto.rowActions.mapIndexed { rIdx, ra ->
                    val combinedMappings = buildMap {
                        putAll(ra.paramMappings)
                        ra.mappingPairs.forEach { pair ->
                            if (pair.key.isNotBlank()) put(pair.key, pair.value)
                        }
                    }
                    OutputRowAction(
                        id = ra.id.ifBlank { "row_action_$rIdx" },
                        label = ra.label.ifBlank { "Run" },
                        targetActionId = ra.targetActionId,
                        paramMappings = combinedMappings,
                        iconName = ra.iconName.ifBlank { "play" },
                        riskLevel = runCatching { RiskLevel.valueOf(ra.riskLevel.uppercase()) }.getOrDefault(RiskLevel.SAFE),
                        requiresSudo = ra.requiresSudo
                    )
                }
            )
        }.ifEmpty {
            preserveExisting?.actions ?: listOf(
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

        val presets = dto.presets.mapIndexedNotNull { idx, presetDto ->
            if (presetDto.label.isBlank() || presetDto.actionId.isBlank()) return@mapIndexedNotNull null
            val mergedValues = buildMap {
                putAll(presetDto.values)
                presetDto.valuePairs.forEach { pair ->
                    if (pair.key.isNotBlank()) put(pair.key, pair.value)
                }
            }
            PluginPreset(
                id = presetDto.id.ifBlank { "preset_$idx" },
                label = presetDto.label,
                description = presetDto.description,
                actionId = presetDto.actionId,
                values = mergedValues,
                iconName = presetDto.iconName.ifBlank { "bolt" }
            )
        }

        val mappedOs = when (dto.targetOs.uppercase()) {
            "DARWIN", "MAC", "MACOS" -> TargetOs.MACOS
            "LINUX" -> TargetOs.LINUX
            "WINDOWS", "WINDOWS_POSIX" -> TargetOs.WINDOWS_POSIX
            else -> TargetOs.POSIX
        }

        val resolvedHtml = dto.customHtmlTemplate?.takeIf { it.isNotBlank() }
            ?: if (parsedUiMode == PluginGuiStyle.HTML_CANVAS_CUSTOM) {
                preserveExisting?.customHtmlTemplate ?: generateFallbackCustomHtml(
                    displayName = dto.displayName.ifBlank { cleanCmd },
                    commandName = cleanCmd,
                    actions = actions
                )
            } else {
                dto.customHtmlTemplate?.takeIf { it.isNotBlank() } ?: preserveExisting?.customHtmlTemplate
            }

        val plugin = CliPlugin(
            schemaVersion = "shellcraft.plugin.v2",
            id = preserveExisting?.id
                ?: "ai-${cleanCmd.lowercase().replace(Regex("[^a-z0-9_-]"), "-")}-${System.currentTimeMillis()}",
            commandName = cleanCmd,
            displayName = dto.displayName.ifBlank { preserveExisting?.displayName ?: cleanCmd },
            description = dto.description.ifBlank { preserveExisting?.description ?: "AI-generated GUI plugin for $cleanCmd" },
            category = dto.category.ifBlank { preserveExisting?.category ?: "System" },
            targetOs = mappedOs,
            version = if (preserveExisting != null) bumpPatchVersion(preserveExisting.version) else "2.0.0",
            author = preserveExisting?.author?.takeIf { it.isNotBlank() } ?: authorOverride,
            source = preserveExisting?.source ?: PluginSource.AI_GENERATED,
            updatedAtEpochMs = System.currentTimeMillis(),
            actions = actions,
            uiMode = parsedUiMode,
            presets = if (presets.isNotEmpty()) presets else preserveExisting?.presets.orEmpty(),
            customHtmlTemplate = resolvedHtml,
            customFromBuiltinId = preserveExisting?.customFromBuiltinId
        )

        val summary = dto.assistantSummary.ifBlank {
            "Generated ${plugin.displayName} (${plugin.uiMode.displayName}) with ${plugin.actions.size} actions and ${plugin.presets.size} presets."
        }
        return plugin to summary
    }

    private fun bumpPatchVersion(version: String): String {
        val parts = version.split(".")
        val last = parts.lastOrNull()?.toIntOrNull() ?: return "$version.1"
        return (parts.dropLast(1) + (last + 1).toString()).joinToString(".")
    }

    fun generateFallbackCustomHtml(
        displayName: String,
        commandName: String,
        actions: List<PluginAction>
    ): String {
        val firstAction = actions.firstOrNull()?.id.orEmpty()
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<style>
  body { margin:0; padding:14px; font-family: system-ui, sans-serif; background: var(--sc-bg, #070A12); color: var(--sc-text, #F3F7FF); }
  .card { background: var(--sc-surface, #0D1322); border: 1px solid var(--sc-border, #2B3C63); border-radius: 14px; padding: 14px; margin-bottom: 12px; }
  .btn { background: var(--sc-primary, #00E5FF); color: #001F28; border:none; border-radius:10px; padding:10px 14px; font-weight:700; cursor:pointer; }
  .out { font-family: monospace; font-size:12px; white-space: pre-wrap; background: rgba(0,0,0,0.3); padding:10px; border-radius:10px; }
</style>
</head>
<body>
  <div class="card">
    <h3 style="margin:0 0 6px 0">$displayName ($commandName)</h3>
    <p style="margin:0 0 12px 0; color: var(--sc-muted, #A3B4D4)">Custom HTML5 control surface ready.</p>
    <button class="btn" onclick="window.ShellCraftBridge && window.ShellCraftBridge.runAction('$firstAction', '{}', false)">Run ${actions.firstOrNull()?.label ?: "Action"}</button>
  </div>
  <div class="card">
    <strong>Live Telemetry</strong>
    <pre id="out" class="out">Awaiting execution...</pre>
  </div>
  <script>
    window.onShellCraftOutput = function(jsonStr) {
      try {
        var payload = JSON.parse(jsonStr);
        document.getElementById('out').textContent = payload.output || '(no output)';
      } catch(e) { document.getElementById('out').textContent = jsonStr; }
    };
  </script>
</body>
</html>
""".trimIndent()
    }

    const val SYSTEM_JSON_INSTRUCTIONS =
        "You are a POSIX CLI-to-GUI compiler emitting `shellcraft.plugin.v2` JSON objects with actions, parameters (widget, group, minValue, maxValue, stepValue, unitLabel), presets (actionId, values map), rowActions, uiMode (MODULAR_DASHBOARD or HTML_CANVAS_CUSTOM), optional customHtmlTemplate, and assistantSummary. Return ONLY valid JSON."

    @Serializable
    private data class KeyValueDto(
        val key: String = "",
        val value: String = ""
    )

    @Serializable
    private data class GeneratedPresetDto(
        val id: String = "",
        val label: String = "",
        val description: String = "",
        val actionId: String = "",
        val iconName: String = "bolt",
        val values: Map<String, String> = emptyMap(),
        val valuePairs: List<KeyValueDto> = emptyList()
    )

    @Serializable
    private data class GeneratedRowActionDto(
        val id: String = "",
        val label: String = "",
        val targetActionId: String = "",
        val iconName: String = "play",
        val riskLevel: String = "SAFE",
        val requiresSudo: Boolean = false,
        val paramMappings: Map<String, String> = emptyMap(),
        val mappingPairs: List<KeyValueDto> = emptyList()
    )

    @Serializable
    private data class GeneratedPluginDto(
        val assistantSummary: String = "",
        val commandName: String = "",
        val displayName: String = "",
        val description: String = "",
        val category: String = "System",
        val targetOs: String = "POSIX",
        val uiMode: String? = null,
        val customHtmlTemplate: String? = null,
        val presets: List<GeneratedPresetDto> = emptyList(),
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
        val parameters: List<GeneratedParamDto> = emptyList(),
        val rowActions: List<GeneratedRowActionDto> = emptyList()
    )

    @Serializable
    private data class GeneratedParamDto(
        val id: String = "",
        val label: String = "",
        val description: String = "",
        val type: String = "TEXT",
        val widget: String? = null,
        val group: String = "Primary",
        val flag: String? = null,
        val positionalIndex: Int? = null,
        val required: Boolean = false,
        val defaultValue: String = "",
        val placeholder: String = "",
        val options: List<String> = emptyList(),
        val minValue: Float? = null,
        val maxValue: Float? = null,
        val stepValue: Float? = null,
        val unitLabel: String = ""
    )
}
