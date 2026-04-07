<#
.SYNOPSIS
    Automates uninstall + reinstall of the Capella Agent feature in Eclipse Capella.

.DESCRIPTION
    Replaces the manual GUI loop:
      Help -> Install Details -> Uninstall -> Restart -> Install New Software -> Add Local -> Install -> Restart

    Two modes:
      dropins  (default, fast)  Builds plugin jars and copies them to <capella>/dropins/capella-agent/plugins/
      director (clean p2)        Runs `mvn clean verify` then uses Eclipse p2 director to uninstall + install

.PARAMETER Mode
    dropins | director  (default: dropins)

.PARAMETER SkipBuild
    Skip Maven; reuse jars already in target/ folders.

.PARAMETER Launch
    Start Capella after install completes.

.PARAMETER CapellaHome
    Override autodetection. Folder containing capella.exe.

.PARAMETER Repository
    Override the p2 repository to install from. Accepts:
      - Absolute folder path:  C:\builds\beta2\repository
      - file: URI:             file:/C:/builds/beta2/repository
      - http(s) URL:           https://example.com/p2/capella-agent/
    If unset, defaults to <repo-root>/com.capellaagent.site/target/repository
    (the repository built by the worktree this script lives in).
    Also reads the $env:CAPELLA_AGENT_REPOSITORY environment variable.

.PARAMETER VerboseEclipse
    Pass -debug -consoleLog to Eclipse during director runs.

.EXAMPLE
    .\reinstall.ps1
        Default: dropins mode, builds and refreshes jars, no relaunch.

.EXAMPLE
    .\reinstall.ps1 -Mode director -Launch
        Clean p2 reinstall and relaunch Capella.

.EXAMPLE
    .\reinstall.ps1 -SkipBuild -Launch
        Reuse last build, copy jars, relaunch.
#>

[CmdletBinding()]
param(
    [ValidateSet('dropins','director')]
    [string]$Mode = 'dropins',
    [switch]$SkipBuild,
    [switch]$Launch,
    [string]$CapellaHome,
    [string]$Repository,
    [switch]$VerboseEclipse
)

$ErrorActionPreference = 'Stop'

# ============================================================================
# Configuration  -  edit these defaults once for your machine
# ============================================================================

# Default Capella install location if $env:CAPELLA_HOME and -CapellaHome are unset
$DefaultCapellaHome = 'C:\Apps\capella-7.0.1\capella'

# Default p2 repository if -Repository and $env:CAPELLA_AGENT_REPOSITORY are unset.
# Empty string means: use this worktree's freshly built site (the common case).
# Examples for other branches / hosted builds:
#   $DefaultRepository = 'C:\Apps\Claude\capella-agent\.claude\worktrees\feature-x\com.capellaagent.site\target\repository'
#   $DefaultRepository = 'https://digitalthreadai.github.io/capella-agent/p2/'
$DefaultRepository = ''

# Feature IU id (from com.capellaagent.feature/feature.xml)
$FeatureIU = 'com.capellaagent.feature.feature.group'

# Project root = parent of this script's tools/ folder
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# All plugin module folders that produce jars to ship
$PluginModules = @(
    'com.capellaagent.core',
    'com.capellaagent.core.ui',
    'com.capellaagent.modelchat',
    'com.capellaagent.modelchat.ui',
    'com.capellaagent.mcp',
    'com.capellaagent.simulation',
    'com.capellaagent.simulation.ui',
    'com.capellaagent.teamcenter'
)

# ============================================================================
# Helpers
# ============================================================================

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "    [ok] $msg" -ForegroundColor Green
}

function Write-Warn($msg) {
    Write-Host "    [warn] $msg" -ForegroundColor Yellow
}

function Resolve-CapellaHome {
    if ($CapellaHome) { return $CapellaHome }
    if ($env:CAPELLA_HOME) { return $env:CAPELLA_HOME }
    return $DefaultCapellaHome
}

function Resolve-RepositoryUri {
    # Precedence: -Repository flag > $env:CAPELLA_AGENT_REPOSITORY > $DefaultRepository > built-in worktree default
    $raw = $Repository
    if (-not $raw) { $raw = $env:CAPELLA_AGENT_REPOSITORY }
    if (-not $raw) { $raw = $DefaultRepository }
    if (-not $raw) {
        $raw = Join-Path $RepoRoot 'com.capellaagent.site\target\repository'
    }

    # Already a URI? Pass through.
    if ($raw -match '^(file:|https?:|jar:)') {
        return @{ Uri = $raw; Source = "explicit URI" }
    }

    # Local path: validate and convert to file: URI with forward slashes
    if (-not (Test-Path $raw)) {
        throw "Repository path '$raw' does not exist. Build it first or pass -Repository <path|url>."
    }
    if (-not (Test-Path (Join-Path $raw 'content.jar')) `
        -and -not (Test-Path (Join-Path $raw 'content.xml')) `
        -and -not (Test-Path (Join-Path $raw 'compositeContent.xml'))) {
        throw "'$raw' does not look like a p2 repository (no content.jar / content.xml / compositeContent.xml)."
    }
    $abs = (Resolve-Path $raw).Path
    $uri = 'file:/' + ($abs -replace '\\','/')
    return @{ Uri = $uri; Source = $abs }
}

function Stop-Capella {
    $procs = Get-Process -Name capella -ErrorAction SilentlyContinue
    if ($procs) {
        Write-Step "Stopping running Capella instance(s)"
        $procs | Stop-Process -Force
        Start-Sleep -Seconds 2
        Write-Ok "Capella stopped"
    }
}

function Get-CapellaProfile($capellaHome) {
    $registry = Join-Path $capellaHome 'p2\org.eclipse.equinox.p2.engine\profileRegistry'
    if (-not (Test-Path $registry)) {
        throw "Profile registry not found at $registry. Is $capellaHome really a Capella install?"
    }
    $profiles = Get-ChildItem $registry -Filter '*.profile' -Directory
    if ($profiles.Count -eq 0) {
        throw "No *.profile directory found in $registry"
    }
    # The folder name is "<profile>.profile"; profile id is the bit before .profile
    return ($profiles[0].Name -replace '\.profile$','')
}

function Run-Maven($mvnArgs) {
    Push-Location $RepoRoot
    try {
        $cmd = if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { 'mvn.cmd' } else { 'mvn' }
        & $cmd @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

# ============================================================================
# Mode A:  dropins  (fast iteration)
# ============================================================================

function Invoke-DropinsMode {
    $capellaHome = Resolve-CapellaHome
    Write-Step "Capella home: $capellaHome"
    if (-not (Test-Path (Join-Path $capellaHome 'capella.exe'))) {
        throw "capella.exe not found in $capellaHome"
    }

    if (-not $SkipBuild) {
        Write-Step "Building plugin jars (mvn package -DskipTests)"
        $modulesArg = ($PluginModules -join ',')
        Run-Maven @('-pl', $modulesArg, '-am', 'clean', 'package', '-DskipTests')
        Write-Ok "Build complete"
    } else {
        Write-Warn "SkipBuild: reusing existing target/ jars"
    }

    Stop-Capella

    $dropinsRoot = Join-Path $capellaHome 'dropins\capella-agent'
    $dropinsPlugins = Join-Path $dropinsRoot 'plugins'

    Write-Step "Refreshing $dropinsRoot"
    if (Test-Path $dropinsRoot) {
        Remove-Item $dropinsRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dropinsPlugins -Force | Out-Null

    foreach ($module in $PluginModules) {
        $targetDir = Join-Path (Join-Path $RepoRoot $module) 'target'
        if (-not (Test-Path $targetDir)) {
            Write-Warn "$module has no target/ folder, skipping"
            continue
        }
        # Pick the main plugin jar: <module>-<version>.jar, exclude -sources, -tests, original-
        $jar = Get-ChildItem $targetDir -Filter "$module-*.jar" -File `
            | Where-Object { $_.Name -notlike '*-sources.jar' `
                           -and $_.Name -notlike '*-tests.jar' `
                           -and $_.Name -notlike 'original-*' } `
            | Sort-Object LastWriteTime -Descending `
            | Select-Object -First 1
        if (-not $jar) {
            Write-Warn "No jar found in $targetDir for $module"
            continue
        }
        Copy-Item $jar.FullName $dropinsPlugins
        Write-Ok "Copied $($jar.Name)"
    }

    Write-Step "Wiping OSGi cache"
    $osgi = Join-Path $capellaHome 'configuration\org.eclipse.osgi'
    if (Test-Path $osgi) {
        Remove-Item $osgi -Recurse -Force -ErrorAction SilentlyContinue
        Write-Ok "OSGi cache cleared"
    }

    if ($Launch) {
        Write-Step "Launching Capella with -clean"
        Start-Process (Join-Path $capellaHome 'capella.exe') -ArgumentList '-clean'
        Write-Ok "Launched"
    } else {
        Write-Host ""
        Write-Host "Done. Start Capella manually (or rerun with -Launch)." -ForegroundColor Green
    }
}

# ============================================================================
# Mode B:  director  (clean p2 install/uninstall)
# ============================================================================

function Invoke-DirectorMode {
    $capellaHome = Resolve-CapellaHome
    Write-Step "Capella home: $capellaHome"
    $capellaExe = Join-Path $capellaHome 'capella.exe'
    if (-not (Test-Path $capellaExe)) {
        throw "capella.exe not found in $capellaHome"
    }

    $profile = Get-CapellaProfile $capellaHome
    Write-Ok "Detected p2 profile: $profile"

    # An explicit -Repository or $env:CAPELLA_AGENT_REPOSITORY means "use that, don't build"
    $repoExplicit = [bool]$Repository -or [bool]$env:CAPELLA_AGENT_REPOSITORY -or [bool]$DefaultRepository
    if ($repoExplicit) {
        Write-Warn "External repository specified; skipping local build"
    } elseif (-not $SkipBuild) {
        Write-Step "Building p2 repository (mvn clean verify)"
        Run-Maven @('clean', 'verify')
        Write-Ok "Build complete"
    } else {
        Write-Warn "SkipBuild: reusing existing com.capellaagent.site/target/repository"
    }

    $repoInfo = Resolve-RepositoryUri
    $repoUri = $repoInfo.Uri
    Write-Ok "Repository: $($repoInfo.Source)"

    Stop-Capella

    $commonArgs = @(
        '-nosplash',
        '-application', 'org.eclipse.equinox.p2.director',
        '-destination', $capellaHome,
        '-profile', $profile,
        '-consoleLog'
    )
    if ($VerboseEclipse) { $commonArgs += '-debug' }

    Write-Step "Uninstalling $FeatureIU (ignored if not installed)"
    $uninstallArgs = $commonArgs + @('-uninstallIU', $FeatureIU)
    & $capellaExe @uninstallArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Uninstall returned exit code $LASTEXITCODE (likely 'not installed', continuing)"
    } else {
        Write-Ok "Uninstall complete"
    }

    Write-Step "Installing $FeatureIU from $repoUri"
    $installArgs = $commonArgs + @('-repository', $repoUri, '-installIU', $FeatureIU)
    & $capellaExe @installArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Install failed with exit code $LASTEXITCODE"
    }
    Write-Ok "Install complete"

    if ($Launch) {
        Write-Step "Launching Capella"
        Start-Process $capellaExe
        Write-Ok "Launched"
    } else {
        Write-Host ""
        Write-Host "Done. Start Capella manually (or rerun with -Launch)." -ForegroundColor Green
    }
}

# ============================================================================
# Main
# ============================================================================

Write-Host "Capella Agent reinstall  -  mode: $Mode" -ForegroundColor Magenta
Write-Host "Repo root: $RepoRoot"

switch ($Mode) {
    'dropins'  { Invoke-DropinsMode }
    'director' { Invoke-DirectorMode }
}
