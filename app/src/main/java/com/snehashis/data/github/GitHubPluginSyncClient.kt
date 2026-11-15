package com.snehashis.data.github

import android.util.Base64
import android.util.Log
import com.snehashis.core.security.SecretCipher
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.PluginSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val TAG = "GitHubPluginSyncClient"

class GitHubPluginSyncClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun findExistingPlugin(
        commandName: String,
        config: GitHubRepoConfig
    ): Pair<CliPlugin, String>? = withContext(Dispatchers.IO) {
        if (!config.isConfigured) return@withContext null
        val normalized = commandName.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        val dir = config.pluginsDirectory.trim('/')
        val path = if (dir.isBlank()) "$normalized.json" else "$dir/$normalized.json"
        val url = "https://api.github.com/repos/${config.ownerRepo}/contents/$path?ref=${config.branch}"

        val req = buildGitHubRequest(url, config).get().build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) {
                throw IllegalStateException("GitHub lookup HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body?.string().orEmpty()
            val fileDto = json.decodeFromString<GitHubContentFile>(body)
            val decoded = decodeBase64Content(fileDto.content)
            val plugin = json.decodeFromString<CliPlugin>(decoded).copy(
                source = PluginSource.GITHUB_SYNC
            )
            plugin to path
        }
    }

    suspend fun syncAllPlugins(config: GitHubRepoConfig): List<CliPlugin> = withContext(Dispatchers.IO) {
        require(config.isConfigured) { "GitHub repository is not configured." }
        val dir = config.pluginsDirectory.trim('/')
        val url = "https://api.github.com/repos/${config.ownerRepo}/contents/$dir?ref=${config.branch}"
        val req = buildGitHubRequest(url, config).get().build()

        val entries = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("GitHub sync failed (HTTP ${resp.code}): ${resp.message}")
            }
            val body = resp.body?.string().orEmpty()
            json.decodeFromString<List<GitHubDirEntry>>(body)
        }

        val synced = mutableListOf<CliPlugin>()
        for (entry in entries.filter { it.type == "file" && it.name.endsWith(".json", ignoreCase = true) }) {
            try {
                val downloadUrl = entry.downloadUrl ?: continue
                val fileReq = buildGitHubRequest(downloadUrl, config).get().build()
                val rawJson = client.newCall(fileReq).execute().use { fResp ->
                    if (!fResp.isSuccessful) return@use null
                    fResp.body?.string()
                } ?: continue
                val plugin = json.decodeFromString<CliPlugin>(rawJson).copy(
                    source = PluginSource.GITHUB_SYNC
                )
                synced.add(plugin)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed plugin file ${entry.name}", e)
            }
        }
        synced
    }

    suspend fun publishPlugin(plugin: CliPlugin, config: GitHubRepoConfig): String = withContext(Dispatchers.IO) {
        require(config.isConfigured) { "Configure a GitHub repository in Settings first." }
        val pat = SecretCipher.decrypt(config.encryptedPat)
        require(pat.isNotBlank()) { "A GitHub Personal Access Token (repo contents write) is required to publish plugins." }

        val fileName = "${plugin.commandName.lowercase().replace(Regex("[^a-z0-9._-]"), "-")}.json"
        val dir = config.pluginsDirectory.trim('/')
        val path = if (dir.isBlank()) fileName else "$dir/$fileName"
        val apiUrl = "https://api.github.com/repos/${config.ownerRepo}/contents/$path"

        val existingSha = runCatching {
            val getReq = buildGitHubRequest("$apiUrl?ref=${config.branch}", config).get().build()
            client.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    json.decodeFromString<GitHubContentFile>(resp.body?.string().orEmpty()).sha
                } else null
            }
        }.getOrNull()

        val encodedContent = Base64.encodeToString(
            json.encodeToString(plugin).toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )

        val putPayload = GitHubPutFileRequest(
            message = "chore(plugins): publish ${plugin.commandName} v${plugin.version} via ShellCraft",
            content = encodedContent,
            branch = config.branch,
            sha = existingSha
        )
        val reqBody = json.encodeToString(putPayload).toRequestBody("application/json".toMediaType())
        val putReq = buildGitHubRequest(apiUrl, config).put(reqBody).build()

        client.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("GitHub publish failed (HTTP ${resp.code}): ${resp.body?.string()?.take(200)}")
            }
        }
        path
    }

    private fun buildGitHubRequest(url: String, config: GitHubRepoConfig): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ShellCraft-Android")
        val pat = SecretCipher.decrypt(config.encryptedPat)
        if (pat.isNotBlank()) {
            builder.header("Authorization", "Bearer $pat")
        }
        return builder
    }

    private fun decodeBase64Content(base64Raw: String): String {
        val cleaned = base64Raw.replace("\n", "").replace("\r", "")
        return String(Base64.decode(cleaned, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    @Serializable
    private data class GitHubDirEntry(
        val name: String,
        val path: String,
        val type: String,
        val sha: String = "",
        @kotlinx.serialization.SerialName("download_url") val downloadUrl: String? = null
    )

    @Serializable
    private data class GitHubContentFile(
        val sha: String = "",
        val content: String = ""
    )

    @Serializable
    private data class GitHubPutFileRequest(
        val message: String,
        val content: String,
        val branch: String,
        val sha: String? = null
    )
}
