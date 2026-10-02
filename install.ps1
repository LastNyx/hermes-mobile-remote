<#
.SYNOPSIS
    Install the Hermes Mobile Remote bridge on this PC (Windows 10/11, runs as a Scheduled Task).

.DESCRIPTION
    .\install.ps1              install or update, start the task, offer firewall + pairing
    .\install.ps1 -InstallUv   install uv without asking if it is missing
    .\install.ps1 -Unattended  never prompt (answers "no" to every question; for CI)
    .\install.ps1 -Uninstall   stop and remove the task and the firewall rule
                               (keeps paired devices and config)

    Safe to re-run. Needs no admin rights except for the optional firewall rule, which asks first
    (one UAC prompt). If PowerShell blocks the script, run it as:
        powershell -ExecutionPolicy Bypass -File .\install.ps1

    EXPERIMENTAL: written against Microsoft's documented cmdlets, not yet run on real hardware by
    its author. See docs\PLATFORMS.md for what is verified and what is not.
#>
[CmdletBinding()]
param([switch]$Uninstall, [switch]$Unattended, [switch]$InstallUv)

$ErrorActionPreference = 'Stop'
$Repo     = $PSScriptRoot
$Bridge   = Join-Path $Repo 'bridge'
$TaskName = 'Hermes Mobile Remote'
$Local    = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { Join-Path $HOME 'AppData\Local' }
$ConfDir  = Join-Path $Local 'hermes-remote\config'
$StateDir = Join-Path $Local 'hermes-remote\state'
$HermesHome = if ($env:HERMES_HOME) { $env:HERMES_HOME } else { Join-Path $Local 'hermes' }
$Bin      = Join-Path $Bridge '.venv\Scripts\hermes-remote-bridge.exe'

function Bold($t) { Write-Host $t -ForegroundColor White }
function Info($t) { Write-Host "  $t" }
function Die($t)  { Write-Host "Error: $t" -ForegroundColor Red; exit 1 }
function Ask($q)  { if ($Unattended) { return $false }; $a = Read-Host "  $q [Y/n]"; return ($a -eq '' -or $a -match '^[Yy]') }

# A fresh install of uv (or anything else) edits the PATH stored in the registry, which the running
# PowerShell never re-reads. Pull the persistent entries into this session so the next command
# can find it without asking the user to reopen the terminal.
function Update-SessionPath {
    $have = @($env:Path -split ';')
    $stored = @([Environment]::GetEnvironmentVariable('Path', 'Machine'),
                [Environment]::GetEnvironmentVariable('Path', 'User'),
                (Join-Path $HOME '.local\bin'), (Join-Path $HOME '.cargo\bin')) -join ';'
    foreach ($p in ($stored -split ';')) {
        if ($p -and ($have -notcontains $p)) { $env:Path += ";$p" }
    }
}

function Install-Uv {
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Info 'Installing uv with winget...'
        try { winget install --id=astral-sh.uv -e --accept-source-agreements --accept-package-agreements | Out-Host } catch { }
        Update-SessionPath
        if (Get-Command uv -ErrorAction SilentlyContinue) { return $true }
    }
    Info 'Installing uv with the official installer (astral.sh)...'
    try { Invoke-RestMethod https://astral.sh/uv/install.ps1 | Invoke-Expression } catch { Info "That failed: $_" }
    Update-SessionPath
    return [bool](Get-Command uv -ErrorAction SilentlyContinue)
}

if ($Uninstall) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Get-Process -Name hermes-remote-bridge -ErrorAction SilentlyContinue | Stop-Process -Force
    if (Get-NetFirewallRule -DisplayName $TaskName -ErrorAction SilentlyContinue) {
        Info 'A firewall rule named "Hermes Mobile Remote" exists; removing it needs admin (UAC).'
        $rm = "Remove-NetFirewallRule -DisplayName '$TaskName'"
        Start-Process powershell -Verb RunAs -Wait -ArgumentList '-NoProfile', '-Command', $rm
    }
    Write-Host "Task removed. Paired devices and settings are still in $ConfDir (delete it to forget them)."
    exit 0
}

Bold '1/5 Checking requirements'
if ($PSVersionTable.PSVersion.Major -lt 5) { Die 'PowerShell 5.1 or newer is required.' }
Update-SessionPath
if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    Info 'uv (the Python package manager the bridge is installed with) was not found.'
    if ($InstallUv -or (Ask 'Install it now? (no admin needed)')) {
        if (-not (Install-Uv)) {
            Die 'Could not install uv. Install it yourself from https://docs.astral.sh/uv/getting-started/installation/ , open a NEW PowerShell window, and run this again.'
        }
        Info 'uv installed.'
    } else {
        Die 'uv is required. Run again with -InstallUv, or install it from https://docs.astral.sh/uv/getting-started/installation/ (winget install --id=astral-sh.uv) and open a NEW PowerShell window.'
    }
}
if (-not (Test-Path $HermesHome)) {
    Die "Hermes Agent not found at $HermesHome. Install Hermes first: https://hermes-agent.nousresearch.com"
}
if (-not (Get-Command tailscale -ErrorAction SilentlyContinue) -and
    -not (Test-Path (Join-Path $env:ProgramFiles 'Tailscale\tailscale.exe'))) {
    Info 'Optional: Tailscale is not installed; the phone will only connect on trusted Wi-Fi.'
}
Info 'ok'

Bold '2/5 Hermes API server'
$EnvFile = Join-Path $HermesHome '.env'
if (-not (Test-Path $EnvFile)) { New-Item -ItemType File -Path $EnvFile | Out-Null }
$lines = @(Get-Content $EnvFile -ErrorAction SilentlyContinue)
$changed = $false
if (-not ($lines -match '^API_SERVER_ENABLED=true')) {
    $lines = @($lines | Where-Object { $_ -notmatch '^API_SERVER_ENABLED=' }) + 'API_SERVER_ENABLED=true'
    $changed = $true
}
if (-not ($lines -match '^API_SERVER_KEY=.+') -or ($lines -match '^API_SERVER_KEY=change-me')) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $key = ([Convert]::ToBase64String($bytes) -replace '[/+=]', '').Substring(0, 40)
    $lines = @($lines | Where-Object { $_ -notmatch '^API_SERVER_KEY=' }) + "API_SERVER_KEY=$key"
    $changed = $true
}
if ($changed) {
    # UTF-8 without a BOM: a BOM would glue itself to the first variable name.
    [System.IO.File]::WriteAllLines($EnvFile, [string[]]$lines, (New-Object System.Text.UTF8Encoding($false)))
    Info "Enabled the API server with a fresh key in $EnvFile (loopback only; the key never leaves this PC)."
    Info 'Restarting the Hermes gateway so it picks this up...'
    try { hermes gateway restart *> $null } catch { Info "Couldn't restart it; run 'hermes gateway restart' (or start Hermes) yourself." }
} else {
    Info 'already enabled'
}

Bold '3/5 Installing the bridge'
Push-Location $Bridge
try { uv sync --quiet --no-dev --inexact; if ($LASTEXITCODE -ne 0) { Die 'uv sync failed' } } finally { Pop-Location }
New-Item -ItemType Directory -Force -Path $ConfDir, $StateDir | Out-Null
$ConfFile = Join-Path $ConfDir 'config.toml'
if (-not (Test-Path $ConfFile)) {
    Set-Content -Path $ConfFile -Encoding ASCII -Value "# See bridge\hermes_remote_bridge\config.py for all keys.`nlan = true"
}
Info "installed into $Bridge\.venv"

Bold '4/5 Background task'
$script = Join-Path $Bridge 'windows\run-bridge.ps1'
$log    = Join-Path $StateDir 'bridge.log'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument (
    "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" -Bin `"$Bin`" -LogFile `"$log`"")
$trigger  = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings `
    -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $TaskName
Info "running; starts at every logon (log: $log)"

Bold '5/5 Network'
if (Ask 'Is this your home or office network (let the phone connect over this Wi-Fi without Tailscale)?') {
    try { & $Bin trust } catch { Info "Couldn't trust it yet: $_" }
}
try { & $Bin firewall --if-needed } catch { }

Write-Host ''
try { & $Bin doctor } catch { }
Write-Host ''
Bold 'Next: pair your phone'
Info 'Install the app (APK from the GitHub releases page), then run:'
Info "  & '$Bin' pair phone"
Info 'and scan the QR code with the app.'
