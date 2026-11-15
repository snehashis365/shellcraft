package com.example.data.repository

import android.content.Context
import com.example.data.local.ExecutionLogEntity
import com.example.data.local.HostEntity
import com.example.data.local.PluginEntity
import com.example.data.local.SeedPlugins
import com.example.data.local.ShellCraftDatabase
import com.example.data.local.SettingsDataStore
import com.example.domain.model.CliPlugin
import com.example.domain.model.SshHostProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ShellCraftRepository(context: Context) {

    private val db = ShellCraftDatabase.getInstance(context)
    private val hostDao = db.hostDao()
    private val pluginDao = db.pluginDao()
    private val logDao = db.executionLogDao()
    val settingsDataStore = SettingsDataStore(context)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val hostsFlow: Flow<List<SshHostProfile>> =
        hostDao.observeHosts().map { list -> list.map { it.toDomain() } }

    val pluginsFlow: Flow<List<CliPlugin>> =
        pluginDao.observePlugins().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { json.decodeFromString<CliPlugin>(entity.pluginJson) }.getOrNull()
            }
        }

    val recentLogsFlow: Flow<List<ExecutionLogEntity>> = logDao.observeRecentLogs()

    suspend fun ensureSeedPlugins() {
        if (pluginDao.countPlugins() == 0) {
            pluginDao.upsertPlugins(SeedPlugins.initialEntities())
        }
    }

    suspend fun saveHost(profile: SshHostProfile) {
        hostDao.upsertHost(HostEntity.fromDomain(profile))
    }

    suspend fun deleteHost(id: String) {
        hostDao.deleteHost(id)
    }

    suspend fun markHostConnected(id: String) {
        hostDao.updateLastConnected(id, System.currentTimeMillis())
    }

    suspend fun savePlugin(plugin: CliPlugin) {
        val entity = PluginEntity(
            id = plugin.id,
            commandName = plugin.commandName,
            displayName = plugin.displayName,
            category = plugin.category,
            source = plugin.source.name,
            updatedAtEpochMs = plugin.updatedAtEpochMs,
            pluginJson = json.encodeToString(plugin)
        )
        pluginDao.upsertPlugin(entity)
    }

    suspend fun savePlugins(plugins: List<CliPlugin>) {
        val entities = plugins.map { plugin ->
            PluginEntity(
                id = plugin.id,
                commandName = plugin.commandName,
                displayName = plugin.displayName,
                category = plugin.category,
                source = plugin.source.name,
                updatedAtEpochMs = plugin.updatedAtEpochMs,
                pluginJson = json.encodeToString(plugin)
            )
        }
        pluginDao.upsertPlugins(entities)
    }

    suspend fun findLocalPluginByCommand(commandName: String): CliPlugin? {
        val entity = pluginDao.findByCommandName(commandName.trim()) ?: return null
        return runCatching { json.decodeFromString<CliPlugin>(entity.pluginJson) }.getOrNull()
    }

    suspend fun deletePlugin(id: String) {
        pluginDao.deletePlugin(id)
    }

    suspend fun recordExecution(
        hostLabel: String,
        pluginName: String,
        command: String,
        exitCode: Int,
        outputPreview: String,
        elevatedWithSudo: Boolean,
        durationMs: Long
    ) {
        logDao.insertLog(
            ExecutionLogEntity(
                hostLabel = hostLabel,
                pluginName = pluginName,
                command = command,
                exitCode = exitCode,
                outputPreview = outputPreview.take(1500),
                elevatedWithSudo = elevatedWithSudo,
                durationMs = durationMs
            )
        )
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }

    fun encodePluginJson(plugin: CliPlugin): String = json.encodeToString(plugin)

    fun decodePluginJson(rawJson: String): CliPlugin = json.decodeFromString(rawJson)
}
