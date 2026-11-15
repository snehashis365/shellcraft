package com.snehashis.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthType {
    PASSWORD,
    PRIVATE_KEY
}

data class SshHostProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val encryptedPassword: String = "",
    val encryptedPrivateKey: String = "",
    val encryptedKeyPassphrase: String = "",
    val targetOsHint: TargetOs = TargetOs.POSIX,
    val lastConnectedAtMs: Long? = null
)

data class RemoteEnvironmentInfo(
    val osName: String = "Unknown",
    val architecture: String = "",
    val shell: String = "/bin/sh",
    val currentUser: String = "",
    val hasSudo: Boolean = false
)

sealed interface SshConnectionState {
    data object Disconnected : SshConnectionState
    data class Connecting(val hostLabel: String) : SshConnectionState
    data class Connected(
        val host: SshHostProfile,
        val env: RemoteEnvironmentInfo,
        val latencyMs: Long? = null,
        val lastHeartbeatAtMs: Long = System.currentTimeMillis(),
        val isHealthy: Boolean = true
    ) : SshConnectionState
    data class Error(val message: String) : SshConnectionState
}

data class SshExecutionResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val elevatedWithSudo: Boolean
)
