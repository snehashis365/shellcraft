# LLM Backends & Sleep-Resilient Generation Guide

ShellCraft decouples CLI-to-GUI synthesis from any single AI vendor. You can switch between managed cloud **Gemini** via Firebase AI Logic and self-hosted local models (**Ollama** or **OpenAI-compatible** servers like LM Studio and vLLM) at any time in **Settings**.

---

## Supported Providers

| Provider Type | Protocol & Streaming | Structured Output Method | Typical Latency |
| :--- | :--- | :--- | :--- |
| **`FIREBASE_GEMINI`** | Firebase AI Logic SDK (`Firebase.ai`) | Enforced `Schema.obj` JSON Schema (`responseMimeType = "application/json"`) | `2s – 12s` |
| **`OLLAMA_LOCAL`** | `POST <url>/api/chat` (`stream: true`, NDJSON) | `format: "json"` + system schema prompt + `thinking` stream tracking | `10s – 180s` |
| **`OPENAI_COMPATIBLE`** | `POST <url>/chat/completions` (`stream: true`, SSE) | System schema prompt + SSE delta/reasoning tracking (`delta.reasoning_content`) | `10s – 300s+` |
| **`ON_DEVICE_NANO_EXPERIMENTAL`** | ML Kit GenAI Prompt API (`Gemini Nano` via Android AICore) | Compact 1-shot system prompt + CLI doc condensation + JSON repair | `3s – 25s` (offline) |

---

## 1. Firebase AI Logic (Cloud Gemini)

- **No Client API Key Required**: Requests authenticate through Firebase AI Logic + Firebase App Check without shipping raw `GEMINI_API_KEY` secrets inside the APK.
- **Recommended Models**: `gemini-3.8-flash` (default) or `gemini-2.5-flash`.
- **Debug APK on Physical Devices**:
  - Debug builds install `DebugAppCheckProviderFactory`.
  - When launched inside AI Studio, a debug token is injected automatically; when sideloading a debug APK onto a physical phone, copy the App Check debug secret logged by `AppCheckInit` / Firebase SDK in Logcat and register it under **Firebase Console → App Check → Apps → Manage debug tokens** (or switch to a local LLM backend).

---

## 2. Local Ollama

1. Start Ollama listening on your LAN (`OLLAMA_HOST=0.0.0.0:11434 ollama serve`).
2. In ShellCraft **Settings → LLM Backend**, select **Ollama (Local / LAN)**:
   - **Endpoint URL**: `http://<LAN-IP>:11434` (cleartext LAN HTTP is enabled via `network_security_config.xml`).
   - **Model Name**: e.g. `qwen2.5-coder:7b`, `gemma3:12b`, or `llama3.1:8b`.
3. Tap **Test Connection** (`GET /api/tags`) to verify connectivity and inspect discovered model count.

---

## 3. BYOK Cloud Presets & OpenAI-Compatible APIs (Free Tiers & Self-Hosted)

Not everyone can host a local GPU server or absorb developer billing on a managed Firebase project. Under **Settings → BYOK Cloud (API)**, ShellCraft provides one-tap presets with **per-provider isolated credentials** (encrypted via `AndroidKeyStore`) and a **Get Free Key** link to each provider's API-key portal:

| Preset | Base URL | Free Tier Highlights | Key Portal |
| :--- | :--- | :--- | :--- |
| **Gemini AI Studio** | `https://generativelanguage.googleapis.com/v1beta/openai` | Use your own Google AI Studio free-tier key (`AIza...`) with no Firebase App Check required | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **NVIDIA NIM** | `https://integrate.api.nvidia.com/v1` | Free developer program credits for `DeepSeek-R1`, `Qwen`, `Llama`, and `Nemotron` | [build.nvidia.com](https://build.nvidia.com/) |
| **Groq** | `https://api.groq.com/openai/v1` | Ultra-fast LPU free tier (~30 RPM, no credit card needed) | [console.groq.com/keys](https://console.groq.com/keys) |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Unified gateway with zero-cost `:free` community endpoints | [openrouter.ai/keys](https://openrouter.ai/keys) |
| **Custom URL** | e.g. `http://192.168.1.50:1234` | LM Studio, vLLM, llama.cpp, or any self-hosted OpenAI-compatible proxy | — |

### Live `/models` & `/api/tags` Model Discovery

1. Select a preset (or **Local Ollama**), enter your endpoint / API key, and tap **Test & Fetch Models**.
2. ShellCraft queries `GET /models` (or `GET /api/tags` on Ollama), filters out non-chat embedding/image/audio weights, strips Google AI Studio's `models/` prefix automatically, and populates **Quick Pick** chips directly in Settings.
3. Tap the **Active Model** selector card to open the searchable **Model Picker** dialog with capability filter pills (`FREE`, `CODER`, `THINK`, `FAST`) plus a manual **Custom Model ID** override if needed.

---

## 4. On-Device Gemini Nano (Experimental · Android AICore)

ShellCraft includes an experimental **100% offline** plugin synthesizer powered by Android's official **ML Kit GenAI Prompt API** (`com.google.mlkit:genai-prompt`) and **Gemini Nano** managed by **Android AICore**:

- **Zero Bundled Weights**: Model weights are downloaded and updated securely by Google Play Services / Android AICore (Private Compute Services) rather than shipping large files inside the APK (`minSdk 26+` required).
- **Automatic Hardware Delegation**: AICore schedules inference across the device NPU, GPU, and CPU automatically.
- **4,000-Token Window Safeguards**: `CliDocCondenser` strips verbose man-page prose down to synopsis, subcommands, and flags before prompting, and `OnDeviceNanoEngine` verifies the token budget via `countTokens()` plus repairs truncated JSON if output nears the token ceiling.
- **Foreground Constraint & Auto-Fallback**: Android AICore restricts on-device inference to active foreground sessions. In **Settings → On-Device Nano (Exp.)**, you can inspect live AICore readiness, trigger system model download, toggle doc condensation, or enable **Auto-Fallback** to Cloud Gemini / Ollama / OpenAI-Compatible if a device does not support AICore or hits battery quota limits.

---

## Reasoning Models & Timeout Tuning

Large local reasoning models (e.g. `Qwen3.6-35B-A3B`, `DeepSeek-R1`) often emit `delta.reasoning_content` or Ollama `thinking` tokens for 1–4 minutes before emitting final JSON. ShellCraft handles this gracefully:

- **SSE / NDJSON Streaming**: Reads chunks incrementally so active reasoning models never hit idle read timeouts while thinking, and displays live character counts in the Forge progress card.
- **Null-Safe Normalization**: Automatically ignores `null` JSON fields (`contentOrNull`), coerces schema aliases (`"STRING"` → `TEXT`, `"Darwin"` → `MACOS`), and strips redundant command prefixes inside `subcommand`.
- **Configurable Request Timeout**: Adjustable in Settings (`60s`, `180s`, `240s` default, `300s`, `480s`, clamped `30s–600s`).

---

## Sleep-Resilient Background Execution

To prevent Android Doze mode or screen locking from killing long-running local generations:

- **`AppContainer` Pipeline Scope**: Generation runs in a process-wide `SupervisorJob` coroutine rather than a screen-bound `viewModelScope`.
- **Wake & Wi-Fi Locks**: Acquires `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` (on Android 10+) for the duration of synthesis.
- **Keep Screen On & Companion Notification**: Keeps the Forge screen awake while visible (`FLAG_KEEP_SCREEN_ON`) and attaches a non-fatal `dataSync` companion notification (`ForgeGenerationService`) with a **Stop** action so you can safely lock your phone or cancel at any time.
