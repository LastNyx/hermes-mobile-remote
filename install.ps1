<#
.SYNOPSIS
    Install the Hermes Mobile Remote bridge on this PC (Windows 10/11, runs as a Scheduled Task).

.DESCRIPTION
    .\install.ps1              install or update, start the task, offer firewall + pairing
    .\install.ps1 -Unattended  never prompt (answers "no" to every question; for CI)
    .\install.ps1 -Uninstall   stop and remove the task and the firewall rule
                               (keeps paired devices and config)

    Safe to re-run. Needs no admin rights except for the optional firewall rule, which asks first
    (one UAC prompt). If PowerShell blocks the script, run it as:
        powershell -ExecutionPolicy Bypass -File .\install.ps1

    EXPERIMENTAL: written against Microsoft's documented cmdlets, not yet run on real hardware by
    its author. See docs\WINDOWS.md for what is verified and what is not.
#>
[CmdletBinding()]
param([switch]$Uninstall, [switch]$Unattended)

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
if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    Die 'uv is required: https://docs.astral.sh/uv/getting-started/installation/  (winget install --id=astral-sh.uv)'
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
