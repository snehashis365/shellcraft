package com.example

import com.example.core.security.SafetyGuard
import com.example.domain.model.CliPlugin
import com.example.domain.model.ParameterType
import com.example.domain.model.PluginAction
import com.example.domain.model.PluginCommandBuilder
import com.example.domain.model.PluginParameter
import com.example.domain.model.RiskLevel
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
}
