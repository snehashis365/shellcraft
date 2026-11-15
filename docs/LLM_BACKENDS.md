# LLM Backends & Sleep-Resilient Generation Guide

ShellCraft decouples CLI-to-GUI synthesis from any single AI vendor. You can switch between managed cloud **Gemini** via Firebase AI Logic and self-hosted local models (**Ollama** or **OpenAI-compatible** servers like LM Studio and vLLM) at any time in **Settings**.

---

## Supported Providers

| Provider Type | Protocol & Streaming | Structured Output Method | Typical Latency |
| :--- | :--- | :--- | :--- |
| **`FIREBASE_GEMINI`** | Firebase AI Logic SDK (`Firebase.ai`) | Enforced `Schema.obj` JSON Schema (`responseMimeType = "application/json"`) | `2s – 12s` |
| **`OLLAMA_LOCAL`** | `POST <url>/api/chat` (`stream: true`, NDJSON) | `format: "json"` + system schema prompt + `thinking` stream tracking | `10s – 180s` |
| **`OPENAI_COMPATIBLE`** | `POST <url>/chat/completions` (`stream: true`, SSE) | System schema prompt + SSE delta/reasoning tracking (`delta.reasoning_content`) | `10s – 300s+` |

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

## 3. LM Studio / vLLM / OpenAI-Compatible Servers

1. Start your local inference server (e.g. LM Studio Local Server on port `1234`, or a Tailscale/Cloudflare HTTPS proxy).
2. In ShellCraft **Settings → LLM Backend**, select **OpenAI-Compatible API**:
   - **Endpoint URL**: Base URL such as `http://192.168.1.50:1234` or `https://api.example.com/v1` (ShellCraft automatically avoids double `/v1` if `/v1` or `/openai` is already present).
   - **Model Name**: Exact model ID served by `/models` (e.g. `qwen/qwen3.6-35b-a3b`, `google/gemma-4-12b-qat`).
   - **Bearer API Key**: Optional; encrypted locally with `AndroidKeyStore`.
3. Tap **Test Connection** (`GET /v1/models`) to confirm reachability.

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
