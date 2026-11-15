package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GitHubSyncPolicy {
    AUTOMATIC,
    INTERVAL_HOURLY,
    INTERVAL_DAILY,
    ON_SSH_CONNECT,
    MANUAL_ONLY
}

data class GitHubRepoConfig(
    val enabled: Boolean = false,
    val ownerRepo: String = "",
    val branch: String = "main",
    val pluginsDirectory: String = "plugins",
    val encryptedPat: String = "",
    val syncPolicy: GitHubSyncPolicy = GitHubSyncPolicy.AUTOMATIC,
    val lastSyncedAtMs: Long? = null
) {
    val isConfigured: Boolean
        get() = enabled && ownerRepo.contains("/") && ownerRepo.substringBefore("/").isNotBlank() &&
            ownerRepo.substringAfter("/").isNotBlank()
}

sealed interface SyncStatus {
    data object LocalOnly : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Success(val syncedCount: Int, val timestampMs: Long) : SyncStatus
    data class Error(val message: String) : SyncStatus
}
