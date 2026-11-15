package com.snehashis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.RiskLevel
import com.snehashis.ui.theme.JetBrainsMonoFontFamily

@Composable
fun riskColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.SAFE -> MaterialTheme.colorScheme.secondary
    RiskLevel.MODERATE -> MaterialTheme.colorScheme.tertiary
    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
    RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
}

@Composable
fun riskContainerColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.SAFE -> MaterialTheme.colorScheme.secondaryContainer
    RiskLevel.MODERATE -> MaterialTheme.colorScheme.tertiaryContainer
    RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
    RiskLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun RiskBadge(
    risk: RiskLevel,
    requiresSudo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val fg by animateColorAsState(
        targetValue = riskColor(risk),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "riskFg"
    )
    val bg by animateColorAsState(
        targetValue = riskContainerColor(risk),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "riskBg"
    )
    val icon = when (risk) {
        RiskLevel.SAFE -> Icons.Filled.VerifiedUser
        RiskLevel.MODERATE -> Icons.Filled.Shield
        RiskLevel.HIGH, RiskLevel.CRITICAL -> Icons.Filled.Warning
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = bg,
            contentColor = fg,
            shape = MaterialTheme.shapes.extraSmall,
            border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
                Text(
                    text = risk.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (requiresSudo) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.tertiary,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                    Text(
                        text = "SUDO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SourceChip(source: PluginSource, modifier: Modifier = Modifier) {
    val (label, icon, tint) = when (source) {
        PluginSource.BUILT_IN -> Triple("CORE", Icons.Filled.Terminal, MaterialTheme.colorScheme.primary)
        PluginSource.AI_GENERATED -> Triple("AI FORGED", Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.secondary)
        PluginSource.GITHUB_SYNC -> Triple("GITHUB", Icons.Filled.CloudSync, MaterialTheme.colorScheme.tertiary)
        PluginSource.USER_IMPORTED -> Triple("IMPORTED", Icons.Filled.DownloadDone, MaterialTheme.colorScheme.primary)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = tint,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CommandPreviewBox(
    command: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ $command",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.testTag("copy_command_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy resolved POSIX command",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
