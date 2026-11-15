# ShellCraft Plugin Architecture & Developer SDK (`shellcraft.plugin.v1`)

ShellCraft uses a declarative JSON manifest specification (**`shellcraft.plugin.v1`**) so both human developers and AI/documentation agents can author, share, import, and synchronize consistent Material 3 GUIs for any command-line utility without writing Android code or recompiling the app.

---

## Design Philosophy

1. **Uniform Interaction Across Every CLI Tool** — Whether controlling `docker`, `systemctl`, `brew`, `rsync`, or an internal script, users interact with consistent action cards, typed parameter widgets, safety badges, and formatted output cards.
2. **Zero-Code Extensibility** — Plugins are pure data (`JSON`). No executable code (`.dex`, `.jar`, scripts) is ever downloaded or evaluated dynamically.
3. **Deterministic Command Assembly** — Commands are constructed strictly from typed manifests using single-quote POSIX shell escaping (`PluginCommandBuilder.posixQuote`).
4. **Check-Before-Generate Collaboration** — Shared manifests live as `<commandName>.json` files inside a GitHub repository so teams avoid duplicate LLM generation.

---

## Schema Reference (`shellcraft.plugin.v1`)

### Root Object (`CliPlugin`)

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `schemaVersion` | `string` | Yes | Always `"shellcraft.plugin.v1"`. |
| `id` | `string` | Yes | Unique plugin identifier (e.g. `"builtin-systemctl"`, `"community-brew"`). |
| `commandName` | `string` | Yes | Base executable name (e.g. `"brew"`, `"docker"`, `"ufw"`). |
| `displayName` | `string` | Yes | Human-friendly title rendered in cards and headers. |
| `description` | `string` | Yes | Concise 1-sentence summary of the tool's purpose. |
| `category` | `string` | No | Grouping label (`"System"`, `"Network"`, `"Containers"`, `"Files"`, `"Security"`, `"Diagnostics"`). Default: `"System"`. |
| `targetOs` | `enum` | No | One of `POSIX`, `LINUX`, `MACOS`, `WINDOWS_POSIX`. Default: `POSIX`. |
| `version` | `string` | No | Semantic version string (default `"1.0.0"`). |
| `author` | `string` | No | Creator or agent attribution (default `"ShellCraft"`). |
| `source` | `enum` | No | Managed by ShellCraft (`BUILT_IN`, `AI_GENERATED`, `GITHUB_SYNC`, `USER_IMPORTED`). |
| `actions` | `PluginAction[]` | Yes | 1 or more action modes ordered from safest read-only inspection to administrative mutations. |

---

### Action Object (`PluginAction`)

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `id` | `string` | Yes | Unique `snake_case` identifier within the plugin. |
| `label` | `string` | Yes | Short button/card label (e.g. `"Unit Status"`, `"List Containers"`). |
| `description` | `string` | No | Explanatory subtitle displayed on the action card and detail header. |
| `subcommand` | `string` | No | Subcommand appended after `commandName` (e.g. `"system prune"`). **Do not** repeat `commandName`. |
| `riskLevel` | `enum` | No | `SAFE`, `MODERATE`, `HIGH`, or `CRITICAL`. Default: `SAFE`. |
| `requiresSudo` | `boolean` | No | Pre-enables superuser elevation & biometric/password gating. Default: `false`. |
| `outputHint` | `string` | No | Rendering/execution hint: `"text"`, `"table"`, `"json"`, or `"interactive"`. Default: `"text"`. |
| `parameters` | `PluginParameter[]` | No | Ordered list of UI controls mapped to CLI flags or positional operands. |

---

### Parameter Object (`PluginParameter`)

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `id` | `string` | Yes | `snake_case` parameter identifier. |
| `label` | `string` | Yes | Human-readable form label. |
| `description` | `string` | No | Helper subtitle shown beneath the control. |
| `type` | `enum` | Yes | Determines which Material 3 control is rendered (see table below). |
| `flag` | `string?` | No | Flag prefix such as `"-n"`, `"--tail"`, or `"--format="`. Omit (`null`) for positional arguments. |
| `positionalIndex` | `int?` | No | 0-based order when `flag` is `null`. Positionals are appended after all flags. |
| `required` | `boolean` | No | Disables execution and highlights missing fields until populated. Default: `false`. |
| `defaultValue` | `string` | No | Initial control value (`"true"`/`"false"` for `BOOLEAN_FLAG`). Default: `""`. |
| `placeholder` | `string` | No | Input hint displayed when empty. |
| `options` | `string[]` | Conditional | Allowed choices when `type` is `SELECT_ENUM`. |
| `validationRegex` | `string?` | No | Optional pattern hint for field validation. |

---

### Parameter Types & UI Control Mapping

| `ParameterType` | Rendered Material 3 Control | CLI Serialization Behavior |
| :--- | :--- | :--- |
| `BOOLEAN_FLAG` | Material `Switch` inside a highlighted card | Emits `flag` only when value is `"true"`. |
| `NUMBER` | Haptic `-` / `+` stepper buttons + numeric field | Emits `flag <value>` (or `<value>` if positional). |
| `PORT` | Haptic `-` / `+` stepper + quick port presets | Emits `flag <port>` (or `<port>` if positional). |
| `SELECT_ENUM` | Segmented `FilterChip` pills (`<= 4` options) or exposed dropdown (`> 4`) | Emits `flag 'value'` with POSIX quoting. |
| `PATH` | Text field + quick POSIX path chips (`~`, `/var/log`, `/etc`, `/tmp`) | Emits POSIX-quoted path argument. |
| `KEY_VALUE` | Monospace key/value input (`KEY=VALUE`) | Emits POSIX-quoted pair. |
| `TEXT` | Single-line text field | Emits `flag 'value'` or positional `'value'`. |

---

## Deterministic Command Construction

`PluginCommandBuilder.buildCommand(...)` builds remote commands safely:

1. Prepends `sudo` if the user enabled **Elevate with sudo** or `action.requiresSudo == true`.
2. Appends `plugin.commandName` and whitespace-split `action.subcommand` tokens.
3. Emits all non-empty **flagged** parameters first (`flag=value` if `flag` ends with `=`, otherwise `flag` followed by `posixQuote(value)`).
4. Emits all non-empty **positional** parameters sorted ascending by `positionalIndex`.
5. `posixQuote(raw)` passes safe tokens (`^[a-zA-Z0-9._/:=@+-]+$`) unquoted and wraps anything containing spaces, quotes, `$`, `;`, or special characters in POSIX single quotes (`'...'`) with internal single quotes escaped (`'"'"'`).

---

## Interactive Shells & Output Hints

Commands execute over an SSH **`vt100` PTY** (`SshEngine`):

- **Interactive Prompts (`"outputHint": "interactive"`)**: When a running CLI pauses for input (`[y/N]`, `(yes/no)`, `Password:`, `Press RETURN`, or stalled partial-line prompts ending in `:`, `?`, `>`, `#`), `InteractivePromptDetector` displays one-tap confirmation buttons, masked password inputs, `Enter ↵`, `Ctrl+D`, and `Ctrl+C` chips directly inside the terminal drawer and inline runner card.
- **Formatted Output Cards (`OutputFormatter`)**: Completed outputs automatically render rich views for ICMP `ping` stats, `systemctl status` service health, whitespace/tab-separated tables, structured JSON, key-value metrics, and severity-highlighted logs.

---

## Complete Example Plugin Manifest

Save as `plugins/nginx.json` (or paste via **Plugins → Schema / Import**):

```json
{
  "schemaVersion": "shellcraft.plugin.v1",
  "id": "community-nginx",
  "commandName": "nginx",
  "displayName": "Nginx Web Server",
  "description": "Validate configuration syntax and control Nginx worker processes.",
  "category": "Network",
  "targetOs": "POSIX",
  "version": "1.0.0",
  "author": "ShellCraft Community",
  "actions": [
    {
      "id": "test_config",
      "label": "Test Configuration",
      "description": "Validate nginx.conf syntax without reloading workers.",
      "subcommand": "",
      "riskLevel": "SAFE",
      "requiresSudo": true,
      "outputHint": "text",
      "parameters": [
        {
          "id": "test",
          "label": "Syntax Check (-t)",
          "type": "BOOLEAN_FLAG",
          "flag": "-t",
          "defaultValue": "true"
        },
        {
          "id": "conf_path",
          "label": "Custom Config Path (-c)",
          "type": "PATH",
          "flag": "-c",
          "placeholder": "/etc/nginx/nginx.conf"
        }
      ]
    },
    {
      "id": "signal_reload",
      "label": "Graceful Reload",
      "description": "Send reload/stop/reopen signal to master process.",
      "subcommand": "",
      "riskLevel": "MODERATE",
      "requiresSudo": true,
      "parameters": [
        {
          "id": "signal",
          "label": "Master Signal (-s)",
          "type": "SELECT_ENUM",
          "flag": "-s",
          "required": true,
          "defaultValue": "reload",
          "options": ["reload", "reopen", "quit", "stop"]
        }
      ]
    }
  ]
}
```

---

## Third-Party & AI Agent Contribution Guide

Automated doc agents and contributors can expand the shared ecosystem effortlessly:

1. Inspect `<tool> --help` and `man <tool>` on the target OS.
2. Emit a valid `shellcraft.plugin.v1` JSON file naming safe inspection actions first and administrative/destructive actions last with accurate `riskLevel` and `requiresSudo` flags.
3. Commit the file to `plugins/<commandName>.json` on your repository's configured branch (`main` by default).
4. Any connected ShellCraft client checks `<pluginsDirectory>/<commandName>.json` automatically on sync and prior to LLM synthesis.
