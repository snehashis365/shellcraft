package com.snehashis.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.snehashis.ui.theme.LocalDisableHeavyAnimations
import kotlin.math.cos
import kotlin.math.sin

enum class HeroSceneMode(val assetFileName: String) {
    SSH_TELEMETRY("ssh_hero.html"),
    FORGE_NEURAL("forge_hero.html")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeJsHeroCanvas(
    mode: HeroSceneMode,
    stateName: String,
    primaryColor: Color,
    secondaryColor: Color,
    speed: Float = 1.0f,
    pulse: Float = 0.25f,
    modifier: Modifier = Modifier
) {
    val disableHeavyAnimations = LocalDisableHeavyAnimations.current
    val isInspection = LocalInspectionMode.current
    val isRobolectric = remember {
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    }

    if (disableHeavyAnimations || isInspection || isRobolectric) {
        LightweightVectorHeroBackdrop(
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            modifier = modifier
        )
        return
    }

    val bgHex = colorToHex(MaterialTheme.colorScheme.surfaceContainer)
    val primaryHex = colorToHex(primaryColor)
    val secondaryHex = colorToHex(secondaryColor)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(stateName, primaryHex, secondaryHex, bgHex, speed, pulse, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val js = "if(window.setHeroState){window.setHeroState('$stateName','$primaryHex','$secondaryHex','$bgHex',$speed,$pulse);}"
        wv.evaluateJavascript(js, null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    loadUrl("file:///android_asset/${mode.assetFileName}")
                    webViewRef = this
                }
            },
            update = { wv ->
                val js = "if(window.setHeroState){window.setHeroState('$stateName','$primaryHex','$secondaryHex','$bgHex',$speed,$pulse);}"
                wv.evaluateJavascript(js, null)
            }
        )
    }
}

@Composable
private fun LightweightVectorHeroBackdrop(
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.84f
        val cy = size.height * 0.50f
        val baseRadius = size.minDimension * 0.36f
        if (baseRadius <= 1f) return@Canvas

        drawCircle(
            color = primaryColor.copy(alpha = 0.26f),
            radius = baseRadius * 1.15f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.22f),
            radius = baseRadius * 0.78f,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        for (i in 0 until 6) {
            val a0 = Math.toRadians((i * 60).toDouble())
            val a1 = Math.toRadians(((i + 1) * 60).toDouble())
            val p0 = Offset(
                cx + (cos(a0) * baseRadius * 0.55f).toFloat(),
                cy + (sin(a0) * baseRadius * 0.55f).toFloat()
            )
            val p1 = Offset(
                cx + (cos(a1) * baseRadius * 0.55f).toFloat(),
                cy + (sin(a1) * baseRadius * 0.55f).toFloat()
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.45f),
                start = p0,
                end = p1,
                strokeWidth = 2f
            )
        }
    }
}

private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}
