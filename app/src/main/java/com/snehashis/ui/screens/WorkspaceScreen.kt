package com.snehashis.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.R
import com.snehashis.data.local.ExecutionLogEntity
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.domain.model.SshHostProfile
import com.snehashis.ui.components.GlowSurfaceCard
import com.snehashis.ui.components.SectionHeader
import com.snehashis.ui.components.springPressScale
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun WorkspaceScreen(
    connectionState: SshConnectionState,
    hosts: List<SshHostProfile>,
    plugins: List<CliPlugin>,
    recentLogs: List<ExecutionLogEntity>,
    onAddHost: () -> Unit,
    onEditHost: (SshHostProfile) -> Unit,
    onDeleteHost: (String) -> Unit,
    onConnectHost: (SshHostProfile) -> Unit,
    onDisconnectHost: () -> Unit,
    onCheckHealthNow: () -> Unit,
    onReconnectLast: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    onOpenForge: () -> Unit,
    onClearLogs: () -> Unit,
    onHapticTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConnectionHeroCard(
                    connectionState = connectionState,
                    hasAnyHost = hosts.isNotEmpty(),
                    onDisconnect = onDisconnectHost,
                    onCheckHealthNow = onCheckHealthNow,
                    onReconnectLast = onReconnectLast,
                    onOpenForge = onOpenForge
                )
            }

            item {
                SectionHeader(
                    title = "SSH Endpoints",
                    subtitle = "POSIX-compliant Linux, macOS, and Windows OpenSSH targets",
                    trailing = {
                        FilledTonalButton(
                            onClick = {
                                onHapticTick()
                                onAddHost()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_host_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Host")
                        }
                    }
                )
            }

            if (hosts.isEmpty()) {
                item {
                    EmptyHostsCard(onAddHost = onAddHost)
                }
            } else {
                items(hosts, key = { it.id }) { host ->
                    val isThisConnected = connectionState is SshConnectionState.Connected &&
                        connectionState.host.id == host.id
                    val isThisConnecting = connectionState is SshConnectionState.Connecting &&
                        connectionState.hostLabel == host.label
                    HostProfileCard(
                        host = host,
                        isConnected = isThisConnected,
                        isConnecting = isThisConnecting,
                        onConnect = { onConnectHost(host) },
                        onDisconnect = onDisconnectHost,
                        onEdit = { onEditHost(host) },
                        onDelete = { onDeleteHost(host.id) }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Quick GUI Launch",
                    subtitle = "Tap any plugin to render its interactive control surface"
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    plugins.take(8).forEach { plugin ->
                        QuickPluginCard(
                            plugin = plugin,
                            onHapticTick = onHapticTick,
                            onClick = { onOpenPlugin(plugin.id) }
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Execution Audit Trail",
                    subtitle = "Recent SSH invocations and exit codes",
                    trailing = {
                        if (recentLogs.isNotEmpty()) {
                            TextButton(onClick = onClearLogs) {
                                Text("Clear")
                            }
                        }
                    }
                )
            }

            if (recentLogs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No commands executed yet. Connect to an SSH host and launch a plugin.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentLogs.take(10), key = { it.id }) { log ->
                    ExecutionLogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeroCard(
    connectionState: SshConnectionState,
    hasAnyHost: Boolean,
    onDisconnect: () -> Unit,
    onCheckHealthNow: () -> Unit,
    onReconnectLast: () -> Unit,
    onOpenForge: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "sshPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val accentColor = when (connectionState) {
        is SshConnectionState.Connected -> MaterialTheme.colorScheme.secondary
        is SshConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        is SshConnectionState.Error -> MaterialTheme.colorScheme.error
        is SshConnectionState.Disconnected -> MaterialTheme.colorScheme.primary
    }

    GlowSurfaceCard(
        accentColor = accentColor,
        isPulsing = connectionState is SshConnectionState.Connected || connectionState is SshConnectionState.Connecting
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .alpha(if (connectionState is SshConnectionState.Connected) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = when (connectionState) {
                            is SshConnectionState.Connected -> "SSH ACTIVE · KEEP-ALIVE 15s"
                            is SshConnectionState.Connecting -> "HANDSHAKING ${connectionState.hostLabel}..."
                            is SshConnectionState.Error -> "TUNNEL INTERRUPTED"
                            is SshConnectionState.Disconnected -> "STANDBY · READY TO CONNECT"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                if (connectionState is SshConnectionState.Connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onCheckHealthNow,
                            modifier = Modifier.testTag("check_ssh_health_button")
                        ) {
                            Icon(
                                Icons.Filled.MonitorHeart,
                                contentDescription = "Verify SSH heartbeat now",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.heightIn(min = 40.dp).testTag("disconnect_ssh_button")
                        ) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect")
                        }
                    }
                }
            }

            when (connectionState) {
                is SshConnectionState.Connected -> {
                    Text(
                        text = "${connectionState.host.username}@${connectionState.host.host}:${connectionState.host.port}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CapabilityPill(
                            text = "${connectionState.env.osName} ${connectionState.env.architecture}".trim(),
                            icon = Icons.Filled.Terminal
                        )
                        CapabilityPill(
                            text = connectionState.env.shell.substringAfterLast('/')
                        )
                        connectionState.latencyMs?.let { rtt ->
                            CapabilityPill(
                                text = "${rtt}ms RTT",
                                icon = Icons.Filled.MonitorHeart
                            )
                        }
                        if (connectionState.env.hasSudo) {
                            CapabilityPill(
                                text = "sudo ready",
                                icon = Icons.Filled.Lock
                            )
                        }
                    }
                    Button(
                        onClick = onOpenForge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hero_forge_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Remote CLI Tool with AI")
                    }
                }
                is SshConnectionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Negotiating SSH keys and probing POSIX capabilities...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SshConnectionState.Error -> {
                    Text(
                        text = connectionState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (hasAnyHost) {
                        Button(
                            onClick = onReconnectLast,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reconnect_ssh_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reconnect SSH Tunnel")
                        }
                    }
                }
                is SshConnectionState.Disconnected -> {
                    Text(
                        text = "Connect to any Linux, macOS, or Windows OpenSSH host to probe `man` pages and run GUI-driven commands with automatic 15s keepalive monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHostsCard(onAddHost: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_ssh),
                contentDescription = "SSH remote host connection illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = "No SSH Endpoints Configured",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Add a remote server, Raspberry Pi, cloud VM, or local WSL/macOS machine. Credentials stay encrypted with Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddHost,
                modifier = Modifier.heightIn(min = 48.dp).testTag("empty_add_host_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add First SSH Host")
            }
        }
    }
}

@Composable
private fun HostProfileCard(
    host: SshHostProfile,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth().testTag("host_card_${host.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.WifiTethering,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = host.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${host.username}@${host.host}:${host.port} · ${host.targetOsHint.name}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_host_${host.id}")) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit host", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_host_${host.id}")) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete host",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = if (isConnected) onDisconnect else onConnect,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("connect_host_${host.id}")
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.LinkOff else Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isConnected) "Active" else if (isConnecting) "..." else "Connect")
                }
            }
        }
    }
}

@Composable
private fun QuickPluginCard(
    plugin: CliPlugin,
    onHapticTick: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .width(178.dp)
            .clip(MaterialTheme.shapes.medium)
            .springPressScale(onHapticTick = onHapticTick, onClick = onClick)
            .testTag("quick_plugin_${plugin.commandName}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = plugin.commandName,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = plugin.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${plugin.actions.size} interactive actions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExecutionLogCard(log: ExecutionLogEntity) {
    val ok = log.exitCode == 0
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${log.pluginName} · ${log.hostLabel}",
                    style = MaterialTheme.typography.labelLarge
                )
                Surface(
                    color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "EXIT ${log.exitCode} · ${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "$ ${log.command}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CapabilityPill(
    text: String,
    icon: ImageVector? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
