[CmdletBinding()]
param(
    [switch]$ConnectedAndroid,
    [switch]$SkipLint
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradlew = Join-Path $root "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    throw "Gradle wrapper nao encontrado em $gradlew"
}

Push-Location $root
try {
    Write-Host "=== DP CONTINUITY: GATE AUTOMATIZADO ==="
    Write-Host "Repo: $root"
    & git rev-parse --short HEAD
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel ler o commit Git." }

    $tasks = @(
        ":game-core:test",
        ":server:test",
        ":miniproduction-client:test",
        ":app:testDebugUnitTest"
    )
    if (-not $SkipLint) {
        $tasks += ":app:lintDebug"
    }

    Write-Host "=== UNIT TESTS (JVM + ANDROID) ==="
    & $gradlew @tasks "--stacktrace" "--no-daemon"
    if ($LASTEXITCODE -ne 0) {
        throw "Falha no gate unitario ou lint do DP (exit=$LASTEXITCODE)."
    }

    if ($ConnectedAndroid) {
        $adb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $adb) {
            throw "adb nao encontrado no PATH. Configure Android SDK Platform Tools."
        }

        $devices = & adb devices
        if ($LASTEXITCODE -ne 0 -or -not ($devices | Select-String -Pattern "\tdevice$")) {
            throw "Nenhum Android autorizado. Conecte um dispositivo ou inicie um emulador."
        }

        Write-Host "=== TESTES INSTRUMENTADOS NO DISPOSITIVO ==="
        & $gradlew ":app:connectedDebugAndroidTest" "--stacktrace" "--no-daemon"
        if ($LASTEXITCODE -ne 0) {
            throw "Falha nos testes instrumentados (exit=$LASTEXITCODE)."
        }
    } else {
        Write-Host "Testes instrumentados opcionais: execute com -ConnectedAndroid."
    }

    Write-Host "=== GATE LOCAL APROVADO ==="
    Write-Host "Resultados: build/test-results e build/reports por modulo."
}
finally {
    Pop-Location
}
