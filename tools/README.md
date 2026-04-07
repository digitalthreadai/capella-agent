# Capella Agent Dev Tools

Helper scripts that live **outside** all OSGi bundles. They are not packaged into
the feature, not built by Maven, and not shipped to end users. Edit / delete /
ignore freely.

## reinstall.ps1 / reinstall.bat

One-command replacement for the manual GUI loop you used to run after every
code change:

```
Help -> About -> Installation Details -> Uninstall com.capellaagent.feature
  -> Restart -> Install New Software -> Add -> Local -> repository folder
  -> Install -> Restart
```

> **Why this matters beyond convenience.** The manual GUI install (and `-Mode director`)
> writes into Capella's own `p2repo/` folder — the same folder Tycho uses as its
> target platform during `mvn clean verify`. Doing both repeatedly poisons the
> local p2 metadata and breaks future builds with cryptic errors like
> `Could not mirror artifact com.capellaagent.core.ui ... file not found`.
> The default **dropins** mode never touches `p2repo/`, so it cannot cause this
> collision. Use dropins for day-to-day work; reserve director / GUI for the
> rare clean-install case.

### One-time setup

1. Find your Capella install (the folder containing `capella.exe`). Common paths:
   - `C:\Capella\capella`
   - `C:\Program Files\Capella\capella`
   - Wherever you unzipped the Capella 7.0.1 zip
2. Tell the script where it lives. Pick **one**:
   - **Env var (recommended):** `setx CAPELLA_HOME "C:\Capella\capella"` then open a new shell
   - **Edit the script:** open `reinstall.ps1` and change `$DefaultCapellaHome`
   - **Per-call flag:** `reinstall.bat -CapellaHome "C:\Capella\capella"`
3. (First run only) If PowerShell complains about execution policy:
   `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` — the `.bat` shim already
   passes `-ExecutionPolicy Bypass` so this is usually unnecessary.

### Two modes

| Mode | When to use | Speed | What it does |
|---|---|---|---|
| `dropins` (default) | Day-to-day Java edits | ~30 s | `mvn package -DskipTests` -> copies jars to `<capella>/dropins/capella-agent/plugins/` -> wipes OSGi cache -> done |
| `director` | After feature.xml / plugin.xml / version changes, or anytime you want a clean p2 reinstall | ~2 min | `mvn clean verify` -> kills Capella -> p2 director uninstall -> p2 director install from `com.capellaagent.site/target/repository` |

### Usage

```powershell
# Default: dropins mode, build + refresh, do NOT launch
.\reinstall.bat

# Skip the build, just copy the existing target/ jars
.\reinstall.bat -SkipBuild

# Build, refresh, AND launch Capella
.\reinstall.bat -Launch

# Clean p2 reinstall (matches what the Install/Uninstall wizard does)
.\reinstall.bat -Mode director -Launch

# Verbose Eclipse output for debugging install failures
.\reinstall.bat -Mode director -VerboseEclipse

# Override Capella location for one call
.\reinstall.bat -CapellaHome "D:\tools\capella"

# Install a different feature branch's already-built repository
.\reinstall.bat -Mode director -Repository "C:\Apps\Claude\capella-agent\.claude\worktrees\feature-x\com.capellaagent.site\target\repository" -Launch

# Install from a hosted update site (e.g. CI artifact, GitHub Pages)
.\reinstall.bat -Mode director -Repository "https://digitalthreadai.github.io/capella-agent/p2/" -Launch
```

### Pointing at a specific build / feature branch

The `-Repository` flag (director mode only) overrides what gets installed.
Three ways to set it, in precedence order:

1. **`-Repository` flag** (per-call, wins over everything)
2. **`$env:CAPELLA_AGENT_REPOSITORY`** environment variable (per-shell)
3. **`$DefaultRepository`** constant near the top of `reinstall.ps1` (per-machine)
4. *(fallback)* this worktree's `com.capellaagent.site/target/repository`

Accepted values:
- Absolute folder path: `C:\builds\beta2\repository`
- `file:` URI: `file:/C:/builds/beta2/repository`
- `http(s)://` URL: `https://example.com/p2/capella-agent/`

When an explicit repository is supplied, the Maven build step is **skipped**
automatically — you're installing somebody else's pre-built artifacts. Pass
`-Mode director` (dropins mode always uses local jars and ignores `-Repository`).

Examples:

```powershell
# One-off install of a teammate's branch
.\reinstall.bat -Mode director `
  -Repository "\\fileshare\builds\feature-rag\repository" -Launch

# Switch this shell to always pull from the staging update site
$env:CAPELLA_AGENT_REPOSITORY = "https://staging.example.com/capella-agent/p2/"
.\reinstall.bat -Mode director -Launch
```

### How it works

- **dropins** uses Eclipse's "drop-in plugins" folder. Capella scans
  `<capella>/dropins/` on every launch. Wiping `configuration/org.eclipse.osgi/`
  forces a cache rebuild so updated jars are picked up immediately. This
  bypasses p2 entirely, which is why it's so fast — but it also means p2 has
  no record of the install. If you previously installed via the GUI, run
  `director` mode once first to remove the p2 record.

- **director** uses Eclipse's headless p2 application
  (`org.eclipse.equinox.p2.director`). This is the same machinery the
  Install / Uninstall wizard runs underneath, just driven from the command
  line. The script auto-detects your p2 profile id by reading
  `<capella>/p2/org.eclipse.equinox.p2.engine/profileRegistry/`.

### Troubleshooting

| Symptom | Fix |
|---|---|
| `capella.exe not found in <path>` | Set `CAPELLA_HOME` correctly, or pass `-CapellaHome` |
| `Profile registry not found` | Path points at the wrong folder. You want the inner `capella` folder (with `capella.exe`), not the outer install root |
| Uninstall returns non-zero exit | Usually means "feature wasn't installed" — script ignores this and continues |
| Install fails with `No repository found` | Build the p2 site first: `mvn -pl com.capellaagent.site -am clean verify` |
| Capella starts but the chat view is missing | Try `-Mode director` for a clean p2 install. Dropins mode can collide with a previous p2 install |
| `Set-ExecutionPolicy` errors | Use `reinstall.bat` (the shim) instead of `reinstall.ps1` directly |
| Stale `com.capellaagent.core-1.0.0.20260326*.jar` errors during build | Clear the Tycho cache: `rmdir /s /q "%USERPROFILE%\.m2\repository\p2\osgi\bundle\com.capellaagent.core"` |

### Why this is outside the OSGi bundles

- Not Java code, not part of any plugin
- Not referenced by `feature.xml`, `category.xml`, or any `pom.xml`
- Maven never sees it (no `<module>tools</module>` entry)
- Safe to delete, edit, or replace without touching the build
