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
$supervisorAlive = $null -ne (
    Get-Process -Id ([int]$activeRun.supervisorPid) -ErrorAction SilentlyContinue
)
$serverAlive = $false
$populationAlive = $false

if ($null -ne $activeRun.serverPid) {
    $serverAlive = $null -ne (
        Get-Process -Id ([int]$activeRun.serverPid) -ErrorAction SilentlyContinue
    )
}
if ($null -ne $activeRun.populationPid) {
    $populationAlive = $null -ne (
        Get-Process -Id ([int]$activeRun.populationPid) -ErrorAction SilentlyContinue
    )
}

$ready = $false
try {
    $response = Invoke-WebRequest -UseBasicParsing `
        -Uri ([string]$activeRun.readinessUrl) `
        -TimeoutSec 2
    $ready = $response.StatusCode -eq 200
} catch {
    $ready = $false
}

$status = "DEGRADED"
if ($supervisorAlive -and $serverAlive -and $populationAlive -and $ready) {
    $status = "RUNNING"
} elseif (-not $supervisorAlive) {
    $status = "STALE_METADATA"
}

Write-Host "STATUS=$status"
Write-Host "RUN_ID=$($activeRun.runId)"
Write-Host "STARTED_AT=$($activeRun.startedAt)"
Write-Host "ENDS_AT=$($activeRun.endsAt)"
Write-Host "SUPERVISOR_PID=$($activeRun.supervisorPid)"
Write-Host "SERVER_PID=$($activeRun.serverPid)"
Write-Host "POPULATION_PID=$($activeRun.populationPid)"
Write-Host "READY=$ready"
Write-Host "RUN_DIRECTORY=$($activeRun.runDirectory)"
