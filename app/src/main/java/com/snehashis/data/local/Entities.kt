package com.snehashis.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snehashis.domain.model.PluginSource
import com.snehashis.domain.model.SshAuthType
import com.snehashis.domain.model.SshHostProfile
import com.snehashis.domain.model.TargetOs

@Entity(tableName = "ssh_hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val encryptedPassword: String,
    val encryptedPrivateKey: String,
    val encryptedKeyPassphrase: String,
    val targetOsHint: String,
    val lastConnectedAtMs: Long?
) {
    fun toDomain(): SshHostProfile = SshHostProfile(
        id = id,
        label = label,
        host = host,
        port = port,
        username = username,
        authType = runCatching { SshAuthType.valueOf(authType) }.getOrDefault(SshAuthType.PASSWORD),
        encryptedPassword = encryptedPassword,
        encryptedPrivateKey = encryptedPrivateKey,
        encryptedKeyPassphrase = encryptedKeyPassphrase,
        targetOsHint = runCatching { TargetOs.valueOf(targetOsHint) }.getOrDefault(TargetOs.POSIX),
        lastConnectedAtMs = lastConnectedAtMs
    )

    companion object {
        fun fromDomain(profile: SshHostProfile): HostEntity = HostEntity(
            id = profile.id,
            label = profile.label,
            host = profile.host,
            port = profile.port,
            username = profile.username,
            authType = profile.authType.name,
            encryptedPassword = profile.encryptedPassword,
            encryptedPrivateKey = profile.encryptedPrivateKey,
            encryptedKeyPassphrase = profile.encryptedKeyPassphrase,
            targetOsHint = profile.targetOsHint.name,
            lastConnectedAtMs = profile.lastConnectedAtMs
        )
    }
}

@Entity(tableName = "cli_plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val commandName: String,
    val displayName: String,
    val category: String,
    val source: String,
    val updatedAtEpochMs: Long,
    val pluginJson: String
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostLabel: String,
    val pluginName: String,
    val command: String,
    val exitCode: Int,
    val outputPreview: String,
    val elevatedWithSudo: Boolean,
    val durationMs: Long,
    val timestampMs: Long = System.currentTimeMillis()
)
