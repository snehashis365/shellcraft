package com.snehashis

import com.snehashis.core.security.SafetyGuard
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginCommandBuilder
import com.snehashis.domain.model.PluginParameter
import com.snehashis.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCraftCoreTest {

    @Test
    fun posixQuote_escapesSpacesAndSingleQuotesSafely() {
        assertEquals("nginx", PluginCommandBuilder.posixQuote("nginx"))
        assertEquals("'hello world'", PluginCommandBuilder.posixQuote("hello world"))
        assertEquals("'it'\"'\"'s a test'", PluginCommandBuilder.posixQuote("it's a test"))
        assertEquals("''", PluginCommandBuilder.posixQuote(""))
    }

    @Test
    fun buildCommand_assemblesFlagsPositionalsAndSudoCorrectly() {
        val plugin = CliPlugin(
            id = "test-docker",
            commandName = "docker",
            displayName = "Docker",
            description = "Test",
            actions = emptyList()
        )
        val action = PluginAction(
            id = "logs",
            label = "Logs",
            subcommand = "logs",
            riskLevel = RiskLevel.SAFE,
            parameters = listOf(
                PluginParameter(
                    id = "tail",
                    label = "Tail",
                    type = ParameterType.NUMBER,
                    flag = "--tail"
                ),
                PluginParameter(
                    id = "timestamps",
                    label = "Timestamps",
                    type = ParameterType.BOOLEAN_FLAG,
                    flag = "-t"
                ),
                PluginParameter(
                    id = "container",
                    label = "Container",
                    type = ParameterType.TEXT,
                    positionalIndex = 0
                )
            )
        )

        val cmd = PluginCommandBuilder.buildCommand(
            plugin = plugin,
            action = action,
            values = mapOf(
                "tail" to "50",
                "timestamps" to "true",
                "container" to "my web app"
            ),
            elevateWithSudo = true
        )

        assertEquals("sudo docker logs --tail 50 -t 'my web app'", cmd)
    }

    @Test
    fun safetyGuard_flagsDestructiveAndSudoCommands() {
        val rmResult = SafetyGuard.assess(
            command = "rm -rf /var/log/old",
            action = null,
            elevateWithSudo = false,
            confirmModerateCommands = true
        )
        assertEquals(RiskLevel.CRITICAL, rmResult.effectiveRisk)
        assertTrue(rmResult.requiresConfirmation)
        assertFalse(rmResult.requiresSuperuserAuth)

        val sudoResult = SafetyGuard.assess(
            command = "sudo systemctl restart nginx",
            action = null,
            elevateWithSudo = true,
            confirmModerateCommands = true
        )
        assertTrue(sudoResult.effectiveRisk >= RiskLevel.HIGH)
        assertTrue(sudoResult.requiresConfirmation)
        assertTrue(sudoResult.requiresSuperuserAuth)

        val safeResult = SafetyGuard.assess(
            command = "docker ps -a",
            action = PluginAction(id = "ps", label = "PS", riskLevel = RiskLevel.SAFE),
            elevateWithSudo = false,
            confirmModerateCommands = true
        )
        assertEquals(RiskLevel.SAFE, safeResult.effectiveRisk)
        assertFalse(safeResult.requiresConfirmation)
        assertFalse(safeResult.requiresSuperuserAuth)
    }

    @Test
    fun outputFormatter_parsesPingSystemdTableJsonKeyValueAndLogs() {
        val pingRaw = """
            PING 8.8.8.8 (8.8.8.8): 56 data bytes
            64 bytes from 8.8.8.8: icmp_seq=0 ttl=117 time=12.4 ms
            64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=14.1 ms
            --- 8.8.8.8 ping statistics ---
            2 packets transmitted, 2 packets received, 0.0% packet loss
            round-trip min/avg/max/stddev = 12.4/13.25/14.1/0.85 ms
        """.trimIndent()
        val pingParsed = com.snehashis.domain.model.OutputFormatter.parse("ping -c 2 8.8.8.8", pingRaw)
        assertTrue(pingParsed is com.snehashis.domain.model.FormattedOutput.PingReport)
        pingParsed as com.snehashis.domain.model.FormattedOutput.PingReport
        assertEquals("8.8.8.8", pingParsed.host)
        assertEquals("0.0%", pingParsed.lossPercent)
        assertEquals("13.25 ms", pingParsed.avgMs)

        val systemdRaw = """
            ● nginx.service - A high performance web server
               Loaded: loaded (/lib/systemd/system/nginx.service; enabled)
               Active: active (running) since Mon 2026-11-16 10:00:00 UTC; 2 days ago
             Main PID: 1234 (nginx)
                Tasks: 3
               Memory: 14.2M
        """.trimIndent()
        val systemdParsed = com.snehashis.domain.model.OutputFormatter.parse("systemctl status nginx", systemdRaw)
        assertTrue(systemdParsed is com.snehashis.domain.model.FormattedOutput.SystemdStatusReport)
        systemdParsed as com.snehashis.domain.model.FormattedOutput.SystemdStatusReport
        assertEquals("nginx.service", systemdParsed.unit)
        assertTrue(systemdParsed.isHealthy)
        assertEquals("14.2M", systemdParsed.memory)

        val tableRaw = """
            CONTAINER ID   IMAGE          STATUS        NAMES
            a1b2c3d4       nginx:latest   Up 3 hours    web-proxy
            e5f6g7h8       redis:7        Up 2 days     cache
        """.trimIndent()
        val tableParsed = com.snehashis.domain.model.OutputFormatter.parse("docker ps", tableRaw)
        assertTrue(tableParsed is com.snehashis.domain.model.FormattedOutput.TableReport)
        tableParsed as com.snehashis.domain.model.FormattedOutput.TableReport
        assertEquals(listOf("CONTAINER ID", "IMAGE", "STATUS", "NAMES"), tableParsed.headers)
        assertEquals(2, tableParsed.rows.size)

        val jsonParsed = com.snehashis.domain.model.OutputFormatter.parse(
            "curl -s https://api.example.com/health",
            """{"status":"ok","version":"2.4.1","workers":4}"""
        )
        assertTrue(jsonParsed is com.snehashis.domain.model.FormattedOutput.StructuredJsonReport)
        jsonParsed as com.snehashis.domain.model.FormattedOutput.StructuredJsonReport
        assertTrue(jsonParsed.summaryPairs.any { it.first == "status" && it.second == "ok" })

        val kvParsed = com.snehashis.domain.model.OutputFormatter.parse(
            "uname -a",
            """
            Kernel: Linux 6.8.0
            Architecture: x86_64
            Hostname: prod-node-1
            """.trimIndent()
        )
        assertTrue(kvParsed is com.snehashis.domain.model.FormattedOutput.KeyValueReport)

        val logParsed = com.snehashis.domain.model.OutputFormatter.parse(
            "tail -n 3 /var/log/syslog",
            """
            Server started and listening on port 443
            WARN: retry scheduled for upstream
            ERROR: connection denied by peer
            """.trimIndent()
        )
        assertTrue(logParsed is com.snehashis.domain.model.FormattedOutput.LogLinesReport)
        logParsed as com.snehashis.domain.model.FormattedOutput.LogLinesReport
        assertEquals(com.snehashis.domain.model.LogSeverity.SUCCESS, logParsed.entries[0].severity)
        assertEquals(com.snehashis.domain.model.LogSeverity.WARNING, logParsed.entries[1].severity)
        assertEquals(com.snehashis.domain.model.LogSeverity.ERROR, logParsed.entries[2].severity)
    }

    @Test
    fun sshEngine_wrapsPosixCommandsWithHomebrewAndLocalBinPath() {
        val macEnv = com.snehashis.domain.model.RemoteEnvironmentInfo(
            osName = "Darwin",
            architecture = "arm64",
            currentUser = "dev",
            shell = "/bin/zsh",
            hasSudo = true
        )
        val wrappedMac = com.snehashis.data.ssh.SshEngine.wrapWithExtendedPath("brew list", macEnv)
        assertTrue(wrappedMac.contains("/opt/homebrew/bin"))
        assertTrue(wrappedMac.contains("/usr/local/bin"))
        assertTrue(wrappedMac.endsWith("brew list"))

        val winCmdEnv = com.snehashis.domain.model.RemoteEnvironmentInfo(
            osName = "Windows_NT",
            architecture = "x86_64",
            currentUser = "admin",
            shell = "C:\\Windows\\System32\\cmd.exe",
            hasSudo = false
        )
        assertEquals("dir", com.snehashis.data.ssh.SshEngine.wrapWithExtendedPath("dir", winCmdEnv))
    }

    @Test
    fun interactivePromptDetector_classifiesPromptsAndStripsAnsi() {
        val ynPrompt = com.snehashis.domain.model.InteractivePromptDetector.detect(
            "\u001B[1mCommand may disrupt existing ssh connections. Proceed with operation (y|n)? [y/N]\u001B[0m"
        )
        assertEquals(com.snehashis.domain.model.PromptKind.CONFIRM_YN, ynPrompt?.kind)
        assertEquals("y", ynPrompt?.quickReplies?.first()?.second)

        val yesNoPrompt = com.snehashis.domain.model.InteractivePromptDetector.detect(
            "Are you sure you want to continue connecting (yes/no)?"
        )
        assertEquals(com.snehashis.domain.model.PromptKind.CONFIRM_YES_NO, yesNoPrompt?.kind)

        val secretPrompt = com.snehashis.domain.model.InteractivePromptDetector.detect(
            "Enter passphrase for key '/Users/dev/.ssh/id_ed25519':"
        )
        assertEquals(com.snehashis.domain.model.PromptKind.SECRET_INPUT, secretPrompt?.kind)

        val enterPrompt = com.snehashis.domain.model.InteractivePromptDetector.detect(
            "Press RETURN to continue or any other key to abort:"
        )
        assertEquals(com.snehashis.domain.model.PromptKind.PRESS_ENTER, enterPrompt?.kind)

        val freePrompt = com.snehashis.domain.model.InteractivePromptDetector.detect(
            "psql (16.4)\npostgres=# ",
            stalledOnPartialLine = true
        )
        assertEquals(com.snehashis.domain.model.PromptKind.FREE_INPUT, freePrompt?.kind)
    }

    @Test
    fun cliDocCondenser_condensesLargeDocsAndPreservesSynopsisAndFlags() {
        val longProse = buildString {
            appendLine("rsync - a fast, versatile, remote file-copying tool")
            appendLine("SYNOPSIS")
            appendLine("  rsync [OPTION]... SRC [DEST]")
            appendLine("DESCRIPTION")
            repeat(180) { i ->
                appendLine("  Verbose explanatory paragraph line $i about rsync daemon protocol internals and delta-transfer algorithm details.")
            }
            appendLine("OPTIONS")
            appendLine("  -a, --archive")
            appendLine("      archive mode; equals -rlptgoD")
            appendLine("  -z, --compress")
            appendLine("      compress file data during the transfer")
        }
        val condensed = com.snehashis.domain.model.CliDocCondenser.condense("rsync", longProse, maxChars = 1500)
        assertTrue(condensed.length <= 1500)
        assertTrue(condensed.contains("rsync [OPTION]... SRC [DEST]"))
        assertTrue(condensed.contains("--archive"))
        assertTrue(condensed.contains("--compress"))
        assertTrue(com.snehashis.domain.model.CliDocCondenser.estimateTokens(condensed) < 400)
    }

    @Test
    fun cliDocCondenser_repairsFencedTrailingCommasAndTruncatedJson() {
        val fenced = """
            ```json
            {"commandName":"ping","displayName":"Ping","actions":[{"id":"run","label":"Run",}],}
            ```
        """.trimIndent()
        val cleaned = com.snehashis.domain.model.CliDocCondenser.repairJson(fenced)
        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains(",]"))
        assertFalse(cleaned.contains(",}"))
        assertTrue(cleaned.startsWith("{") && cleaned.endsWith("}"))

        val truncated = """{"commandName":"ufw","displayName":"UFW","actions":[{"id":"status","label":"Status"}"""
        val closed = com.snehashis.domain.model.CliDocCondenser.repairJson(truncated)
        assertTrue(closed.endsWith("]}"))
    }

    @Test
    fun parseOpenAiModelsResponse_handlesGoogleStudioOpenRouterGroqAndNvidia() {
        val aiStudioJson = """
            {
              "data": [
                {"id": "models/gemini-2.5-flash", "owned_by": "google"},
                {"id": "models/text-embedding-004", "owned_by": "google"},
                {"id": "models/imagen-3.0-generate-002", "owned_by": "google"}
              ]
            }
        """.trimIndent()
        val aiStudioModels = com.snehashis.data.llm.LlmPluginAnalyzer.parseOpenAiModelsResponse(
            aiStudioJson,
            "https://generativelanguage.googleapis.com/v1beta/openai"
        )
        assertEquals(1, aiStudioModels.size)
        assertEquals("gemini-2.5-flash", aiStudioModels.first().id)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.FREE in aiStudioModels.first().tags)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.FAST in aiStudioModels.first().tags)

        val openRouterJson = """
            {
              "data": [
                {"id": "openai/gpt-4o", "pricing": {"prompt": "0.0000025"}},
                {"id": "deepseek/deepseek-r1", "pricing": {"prompt": "0"}},
                {"id": "meta-llama/llama-3.3-70b-instruct:free", "pricing": {"prompt": "0"}}
              ]
            }
        """.trimIndent()
        val orModels = com.snehashis.data.llm.LlmPluginAnalyzer.parseOpenAiModelsResponse(
            openRouterJson,
            "https://openrouter.ai/api/v1"
        )
        assertEquals(3, orModels.size)
        val deepseek = orModels.first { it.id == "deepseek/deepseek-r1" }
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.FREE in deepseek.tags)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.REASONING in deepseek.tags)
        val paid = orModels.first { it.id == "openai/gpt-4o" }
        assertFalse(com.snehashis.domain.model.ModelCapabilityTag.FREE in paid.tags)

        val groqJson = """
            {
              "data": [
                {"id": "whisper-large-v3", "owned_by": "OpenAI", "active": true},
                {"id": "llama-3.1-70b-deactivated", "owned_by": "Meta", "active": false},
                {"id": "qwen/qwen2.5-coder-32b", "owned_by": "Alibaba", "active": true}
              ]
            }
        """.trimIndent()
        val groqModels = com.snehashis.data.llm.LlmPluginAnalyzer.parseOpenAiModelsResponse(
            groqJson,
            "https://api.groq.com/openai/v1"
        )
        assertEquals(1, groqModels.size)
        assertEquals("qwen/qwen2.5-coder-32b", groqModels.first().id)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.FREE in groqModels.first().tags)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.CODER in groqModels.first().tags)
    }

    @Test
    fun parseOllamaTagsResponse_extractsFamilyAndCapabilityTags() {
        val ollamaTagsJson = """
            {
              "models": [
                {"name": "qwen2.5-coder:7b", "details": {"family": "qwen2"}},
                {"name": "deepseek-r1:8b", "details": {"family": "llama"}}
              ]
            }
        """.trimIndent()
        val models = com.snehashis.data.llm.LlmPluginAnalyzer.parseOllamaTagsResponse(ollamaTagsJson)
        assertEquals(2, models.size)
        val coder = models.first { it.id == "qwen2.5-coder:7b" }
        assertEquals("qwen2", coder.ownedBy)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.CODER in coder.tags)
        val r1 = models.first { it.id == "deepseek-r1:8b" }
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.REASONING in r1.tags)
        assertTrue(com.snehashis.domain.model.ModelCapabilityTag.FAST in r1.tags)
    }
}
