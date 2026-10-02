# Runs the bridge and restarts it when it asks to (exit 75: the set of addresses it should serve
# changed, e.g. new Wi-Fi). Started by the "Hermes Mobile Remote" Scheduled Task; also fine to run
# by hand. Any other exit code is left to the task's own restart policy.
param([Parameter(Mandatory)][string]$Bin, [Parameter(Mandatory)][string]$LogFile)

$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force -Path (Split-Path $LogFile) | Out-Null

while ($true) {
    if ((Test-Path $LogFile) -and (Get-Item $LogFile).Length -gt 2MB) {
        Move-Item -Force $LogFile "$LogFile.1"
    }
    # Windows PowerShell 5.1's `*>>` writes UTF-16, which most tools show as garbage. Stringify each
    # line and append it as UTF-8 instead.
    & $Bin serve 2>&1 | ForEach-Object { "$_" } | Out-File -FilePath $LogFile -Append -Encoding utf8
    $code = $LASTEXITCODE
    if ($code -ne 75) { exit $code }
    Start-Sleep -Seconds 2
}
