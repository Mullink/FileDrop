<#
.SYNOPSIS
  Test LiquorBee Mobile against HennyAdmin running on this PC (no publish needed).

.DESCRIPTION
  1. Starts HennyAdmin's API (launch profile "HennyAdmin API (local phone)", http://localhost:5227) in a new window.
     It uses HennyAdmin's own appsettings (same dev database as the deployed dev site) and a separate
     local Hangfire schema, so it doesn't fight the deployed site over jobs.
  2. Runs "adb reverse" so the phone's localhost:5227 is this PC's localhost:5227 (USB or wireless adb).
  3. Builds and installs the "local" build: a separate app called "LB Local" next to the normal LB Mobile.

  Online UPC lookups need an OpenRouter key on this PC. Set it once (it is never stored in either repo):
      [Environment]::SetEnvironmentVariable("OPENROUTER_API_KEY", "<key>", "User")
  then open a new terminal. Without it everything else works and lookups say they aren't available.

  adb reverse resets when the phone reconnects; rerun with -SkipServer -SkipInstall to restore it.

.EXAMPLE
  .\scripts\run-local.ps1
  .\scripts\run-local.ps1 -Device 192.168.40.235:38199
  .\scripts\run-local.ps1 -SkipServer -SkipInstall   # just redo adb reverse
#>
param(
    [string]$Device,
    [string]$HennyAdminRepo = "C:\Users\Micha\source\repos\HennyAdminOnline",
    [int]$Port = 5227,
    [switch]$SkipServer,
    [switch]$SkipInstall
)
$ErrorActionPreference = "Stop"
$mobileRoot = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] }
if (-not $Device) {
    if (@($devices).Count -eq 0) { throw "No phone connected. Pair/connect it with adb first." }
    # Wireless adb often lists the same phone twice (ip:port and an mDNS name); prefer the ip:port entry.
    $Device = @($devices | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+:\d+$' })[0]
    if (-not $Device) { $Device = @($devices)[0] }
    if (@($devices).Count -gt 1) { Write-Host "Several devices listed ($($devices -join ', ')); using $Device. Pass -Device to choose." }
}
Write-Host "Phone: $Device"

if (-not $SkipServer) {
    $project = Join-Path $HennyAdminRepo "HennyAdmin\HennyAdmin.WebApi.csproj"
    if (-not (Test-Path $project)) { throw "HennyAdmin project not found at $project" }
    if (-not $env:OPENROUTER_API_KEY -and -not [Environment]::GetEnvironmentVariable("OPENROUTER_API_KEY", "User")) {
        Write-Warning "OPENROUTER_API_KEY isn't set, so online UPC lookups will report 'not available'. See Get-Help .\scripts\run-local.ps1"
    }
    Write-Host "Starting HennyAdmin API on http://localhost:$Port in a new window..."
    Start-Process powershell -ArgumentList @("-NoExit", "-Command",
        "`$host.UI.RawUI.WindowTitle = 'HennyAdmin local API'; if (-not `$env:OPENROUTER_API_KEY) { `$env:OPENROUTER_API_KEY = [Environment]::GetEnvironmentVariable('OPENROUTER_API_KEY', 'User') }; dotnet run --project `"$project`" --launch-profile `"HennyAdmin API (local phone)`"")
}

& $adb -s $Device reverse "tcp:$Port" "tcp:$Port" | Out-Null
Write-Host "adb reverse: phone localhost:$Port -> this PC localhost:$Port"

if (-not $SkipInstall) {
    Push-Location $mobileRoot
    try {
        $env:ANDROID_SERIAL = $Device
        & .\gradlew.bat installLocal "-PlbApiUrl=http://localhost:$Port/"
        if ($LASTEXITCODE -ne 0) { throw "Build/install failed." }
    } finally { Pop-Location; Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue }
    Write-Host "Installed 'LB Local'. Open it on the phone and sign in as usual; it talks to this PC."
}
