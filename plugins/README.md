# Shared ShellCraft Plugins Directory (`plugins/`)

ShellCraft can synchronize plugin manifests from **any GitHub repository** — including the **same repository** that hosts this Android application! Keeping plugins under `plugins/` alongside the app keeps one clean, versioned repo for both code and community/team CLI integrations.

---

## How Check-Before-Generate Works

1. Open **Settings → GitHub Plugin Repository Sync** in ShellCraft.
2. Enable sync and enter:
   - **Repository (`owner/repo`)**: Your GitHub repository (e.g. `your-user/shellcraft`)
   - **Branch**: `main`
   - **Directory**: `plugins`
   - **Sync Policy**: `AUTOMATIC` *(default)*, `INTERVAL_HOURLY`, `INTERVAL_DAILY`, `ON_SSH_CONNECT`, or `MANUAL_ONLY`
   - **GitHub PAT**: Optional for reading public repos; required (`repo` / `Contents: Read & Write`) for private repos or publishing from the app.
3. Before invoking any LLM in **Forge**, ShellCraft requests:
   ```text
   GET https://api.github.com/repos/<owner>/<repo>/contents/plugins/<commandName>.json?ref=<branch>
   ```
   If found, the existing GUI loads instantly with zero LLM tokens used.

---

## File Naming Convention

- Store each CLI integration as lowercase `<commandName>.json`:
  - `plugins/brew.json`
  - `plugins/docker.json`
  - `plugins/ufw.json`
  - `plugins/git.json`
- See [`../docs/PLUGIN_ARCHITECTURE.md`](../docs/PLUGIN_ARCHITECTURE.md) for the complete `shellcraft.plugin.v1` JSON schema.
