[CmdletBinding()]
param(
    [string]$RuntimeRoot = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    $RuntimeRoot = Join-Path $env:LOCALAPPDATA "DominoPE\miniproduction"
}

$activeRunFile = Join-Path $RuntimeRoot "active-run.json"
if (-not (Test-Path -LiteralPath $activeRunFile -PathType Leaf)) {
    Write-Host "STATUS=NOT_RUNNING"
    exit 0
}

$activeRun = Get-Content -LiteralPath $activeRunFile -Raw |
    ConvertFrom-Json
$supervisorProcess = Get-Process -Id ([int]$activeRun.supervisorPid) `
    -ErrorAction SilentlyContinue

if ($null -eq $supervisorProcess) {
    Write-Host "STATUS=STALE_METADATA"
    Write-Host "RUN_ID=$($activeRun.runId)"
    exit 2
}

$stopFile = [string]$activeRun.stopFile
if ([string]::IsNullOrWhiteSpace($stopFile)) {
    throw "O metadado ativo não contém o arquivo de parada."
}

$null = New-Item -ItemType File -Path $stopFile -Force
Write-Host "STATUS=STOP_REQUESTED"
Write-Host "RUN_ID=$($activeRun.runId)"
Write-Host "SUPERVISOR_PID=$($activeRun.supervisorPid)"
