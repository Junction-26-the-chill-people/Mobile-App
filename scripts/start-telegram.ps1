param([string]$NodeExecutable = 'node', [string]$AdbExecutable = 'adb')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'telegram-bridge\.env'))) {
    throw 'Copy telegram-bridge/.env.example to telegram-bridge/.env and add the bot token first.'
}
if (-not (Get-Command $NodeExecutable -ErrorAction SilentlyContinue)) {
    if ($NodeExecutable -eq 'node' -and (Test-Path -LiteralPath 'E:\nodejs\node.exe')) { $NodeExecutable = 'E:\nodejs\node.exe' }
    else { throw 'Install Node 20+, or pass -NodeExecutable with the path to node.exe.' }
}
if (-not (Get-Command $AdbExecutable -ErrorAction SilentlyContinue)) {
    $localAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if ($AdbExecutable -eq 'adb' -and (Test-Path -LiteralPath $localAdb)) { $AdbExecutable = $localAdb }
    else { throw 'Add ADB to PATH, or pass -AdbExecutable with its path.' }
}
& $AdbExecutable reverse tcp:3001 tcp:3001
if ($LASTEXITCODE -ne 0) { throw 'Connect one phone with USB debugging enabled first.' }
& $AdbExecutable shell am start -n com.example.junction/.MainActivity --es bridge_url http://127.0.0.1:3001
Write-Output 'Starting the local Sola bridge. Keep this terminal open; Ctrl+C stops it.'
& $NodeExecutable (Join-Path $projectRoot 'telegram-bridge\bridge.mjs')
