package com.snehashis.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.domain.model.FormattedOutput
import com.snehashis.domain.model.LogSeverity
import com.snehashis.domain.model.OutputFormatter
import com.snehashis.domain.model.OutputRowAction
import com.snehashis.domain.model.RiskLevel
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun RichCommandOutputView(
    command: String,
    rawOutput: String,
    exitCode: Int?,
    durationMs: Long?,
    onCopyRaw: () -> Unit,
    rowActions: List<OutputRowAction> = emptyList(),
    onInvokeRowAction: ((OutputRowAction, Map<String, String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRawTerminal by remember(rawOutput) { mutableStateOf(false) }
    val parsed = remember(command, rawOutput) { OutputFormatter.parse(command, rawOutput) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !showRawTerminal,
                    onClick = { showRawTerminal = false },
                    label = { Text("Formatted GUI") },
                    leadingIcon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = showRawTerminal,
                    onClick = { showRawTerminal = true },
                    label = { Text("Raw Shell") },
                    leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (exitCode != null) {
                    val ok = exitCode == 0
                    Surface(
                        color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "EXIT $exitCode",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
                if (durationMs != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onCopyRaw) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy raw output", modifier = Modifier.size(18.dp))
                }
            }
        }

        AnimatedContent(
            targetState = showRawTerminal,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "outputViewSwitch"
        ) { rawMode ->
            if (rawMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "$ $command",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rawOutput,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            } else {
                when (parsed) {
                    is FormattedOutput.PingReport -> PingReportCard(parsed)
                    is FormattedOutput.SystemdStatusReport -> SystemdStatusCard(
                        report = parsed,
                        rowActions = rowActions,
                        onInvokeRowAction = onInvokeRowAction
                    )
                    is FormattedOutput.TableReport -> TableReportCards(
                        report = parsed,
                        rowActions = rowActions,
                        onInvokeRowAction = onInvokeRowAction
                    )
                    is FormattedOutput.StructuredJsonReport -> JsonReportCard(parsed)
                    is FormattedOutput.KeyValueReport -> KeyValueReportCard(parsed)
                    is FormattedOutput.LogLinesReport -> LogLinesCard(parsed)
                }
            }
        }
    }
}

@Composable
private fun PingReportCard(report: FormattedOutput.PingReport) {
    val avgFloat = Regex("""([\d.]+)""").find(report.avgMs)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val lossFloat = Regex("""([\d.]+)""").find(report.lossPercent)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.NetworkPing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("ICMP Reachability · ${report.host}", style = MaterialTheme.typography.titleSmall)
                    }
                    Surface(
                        color = if (lossFloat == 0f) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (lossFloat == 0f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (lossFloat == 0f) "HEALTHY" else "${report.lossPercent} LOSS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Packets", "${report.received}/${report.transmitted}", Modifier.weight(1f))
                    MetricTile("Loss", report.lossPercent, Modifier.weight(1f))
                    MetricTile("Avg RTT", report.avgMs, Modifier.weight(1f))
                }
                if (avgFloat != null) {
                    val qualityFraction = (1f - (avgFloat / 250f)).coerceIn(0.12f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Latency Quality Index",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { qualityFraction },
                            color = if (avgFloat < 80f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth().height(7.dp)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Min RTT", report.minMs, Modifier.weight(1f))
                    MetricTile("Max RTT", report.maxMs, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SystemdStatusCard(
    report: FormattedOutput.SystemdStatusReport,
    rowActions: List<OutputRowAction>,
    onInvokeRowAction: ((OutputRowAction, Map<String, String>) -> Unit)?
) {
    val accent = if (report.isHealthy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val cleanUnit = report.unit.trim().substringBefore(" ")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(report.unit, style = MaterialTheme.typography.titleMedium)
                Surface(
                    color = if (report.isHealthy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = accent,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = report.activeState.substringBefore(";"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Main PID", report.mainPid.substringBefore(" "), Modifier.weight(1f))
                MetricTile("Memory", report.memory, Modifier.weight(1f))
                MetricTile("Tasks", report.tasks.substringBefore(" "), Modifier.weight(1f))
            }
            Text(
                text = "Loaded: ${report.loaded}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (rowActions.isNotEmpty() && onInvokeRowAction != null && cleanUnit.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowActions.forEach { ra ->
                        RowActionButton(
                            rowAction = ra,
                            testTag = "systemd_row_action_${ra.id}",
                            onClick = {
                                val tokens = mapOf(
                                    "unit" to cleanUnit,
                                    "col:0" to cleanUnit,
                                    "pid" to report.mainPid.substringBefore(" ")
                                )
                                onInvokeRowAction(ra, tokens)
                            }
                        )
                    }
                }
            }

            if (report.recentLogs.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        report.recentLogs.take(6).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableReportCards(
    report: FormattedOutput.TableReport,
    rowActions: List<OutputRowAction>,
    onInvokeRowAction: ((OutputRowAction, Map<String, String>) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${report.rows.size} item(s) · ${report.headers.joinToString(" / ")}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        report.rows.take(25).forEachIndexed { rowIndex, row ->
            val primaryTitle = row.firstOrNull().orEmpty()
            val statusCell = row.find { cell ->
                cell.contains("up", true) || cell.contains("running", true) ||
                    cell.contains("active", true) || cell.contains("exited", true) ||
                    cell.contains("failed", true) || cell.contains("listen", true)
            }
            val pctMatch = row.firstNotNullOfOrNull { cell ->
                Regex("""(\d{1,3})%""").find(cell)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }

            val rowTokens = remember(report.headers, row) {
                buildMap {
                    row.forEachIndexed { idx, value ->
                        put("col:$idx", value.trim())
                        report.headers.getOrNull(idx)?.let { hdr ->
                            put("col:${hdr.trim()}", value.trim())
                        }
                    }
                    put("unit", primaryTitle.trim())
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = primaryTitle.ifBlank { "Entry" },
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (statusCell != null) {
                            val healthy = statusCell.contains("up", true) ||
                                statusCell.contains("running", true) ||
                                statusCell.contains("active", true) ||
                                statusCell.contains("listen", true)
                            Surface(
                                color = if (healthy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (healthy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = statusCell,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (pctMatch != null) {
                        LinearProgressIndicator(
                            progress = { (pctMatch / 100f).coerceIn(0f, 1f) },
                            color = if (pctMatch >= 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        report.headers.drop(1).forEachIndexed { idx, header ->
                            val value = row.getOrNull(idx + 1).orEmpty()
                            if (value.isNotBlank() && value != statusCell) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (rowActions.isNotEmpty() && onInvokeRowAction != null && primaryTitle.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowActions.forEach { ra ->
                                RowActionButton(
                                    rowAction = ra,
                                    testTag = "row_action_${ra.id}_$rowIndex",
                                    onClick = { onInvokeRowAction(ra, rowTokens) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActionButton(
    rowAction: OutputRowAction,
    testTag: String,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (rowAction.iconName.lowercase()) {
        "refresh", "restart" -> Icons.Filled.Refresh
        "stop", "kill" -> Icons.Filled.Stop
        "description", "logs" -> Icons.Filled.Description
        "info", "inspect" -> Icons.Filled.Info
        else -> Icons.Filled.PlayArrow
    }
    val tint = when {
        rowAction.riskLevel >= RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        rowAction.riskLevel == RiskLevel.MODERATE || rowAction.requiresSudo -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.65f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        modifier = Modifier.heightIn(min = 48.dp).testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = rowAction.label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun JsonReportCard(report: FormattedOutput.StructuredJsonReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (report.summaryPairs.isNotEmpty()) {
            KeyValueReportCard(FormattedOutput.KeyValueReport(report.summaryPairs))
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = report.prettyJson,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun KeyValueReportCard(report: FormattedOutput.KeyValueReport) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.entries.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.9f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLinesCard(report: FormattedOutput.LogLinesReport) {
    var filterSeverity by remember { mutableStateOf<LogSeverity?>(null) }
    val filtered = remember(report, filterSeverity) {
        if (filterSeverity == null) report.entries else report.entries.filter { it.severity == filterSeverity }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasErrors = report.entries.any { it.severity == LogSeverity.ERROR }
        val hasWarnings = report.entries.any { it.severity == LogSeverity.WARNING }
        if (hasErrors || hasWarnings) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterSeverity == null,
                    onClick = { filterSeverity = null },
                    label = { Text("All (${report.entries.size})") }
                )
                if (hasErrors) {
                    FilterChip(
                        selected = filterSeverity == LogSeverity.ERROR,
                        onClick = { filterSeverity = LogSeverity.ERROR },
                        label = { Text("Errors") }
                    )
                }
                if (hasWarnings) {
                    FilterChip(
                        selected = filterSeverity == LogSeverity.WARNING,
                        onClick = { filterSeverity = LogSeverity.WARNING },
                        label = { Text("Warnings") }
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filtered.take(120).forEach { entry ->
                    val (icon, color) = when (entry.severity) {
                        LogSeverity.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                        LogSeverity.WARNING -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
                        LogSeverity.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
                        LogSeverity.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (entry.severity != LogSeverity.INFO) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.padding(top = 2.dp).size(14.dp)
                            )
                        }
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = if (entry.severity == LogSeverity.INFO) {
                                    MaterialTheme.colorScheme.onSurface
                                } else color
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
