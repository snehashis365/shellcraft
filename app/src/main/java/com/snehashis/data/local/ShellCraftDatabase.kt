package com.snehashis.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginParameter
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.domain.model.TargetOs
import kotlinx.serialization.json.Json

@Database(
    entities = [HostEntity::class, PluginEntity::class, ExecutionLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShellCraftDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun pluginDao(): PluginDao
    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        @Volatile
        private var INSTANCE: ShellCraftDatabase? = null

        fun getInstance(context: Context): ShellCraftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShellCraftDatabase::class.java,
                    "shellcraft_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object SeedPlugins {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun initialEntities(): List<PluginEntity> {
        return starterPlugins().map { plugin ->
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
    }

    fun starterPlugins(): List<CliPlugin> = listOf(
        CliPlugin(
            id = "builtin-systemctl",
            commandName = "systemctl",
            displayName = "Systemd Service Control",
            description = "Inspect, start, restart, or stop POSIX systemd units and daemon health.",
            category = "System",
            targetOs = TargetOs.LINUX,
            version = "1.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            actions = listOf(
                PluginAction(
                    id = "status",
                    label = "Unit Status",
                    description = "Check runtime status and recent cgroup logs for a service.",
                    subcommand = "status",
                    riskLevel = RiskLevel.SAFE,
                    requiresSudo = false,
                    parameters = listOf(
                        PluginParameter(
                            id = "unit",
                            label = "Service Unit",
                            description = "Unit name (e.g. nginx, sshd, docker)",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            required = true,
                            defaultValue = "ssh",
                            placeholder = "nginx.service"
                        ),
                        PluginParameter(
                            id = "lines",
                            label = "Log Lines",
                            type = ParameterType.NUMBER,
                            flag = "-n",
                            defaultValue = "20"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "Disable Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true"
                        )
                    )
                ),
                PluginAction(
                    id = "list_failed",
                    label = "Failed Units",
                    description = "List all failed systemd units on the host.",
                    subcommand = "list-units",
                    riskLevel = RiskLevel.SAFE,
                    requiresSudo = false,
                    parameters = listOf(
                        PluginParameter(
                            id = "failed",
                            label = "Only Failed",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--failed",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "Disable Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true"
                        )
                    )
                ),
                PluginAction(
                    id = "restart",
                    label = "Restart Service",
                    description = "Gracefully restart a service unit (requires superuser).",
                    subcommand = "restart",
                    riskLevel = RiskLevel.MODERATE,
                    requiresSudo = true,
                    parameters = listOf(
                        PluginParameter(
                            id = "unit",
                            label = "Service Unit",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "nginx"
                        )
                    )
                ),
                PluginAction(
                    id = "stop",
                    label = "Stop Service",
                    description = "Halt a running systemd service immediately.",
                    subcommand = "stop",
                    riskLevel = RiskLevel.HIGH,
                    requiresSudo = true,
                    parameters = listOf(
                        PluginParameter(
                            id = "unit",
                            label = "Service Unit",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "nginx"
                        )
                    )
                )
            )
        ),
        CliPlugin(
            id = "builtin-docker",
            commandName = "docker",
            displayName = "Docker Engine",
            description = "Inspect running containers, tail container logs, and prune unused assets.",
            category = "Containers",
            targetOs = TargetOs.POSIX,
            version = "1.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            actions = listOf(
                PluginAction(
                    id = "ps",
                    label = "List Containers",
                    description = "Show running and optionally stopped containers.",
                    subcommand = "ps",
                    riskLevel = RiskLevel.SAFE,
                    parameters = listOf(
                        PluginParameter(
                            id = "all",
                            label = "Include Stopped (-a)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-a",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "format",
                            label = "Output Format",
                            type = ParameterType.SELECT_ENUM,
                            flag = "--format",
                            defaultValue = "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}",
                            options = listOf(
                                "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}",
                                "json",
                                "table {{.ID}}\\t{{.Image}}\\t{{.State}}"
                            )
                        )
                    )
                ),
                PluginAction(
                    id = "logs",
                    label = "Tail Logs",
                    description = "Fetch recent container stdout/stderr logs.",
                    subcommand = "logs",
                    riskLevel = RiskLevel.SAFE,
                    parameters = listOf(
                        PluginParameter(
                            id = "tail",
                            label = "Tail Lines",
                            type = ParameterType.NUMBER,
                            flag = "--tail",
                            defaultValue = "100"
                        ),
                        PluginParameter(
                            id = "timestamps",
                            label = "Include Timestamps",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-t",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "container",
                            label = "Container Name or ID",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "api-gateway"
                        )
                    )
                ),
                PluginAction(
                    id = "prune",
                    label = "System Prune",
                    description = "Remove stopped containers, unused networks, and dangling images.",
                    subcommand = "system prune",
                    riskLevel = RiskLevel.HIGH,
                    parameters = listOf(
                        PluginParameter(
                            id = "all_images",
                            label = "Remove All Unused Images (-a)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-a",
                            defaultValue = "false"
                        ),
                        PluginParameter(
                            id = "force",
                            label = "Skip Interactive Prompt (-f)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-f",
                            defaultValue = "true"
                        )
                    )
                )
            )
        ),
        CliPlugin(
            id = "builtin-journalctl",
            commandName = "journalctl",
            displayName = "Systemd Journal Logs",
            description = "Query kernel and system service logs with priority and time filtering.",
            category = "Diagnostics",
            targetOs = TargetOs.LINUX,
            version = "1.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            actions = listOf(
                PluginAction(
                    id = "query",
                    label = "Query Journal",
                    description = "Read recent journal entries filtered by unit and severity.",
                    subcommand = "",
                    riskLevel = RiskLevel.SAFE,
                    parameters = listOf(
                        PluginParameter(
                            id = "unit",
                            label = "Filter by Unit (-u)",
                            type = ParameterType.TEXT,
                            flag = "-u",
                            placeholder = "sshd"
                        ),
                        PluginParameter(
                            id = "priority",
                            label = "Priority Level (-p)",
                            type = ParameterType.SELECT_ENUM,
                            flag = "-p",
                            defaultValue = "err",
                            options = listOf("emerg", "alert", "crit", "err", "warning", "notice", "info", "debug")
                        ),
                        PluginParameter(
                            id = "lines",
                            label = "Lines (-n)",
                            type = ParameterType.NUMBER,
                            flag = "-n",
                            defaultValue = "50"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "No Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true"
                        )
                    )
                )
            )
        ),
        CliPlugin(
            id = "builtin-rsync",
            commandName = "rsync",
            displayName = "Rsync File Transfer",
            description = "Fast incremental POSIX directory synchronization with dry-run support.",
            category = "Files",
            targetOs = TargetOs.POSIX,
            version = "1.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            actions = listOf(
                PluginAction(
                    id = "sync",
                    label = "Synchronize Directories",
                    description = "Copy and mirror files incrementally with optional dry-run.",
                    subcommand = "",
                    riskLevel = RiskLevel.MODERATE,
                    parameters = listOf(
                        PluginParameter(
                            id = "archive",
                            label = "Archive Mode (-a)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-a",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "verbose",
                            label = "Verbose (-v)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-v",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "compress",
                            label = "Compress (-z)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-z",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "dry_run",
                            label = "Dry Run (--dry-run)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--dry-run",
                            defaultValue = "true"
                        ),
                        PluginParameter(
                            id = "delete",
                            label = "Delete Extraneous Files (--delete)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--delete",
                            defaultValue = "false"
                        ),
                        PluginParameter(
                            id = "source",
                            label = "Source Path",
                            type = ParameterType.PATH,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "/var/www/app/"
                        ),
                        PluginParameter(
                            id = "destination",
                            label = "Destination Path",
                            type = ParameterType.PATH,
                            positionalIndex = 1,
                            required = true,
                            placeholder = "/backup/app/"
                        )
                    )
                )
            )
        )
    )
}
