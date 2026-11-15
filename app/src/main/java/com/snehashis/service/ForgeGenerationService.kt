package com.snehashis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.snehashis.MainActivity
import com.snehashis.R
import com.snehashis.core.AppContainer
import com.snehashis.domain.model.ForgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "ForgeGenService"
private const val CHANNEL_ID = "shellcraft_forge_channel"
private const val NOTIFICATION_ID = 4102

/**
 * Lightweight foreground companion service that keeps the process prioritized during screen lock
 * and mirrors [AppContainer.forgeState] into a persistent status notification with a Stop action.
 */
class ForgeGenerationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppContainer.ensureInitialized(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppContainer.cancelForgePipeline()
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_COMPANION -> {
                val commandName = intent.getStringExtra(EXTRA_COMMAND_NAME).orEmpty().ifBlank { "CLI" }
                val promoted = tryPromoteForeground("Synthesizing `$commandName` plugin...")
                if (!promoted) {
                    // Started via startService (not startForegroundService), so stopping is non-fatal.
                    stopSelf()
                    return START_NOT_STICKY
                }
                observePipelineState()
            }
            else -> {
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    private fun tryPromoteForeground(initialText: String): Boolean {
        return try {
            val notification = buildNotification(initialText, ongoing = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Foreground promotion skipped cleanly; pipeline continues in-process", t)
            false
        }
    }

    private fun observePipelineState() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            var lastNotifyMs = 0L
            AppContainer.forgeState.collect { state ->
                when (state) {
                    is ForgeState.CheckingGitHub -> {
                        updateNotification("Checking GitHub & local cache for `${state.commandName}`...")
                    }
                    is ForgeState.ProbingSshDocs -> {
                        updateNotification("Probing `man` / `--help` over SSH for `${state.commandName}`...")
                    }
                    is ForgeState.AnalyzingWithLlm -> {
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyMs > 1_500L) {
                            lastNotifyMs = now
                            updateNotification(state.statusMessage)
                        }
                    }
                    is ForgeState.Ready -> {
                        postCompletionNotification(
                            "GUI plugin ready for `${state.plugin.displayName}` (${state.plugin.actions.size} actions)"
                        )
                        stopSelfCleanly()
                    }
                    is ForgeState.FoundExistingRemote -> {
                        postCompletionNotification("Found existing `${state.plugin.displayName}`")
                        stopSelfCleanly()
                    }
                    is ForgeState.Error -> {
                        if (!state.message.contains("cancelled", ignoreCase = true)) {
                            postCompletionNotification("Generation failed: ${state.message.take(80)}")
                        }
                        stopSelfCleanly()
                    }
                    ForgeState.Idle -> {
                        stopSelfCleanly()
                    }
                }
            }
        }
    }

    private fun stopSelfCleanly() {
        observerJob?.cancel()
        observerJob = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ShellCraft AI Plugin Forge",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps long-running local and cloud LLM GUI synthesis alive while the screen sleeps."
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.createNotificationChannel(channel)
            }
        }
    }

    private fun updateNotification(text: String) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification(text, ongoing = true))
        }
    }

    private fun postCompletionNotification(text: String) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID + 1, buildNotification(text, ongoing = false))
        }
    }

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forge_notification)
            .setContentTitle(if (ongoing) "ShellCraft AI Forge Running" else "ShellCraft AI Forge")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)

        if (ongoing) {
            val stopIntent = Intent(this, ForgeGenerationService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPi = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setProgress(0, 0, true)
            builder.addAction(R.drawable.ic_forge_notification, "Stop", stopPi)
        }
        return builder.build()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_COMPANION = "com.snehashis.action.FORGE_COMPANION"
        private const val ACTION_STOP = "com.snehashis.action.FORGE_STOP"
        private const val EXTRA_COMMAND_NAME = "extra_command_name"

        fun startCompanion(context: Context, commandName: String) {
            try {
                val intent = Intent(context, ForgeGenerationService::class.java).apply {
                    action = ACTION_COMPANION
                    putExtra(EXTRA_COMMAND_NAME, commandName)
                }
                context.startService(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Companion service start skipped cleanly", t)
            }
        }
    }
}
