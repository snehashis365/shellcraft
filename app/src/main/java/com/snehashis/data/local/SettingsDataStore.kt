@file:Suppress("DEPRECATION")

package com.snehashis.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.snehashis.core.security.SecretCipher
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.GitHubSyncPolicy
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.PresetCredentialSlot
import com.snehashis.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shellcraft_prefs")

data class SecuritySettings(
    val requireBiometricForSudo: Boolean = true,
    val confirmModerateCommands: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val darkTheme: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.NEON_DARK,
    val disableHeavyAnimations: Boolean = false,
    val defaultForgeGuiStyle: PluginGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD
)

object LlmSettingsMigration {
    fun migrateLegacyProvider(
        rawProvider: String?,
        rawPreset: String?,
        rawUrl: String
    ): Triple<LlmProviderType, ByokProviderPreset, String> {
        val parsedProvider = runCatching {
            LlmProviderType.valueOf(rawProvider ?: LlmProviderType.FIREBASE_GEMINI.name)
        }.getOrDefault(LlmProviderType.FIREBASE_GEMINI)

        val parsedPreset = runCatching {
            ByokProviderPreset.valueOf(rawPreset ?: ByokProviderPreset.GEMINI_AI_STUDIO.name)
        }.getOrDefault(ByokProviderPreset.GEMINI_AI_STUDIO)

        return if (parsedProvider == LlmProviderType.OLLAMA_LOCAL) {
            Triple(
                LlmProviderType.OPENAI_COMPATIBLE,
                ByokProviderPreset.LOCAL_OLLAMA,
                normalizeOllamaV1Url(rawUrl)
            )
        } else {
            Triple(parsedProvider, parsedPreset, rawUrl.trim().trimEnd('/'))
        }
    }

    fun normalizeOllamaV1Url(rawUrl: String): String {
        val clean = rawUrl.trim().trimEnd('/')
        if (clean.isBlank()) return ByokProviderPreset.LOCAL_OLLAMA.defaultBaseUrl
        return if (clean.endsWith("/v1") || clean.endsWith("/openai")) clean else "$clean/v1"
    }
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val BYOK_PRESET = stringPreferencesKey("byok_preset")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val ENCRYPTED_LLM_KEY = stringPreferencesKey("encrypted_llm_key")
        val LLM_TIMEOUT_SEC = intPreferencesKey("llm_timeout_sec")
        val ON_DEVICE_FALLBACK_ENABLED = booleanPreferencesKey("on_device_fallback_enabled")
        val ON_DEVICE_FALLBACK_PROVIDER = stringPreferencesKey("on_device_fallback_provider")
        val ON_DEVICE_CONDENSE_DOCS = booleanPreferencesKey("on_device_condense_docs")
        val ON_DEVICE_TEMPERATURE = floatPreferencesKey("on_device_temperature")

        fun presetUrlKey(preset: ByokProviderPreset) = stringPreferencesKey("preset_url_${preset.name}")
        fun presetModelKey(preset: ByokProviderPreset) = stringPreferencesKey("preset_model_${preset.name}")
        fun presetEncryptedKey(preset: ByokProviderPreset) = stringPreferencesKey("preset_key_${preset.name}")

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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DISABLE_HEAVY_ANIMATIONS = booleanPreferencesKey("disable_heavy_animations")
        val DEFAULT_FORGE_GUI_STYLE = stringPreferencesKey("default_forge_gui_style")
    }

    val llmConfigFlow: Flow<LlmBackendConfig> = context.dataStore.data.map { prefs ->
        val rawUrl = prefs[Keys.CUSTOM_ENDPOINT] ?: ByokProviderPreset.LOCAL_OLLAMA.defaultBaseUrl
        val (provider, activePreset, normalizedLegacyUrl) = LlmSettingsMigration.migrateLegacyProvider(
            rawProvider = prefs[Keys.LLM_PROVIDER],
            rawPreset = prefs[Keys.BYOK_PRESET],
            rawUrl = rawUrl
        )
        val wasLegacyOllama = prefs[Keys.LLM_PROVIDER] == LlmProviderType.OLLAMA_LOCAL.name
        val legacyModel = prefs[Keys.CUSTOM_MODEL] ?: "qwen2.5-coder:7b"
        val legacyKey = prefs[Keys.ENCRYPTED_LLM_KEY] ?: ""

        val slots = ByokProviderPreset.entries.associateWith { preset ->
            val hasStoredUrl = prefs.contains(Keys.presetUrlKey(preset))
            val fallbackUrl = when {
                preset == ByokProviderPreset.LOCAL_OLLAMA && wasLegacyOllama && !hasStoredUrl ->
                    normalizedLegacyUrl
                preset == ByokProviderPreset.CUSTOM && !hasStoredUrl ->
                    rawUrl
                else -> preset.defaultBaseUrl
            }
            val fallbackModel = when {
                preset == ByokProviderPreset.LOCAL_OLLAMA && wasLegacyOllama && !prefs.contains(Keys.presetModelKey(preset)) ->
                    legacyModel
                preset == ByokProviderPreset.CUSTOM && !prefs.contains(Keys.presetModelKey(preset)) ->
                    legacyModel
                else -> preset.defaultModel
            }
            val fallbackKey = if ((preset == ByokProviderPreset.CUSTOM || (preset == ByokProviderPreset.LOCAL_OLLAMA && wasLegacyOllama)) &&
                !prefs.contains(Keys.presetEncryptedKey(preset))
            ) {
                legacyKey
            } else {
                ""
            }
            PresetCredentialSlot(
                endpointUrl = prefs[Keys.presetUrlKey(preset)] ?: fallbackUrl,
                modelName = prefs[Keys.presetModelKey(preset)] ?: fallbackModel,
                encryptedApiKey = prefs[Keys.presetEncryptedKey(preset)] ?: fallbackKey
            )
        }

        val resolvedUrl = if (provider == LlmProviderType.OPENAI_COMPATIBLE) {
            slots[activePreset]?.endpointUrl ?: normalizedLegacyUrl
        } else {
            normalizedLegacyUrl
        }
        val resolvedModel = if (provider == LlmProviderType.OPENAI_COMPATIBLE) {
            slots[activePreset]?.modelName ?: legacyModel
        } else {
            legacyModel
        }
        val resolvedKey = if (provider == LlmProviderType.OPENAI_COMPATIBLE) {
            slots[activePreset]?.encryptedApiKey ?: legacyKey
        } else {
            legacyKey
        }

        val rawFallbackProvider = runCatching {
            LlmProviderType.valueOf(
                prefs[Keys.ON_DEVICE_FALLBACK_PROVIDER] ?: LlmProviderType.FIREBASE_GEMINI.name
            )
        }.getOrDefault(LlmProviderType.FIREBASE_GEMINI)
        val migratedFallbackProvider = if (rawFallbackProvider == LlmProviderType.OLLAMA_LOCAL) {
            LlmProviderType.OPENAI_COMPATIBLE
        } else {
            rawFallbackProvider
        }

        LlmBackendConfig(
            providerType = provider,
            byokPreset = activePreset,
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-3.8-flash",
            customEndpointUrl = resolvedUrl,
            customModelName = resolvedModel,
            encryptedApiKey = resolvedKey,
            presetSlots = slots,
            timeoutSeconds = prefs[Keys.LLM_TIMEOUT_SEC] ?: 240,
            onDeviceFallbackEnabled = prefs[Keys.ON_DEVICE_FALLBACK_ENABLED] ?: false,
            onDeviceFallbackProvider = migratedFallbackProvider,
            onDeviceCondenseDocs = prefs[Keys.ON_DEVICE_CONDENSE_DOCS] ?: true,
            onDeviceTemperature = prefs[Keys.ON_DEVICE_TEMPERATURE] ?: 0.2f
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
        val legacyDark = prefs[Keys.DARK_THEME] ?: true
        val themeMode = runCatching {
            prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) }
        }.getOrNull() ?: if (legacyDark) ThemeMode.NEON_DARK else ThemeMode.DAYLIGHT_LIGHT

        val defaultGui = runCatching {
            prefs[Keys.DEFAULT_FORGE_GUI_STYLE]?.let { PluginGuiStyle.valueOf(it) }
        }.getOrNull() ?: PluginGuiStyle.MODULAR_DASHBOARD

        SecuritySettings(
            requireBiometricForSudo = prefs[Keys.BIOMETRIC_SUDO] ?: true,
            confirmModerateCommands = prefs[Keys.CONFIRM_MODERATE] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            darkTheme = themeMode != ThemeMode.DAYLIGHT_LIGHT,
            themeMode = themeMode,
            disableHeavyAnimations = prefs[Keys.DISABLE_HEAVY_ANIMATIONS] ?: false,
            defaultForgeGuiStyle = defaultGui
        )
    }

    suspend fun saveLlmConfig(
        providerType: LlmProviderType,
        byokPreset: ByokProviderPreset = ByokProviderPreset.CUSTOM,
        geminiModel: String,
        customEndpointUrl: String,
        customModelName: String,
        plainApiKey: String?,
        timeoutSeconds: Int,
        onDeviceFallbackEnabled: Boolean = false,
        onDeviceFallbackProvider: LlmProviderType = LlmProviderType.FIREBASE_GEMINI,
        onDeviceCondenseDocs: Boolean = true,
        onDeviceTemperature: Float = 0.2f
    ) {
        context.dataStore.edit { prefs ->
            val effectiveProvider = if (providerType == LlmProviderType.OLLAMA_LOCAL) {
                LlmProviderType.OPENAI_COMPATIBLE
            } else {
                providerType
            }
            val effectivePreset = if (providerType == LlmProviderType.OLLAMA_LOCAL) {
                ByokProviderPreset.LOCAL_OLLAMA
            } else {
                byokPreset
            }
            prefs[Keys.LLM_PROVIDER] = effectiveProvider.name
            prefs[Keys.BYOK_PRESET] = effectivePreset.name
            prefs[Keys.GEMINI_MODEL] = geminiModel.ifBlank { "gemini-3.8-flash" }
            val cleanUrl = if (effectivePreset == ByokProviderPreset.LOCAL_OLLAMA) {
                LlmSettingsMigration.normalizeOllamaV1Url(customEndpointUrl)
            } else {
                customEndpointUrl.trim().trimEnd('/')
            }
            val cleanModel = customModelName.trim()
            prefs[Keys.CUSTOM_ENDPOINT] = cleanUrl
            prefs[Keys.CUSTOM_MODEL] = cleanModel

            if (effectiveProvider == LlmProviderType.OPENAI_COMPATIBLE) {
                prefs[Keys.presetUrlKey(effectivePreset)] = cleanUrl.ifBlank { effectivePreset.defaultBaseUrl }
                prefs[Keys.presetModelKey(effectivePreset)] = cleanModel.ifBlank { effectivePreset.defaultModel }
                val existingSlotKey = prefs[Keys.presetEncryptedKey(effectivePreset)] ?: ""
                val resolvedEncryptedKey = if (plainApiKey != null) {
                    if (plainApiKey.isBlank()) "" else SecretCipher.encrypt(plainApiKey)
                } else {
                    existingSlotKey
                }
                prefs[Keys.presetEncryptedKey(effectivePreset)] = resolvedEncryptedKey
                prefs[Keys.ENCRYPTED_LLM_KEY] = resolvedEncryptedKey
            } else if (plainApiKey != null) {
                prefs[Keys.ENCRYPTED_LLM_KEY] = if (plainApiKey.isBlank()) "" else SecretCipher.encrypt(plainApiKey)
            }

            prefs[Keys.LLM_TIMEOUT_SEC] = timeoutSeconds.coerceIn(30, 600)
            prefs[Keys.ON_DEVICE_FALLBACK_ENABLED] = onDeviceFallbackEnabled
            prefs[Keys.ON_DEVICE_FALLBACK_PROVIDER] = when (onDeviceFallbackProvider) {
                LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL -> LlmProviderType.FIREBASE_GEMINI.name
                LlmProviderType.OLLAMA_LOCAL -> LlmProviderType.OPENAI_COMPATIBLE.name
                else -> onDeviceFallbackProvider.name
            }
            prefs[Keys.ON_DEVICE_CONDENSE_DOCS] = onDeviceCondenseDocs
            prefs[Keys.ON_DEVICE_TEMPERATURE] = onDeviceTemperature.coerceIn(0.0f, 1.0f)
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
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.DARK_THEME] = settings.themeMode != ThemeMode.DAYLIGHT_LIGHT
            prefs[Keys.DISABLE_HEAVY_ANIMATIONS] = settings.disableHeavyAnimations
            prefs[Keys.DEFAULT_FORGE_GUI_STYLE] = settings.defaultForgeGuiStyle.name
        }
    }
}
