<#
Read versionCode from a Quantic APK and optionally write LiquorBeePOS/version.txt.
This script never modifies/signs the APK or commits/pushes repository changes.
Use -DownloadLatest to inspect the published iMin APK instead of a local file.
#>
[CmdletBinding(DefaultParameterSetName = 'Local')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
    [string]$ApkPath,
    [Parameter(Mandatory = $true, ParameterSetName = 'Download')]
    [switch]$DownloadLatest,
    [string]$VersionFile,
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$temporaryApk = $null
try {
    $buildToolsPath = Join-Path $AndroidSdk 'build-tools'
    $aapt = Get-ChildItem -LiteralPath $buildToolsPath -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if (-not $aapt) { throw "aapt2.exe was not found under $buildToolsPath" }

    if ($DownloadLatest) {
        $temporaryApk = Join-Path ([IO.Path]::GetTempPath()) ("liquorbee-pos-" + [guid]::NewGuid() + '.apk')
        Invoke-WebRequest -Uri 'https://portal.liquorbee.com/download/liquorbeepos/imin.apk' -OutFile $temporaryApk
        $ApkPath = $temporaryApk
    }
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    $badging = (& $aapt dump badging $resolvedApk 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 could not inspect the APK.' }
    $package = [regex]::Match($badging, "(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'")
    if (-not $package.Success) { throw 'APK package/version metadata was not found.' }
    if ($package.Groups[1].Value -ne 'com.liquorbee.liquorbeepos') {
        throw 'This is not the LiquorBee POS APK. The version file was not changed.'
    }
    $code = [long]$package.Groups[2].Value
    if ($code -le 0) { throw 'APK versionCode must be positive.' }
    if ($VersionFile) {
        $versionTarget = [IO.Path]::GetFullPath($VersionFile)
        if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($versionTarget)) -PathType Container)) {
            throw 'The version file parent directory must already exist.'
        }
        if (Test-Path -LiteralPath $versionTarget) {
            $oldText = (Get-Content -LiteralPath $versionTarget -Raw).Trim()
            $oldCode = 0L
            if ([long]::TryParse($oldText, [ref]$oldCode) -and $oldCode -gt $code) {
                throw "Refusing to lower the published build from $oldCode to $code."
            }
        }
        [IO.File]::WriteAllText($versionTarget, "$code`n", (New-Object System.Text.UTF8Encoding($false)))
        Write-Output "Updated $versionTarget"
    }
    Write-Output ("LiquorBee POS " + $package.Groups[3].Value + " | versionCode=" + $code)
    Write-Output 'Publish the matching APK before committing the new version.txt value.'
} finally {
    if ($temporaryApk -and (Test-Path -LiteralPath $temporaryApk)) {
        Remove-Item -LiteralPath $temporaryApk -Force
    }
}
