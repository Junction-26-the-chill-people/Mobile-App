[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('show', 'stop', 'install', 'logs', 'random')]
    [string]$Command = 'show',

    [string]$Serial,

    [string]$ApkPath,

    [ValidateRange(90, 86400)]
    [int]$MinIntervalSeconds = 120,

    [ValidateRange(90, 86400)]
    [int]$MaxIntervalSeconds = 300,

    [ValidateRange(0, 2147483647)]
    [int]$Count = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'com.example.junction'
$triggerComponent = "$packageName/.nudge.NudgeTriggerActivity"

if ($Command -eq 'random' -and $MaxIntervalSeconds -lt $MinIntervalSeconds) {
    throw 'MaxIntervalSeconds must be greater than or equal to MinIntervalSeconds.'
}

function Find-Adb {
    $pathCommand = Get-Command 'adb.exe' -ErrorAction SilentlyContinue
    if (-not $pathCommand) {
        $pathCommand = Get-Command 'adb' -ErrorAction SilentlyContinue
    }
    if ($pathCommand) {
        return $pathCommand.Source
    }

    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $localPropertiesPath = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localPropertiesPath |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdkDirectory = ($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
            $sdkDirectory = $sdkDirectory.Replace('\:', ':').Replace('\\', '\')
            $sdkCandidates += $sdkDirectory
        }
    }
    if ($env:LOCALAPPDATA) {
        $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    }

    foreach ($sdkCandidate in $sdkCandidates) {
        if ([string]::IsNullOrWhiteSpace($sdkCandidate)) { continue }
        $candidatePath = Join-Path $sdkCandidate 'platform-tools\adb.exe'
        if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidatePath).Path
        }
    }
    throw 'ADB was not found. Install Android SDK Platform-Tools, add adb to PATH, or set ANDROID_HOME to your SDK directory.'
}

$adbExecutable = Find-Adb

function Invoke-AdbCommand {
    param([string[]]$AdbCommandArguments)

    & $adbExecutable @AdbCommandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB exited with code $LASTEXITCODE. Check that a device is connected, USB debugging is enabled, and this computer is authorized. Use -Serial when multiple devices are connected."
    }
}

$deviceArguments = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $deviceArguments = @('-s', $Serial)
}

switch ($Command) {
    'random' {
        $showArguments = $deviceArguments + @('shell', 'am', 'start', '-n', $triggerComponent)
        $visitsSent = 0
        $countLabel = if ($Count -eq 0) { 'unlimited' } else { [string]$Count }
        Write-Host "Random visits: $MinIntervalSeconds-$MaxIntervalSeconds seconds apart, count $countLabel. Keep this computer awake and ADB connected. Ctrl+C stops future visits."
        while ($Count -eq 0 -or $visitsSent -lt $Count) {
            # A nonzero ADB exit stops the entire runner instead of retrying a disconnected phone.
            Invoke-AdbCommand -AdbCommandArguments $showArguments
            $visitsSent++
            $timestamp = Get-Date -Format 'HH:mm:ss'
            if ($Count -gt 0 -and $visitsSent -ge $Count) {
                Write-Host "[$timestamp] Sent visit $visitsSent of $Count. Random runner finished."
                break
            }
            $intervalSeconds = Get-Random -Minimum $MinIntervalSeconds -Maximum ($MaxIntervalSeconds + 1)
            Write-Host "[$timestamp] Sent visit $visitsSent; next in $intervalSeconds seconds."
            Start-Sleep -Seconds $intervalSeconds
        }
        return
    }
    'show' {
        $adbArguments = $deviceArguments + @('shell', 'am', 'start', '-n', $triggerComponent)
    }
    'stop' {
        $adbArguments = $deviceArguments + @(
            'shell', 'am', 'start', '-n', $triggerComponent, '--ez', 'dismiss', 'true'
        )
    }
    'install' {
        if ([string]::IsNullOrWhiteSpace($ApkPath)) {
            $ApkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
        }
        if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
            throw "APK not found at '$ApkPath'. Run .\gradlew.bat assembleDebug first, or supply -ApkPath."
        }
        $resolvedApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
        $adbArguments = $deviceArguments + @('install', '-r', $resolvedApkPath)
    }
    'logs' {
        Write-Host 'Showing nudge and Android lifecycle logs. Press Ctrl+C to stop.'
        $adbArguments = $deviceArguments + @(
            'logcat', '-v', 'time', 'CatNudge:D', 'AndroidRuntime:E',
            'WindowManager:W', 'ActivityTaskManager:I', '*:S'
        )
    }
}

Invoke-AdbCommand -AdbCommandArguments $adbArguments
