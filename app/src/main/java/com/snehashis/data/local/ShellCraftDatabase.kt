package com.snehashis.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.OutputRowAction
import com.snehashis.domain.model.ParameterType
import com.snehashis.domain.model.ParameterWidget
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PluginParameter
import com.snehashis.domain.model.PluginPreset
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
        return starterPlugins().map { toEntity(it) }
    }

    fun toEntity(plugin: CliPlugin): PluginEntity = PluginEntity(
        id = plugin.id,
        commandName = plugin.commandName,
        displayName = plugin.displayName,
        category = plugin.category,
        source = plugin.source.name,
        updatedAtEpochMs = plugin.updatedAtEpochMs,
        pluginJson = json.encodeToString(plugin)
    )

    fun starterPlugins(): List<CliPlugin> = listOf(
        CliPlugin(
            schemaVersion = "shellcraft.plugin.v2",
            id = "builtin-systemctl",
            commandName = "systemctl",
            displayName = "Systemd Service Control",
            description = "Inspect, start, restart, or stop POSIX systemd units with one-tap row actions.",
            category = "System",
            targetOs = TargetOs.LINUX,
            version = "2.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            uiMode = PluginGuiStyle.MODULAR_DASHBOARD,
            presets = listOf(
                PluginPreset(
                    id = "failed_audit",
                    label = "Audit Failed Units",
                    description = "Find broken services immediately",
                    actionId = "list_failed",
                    values = mapOf("failed" to "true", "no_pager" to "true"),
                    iconName = "warning"
                ),
                PluginPreset(
                    id = "ssh_health",
                    label = "Check SSHD",
                    description = "Inspect ssh service + 30 log lines",
                    actionId = "status",
                    values = mapOf("unit" to "ssh", "lines" to "30", "no_pager" to "true"),
                    iconName = "bolt"
                ),
                PluginPreset(
                    id = "nginx_health",
                    label = "Check Nginx",
                    description = "Inspect nginx web daemon",
                    actionId = "status",
                    values = mapOf("unit" to "nginx", "lines" to "40", "no_pager" to "true"),
                    iconName = "speed"
                )
            ),
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
                            placeholder = "nginx.service",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "lines",
                            label = "Log Tail Lines",
                            type = ParameterType.NUMBER,
                            flag = "-n",
                            defaultValue = "30",
                            widget = ParameterWidget.SLIDER,
                            minValue = 5f,
                            maxValue = 200f,
                            stepValue = 5f,
                            unitLabel = "lines",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "Disable Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true",
                            group = "Advanced"
                        )
                    ),
                    rowActions = listOf(
                        OutputRowAction(
                            id = "restart_unit",
                            label = "Restart",
                            targetActionId = "restart",
                            paramMappings = mapOf("unit" to "{unit}"),
                            iconName = "refresh",
                            riskLevel = RiskLevel.MODERATE,
                            requiresSudo = true
                        ),
                        OutputRowAction(
                            id = "stop_unit",
                            label = "Stop",
                            targetActionId = "stop",
                            paramMappings = mapOf("unit" to "{unit}"),
                            iconName = "stop",
                            riskLevel = RiskLevel.HIGH,
                            requiresSudo = true
                        )
                    )
                ),
                PluginAction(
                    id = "list_failed",
                    label = "Failed Units",
                    description = "List active or failed systemd units with inline drill-down controls.",
                    subcommand = "list-units",
                    riskLevel = RiskLevel.SAFE,
                    requiresSudo = false,
                    parameters = listOf(
                        PluginParameter(
                            id = "failed",
                            label = "Only Failed",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--failed",
                            defaultValue = "true",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "Disable Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true",
                            group = "Advanced"
                        )
                    ),
                    rowActions = listOf(
                        OutputRowAction(
                            id = "inspect_unit",
                            label = "Inspect",
                            targetActionId = "status",
                            paramMappings = mapOf("unit" to "{col:0}"),
                            iconName = "info",
                            riskLevel = RiskLevel.SAFE,
                            requiresSudo = false
                        ),
                        OutputRowAction(
                            id = "restart_row_unit",
                            label = "Restart",
                            targetActionId = "restart",
                            paramMappings = mapOf("unit" to "{col:0}"),
                            iconName = "refresh",
                            riskLevel = RiskLevel.MODERATE,
                            requiresSudo = true
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
            schemaVersion = "shellcraft.plugin.v2",
            id = "builtin-docker",
            commandName = "docker",
            displayName = "Docker Engine",
            description = "Inspect containers, launch per-container logs or restarts directly from output rows, and prune safely.",
            category = "Containers",
            targetOs = TargetOs.POSIX,
            version = "2.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            uiMode = PluginGuiStyle.MODULAR_DASHBOARD,
            presets = listOf(
                PluginPreset(
                    id = "active_containers",
                    label = "Running Containers",
                    description = "Compact table of active containers",
                    actionId = "ps",
                    values = mapOf(
                        "all" to "false",
                        "format" to "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    ),
                    iconName = "bolt"
                ),
                PluginPreset(
                    id = "all_containers",
                    label = "All + Exited",
                    description = "Include stopped containers & image IDs",
                    actionId = "ps",
                    values = mapOf(
                        "all" to "true",
                        "format" to "table {{.Names}}\\t{{.Image}}\\t{{.Status}}"
                    ),
                    iconName = "layers"
                )
            ),
            actions = listOf(
                PluginAction(
                    id = "ps",
                    label = "List Containers",
                    description = "Show containers with one-tap row buttons to tail logs, restart, or stop.",
                    subcommand = "ps",
                    riskLevel = RiskLevel.SAFE,
                    parameters = listOf(
                        PluginParameter(
                            id = "all",
                            label = "Include Stopped (-a)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-a",
                            defaultValue = "true",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "format",
                            label = "Table Columns",
                            type = ParameterType.SELECT_ENUM,
                            flag = "--format",
                            defaultValue = "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}",
                            options = listOf(
                                "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}",
                                "table {{.Names}}\\t{{.Image}}\\t{{.Status}}",
                                "json"
                            ),
                            widget = ParameterWidget.SEGMENTED_CHOICE,
                            group = "Advanced"
                        )
                    ),
                    rowActions = listOf(
                        OutputRowAction(
                            id = "container_logs",
                            label = "Logs",
                            targetActionId = "logs",
                            paramMappings = mapOf("container" to "{col:0}", "tail" to "120"),
                            iconName = "description",
                            riskLevel = RiskLevel.SAFE
                        ),
                        OutputRowAction(
                            id = "container_restart",
                            label = "Restart",
                            targetActionId = "restart_container",
                            paramMappings = mapOf("container" to "{col:0}"),
                            iconName = "refresh",
                            riskLevel = RiskLevel.MODERATE
                        ),
                        OutputRowAction(
                            id = "container_stop",
                            label = "Stop",
                            targetActionId = "stop_container",
                            paramMappings = mapOf("container" to "{col:0}"),
                            iconName = "stop",
                            riskLevel = RiskLevel.HIGH
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
                            id = "container",
                            label = "Container Name or ID",
                            type = ParameterType.TEXT,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "api-gateway",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "tail",
                            label = "Tail Depth",
                            type = ParameterType.NUMBER,
                            flag = "--tail",
                            defaultValue = "100",
                            widget = ParameterWidget.SLIDER,
                            minValue = 20f,
                            maxValue = 1000f,
                            stepValue = 20f,
                            unitLabel = "lines",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "timestamps",
                            label = "Include Timestamps",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-t",
                            defaultValue = "true",
                            group = "Advanced"
                        )
                    )
                ),
                PluginAction(
                    id = "restart_container",
                    label = "Restart Container",
                    description = "Restart a container gracefully.",
                    subcommand = "restart",
                    riskLevel = RiskLevel.MODERATE,
                    parameters = listOf(
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
                    id = "stop_container",
                    label = "Stop Container",
                    description = "Stop a running container.",
                    subcommand = "stop",
                    riskLevel = RiskLevel.HIGH,
                    parameters = listOf(
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
            schemaVersion = "shellcraft.plugin.v2",
            id = "builtin-journalctl",
            commandName = "journalctl",
            displayName = "Systemd Journal Logs",
            description = "Query kernel & service logs using severity chips and continuous tail sliders.",
            category = "Diagnostics",
            targetOs = TargetOs.LINUX,
            version = "2.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            uiMode = PluginGuiStyle.MODULAR_DASHBOARD,
            presets = listOf(
                PluginPreset(
                    id = "critical_errors",
                    label = "Recent Errors",
                    description = "Last 80 error-level journal entries",
                    actionId = "query",
                    values = mapOf("priority" to "err", "lines" to "80", "no_pager" to "true"),
                    iconName = "warning"
                ),
                PluginPreset(
                    id = "auth_ssh_logs",
                    label = "SSH Daemon Logs",
                    description = "Inspect ssh authentication activity",
                    actionId = "query",
                    values = mapOf("unit" to "ssh", "priority" to "info", "lines" to "100", "no_pager" to "true"),
                    iconName = "lock"
                )
            ),
            actions = listOf(
                PluginAction(
                    id = "query",
                    label = "Query Journal",
                    description = "Read recent journal entries filtered by unit and severity.",
                    subcommand = "",
                    riskLevel = RiskLevel.SAFE,
                    parameters = listOf(
                        PluginParameter(
                            id = "priority",
                            label = "Minimum Severity",
                            type = ParameterType.SELECT_ENUM,
                            flag = "-p",
                            defaultValue = "err",
                            options = listOf("crit", "err", "warning", "notice", "info", "debug"),
                            widget = ParameterWidget.SEGMENTED_CHOICE,
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "lines",
                            label = "Entry Limit",
                            type = ParameterType.NUMBER,
                            flag = "-n",
                            defaultValue = "60",
                            widget = ParameterWidget.SLIDER,
                            minValue = 10f,
                            maxValue = 500f,
                            stepValue = 10f,
                            unitLabel = "lines",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "unit",
                            label = "Filter by Unit (-u)",
                            type = ParameterType.TEXT,
                            flag = "-u",
                            placeholder = "sshd",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "no_pager",
                            label = "No Pager",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--no-pager",
                            defaultValue = "true",
                            group = "Advanced"
                        )
                    )
                )
            )
        ),
        CliPlugin(
            schemaVersion = "shellcraft.plugin.v2",
            id = "builtin-rsync",
            commandName = "rsync",
            displayName = "Rsync File Transfer",
            description = "Incremental remote & phone-uploaded file synchronization with interactive SFTP pickers.",
            category = "Files",
            targetOs = TargetOs.POSIX,
            version = "2.0.0",
            author = "ShellCraft Core",
            source = PluginSource.BUILT_IN,
            uiMode = PluginGuiStyle.MODULAR_DASHBOARD,
            presets = listOf(
                PluginPreset(
                    id = "safe_dry_run",
                    label = "Dry-Run Preview",
                    description = "Simulate sync without modifying destination",
                    actionId = "sync",
                    values = mapOf("archive" to "true", "verbose" to "true", "compress" to "true", "dry_run" to "true", "delete" to "false"),
                    iconName = "shield"
                ),
                PluginPreset(
                    id = "mirror_fast",
                    label = "Compressed Sync",
                    description = "Live incremental transfer (-avz)",
                    actionId = "sync",
                    values = mapOf("archive" to "true", "verbose" to "true", "compress" to "true", "dry_run" to "false", "delete" to "false"),
                    iconName = "bolt"
                )
            ),
            actions = listOf(
                PluginAction(
                    id = "sync",
                    label = "Synchronize Directories",
                    description = "Copy and mirror files incrementally; use Browse Host or Upload Phone File on paths.",
                    subcommand = "",
                    riskLevel = RiskLevel.MODERATE,
                    parameters = listOf(
                        PluginParameter(
                            id = "source",
                            label = "Source Path",
                            type = ParameterType.PATH,
                            positionalIndex = 0,
                            required = true,
                            placeholder = "/var/www/app/",
                            widget = ParameterWidget.FILE_PATH,
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "destination",
                            label = "Destination Path",
                            type = ParameterType.PATH,
                            positionalIndex = 1,
                            required = true,
                            placeholder = "/backup/app/",
                            widget = ParameterWidget.FILE_PATH,
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "dry_run",
                            label = "Dry Run (--dry-run)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--dry-run",
                            defaultValue = "true",
                            group = "Primary"
                        ),
                        PluginParameter(
                            id = "archive",
                            label = "Archive Mode (-a)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-a",
                            defaultValue = "true",
                            group = "Advanced"
                        ),
                        PluginParameter(
                            id = "verbose",
                            label = "Verbose (-v)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-v",
                            defaultValue = "true",
                            group = "Advanced"
                        ),
                        PluginParameter(
                            id = "compress",
                            label = "Compress (-z)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "-z",
                            defaultValue = "true",
                            group = "Advanced"
                        ),
                        PluginParameter(
                            id = "delete",
                            label = "Delete Extraneous Files (--delete)",
                            type = ParameterType.BOOLEAN_FLAG,
                            flag = "--delete",
                            defaultValue = "false",
                            group = "Advanced"
                        )
                    )
                )
            )
        )
    )
}
