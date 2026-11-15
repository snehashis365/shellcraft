package com.snehashis

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.snehashis.core.AppContainer
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.service.ForgeGenerationService
import com.snehashis.ui.screens.ForgeScreen
import com.snehashis.ui.theme.ShellCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ShellCraft", appName)
  }

  @Test
  fun `forge pipeline starts companion foreground service with valid notification`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    AppContainer.startForgePipeline(
      context = app,
      commandName = "ping",
      sourceMode = PluginAnalysisSource.MANUAL_DOCS,
      manualDocs = "usage: ping [-c count] host",
      osHint = "POSIX",
      bypassCheck = false
    )

    val startedIntent = Shadows.shadowOf(app).nextStartedService
    assertNotNull("Expected ForgeGenerationService companion intent", startedIntent)

    val controller = Robolectric.buildService(ForgeGenerationService::class.java, startedIntent)
    controller.create().startCommand(0, 0)
    val service = controller.get()
    val fgNotification = Shadows.shadowOf(service).lastForegroundNotification
    assertNotNull("Expected valid foreground notification", fgNotification)
    assertEquals(R.drawable.ic_forge_notification, fgNotification.smallIcon.resId)
    AppContainer.cancelForgePipeline()
    controller.destroy()
  }

  @Test
  fun `clicking forge generate button transitions state cleanly without crashing`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    AppContainer.ensureInitialized(app)
    AppContainer.updateForgeState(com.snehashis.domain.model.ForgeState.Idle)

    composeRule.setContent {
      val forgeState by AppContainer.forgeState.collectAsState()
      ShellCraftTheme {
        ForgeScreen(
          forgeState = forgeState,
          llmConfig = LlmBackendConfig(),
          connectionState = SshConnectionState.Disconnected,
          onAnalyze = { cmd, mode, docs, bypass ->
            AppContainer.startForgePipeline(app, cmd, mode, docs, "POSIX", bypass)
          },
          onCancelAnalysis = { AppContainer.cancelForgePipeline() },
          onSaveAndOpenPlugin = {},
          onResetForge = { AppContainer.updateForgeState(com.snehashis.domain.model.ForgeState.Idle) }
        )
      }
    }

    composeRule.onNodeWithTag("forge_command_input").performTextInput("nmap")
    composeRule.onNodeWithTag("forge_lazy_column").performScrollToNode(hasTestTag("forge_analyze_button"))
    composeRule.onNodeWithTag("forge_analyze_button").performClick()
    composeRule.waitForIdle()
    AppContainer.cancelForgePipeline()
  }

  @Test
  fun `settings screen shows on-device nano diagnostics and fallback controls`() {
    composeRule.setContent {
      ShellCraftTheme {
        com.snehashis.ui.screens.SettingsScreen(
          llmConfig = LlmBackendConfig(
            providerType = com.snehashis.domain.model.LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL,
            onDeviceFallbackEnabled = true
          ),
          githubConfig = com.snehashis.domain.model.GitHubRepoConfig(),
          securitySettings = com.snehashis.data.local.SecuritySettings(),
          onDeviceStatus = com.snehashis.domain.model.OnDeviceModelStatus.Downloadable,
          onRefreshOnDeviceStatus = {},
          onDownloadOnDeviceModel = {},
          onSaveLlmConfig = { _, _, _, _, _, _, _, _, _, _ -> },
          onTestLlmConfig = { Result.success("OK") },
          onSaveGitHubConfig = { _, _, _, _, _, _ -> },
          onSyncNow = {},
          onUpdateSecurity = {},
          onOpenSchemaDocs = {}
        )
      }
    }

    composeRule.onNodeWithTag("llm_ondevice_chip").assertExists()
    composeRule.onNodeWithTag("ondevice_diagnostics_card").assertExists()
    composeRule.onNodeWithTag("ondevice_download_button").assertExists()
    composeRule.onNodeWithTag("ondevice_fallback_switch").assertExists()
    composeRule.onNodeWithTag("ondevice_fallback_firebase_gemini").assertExists()
  }
}
