package com.snehashis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.snehashis.domain.model.ForgeState
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.domain.model.SshHostProfile
import com.snehashis.ui.components.HostEditorDialog
import com.snehashis.ui.components.LiveTerminalSheet
import com.snehashis.ui.components.PluginSchemaDocsSheet
import com.snehashis.ui.components.SafetyAndSudoDialog
import com.snehashis.ui.screens.ForgeScreen
import com.snehashis.ui.screens.PluginRunnerScreen
import com.snehashis.ui.screens.PluginsScreen
import com.snehashis.ui.screens.SettingsScreen
import com.snehashis.ui.screens.WorkspaceScreen
import com.snehashis.ui.theme.JetBrainsMonoFontFamily
import com.snehashis.ui.theme.ShellCraftTheme
import kotlinx.serialization.Serializable

@Serializable
data object WorkspaceRoute

@Serializable
data object PluginsRoute

@Serializable
data class PluginRunnerRoute(val pluginId: String)

@Serializable
data object ForgeRoute

@Serializable
data object SettingsRoute

private data class TopLevelTab<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    TopLevelTab(
        route = WorkspaceRoute,
        label = "Workspace",
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_workspace"
    ),
    TopLevelTab(
        route = PluginsRoute,
        label = "Plugins",
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension,
        testTag = "nav_plugins"
    ),
    TopLevelTab(
        route = ForgeRoute,
        label = "Forge",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_forge"
    ),
    TopLevelTab(
        route = SettingsRoute,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellCraftApp(
    viewModel: ShellCraftViewModel = viewModel()
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val forgeState by viewModel.forgeState.collectAsStateWithLifecycle()
    val onDeviceStatus by viewModel.onDeviceStatus.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val llmConfig by viewModel.llmConfig.collectAsStateWithLifecycle()
    val githubConfig by viewModel.githubConfig.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val pendingExecution by viewModel.pendingExecution.collectAsStateWithLifecycle()
    val terminalState by viewModel.terminalState.collectAsStateWithLifecycle()
    val sftpUploadStatus by viewModel.sftpUploadStatus.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val modelDiscoveryState by viewModel.modelDiscoveryState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var showHostEditor by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<SshHostProfile?>(null) }
    var showSchemaDocs by remember { mutableStateOf(false) }

    val isForgeBusy = forgeState is ForgeState.CheckingGitHub ||
        forgeState is ForgeState.ProbingSshDocs ||
        forgeState is ForgeState.AnalyzingWithLlm

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    ShellCraftTheme(
        themeMode = securitySettings.themeMode,
        disableHeavyAnimations = securitySettings.disableHeavyAnimations
    ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val isRunnerScreen = currentDestination?.hasRoute<PluginRunnerRoute>() == true

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!isRunnerScreen) {
                        CenterAlignedTopAppBar(
                            navigationIcon = {
                                val connected = connectionState as? SshConnectionState.Connected
                                Surface(
                                    color = if (connected != null) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(
                                        1.dp,
                                        if (connected != null) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            viewModel.haptics.tick()
                                            if (connected != null) viewModel.checkSshHealthNow()
                                            else navController.navigate(WorkspaceRoute)
                                        }
                                        .testTag("topbar_ssh_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (connectionState) {
                                                        is SshConnectionState.Connected -> MaterialTheme.colorScheme.secondary
                                                        is SshConnectionState.Error -> MaterialTheme.colorScheme.error
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                        )
                                        Text(
                                            text = when (val st = connectionState) {
                                                is SshConnectionState.Connected -> st.latencyMs?.let { "${it}ms" } ?: "SSH"
                                                is SshConnectionState.Connecting -> "..."
                                                is SshConnectionState.Error -> "Lost"
                                                is SshConnectionState.Disconnected -> "Idle"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                        )
                                    }
                                }
                            },
                            title = { Text("ShellCraft", style = MaterialTheme.typography.headlineSmall) },
                            actions = {
                                if (isForgeBusy) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { navController.navigate(ForgeRoute) }
                                            .testTag("topbar_forge_active_pill")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("AI", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (terminalState.command.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.reopenTerminal() },
                                        modifier = Modifier.testTag("topbar_terminal_status")
                                    ) {
                                        Icon(
                                            Icons.Filled.Terminal,
                                            contentDescription = "Open last execution output",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useNavRail && !isRunnerScreen) {
                        NavigationBar {
                            topLevelTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        viewModel.haptics.tick()
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail && !isRunnerScreen) {
                        NavigationRail {
                            topLevelTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        viewModel.haptics.tick()
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = WorkspaceRoute
                        ) {
                            composable<WorkspaceRoute> {
                                WorkspaceScreen(
                                    connectionState = connectionState,
                                    hosts = hosts,
                                    plugins = plugins,
                                    recentLogs = recentLogs,
                                    onAddHost = {
                                        editingHost = null
                                        showHostEditor = true
                                    },
                                    onEditHost = { host ->
                                        editingHost = host
                                        showHostEditor = true
                                    },
                                    onDeleteHost = { id -> viewModel.deleteHost(id) },
                                    onConnectHost = { host -> viewModel.connectHost(host) },
                                    onDisconnectHost = { viewModel.disconnectHost() },
                                    onCheckHealthNow = { viewModel.checkSshHealthNow() },
                                    onReconnectLast = { viewModel.reconnectLastHost() },
                                    onOpenPlugin = { pluginId ->
                                        navController.navigate(PluginRunnerRoute(pluginId))
                                    },
                                    onOpenForge = {
                                        navController.navigate(ForgeRoute)
                                    },
                                    onClearLogs = { viewModel.clearLogs() },
                                    onHapticTick = { viewModel.haptics.tick() }
                                )
                            }

                            composable<PluginsRoute> {
                                PluginsScreen(
                                    plugins = plugins,
                                    githubConfig = githubConfig,
                                    syncStatus = syncStatus,
                                    onSyncNow = { viewModel.syncGitHubPlugins(silent = false) },
                                    onOpenPlugin = { pluginId ->
                                        navController.navigate(PluginRunnerRoute(pluginId))
                                    },
                                    onOpenSchemaDocs = { showSchemaDocs = true }
                                )
                            }

                            composable<PluginRunnerRoute> { entry ->
                                val route = entry.toRoute<PluginRunnerRoute>()
                                val plugin = plugins.find { it.id == route.pluginId }
                                if (plugin != null) {
                                    PluginRunnerScreen(
                                        plugin = plugin,
                                        githubConfig = githubConfig,
                                        isSshConnected = connectionState is SshConnectionState.Connected,
                                        terminalState = terminalState,
                                        sftpUploadStatus = sftpUploadStatus,
                                        onBack = { navController.popBackStack() },
                                        onExecute = { cmd, action, elevateSudo ->
                                            viewModel.requestCommandExecution(
                                                pluginId = plugin.id,
                                                pluginName = plugin.displayName,
                                                command = cmd,
                                                action = action,
                                                elevateWithSudo = elevateSudo
                                            )
                                        },
                                        onSendInput = { text, isSecret -> viewModel.sendTerminalInput(text, isSecret) },
                                        onSendControl = { byte, label -> viewModel.sendTerminalControl(byte, label) },
                                        onExpandTerminal = { viewModel.reopenTerminal() },
                                        onListRemoteDirectory = { path -> viewModel.listRemoteDirectory(path) },
                                        onUploadPhoneFile = { uri -> viewModel.uploadPhoneFileToRemote(uri) },
                                        onRequestAiRefinement = { currentPlugin, history, instruction ->
                                            viewModel.refinePluginWithChat(currentPlugin, history, instruction)
                                        },
                                        onSaveRefinedPlugin = { updated ->
                                            viewModel.savePlugin(updated, pushToGitHubIfEnabled = false)
                                        },
                                        onResetBuiltinPlugin = { id -> viewModel.resetBuiltinPlugin(id) },
                                        onPublishToGitHub = { viewModel.publishPluginToGitHub(it) },
                                        onExportJson = { viewModel.exportPluginJson(it) },
                                        onDeletePlugin = { viewModel.deletePlugin(it) },
                                        onNotify = { viewModel.showMessage(it) },
                                        onHapticTick = { viewModel.haptics.tick() }
                                    )
                                }
                            }

                            composable<ForgeRoute> {
                                ForgeScreen(
                                    forgeState = forgeState,
                                    llmConfig = llmConfig,
                                    connectionState = connectionState,
                                    defaultGuiStyle = securitySettings.defaultForgeGuiStyle,
                                    onDeviceStatus = onDeviceStatus,
                                    onAnalyze = { cmd, mode, docs, bypass, guiStyle, customInstr ->
                                        viewModel.startForgeAnalysis(cmd, mode, docs, bypass, guiStyle, customInstr)
                                    },
                                    onCancelAnalysis = { viewModel.cancelForgeAnalysis() },
                                    onSaveAndOpenPlugin = { plugin ->
                                        viewModel.savePlugin(plugin)
                                        viewModel.resetForge()
                                        navController.navigate(PluginRunnerRoute(plugin.id))
                                    },
                                    onUpdateReadyPlugin = { updated -> viewModel.updateReadyForgePlugin(updated) },
                                    onRequestAiRefinement = { current, history, instruction, rawDocs ->
                                        viewModel.refinePluginWithChat(current, history, instruction, rawDocs)
                                    },
                                    onResetForge = { viewModel.resetForge() }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    llmConfig = llmConfig,
                                    githubConfig = githubConfig,
                                    securitySettings = securitySettings,
                                    onDeviceStatus = onDeviceStatus,
                                    onRefreshOnDeviceStatus = { viewModel.refreshOnDeviceStatus() },
                                    onDownloadOnDeviceModel = { viewModel.downloadOnDeviceModel() },
                                    onSaveLlmConfig = { p, preset, gm, url, cm, key, to, fbEn, fbProv, cond, temp ->
                                        viewModel.saveLlmBackend(p, preset, gm, url, cm, key, to, fbEn, fbProv, cond, temp)
                                    },
                                    onTestLlmConfig = { cfg -> viewModel.testAndDiscoverModels(cfg) },
                                    modelDiscoveryState = modelDiscoveryState,
                                    onClearModelDiscovery = { viewModel.clearModelDiscovery() },
                                    onSaveGitHubConfig = { en, repo, br, dir, pat, pol ->
                                        viewModel.saveGitHubSettings(en, repo, br, dir, pat, pol)
                                    },
                                    onSyncNow = { viewModel.syncGitHubPlugins(silent = false) },
                                    onUpdateSecurity = { viewModel.updateSecuritySettings(it) },
                                    onOpenSchemaDocs = { showSchemaDocs = true }
                                )
                            }
                        }
                    }
                }
            }

            if (showHostEditor) {
                HostEditorDialog(
                    initialHost = editingHost,
                    onSave = { label, host, port, user, authType, pw, key, pass, os ->
                        viewModel.saveHost(
                            existingId = editingHost?.id,
                            label = label,
                            host = host,
                            port = port,
                            username = user,
                            authType = authType,
                            plainPassword = pw,
                            plainPrivateKey = key,
                            plainPassphrase = pass,
                            targetOsHint = os,
                            existingHost = editingHost
                        )
                        showHostEditor = false
                    },
                    onDismiss = { showHostEditor = false }
                )
            }

            pendingExecution?.let { pending ->
                SafetyAndSudoDialog(
                    pending = pending,
                    requireBiometricForSudo = securitySettings.requireBiometricForSudo,
                    onBiometricVerified = { viewModel.markBiometricVerified() },
                    onConfirm = { sudoPw -> viewModel.confirmPendingExecution(sudoPw) },
                    onDismiss = { viewModel.cancelPendingExecution() }
                )
            }

            if (terminalState.isVisible) {
                LiveTerminalSheet(
                    state = terminalState,
                    onSendInput = { text, isSecret -> viewModel.sendTerminalInput(text, isSecret) },
                    onSendControl = { byte, label -> viewModel.sendTerminalControl(byte, label) },
                    onCopyOutput = { out ->
                        clipboard.setText(AnnotatedString(out))
                        viewModel.showMessage("Copied terminal output")
                    },
                    onStop = { viewModel.cancelRunningExecution() },
                    onDismiss = { viewModel.dismissTerminal() }
                )
            }

            if (showSchemaDocs) {
                PluginSchemaDocsSheet(
                    onImportJson = { raw -> viewModel.importPluginJson(raw) },
                    onCopySpec = { spec ->
                        clipboard.setText(AnnotatedString(spec))
                        viewModel.showMessage("Copied plugin architecture spec")
                    },
                    onDismiss = { showSchemaDocs = false }
                )
            }
        }
    }
}
