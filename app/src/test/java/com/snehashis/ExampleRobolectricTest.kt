package com.snehashis

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.snehashis.core.AppContainer
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.DiscoveredLlmModel
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelCapabilityTag
import com.snehashis.domain.model.ModelDiscoveryState
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
            providerType = LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL,
            onDeviceFallbackEnabled = true
          ),
          githubConfig = com.snehashis.domain.model.GitHubRepoConfig(),
          securitySettings = com.snehashis.data.local.SecuritySettings(),
          onDeviceStatus = com.snehashis.domain.model.OnDeviceModelStatus.Downloadable,
          onRefreshOnDeviceStatus = {},
          onDownloadOnDeviceModel = {},
          onSaveLlmConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
          onTestLlmConfig = { Result.success(EndpointTestResult("OK")) },
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

  @Test
  fun `settings screen supports byok presets, get free key link, and searchable model picker`() {
    val sampleModels = listOf(
      DiscoveredLlmModel(
        id = "qwen/qwen2.5-coder-32b-instruct",
        ownedBy = "qwen",
        tags = setOf(ModelCapabilityTag.CODER)
      ),
      DiscoveredLlmModel(
        id = "deepseek-ai/deepseek-r1",
        ownedBy = "deepseek",
        tags = setOf(ModelCapabilityTag.REASONING)
      ),
      DiscoveredLlmModel(
        id = "meta/llama-3.3-70b-instruct",
        ownedBy = "meta",
        tags = emptySet()
      )
    )

    composeRule.setContent {
      ShellCraftTheme {
        com.snehashis.ui.screens.SettingsScreen(
          llmConfig = LlmBackendConfig(
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            byokPreset = ByokProviderPreset.NVIDIA_NIM,
            customEndpointUrl = ByokProviderPreset.NVIDIA_NIM.defaultBaseUrl,
            customModelName = ByokProviderPreset.NVIDIA_NIM.defaultModel
          ),
          githubConfig = com.snehashis.domain.model.GitHubRepoConfig(),
          securitySettings = com.snehashis.data.local.SecuritySettings(),
          onSaveLlmConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
          onTestLlmConfig = { Result.success(EndpointTestResult("Found 3 models", sampleModels)) },
          modelDiscoveryState = ModelDiscoveryState.Ready(sampleModels, "Found 3 models"),
          onSaveGitHubConfig = { _, _, _, _, _, _ -> },
          onSyncNow = {},
          onUpdateSecurity = {},
          onOpenSchemaDocs = {}
        )
      }
    }

    composeRule.onNodeWithTag("byok_preset_nvidia_nim").assertExists()
    composeRule.onNodeWithTag("byok_preset_gemini_ai_studio").assertExists()
    composeRule.onNodeWithTag("byok_preset_groq").assertExists()
    composeRule.onNodeWithTag("byok_preset_openrouter").assertExists()
    composeRule.onNodeWithTag("byok_get_key_button").assertExists()
    composeRule.onNodeWithTag("quick_model_qwen/qwen2.5-coder-32b-instruct").assertExists()

    // Scroll Model Selector into the Robolectric viewport, open dialog, filter, and pick DeepSeek-R1
    composeRule.onNodeWithTag("model_selector_card").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("model_picker_dialog").assertExists()
    composeRule.onNodeWithTag("model_filter_reasoning").performClick()
    composeRule.onNodeWithTag("model_picker_search").performTextInput("deepseek")
    composeRule.onNodeWithTag("model_option_deepseek-ai/deepseek-r1").performScrollTo().performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("active_model_value", useUnmergedTree = true)
      .assertTextContains("deepseek-ai/deepseek-r1")

    // Scroll lower controls into vertical viewport before tapping the horizontal Quick Pick chip
    composeRule.onNodeWithTag("test_llm_button").performScrollTo()
    composeRule.onNodeWithTag("quick_model_qwen/qwen2.5-coder-32b-instruct").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("model_selector_card").performScrollTo()
    composeRule.onNodeWithTag("active_model_value", useUnmergedTree = true)
      .assertTextContains("qwen/qwen2.5-coder-32b-instruct")
  }
}
