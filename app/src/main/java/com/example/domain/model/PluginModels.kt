package com.example.domain.model

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
    val validationRegex: String? = null
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
    val parameters: List<PluginParameter> = emptyList()
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
    val actions: List<PluginAction> = emptyList()
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
}

object PluginSchemaSpec {
    const val SPEC_MARKDOWN = """# ShellCraft Extensible Plugin Architecture (shellcraft.plugin.v1)

ShellCraft converts any POSIX / Linux / macOS / Windows-OpenSSH CLI application into a uniform Material 3 graphical control panel using declarative JSON plugin manifests.

## Design Philosophy
1. **Predictable Parameter Mapping**: Every CLI flag or positional operand maps to a typed control (`BOOLEAN_FLAG`, `SELECT_ENUM`, `NUMBER`, `PORT`, `PATH`, `KEY_VALUE`, `TEXT`).
2. **Deterministic POSIX Quoting**: Commands are assembled deterministically using single-quote POSIX escaping to prevent shell injection.
3. **Explicit Risk Metadata**: Actions declare `riskLevel` (`SAFE`, `MODERATE`, `HIGH`, `CRITICAL`) and `requiresSudo` so SafetyGuard and Biometric Sudo always gate dangerous operations.
4. **GitHub Check-Before-Generate**: Plugins live as `<commandName>.json` inside your configured GitHub repository (`plugins/` by default). New devices fetch existing manifests before invoking any LLM.

## JSON Schema Example
```json
{
  "schemaVersion": "shellcraft.plugin.v1",
  "id": "builtin-docker",
  "commandName": "docker",
  "displayName": "Docker Engine",
  "description": "Inspect, manage, and prune containers",
  "category": "Containers",
  "targetOs": "POSIX",
  "version": "1.0.0",
  "author": "community",
  "actions": [
    {
      "id": "ps",
      "label": "List Containers",
      "subcommand": "ps",
      "riskLevel": "SAFE",
      "requiresSudo": false,
      "parameters": [
        {
          "id": "all",
          "label": "Include Stopped",
          "type": "BOOLEAN_FLAG",
          "flag": "-a",
          "defaultValue": "true"
        }
      ]
    }
  ]
}
```

## Interactive Shells & Stdin Prompts
ShellCraft executes commands over an allocated SSH pseudo-terminal (PTY) with live bidirectional stdin/stdout streaming:
- Set `"outputHint": "interactive"` on actions that launch interactive prompts or confirmations (e.g., `ufw enable`, `brew upgrade --interactive`, `passwd`, REPLs).
- If a running command pauses for input (`[y/N]`, `(yes/no)`, `Password:`, `Press Enter`, or a custom partial-line prompt), ShellCraft detects the prompt from the stream and surfaces one-tap confirmation buttons, masked secret fields, or a live stdin input bar to forward responses directly to the remote process.

## Contributing via Agents or CI
Third-party developers and automated doc agents can generate `shellcraft.plugin.v1` JSON files from `man` pages or `--help` output and commit them to `plugins/<commandName>.json` in your team's GitHub repository. ShellCraft automatically discovers and validates new manifests on sync."""
}
