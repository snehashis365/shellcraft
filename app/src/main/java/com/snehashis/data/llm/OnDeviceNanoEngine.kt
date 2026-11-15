package com.snehashis.data.llm

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.snehashis.domain.model.OnDeviceModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "OnDeviceNanoEngine"

/**
 * Experimental on-device inference engine wrapping Android's official ML Kit GenAI Prompt API
 * (Gemini Nano managed by Android AICore). Uses system-managed model weights and NPU/GPU/CPU
 * hardware delegation without bundling model files inside the APK.
 */
class OnDeviceNanoEngine {

    private val modelMutex = Mutex()

    @Volatile
    private var client: GenerativeModel? = null

    private fun getOrCreateClient(): GenerativeModel {
        client?.let { return it }
        return synchronized(this) {
            client ?: Generation.getClient().also { client = it }
        }
    }

    suspend fun checkStatus(): OnDeviceModelStatus = withContext(Dispatchers.IO) {
        try {
            val status = getOrCreateClient().checkStatus()
            mapFeatureStatus(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenAiException) {
            Log.w(TAG, "AICore checkStatus GenAiException code=${e.errorCode}", e)
            OnDeviceModelStatus.Unavailable(explainGenAiException(e))
        } catch (t: Throwable) {
            Log.w(TAG, "AICore unavailable on this environment", t)
            OnDeviceModelStatus.Unavailable(
                t.localizedMessage ?: "Android AICore service is not available on this device."
            )
        }
    }

    suspend fun downloadModel(onProgress: (OnDeviceModelStatus) -> Unit): OnDeviceModelStatus =
        withContext(Dispatchers.IO) {
            modelMutex.withLock {
                try {
                    val model = getOrCreateClient()
                    val current = model.checkStatus()
                    if (current == FeatureStatus.AVAILABLE) {
                        onProgress(OnDeviceModelStatus.Available)
                        return@withContext OnDeviceModelStatus.Available
                    }
                    if (current == FeatureStatus.UNAVAILABLE) {
                        val unavailable = OnDeviceModelStatus.Unavailable(
                            "Gemini Nano via AICore is not supported on this device or OS build."
                        )
                        onProgress(unavailable)
                        return@withContext unavailable
                    }

                    var terminalStatus: OnDeviceModelStatus = OnDeviceModelStatus.Downloading(0L)
                    onProgress(terminalStatus)

                    model.download().collect { dl ->
                        terminalStatus = when (dl) {
                            is DownloadStatus.DownloadStarted -> OnDeviceModelStatus.Downloading(0L)
                            is DownloadStatus.DownloadProgress ->
                                OnDeviceModelStatus.Downloading(dl.totalBytesDownloaded)
                            DownloadStatus.DownloadCompleted -> OnDeviceModelStatus.Available
                            is DownloadStatus.DownloadFailed ->
                                OnDeviceModelStatus.Error(
                                    dl.e.localizedMessage ?: "AICore model download failed."
                                )
                        }
                        onProgress(terminalStatus)
                    }
                    if (terminalStatus is OnDeviceModelStatus.Downloading) {
                        val rechecked = mapFeatureStatus(model.checkStatus())
                        onProgress(rechecked)
                        return@withContext rechecked
                    }
                    terminalStatus
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GenAiException) {
                    val err = OnDeviceModelStatus.Error(explainGenAiException(e))
                    onProgress(err)
                    err
                } catch (t: Throwable) {
                    val err = OnDeviceModelStatus.Error(
                        t.localizedMessage ?: "Failed to initiate AICore download."
                    )
                    onProgress(err)
                    err
                }
            }
        }

    suspend fun warmupAndTest(): String = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            val model = getOrCreateClient()
            when (val status = model.checkStatus()) {
                FeatureStatus.AVAILABLE -> Unit
                FeatureStatus.DOWNLOADABLE -> throw IllegalStateException(
                    "Gemini Nano is downloadable on this device. Tap 'Download via AICore' first."
                )
                FeatureStatus.DOWNLOADING -> throw IllegalStateException(
                    "Gemini Nano weights are currently downloading via AICore."
                )
                else -> throw IllegalStateException(
                    "Gemini Nano (AICore) is unavailable on this device (status=$status)."
                )
            }

            val startMs = System.currentTimeMillis()
            runCatching { model.warmup() }
            val testReq = generateContentRequest(TextPart("Reply with exactly: OK")) {
                temperature = 0.0f
                maxOutputTokens = 16
            }
            val tokenCount = runCatching { model.countTokens(testReq).totalTokens }.getOrNull()
            val response = model.generateContent(testReq)
            val reply = response.candidates.firstOrNull()?.text?.trim().orEmpty().ifBlank { "OK" }
            val elapsedMs = System.currentTimeMillis() - startMs
            val tokenNote = tokenCount?.let { " · ${it}t probe" } ?: ""
            "Gemini Nano ready via AICore (${elapsedMs}ms$tokenNote): $reply"
        }
    }

    suspend fun generatePluginJsonStream(
        systemPrompt: String,
        userPrompt: String,
        temperatureValue: Float,
        maxOutputTokensValue: Int = 1024,
        onProgress: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            val model = getOrCreateClient()
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> Unit
                FeatureStatus.DOWNLOADABLE -> throw IllegalStateException(
                    "Gemini Nano weights are not downloaded yet. Download them in Settings or enable Auto-Fallback."
                )
                FeatureStatus.DOWNLOADING -> throw IllegalStateException(
                    "Gemini Nano is still downloading via AICore. Wait for completion or enable Auto-Fallback."
                )
                else -> throw IllegalStateException(
                    "On-device Gemini Nano (AICore) is not supported on this device."
                )
            }

            onProgress?.invoke("Warming up Gemini Nano (AICore)...")
            runCatching { model.warmup() }

            var trimmedUserPrompt = userPrompt
            var request = buildPromptRequest(
                systemPrompt = systemPrompt,
                userPrompt = trimmedUserPrompt,
                temperatureValue = temperatureValue,
                maxOutputTokensValue = maxOutputTokensValue
            )

            // Guard Gemini Nano's 4,000-token total context window
            val initialTokens = runCatching { model.countTokens(request).totalTokens }.getOrNull()
            if (initialTokens != null) {
                onProgress?.invoke("Prompt budget: ~$initialTokens / 4,000 tokens...")
                if (initialTokens > 3_400) {
                    val keepRatio = (3_000f / initialTokens.toFloat()).coerceIn(0.35f, 0.85f)
                    val targetLen = (trimmedUserPrompt.length * keepRatio).toInt().coerceAtLeast(800)
                    trimmedUserPrompt = trimmedUserPrompt.take(targetLen)
                    request = buildPromptRequest(
                        systemPrompt = systemPrompt,
                        userPrompt = trimmedUserPrompt,
                        temperatureValue = temperatureValue,
                        maxOutputTokensValue = maxOutputTokensValue
                    )
                }
            }

            val builder = StringBuilder()
            try {
                model.generateContentStream(request).collect { response ->
                    val chunk = response.candidates.firstOrNull()?.text.orEmpty()
                    if (chunk.isNotEmpty()) {
                        builder.append(chunk)
                        onProgress?.invoke("On-device Nano streaming (${builder.length} chars)...")
                    }
                }
            } catch (e: GenAiException) {
                throw IllegalStateException(explainGenAiException(e), e)
            }

            val output = builder.toString().trim()
            if (output.isBlank()) {
                throw IllegalStateException("On-device Gemini Nano returned an empty response.")
            }
            output
        }
    }

    fun close() {
        runCatching { client?.close() }
        client = null
    }

    private fun buildPromptRequest(
        systemPrompt: String,
        userPrompt: String,
        temperatureValue: Float,
        maxOutputTokensValue: Int
    ) = generateContentRequest(TextPart(userPrompt)) {
        systemInstruction = SystemInstruction(systemPrompt)
        temperature = temperatureValue.coerceIn(0.0f, 1.0f)
        topK = 24
        maxOutputTokens = maxOutputTokensValue
    }

    private fun mapFeatureStatus(status: Int): OnDeviceModelStatus = when (status) {
        FeatureStatus.AVAILABLE -> OnDeviceModelStatus.Available
        FeatureStatus.DOWNLOADABLE -> OnDeviceModelStatus.Downloadable
        FeatureStatus.DOWNLOADING -> OnDeviceModelStatus.Downloading(0L)
        else -> OnDeviceModelStatus.Unavailable(
            "This device or OS build does not expose Gemini Nano via Android AICore."
        )
    }

    private fun explainGenAiException(e: GenAiException): String {
        val raw = e.localizedMessage.orEmpty()
        return when {
            raw.contains("BUSY", ignoreCase = true) ->
                "AICore is currently busy processing another request. Try again in a few seconds."
            raw.contains("QUOTA", ignoreCase = true) ->
                "AICore per-app battery/inference quota reached. Try again later or enable Auto-Fallback."
            raw.contains("BACKGROUND", ignoreCase = true) || raw.contains("FOREGROUND", ignoreCase = true) ->
                "AICore requires ShellCraft to stay in the foreground during on-device generation."
            else -> "AICore error (code ${e.errorCode}): ${raw.ifBlank { "On-device inference failed." }}"
        }
    }
}
