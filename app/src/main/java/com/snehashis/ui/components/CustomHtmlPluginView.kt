package com.snehashis.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.snehashis.domain.model.CliPlugin
import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.PluginCommandBuilder
import com.snehashis.ui.TerminalSessionState
import com.snehashis.ui.theme.LocalDisableHeavyAnimations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CustomHtmlPluginView(
    plugin: CliPlugin,
    htmlTemplate: String,
    terminalState: TerminalSessionState,
    pickedFileUpdate: Pair<String, String>?,
    onExecute: (command: String, action: PluginAction, elevateSudo: Boolean) -> Unit,
    onPickRemoteFile: (paramId: String) -> Unit,
    onPickLocalFile: (paramId: String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current
    val isRobolectric = remember { Build.FINGERPRINT.contains("robolectric", ignoreCase = true) }
    val disableHeavyAnimations = LocalDisableHeavyAnimations.current

    if (isInspection || isRobolectric) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = modifier.fillMaxWidth().testTag("custom_html_plugin_view")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Custom HTML/Canvas GUI Ready (${plugin.displayName})", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Sandboxed WebView bridge active with ${plugin.actions.size} actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                plugin.actions.firstOrNull()?.let { first ->
                    Button(
                        onClick = {
                            val cmd = PluginCommandBuilder.buildCommand(plugin, first, emptyMap(), first.requiresSudo)
                            onExecute(cmd, first, first.requiresSudo)
                        },
                        modifier = Modifier.testTag("custom_html_fallback_run")
                    ) {
                        Text("Run ${first.label}")
                    }
                }
            }
        }
        return
    }

    val themeCss = rememberThemeCssBlock(
        bg = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surfaceContainer,
        surfaceElevated = MaterialTheme.colorScheme.surfaceContainerHigh,
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        accent = MaterialTheme.colorScheme.tertiary,
        text = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        border = MaterialTheme.colorScheme.outline,
        reduceMotion = disableHeavyAnimations
    )

    val composedHtml = remember(htmlTemplate, themeCss) {
        injectThemeCss(htmlTemplate, themeCss)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val currentPlugin by rememberUpdatedState(plugin)
    val currentOnExecute by rememberUpdatedState(onExecute)
    val currentOnPickRemoteFile by rememberUpdatedState(onPickRemoteFile)
    val currentOnPickLocalFile by rememberUpdatedState(onPickLocalFile)
    val currentOnNotify by rememberUpdatedState(onNotify)

    LaunchedEffect(terminalState.output, terminalState.isRunning, terminalState.lastResult, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val payload = JSONObject().apply {
            put("isRunning", terminalState.isRunning)
            put("command", terminalState.command)
            put("output", terminalState.output)
            put("exitCode", terminalState.lastResult?.exitCode ?: JSONObject.NULL)
            put("durationMs", terminalState.lastResult?.durationMs ?: JSONObject.NULL)
        }.toString()
        val quotedPayload = JSONObject.quote(payload)
        wv.evaluateJavascript("if(window.onShellCraftOutput){window.onShellCraftOutput($quotedPayload);}", null)
    }

    LaunchedEffect(pickedFileUpdate, webViewRef) {
        val (paramId, path) = pickedFileUpdate ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        val qParam = JSONObject.quote(paramId)
        val qPath = JSONObject.quote(path)
        wv.evaluateJavascript("if(window.onShellCraftFilePicked){window.onShellCraftFilePicked($qParam, $qPath);}", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("ShellCraftBridge")
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp, max = 560.dp)
            .testTag("custom_html_plugin_view")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 380.dp, max = 560.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = true
                        }
                        val jsBridge = ShellCraftJsBridge(
                            mainHandler = mainHandler,
                            pluginProvider = { currentPlugin },
                            onExecute = { cmd, action, elevate -> currentOnExecute(cmd, action, elevate) },
                            onPickRemoteFile = { paramId -> currentOnPickRemoteFile(paramId) },
                            onPickLocalFile = { paramId -> currentOnPickLocalFile(paramId) },
                            onNotify = { msg -> currentOnNotify(msg) }
                        )
                        addJavascriptInterface(jsBridge, "ShellCraftBridge")
                        loadDataWithBaseURL("https://shellcraft.local/", composedHtml, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    if (wv.tag != composedHtml) {
                        wv.tag = composedHtml
                        wv.loadDataWithBaseURL("https://shellcraft.local/", composedHtml, "text/html", "UTF-8", null)
                    }
                }
            )
        }
    }
}

private fun parseValuesJson(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = Json.parseToJsonElement(raw).jsonObject
        obj.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: v.toString() }
    }.getOrDefault(emptyMap())
}

private fun rememberThemeCssBlock(
    bg: Color,
    surface: Color,
    surfaceElevated: Color,
    primary: Color,
    secondary: Color,
    accent: Color,
    text: Color,
    muted: Color,
    border: Color,
    reduceMotion: Boolean
): String {
    fun hex(c: Color) = String.format(Locale.US, "#%06X", 0xFFFFFF and c.toArgb())
    return """
        <style id="shellcraft-theme-vars">
        :root {
          --sc-bg: ${hex(bg)};
          --sc-surface: ${hex(surface)};
          --sc-surface-elevated: ${hex(surfaceElevated)};
          --sc-primary: ${hex(primary)};
          --sc-secondary: ${hex(secondary)};
          --sc-accent: ${hex(accent)};
          --sc-text: ${hex(text)};
          --sc-muted: ${hex(muted)};
          --sc-border: ${hex(border)};
          --sc-reduce-motion: ${if (reduceMotion) 1 else 0};
        }
        </style>
    """.trimIndent()
}

private fun injectThemeCss(html: String, styleTag: String): String {
    val headIdx = html.indexOf("</head>", ignoreCase = true)
    return if (headIdx >= 0) {
        html.substring(0, headIdx) + styleTag + html.substring(headIdx)
    } else {
        "$styleTag\n$html"
    }
}

private class ShellCraftJsBridge(
    private val mainHandler: Handler,
    private val pluginProvider: () -> CliPlugin,
    private val onExecute: (command: String, action: PluginAction, elevateSudo: Boolean) -> Unit,
    private val onPickRemoteFile: (paramId: String) -> Unit,
    private val onPickLocalFile: (paramId: String) -> Unit,
    private val onNotify: (String) -> Unit
) {
    @JavascriptInterface
    fun runAction(actionId: String, valuesJson: String?, elevateSudo: Boolean) {
        mainHandler.post {
            val plugin = pluginProvider()
            val action = plugin.actions.find { it.id == actionId } ?: plugin.actions.firstOrNull()
            if (action == null) {
                onNotify("Unknown action: $actionId")
                return@post
            }
            val valuesMap = parseValuesJson(valuesJson)
            val resolvedCmd = PluginCommandBuilder.buildCommand(
                plugin = plugin,
                action = action,
                values = valuesMap,
                elevateWithSudo = elevateSudo || action.requiresSudo
            )
            onExecute(resolvedCmd, action, elevateSudo || action.requiresSudo)
        }
    }

    @JavascriptInterface
    fun pickRemoteFile(paramId: String) {
        mainHandler.post { onPickRemoteFile(paramId) }
    }

    @JavascriptInterface
    fun pickLocalFile(paramId: String) {
        mainHandler.post { onPickLocalFile(paramId) }
    }

    @JavascriptInterface
    fun notify(message: String) {
        mainHandler.post { onNotify(message) }
    }
}
