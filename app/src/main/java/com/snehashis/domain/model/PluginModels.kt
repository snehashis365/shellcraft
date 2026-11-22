package com.snehashis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH,
    CRITICAL
}

@Serializable
enum class ParameterType {
    TEXT,
    NUMBER,
    BOOLEAN_FLAG,
    SELECT_ENUM,
    PATH,
    PORT,
    KEY_VALUE
}

@Serializable
enum class ParameterWidget {
    AUTO,
    SLIDER,
    SEGMENTED_CHOICE,
    TOGGLE_CARD,
    FILE_PATH,
    CHIP_MULTI_SELECT,
    STEPPER
}

@Serializable
enum class PluginSource {
    BUILT_IN,
    AI_GENERATED,
    GITHUB_SYNC,
    USER_IMPORTED
}

@Serializable
enum class TargetOs {
    POSIX,
    LINUX,
    MACOS,
    WINDOWS_POSIX
}

@Serializable
data class PluginParameter(
    val id: String,
    val label: String,
    val description: String = "",
    val type: ParameterType = ParameterType.TEXT,
    val flag: String? = null,
    val positionalIndex: Int? = null,
    val required: Boolean = false,
    val defaultValue: String = "",
    val placeholder: String = "",
    val options: List<String> = emptyList(),
    val validationRegex: String? = null,
    val widget: ParameterWidget = ParameterWidget.AUTO,
    val group: String = "Primary",
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val stepValue: Float? = null,
    val unitLabel: String = ""
)

@Serializable
data class OutputRowAction(
    val id: String,
    val label: String,
    val targetActionId: String,
    val paramMappings: Map<String, String> = emptyMap(),
    val iconName: String = "play",
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val requiresSudo: Boolean = false
)

@Serializable
data class PluginAction(
    val id: String,
    val label: String,
    val description: String = "",
    val subcommand: String = "",
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val requiresSudo: Boolean = false,
    val outputHint: String = "text",
    val parameters: List<PluginParameter> = emptyList(),
    val rowActions: List<OutputRowAction> = emptyList()
)

@Serializable
data class PluginPreset(
    val id: String,
    val label: String,
    val description: String = "",
    val actionId: String,
    val values: Map<String, String> = emptyMap(),
    val iconName: String = "bolt"
)

@Serializable
data class CliPlugin(
    val schemaVersion: String = "shellcraft.plugin.v1",
    val id: String,
    val commandName: String,
    val displayName: String,
    val description: String,
    val category: String = "System",
    val targetOs: TargetOs = TargetOs.POSIX,
    val version: String = "1.0.0",
    val author: String = "ShellCraft",
    val source: PluginSource = PluginSource.BUILT_IN,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val actions: List<PluginAction> = emptyList(),
    val uiMode: PluginGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD,
    val accentHex: String? = null,
    val presets: List<PluginPreset> = emptyList(),
    val customHtmlTemplate: String? = null,
    val customFromBuiltinId: String? = null
)

object PluginCommandBuilder {

    fun posixQuote(raw: String): String {
        if (raw.isEmpty()) return "''"
        val safeRegex = Regex("^[a-zA-Z0-9._/:=@+-]+$")
        if (safeRegex.matches(raw)) return raw
        return "'" + raw.replace("'", "'\"'\"'") + "'"
    }

    fun buildCommand(
        plugin: CliPlugin,
        action: PluginAction,
        values: Map<String, String>,
        elevateWithSudo: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (elevateWithSudo || action.requiresSudo) {
            parts.add("sudo")
        }
        parts.add(plugin.commandName.trim())

        if (action.subcommand.isNotBlank()) {
            action.subcommand.trim().split(Regex("\\s+")).forEach { token ->
                if (token.isNotBlank()) parts.add(token)
            }
        }

        val flagged = action.parameters.filter { !it.flag.isNullOrBlank() }
        val positionals = action.parameters
            .filter { it.flag.isNullOrBlank() }
            .sortedBy { it.positionalIndex ?: Int.MAX_VALUE }

        for (param in flagged) {
            val rawVal = (values[param.id] ?: param.defaultValue).trim()
            val flag = param.flag!!.trim()
            when (param.type) {
                ParameterType.BOOLEAN_FLAG -> {
                    if (rawVal.equals("true", ignoreCase = true)) {
                        parts.add(flag)
                    }
                }
                else -> {
                    if (rawVal.isNotEmpty()) {
                        if (flag.endsWith("=")) {
                            parts.add("$flag${posixQuote(rawVal)}")
                        } else {
                            parts.add(flag)
                            parts.add(posixQuote(rawVal))
                        }
                    }
                }
            }
        }

        for (param in positionals) {
            val rawVal = (values[param.id] ?: param.defaultValue).trim()
            if (rawVal.isNotEmpty()) {
                parts.add(posixQuote(rawVal))
            }
        }

        return parts.joinToString(" ")
    }

    fun resolveRowActionCommand(
        plugin: CliPlugin,
        rowAction: OutputRowAction,
        contextTokens: Map<String, String>,
        elevateWithSudo: Boolean = rowAction.requiresSudo
    ): Pair<PluginAction, String>? {
        val targetAction = plugin.actions.find { it.id == rowAction.targetActionId } ?: return null
        val resolvedValues = mutableMapOf<String, String>()
        targetAction.parameters.forEach { p ->
            resolvedValues[p.id] = p.defaultValue
        }
        rowAction.paramMappings.forEach { (paramId, template) ->
            var replaced = template
            contextTokens.forEach { (key, value) ->
                replaced = replaced.replace("{$key}", value)
            }
            resolvedValues[paramId] = replaced.trim()
        }
        val cmd = buildCommand(
            plugin = plugin,
            action = targetAction,
            values = resolvedValues,
            elevateWithSudo = elevateWithSudo || targetAction.requiresSudo || rowAction.requiresSudo
        )
        return targetAction to cmd
    }
}

object PluginSchemaSpec {
    const val SPEC_MARKDOWN = """# ShellCraft Extensible Plugin Architecture (shellcraft.plugin.v2)

ShellCraft converts any POSIX / Linux / macOS / Windows-OpenSSH CLI application into either a rich **Modular Tool Dashboard** (presets, sliders, segmented controls, interactive row actions) or a sandboxed **Custom HTML/Canvas GUI** using declarative JSON plugin manifests.

## Design Philosophy
1. **Rich Control Widgets**: Parameters declare `widget` (`AUTO`, `SLIDER`, `SEGMENTED_CHOICE`, `TOGGLE_CARD`, `FILE_PATH`, `STEPPER`), `group` (`Primary` / `Advanced`), `minValue`, `maxValue`, `stepValue`, and `unitLabel`.
2. **One-Tap Presets & Interactive Outputs**: Plugins define `presets` for common workflows and `rowActions` on actions so table rows (e.g. containers, systemd units, processes) surface inline `Logs`, `Restart`, `Stop`, or `Inspect` buttons.
3. **Deterministic POSIX Quoting**: Commands are assembled deterministically using single-quote POSIX escaping to prevent shell injection — even for Custom HTML/Canvas GUIs routed through `ShellCraftBridge`.
4. **Explicit Risk Metadata**: Actions declare `riskLevel` (`SAFE`, `MODERATE`, `HIGH`, `CRITICAL`) and `requiresSudo` so SafetyGuard and Biometric Sudo always gate dangerous operations."""
}
