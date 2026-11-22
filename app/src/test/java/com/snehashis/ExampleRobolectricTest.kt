package com.snehashis

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.snehashis.core.AppContainer
import com.snehashis.data.local.SecuritySettings
import com.snehashis.data.local.SeedPlugins
import com.snehashis.domain.model.ByokProviderPreset
import com.snehashis.domain.model.DiscoveredLlmModel
import com.snehashis.domain.model.EndpointTestResult
import com.snehashis.domain.model.GitHubRepoConfig
import com.snehashis.domain.model.LlmBackendConfig
import com.snehashis.domain.model.LlmProviderType
import com.snehashis.domain.model.ModelCapabilityTag
import com.snehashis.domain.model.ModelDiscoveryState
import com.snehashis.domain.model.OnDeviceModelStatus
import com.snehashis.domain.model.PluginAnalysisSource
import com.snehashis.domain.model.PluginGuiStyle
import com.snehashis.domain.model.SshConnectionState
import com.snehashis.domain.model.SshExecutionResult
import com.snehashis.service.ForgeGenerationService
import com.snehashis.ui.TerminalSessionState
import com.snehashis.ui.screens.ForgeScreen
import com.snehashis.ui.screens.PluginRunnerScreen
import com.snehashis.ui.screens.SettingsScreen
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
  fun `clicking forge generate opens style selector dialog and transitions state cleanly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    AppContainer.ensureInitialized(app)
    AppContainer.updateForgeState(com.snehashis.domain.model.ForgeState.Idle)
    var capturedStyle: PluginGuiStyle? = null
    var capturedInstructions = ""

    composeRule.setContent {
      val forgeState by AppContainer.forgeState.collectAsState()
      ShellCraftTheme {
        ForgeScreen(
          forgeState = forgeState,
          llmConfig = LlmBackendConfig(),
          connectionState = SshConnectionState.Disconnected,
          defaultGuiStyle = PluginGuiStyle.MODULAR_DASHBOARD,
          onAnalyze = { cmd, mode, docs, bypass, guiStyle, customInstr ->
            capturedStyle = guiStyle
            capturedInstructions = customInstr
            AppContainer.startForgePipeline(app, cmd, mode, docs, "POSIX", bypass, guiStyle, customInstr)
          },
          onCancelAnalysis = { AppContainer.cancelForgePipeline() },
          onSaveAndOpenPlugin = {},
          onResetForge = { AppContainer.updateForgeState(com.snehashis.domain.model.ForgeState.Idle) }
        )
      }
    }

    composeRule.onNodeWithTag("forge_hero_canvas").assertExists()
    composeRule.onNodeWithTag("forge_command_input").performTextInput("nmap")
    composeRule.onNodeWithTag("forge_lazy_column").performScrollToNode(hasTestTag("forge_analyze_button"))
    composeRule.onNodeWithTag("forge_analyze_button").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("forge_style_dialog").assertExists()
    composeRule.onNodeWithTag("forge_style_modular").assertExists()
    composeRule.onNodeWithTag("forge_style_html").assertExists()
    composeRule.onNodeWithTag("forge_custom_instructions_input").performTextInput("Add fast SYN preset")
    composeRule.onNodeWithTag("forge_confirm_generate_button").performClick()
    composeRule.waitForIdle()

    assertEquals(PluginGuiStyle.MODULAR_DASHBOARD, capturedStyle)
    assertEquals("Add fast SYN preset", capturedInstructions)
    AppContainer.cancelForgePipeline()
  }

  @Test
  fun `settings screen consolidates ollama into byok presets and exposes theme and animation controls`() {
    composeRule.setContent {
      ShellCraftTheme {
        SettingsScreen(
          llmConfig = LlmBackendConfig(
            providerType = LlmProviderType.ON_DEVICE_NANO_EXPERIMENTAL,
            onDeviceFallbackEnabled = true
          ),
          githubConfig = GitHubRepoConfig(),
          securitySettings = SecuritySettings(),
          onDeviceStatus = OnDeviceModelStatus.Downloadable,
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

    composeRule.onNodeWithTag("llm_gemini_chip").assertExists()
    composeRule.onNodeWithTag("llm_openai_chip").assertExists()
    composeRule.onNodeWithTag("llm_ondevice_chip").assertExists()
    composeRule.onNodeWithTag("llm_ollama_chip").assertDoesNotExist()
    composeRule.onNodeWithTag("ondevice_diagnostics_card").assertExists()
    composeRule.onNodeWithTag("ondevice_download_button").assertExists()
    composeRule.onNodeWithTag("ondevice_fallback_switch").assertExists()
    composeRule.onNodeWithTag("ondevice_fallback_firebase_gemini").assertExists()
    composeRule.onNodeWithTag("ondevice_fallback_openai_compatible").assertExists()

    composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("appearance_forge_card"))
    composeRule.onNodeWithTag("theme_mode_daylight_light").performScrollTo().assertExists()
    composeRule.onNodeWithTag("default_gui_html_canvas_custom").performScrollTo().assertExists()
    composeRule.onNodeWithTag("toggle_disable_heavy_animations").performScrollTo().assertExists()
  }

  @Test
  fun `settings screen supports byok presets including local ollama and searchable model picker`() {
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
        SettingsScreen(
          llmConfig = LlmBackendConfig(
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            byokPreset = ByokProviderPreset.NVIDIA_NIM,
            customEndpointUrl = ByokProviderPreset.NVIDIA_NIM.defaultBaseUrl,
            customModelName = ByokProviderPreset.NVIDIA_NIM.defaultModel
          ),
          githubConfig = GitHubRepoConfig(),
          securitySettings = SecuritySettings(),
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
    composeRule.onNodeWithTag("byok_preset_local_ollama").performScrollTo().assertExists()
    composeRule.onNodeWithTag("byok_get_key_button").assertExists()
    composeRule.onNodeWithTag("quick_model_qwen/qwen2.5-coder-32b-instruct").assertExists()

    composeRule.onNodeWithTag("model_selector_card").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("model_picker_dialog").assertExists()
    composeRule.onNodeWithTag("model_filter_reasoning").performClick()
    composeRule.onNodeWithTag("model_picker_search").performTextInput("deepseek")
    composeRule.onNodeWithTag("model_option_deepseek-ai/deepseek-r1").performScrollTo().performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("active_model_value", useUnmergedTree = true)
      .assertTextContains("deepseek-ai/deepseek-r1")

    composeRule.onNodeWithTag("test_llm_button").performScrollTo()
    composeRule.onNodeWithTag("quick_model_qwen/qwen2.5-coder-32b-instruct").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("model_selector_card").performScrollTo()
    composeRule.onNodeWithTag("active_model_value", useUnmergedTree = true)
      .assertTextContains("qwen/qwen2.5-coder-32b-instruct")
  }

  @Test
  fun `plugin runner screen renders sliders, file pickers, inline row actions, and AI refine chat`() {
    val docker = SeedPlugins.starterPlugins().first { it.commandName == "docker" }
    val rsync = SeedPlugins.starterPlugins().first { it.commandName == "rsync" }
    val journalctl = SeedPlugins.starterPlugins().first { it.commandName == "journalctl" }

    val dockerTableOutput = """
      CONTAINER ID   IMAGE          STATUS        NAMES
      a1b2c3d4       nginx:latest   Up 3 hours    web-proxy
    """.trimIndent()

    var invokedRowCmd = ""

    composeRule.setContent {
      ShellCraftTheme {
        PluginRunnerScreen(
          plugin = docker,
          githubConfig = GitHubRepoConfig(),
          isSshConnected = true,
          terminalState = TerminalSessionState(
            pluginId = docker.id,
            command = "docker ps",
            output = dockerTableOutput,
            lastResult = SshExecutionResult("docker ps", 0, dockerTableOutput, "", 42L, false)
          ),
          onBack = {},
          onExecute = { cmd, _, _ -> invokedRowCmd = cmd },
          onSendInput = { _, _ -> },
          onSendControl = { _, _ -> },
          onExpandTerminal = {},
          onPublishToGitHub = {},
          onExportJson = { "{}" },
          onDeletePlugin = {},
          onNotify = {},
          onHapticTick = {}
        )
      }
    }

    composeRule.onNodeWithTag("runner_lazy_column").performScrollToNode(hasTestTag("row_action_container_logs_0"))
    composeRule.onNodeWithTag("row_action_container_logs_0").performClick()
    assertEquals("docker logs --tail 120 -t a1b2c3d4", invokedRowCmd)

    composeRule.onNodeWithTag("runner_ai_refine_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("ai_refine_chat_sheet").assertExists()
  }

  @Test
  fun `rsync plugin runner shows dual remote host browse and phone file upload buttons`() {
    val rsync = SeedPlugins.starterPlugins().first { it.commandName == "rsync" }

    composeRule.setContent {
      ShellCraftTheme {
        PluginRunnerScreen(
          plugin = rsync,
          githubConfig = GitHubRepoConfig(),
          isSshConnected = true,
          terminalState = TerminalSessionState(),
          onBack = {},
          onExecute = { _, _, _ -> },
          onSendInput = { _, _ -> },
          onSendControl = { _, _ -> },
          onExpandTerminal = {},
          onPublishToGitHub = {},
          onExportJson = { "{}" },
          onDeletePlugin = {},
          onNotify = {},
          onHapticTick = {}
        )
      }
    }

    composeRule.onNodeWithTag("runner_lazy_column").performScrollToNode(hasTestTag("browse_remote_source"))
    composeRule.onNodeWithTag("browse_remote_source").assertExists()
    composeRule.onNodeWithTag("upload_local_source").assertExists()
  }

  @Test
  fun `journalctl plugin runner renders interactive range slider control`() {
    val journalctl = SeedPlugins.starterPlugins().first { it.commandName == "journalctl" }

    composeRule.setContent {
      ShellCraftTheme {
        PluginRunnerScreen(
          plugin = journalctl,
          githubConfig = GitHubRepoConfig(),
          isSshConnected = true,
          terminalState = TerminalSessionState(),
          onBack = {},
          onExecute = { _, _, _ -> },
          onSendInput = { _, _ -> },
          onSendControl = { _, _ -> },
          onExpandTerminal = {},
          onPublishToGitHub = {},
          onExportJson = { "{}" },
          onDeletePlugin = {},
          onNotify = {},
          onHapticTick = {}
        )
      }
    }

    composeRule.onNodeWithTag("runner_lazy_column").performScrollToNode(hasTestTag("param_slider_lines"))
    composeRule.onNodeWithTag("param_slider_lines").assertExists()
  }
}
