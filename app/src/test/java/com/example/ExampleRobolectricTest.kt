package com.example

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
import com.example.core.AppContainer
import com.example.domain.model.LlmBackendConfig
import com.example.domain.model.PluginAnalysisSource
import com.example.domain.model.SshConnectionState
import com.example.service.ForgeGenerationService
import com.example.ui.screens.ForgeScreen
import com.example.ui.theme.ShellCraftTheme
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
    AppContainer.updateForgeState(com.example.domain.model.ForgeState.Idle)

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
          onResetForge = { AppContainer.updateForgeState(com.example.domain.model.ForgeState.Idle) }
        )
      }
    }

    composeRule.onNodeWithTag("forge_command_input").performTextInput("nmap")
    composeRule.onNodeWithTag("forge_lazy_column").performScrollToNode(hasTestTag("forge_analyze_button"))
    composeRule.onNodeWithTag("forge_analyze_button").performClick()
    composeRule.waitForIdle()
    AppContainer.cancelForgePipeline()
  }
}
