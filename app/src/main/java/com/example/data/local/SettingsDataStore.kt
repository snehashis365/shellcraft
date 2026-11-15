package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.security.SecretCipher
import com.example.domain.model.GitHubRepoConfig
import com.example.domain.model.GitHubSyncPolicy
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.LlmProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shellcraft_prefs")

data class SecuritySettings(
    val requireBiometricForSudo: Boolean = true,
    val confirmModerateCommands: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val darkTheme: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val ENCRYPTED_LLM_KEY = stringPreferencesKey("encrypted_llm_key")
        val LLM_TIMEOUT_SEC = intPreferencesKey("llm_timeout_sec")

        val GH_ENABLED = booleanPreferencesKey("gh_enabled")
        val GH_OWNER_REPO = stringPreferencesKey("gh_owner_repo")
        val GH_BRANCH = stringPreferencesKey("gh_branch")
        val GH_DIR = stringPreferencesKey("gh_dir")
        val ENCRYPTED_GH_PAT = stringPreferencesKey("encrypted_gh_pat")
        val GH_SYNC_POLICY = stringPreferencesKey("gh_sync_policy")
        val GH_LAST_SYNCED_MS = longPreferencesKey("gh_last_synced_ms")

        val BIOMETRIC_SUDO = booleanPreferencesKey("biometric_sudo")
        val CONFIRM_MODERATE = booleanPreferencesKey("confirm_moderate")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val llmConfigFlow: Flow<LlmBackendConfig> = context.dataStore.data.map { prefs ->
        LlmBackendConfig(
            providerType = runCatching {
                LlmProviderType.valueOf(prefs[Keys.LLM_PROVIDER] ?: LlmProviderType.FIREBASE_GEMINI.name)
            }.getOrDefault(LlmProviderType.FIREBASE_GEMINI),
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-3.8-flash",
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT] ?: "http://192.168.1.100:11434",
            customModelName = prefs[Keys.CUSTOM_MODEL] ?: "qwen2.5-coder:7b",
            encryptedApiKey = prefs[Keys.ENCRYPTED_LLM_KEY] ?: "",
            timeoutSeconds = prefs[Keys.LLM_TIMEOUT_SEC] ?: 60
        )
    }

    val githubConfigFlow: Flow<GitHubRepoConfig> = context.dataStore.data.map { prefs ->
        GitHubRepoConfig(
            enabled = prefs[Keys.GH_ENABLED] ?: false,
            ownerRepo = prefs[Keys.GH_OWNER_REPO] ?: "",
            branch = prefs[Keys.GH_BRANCH] ?: "main",
            pluginsDirectory = prefs[Keys.GH_DIR] ?: "plugins",
            encryptedPat = prefs[Keys.ENCRYPTED_GH_PAT] ?: "",
            syncPolicy = runCatching {
                GitHubSyncPolicy.valueOf(prefs[Keys.GH_SYNC_POLICY] ?: GitHubSyncPolicy.AUTOMATIC.name)
            }.getOrDefault(GitHubSyncPolicy.AUTOMATIC),
            lastSyncedAtMs = prefs[Keys.GH_LAST_SYNCED_MS]
        )
    }

    val securitySettingsFlow: Flow<SecuritySettings> = context.dataStore.data.map { prefs ->
        SecuritySettings(
            requireBiometricForSudo = prefs[Keys.BIOMETRIC_SUDO] ?: true,
            confirmModerateCommands = prefs[Keys.CONFIRM_MODERATE] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun saveLlmConfig(
        providerType: LlmProviderType,
        geminiModel: String,
        customEndpointUrl: String,
        customModelName: String,
        plainApiKey: String?,
        timeoutSeconds: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LLM_PROVIDER] = providerType.name
            prefs[Keys.GEMINI_MODEL] = geminiModel.ifBlank { "gemini-3.8-flash" }
            prefs[Keys.CUSTOM_ENDPOINT] = customEndpointUrl.trim().trimEnd('/')
            prefs[Keys.CUSTOM_MODEL] = customModelName.trim()
            if (plainApiKey != null) {
                prefs[Keys.ENCRYPTED_LLM_KEY] = if (plainApiKey.isBlank()) "" else SecretCipher.encrypt(plainApiKey)
            }
            prefs[Keys.LLM_TIMEOUT_SEC] = timeoutSeconds.coerceIn(15, 180)
        }
    }

    suspend fun saveGitHubConfig(
        enabled: Boolean,
        ownerRepo: String,
        branch: String,
        pluginsDirectory: String,
        plainPat: String?,
        syncPolicy: GitHubSyncPolicy
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GH_ENABLED] = enabled
            prefs[Keys.GH_OWNER_REPO] = ownerRepo.trim()
            prefs[Keys.GH_BRANCH] = branch.trim().ifBlank { "main" }
            prefs[Keys.GH_DIR] = pluginsDirectory.trim().trim('/')
            if (plainPat != null) {
                prefs[Keys.ENCRYPTED_GH_PAT] = if (plainPat.isBlank()) "" else SecretCipher.encrypt(plainPat)
            }
            prefs[Keys.GH_SYNC_POLICY] = syncPolicy.name
        }
    }

    suspend fun updateLastSynced(timestampMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GH_LAST_SYNCED_MS] = timestampMs
        }
    }

    suspend fun saveSecuritySettings(settings: SecuritySettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_SUDO] = settings.requireBiometricForSudo
            prefs[Keys.CONFIRM_MODERATE] = settings.confirmModerateCommands
            prefs[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
            prefs[Keys.DARK_THEME] = settings.darkTheme
        }
    }
}
