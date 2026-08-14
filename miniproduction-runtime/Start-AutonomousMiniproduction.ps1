[CmdletBinding()]
param(
    [string]$RepositoryPath = (Get-Location).Path,
    [ValidateRange(1, 180)]
    [int]$DurationMinutes = 60,
    [ValidateRange(12, 20)]
    [int]$PopulationSize = 16,
    [string]$RuntimeRoot = "",
    [string]$GoogleWebClientId = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$MiniProductionPort = 18080
$ProductionPort = 8080
$failure = $null
$result = "FAIL"
$stopReason = "STARTUP_FAILURE"
$runtimeLock = $null
$processJob = $null
$serverProcess = $null
$populationProcess = $null
$runDirectory = $null
$activeRunFile = $null
$evidenceZip = $null
$runId = "uninitialized"
$startedAt = Get-Date
$endsAt = $startedAt
$baseUrl = "http://127.0.0.1:18080"
$readinessUrl = "$baseUrl/ready"
$googleLoginConfigured = $false

function Write-JsonAtomically(
    [Parameter(Mandatory = $true)]$Value,
    [Parameter(Mandatory = $true)][string]$Path
) {
    $temporaryPath = "$Path.tmp"
    $Value | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $temporaryPath -Encoding UTF8
    Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
}

function Test-ProcessAlive([object]$Process) {
    if ($null -eq $Process) {
        return $false
    }
    try {
        $Process.Refresh()
        return -not $Process.HasExited
    } catch {
        return $false
    }
}

function Test-HttpReady([string]$Uri) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-TcpPortOpen([string]$HostName, [int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $asyncResult = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $asyncResult.AsyncWaitHandle.WaitOne(350)) {
            return $false
        }
        $client.EndConnect($asyncResult)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function New-SessionSecret {
    $bytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Resolve-JavaExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaFromHome = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path -LiteralPath $javaFromHome -PathType Leaf) {
            return $javaFromHome
        }
    }

    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        throw "java.exe não foi encontrado em JAVA_HOME ou PATH."
    }
    return [string]$javaCommand.Source
}

function New-JavaArgumentFileLauncher(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$LibraryDirectory,
    [Parameter(Mandatory = $true)][string]$MainClass,
    [Parameter(Mandatory = $true)][string]$JavaExecutable
) {
    if (-not (Test-Path -LiteralPath $LibraryDirectory -PathType Container)) {
        throw "Diretório de bibliotecas ausente para ${Name}: $LibraryDirectory"
    }

    $argumentFile = Join-Path $script:runDirectory "$Name-java.args"
    $launcherFile = Join-Path $script:runDirectory "$Name-java.cmd"
    $classPath = (Join-Path $LibraryDirectory "*").Replace("\", "/")
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding `
        -ArgumentList (,$false)

    [IO.File]::WriteAllLines(
        $argumentFile,
        [string[]]@(
            "-classpath",
            "`"$classPath`"",
            $MainClass
        ),
        $utf8WithoutBom
    )
    [IO.File]::WriteAllLines(
        $launcherFile,
        [string[]]@(
            "@echo off",
            "`"$JavaExecutable`" `"@$argumentFile`"",
            "exit /b %ERRORLEVEL%"
        ),
        [Text.Encoding]::ASCII
    )
    return $launcherFile
}

function Start-OwnedProcess(
    [Parameter(Mandatory = $true)][string]$Launcher,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][hashtable]$EnvironmentValues,
    [Parameter(Mandatory = $true)][string[]]$EnvironmentVariablesToRemove,
    [Parameter(Mandatory = $true)][object]$Job
) {
    $wrapperPath = Join-Path $script:runDirectory "$Name-wrapper.ps1"
    $gatePath = Join-Path $script:runDirectory "$Name-start.gate"
    $stdoutPath = Join-Path $script:runDirectory "$Name.stdout.log"
    $stderrPath = Join-Path $script:runDirectory "$Name.stderr.log"

    @'
param(
    [Parameter(Mandatory = $true)][string]$Launcher,
    [Parameter(Mandatory = $true)][string]$Gate
)
$ErrorActionPreference = "Stop"
while (-not (Test-Path -LiteralPath $Gate -PathType Leaf)) {
    Start-Sleep -Milliseconds 50
}
& $Launcher
exit $LASTEXITCODE
'@ | Set-Content -LiteralPath $wrapperPath -Encoding UTF8

    $savedValues = @{}
    $allNames = @($EnvironmentValues.Keys) + $EnvironmentVariablesToRemove
    foreach ($variableName in ($allNames | Sort-Object -Unique)) {
        $savedValues[$variableName] = [Environment]::GetEnvironmentVariable(
            $variableName,
            [EnvironmentVariableTarget]::Process
        )
    }

    try {
        foreach ($variableName in $EnvironmentVariablesToRemove) {
            [Environment]::SetEnvironmentVariable(
                $variableName,
                $null,
                [EnvironmentVariableTarget]::Process
            )
        }
        foreach ($variableName in $EnvironmentValues.Keys) {
            [Environment]::SetEnvironmentVariable(
                $variableName,
                [string]$EnvironmentValues[$variableName],
                [EnvironmentVariableTarget]::Process
            )
        }

        $powerShellExecutable = (Get-Process -Id $PID).Path
        $argumentLine =
            "-NoProfile -NonInteractive -ExecutionPolicy Bypass " +
            "-File `"$wrapperPath`" -Launcher `"$Launcher`" " +
            "-Gate `"$gatePath`""
        $process = Start-Process -FilePath $powerShellExecutable `
            -ArgumentList $argumentLine `
            -WorkingDirectory $script:RepositoryRoot `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -WindowStyle Hidden `
            -PassThru
    } finally {
        foreach ($variableName in $savedValues.Keys) {
            [Environment]::SetEnvironmentVariable(
                $variableName,
                $savedValues[$variableName],
                [EnvironmentVariableTarget]::Process
            )
        }
    }

    try {
        $Job.AddProcess($process)
        $null = New-Item -ItemType File -Path $gatePath -Force
    } catch {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw
    }
    return $process
}

function Write-ActiveRunMetadata([string]$Phase) {
    $serverPidValue = $null
    $populationPidValue = $null
    if ($null -ne $script:serverProcess) {
        $serverPidValue = $script:serverProcess.Id
    }
    if ($null -ne $script:populationProcess) {
        $populationPidValue = $script:populationProcess.Id
    }

    $metadata = [ordered]@{
        schemaVersion = 1
        runId = $script:runId
        phase = $Phase
        supervisorPid = $PID
        serverPid = $serverPidValue
        populationPid = $populationPidValue
        startedAt = $script:startedAt.ToString("o")
        endsAt = $script:endsAt.ToString("o")
        durationMinutes = $DurationMinutes
        populationSize = $PopulationSize
        baseUrl = $script:baseUrl
        readinessUrl = $script:readinessUrl
        runDirectory = $script:runDirectory
        stopFile = $script:stopFile
        googleLoginConfigured = $script:googleLoginConfigured
    }
    Write-JsonAtomically -Value $metadata -Path $script:activeRunFile
    Write-JsonAtomically -Value $metadata -Path (
        Join-Path $script:runDirectory "run-metadata.json"
    )
}

function Stop-OwnedProcessTree([object]$Process) {
    if (-not (Test-ProcessAlive $Process)) {
        return
    }
    & taskkill.exe /PID $Process.Id /T /F *> $null
}

try {
    if ($MiniProductionPort -eq $ProductionPort) {
        throw "A porta da mini-produção não pode ser a porta produtiva."
    }
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        throw "LOCALAPPDATA não está disponível."
    }
    if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
        $RuntimeRoot = Join-Path $env:LOCALAPPDATA "DominoPE\miniproduction"
    }

    $resolvedRepositoryPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
    $RepositoryRoot = [string](
        & git -C $resolvedRepositoryPath rev-parse --show-toplevel 2>&1
    )
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        throw "RepositoryPath não aponta para um repositório Git válido."
    }
    $RepositoryRoot = $RepositoryRoot.Trim()
    $script:RepositoryRoot = $RepositoryRoot

    $requiredPaths = @(
        "gradlew.bat",
        "server\MINIPRODUCTION.md",
        "miniproduction-client\build.gradle.kts"
    )
    foreach ($relativePath in $requiredPaths) {
        if (-not (Test-Path -LiteralPath (
            Join-Path $RepositoryRoot $relativePath
        ) -PathType Leaf)) {
            throw "Componente obrigatório ausente: $relativePath"
        }
    }

    if (Test-TcpPortOpen -HostName "127.0.0.1" -Port $MiniProductionPort) {
        throw "A porta isolada $MiniProductionPort já está em uso."
    }

    $null = New-Item -ItemType Directory -Path $RuntimeRoot -Force
    $lockPath = Join-Path $RuntimeRoot "supervisor.lock"
    try {
        $runtimeLock = [IO.File]::Open(
            $lockPath,
            [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None
        )
    } catch {
        throw "Já existe uma mini-produção ativa para este RuntimeRoot."
    }

    $runId = (Get-Date -Format "yyyyMMdd-HHmmss") + "-" +
        [Guid]::NewGuid().ToString("N").Substring(0, 8)
    $script:runId = $runId
    $runDirectory = Join-Path (Join-Path $RuntimeRoot "runs") $runId
    $script:runDirectory = $runDirectory
    $null = New-Item -ItemType Directory -Path $runDirectory -Force
    $null = New-Item -ItemType Directory -Path (
        Join-Path $runDirectory "server"
    ) -Force
    $null = New-Item -ItemType Directory -Path (
        Join-Path $runDirectory "clients"
    ) -Force

    $activeRunFile = Join-Path $RuntimeRoot "active-run.json"
    $script:activeRunFile = $activeRunFile
    $stopFile = Join-Path $runDirectory "stop-requested.flag"
    $script:stopFile = $stopFile
    $startedAt = Get-Date
    $endsAt = $startedAt.AddMinutes($DurationMinutes)
    $script:startedAt = $startedAt
    $script:endsAt = $endsAt
    $baseUrl = "http://127.0.0.1:$MiniProductionPort"
    $readinessUrl = "$baseUrl/ready"
    $script:baseUrl = $baseUrl
    $script:readinessUrl = $readinessUrl

    if ([string]::IsNullOrWhiteSpace($GoogleWebClientId)) {
        $GoogleWebClientId = [Environment]::GetEnvironmentVariable(
            "DOMINO_GOOGLE_WEB_CLIENT_ID",
            [EnvironmentVariableTarget]::Process
        )
    }
    $googleLoginConfigured = -not [string]::IsNullOrWhiteSpace(
        $GoogleWebClientId
    )
    $script:googleLoginConfigured = $googleLoginConfigured

    Write-ActiveRunMetadata -Phase "BUILDING"

    $buildLog = Join-Path $runDirectory "build.log"
    Push-Location $RepositoryRoot
    try {
        & (Join-Path $RepositoryRoot "gradlew.bat") `
            :server:installDist `
            :miniproduction-client:installDist `
            --no-daemon 2>&1 | Tee-Object -FilePath $buildLog
        $buildExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($buildExitCode -ne 0) {
        throw "A construção das distribuições falhou: $buildExitCode."
    }

    $javaExecutable = Resolve-JavaExecutable
    $serverLibraryDirectory = Join-Path $RepositoryRoot (
        "server\build\install\server\lib"
    )
    $populationLibraryDirectory = Join-Path $RepositoryRoot (
        "miniproduction-client\build\install\miniproduction-client\lib"
    )
    $serverLauncher = New-JavaArgumentFileLauncher `
        -Name "server" `
        -LibraryDirectory $serverLibraryDirectory `
        -MainClass (
            "com.ahtohiofilho.dominopernambucano.server.ApplicationKt"
        ) `
        -JavaExecutable $javaExecutable
    $populationLauncher = New-JavaArgumentFileLauncher `
        -Name "population" `
        -LibraryDirectory $populationLibraryDirectory `
        -MainClass (
            "com.ahtohiofilho.dominopernambucano.miniproduction." +
            "AutonomousPopulationMainKt"
        ) `
        -JavaExecutable $javaExecutable

    if ($null -eq ("DominoMiniProductionJob" -as [type])) {
        Add-Type -Path (
            Join-Path $PSScriptRoot "DominoMiniProductionJob.cs"
        )
    }
    $processJob = New-Object DominoMiniProductionJob

    $syntheticProvisioningSecret = New-SessionSecret
    $serverEnvironment = @{
        DOMINO_SERVER_ENVIRONMENT = "miniproduction"
        DOMINO_SERVER_PORT = $MiniProductionPort.ToString()
        DOMINO_SESSION_SIGNING_SECRET = (New-SessionSecret)
        DOMINO_SERVER_STATE_FILE = (Join-Path $runDirectory (
            "server\authoritative-state.json"
        ))
        DOMINO_SYNTHETIC_PROVISIONING_SECRET =
            $syntheticProvisioningSecret
    }
    if ($googleLoginConfigured) {
        $serverEnvironment["DOMINO_GOOGLE_WEB_CLIENT_ID"] = $GoogleWebClientId
    }

    $serverProcess = Start-OwnedProcess `
        -Launcher $serverLauncher `
        -Name "server" `
        -EnvironmentValues $serverEnvironment `
        -EnvironmentVariablesToRemove @(
            "DOMINO_AUTO_FILL_BOTS_AFTER_TWO_HUMANS",
            "DOMINO_MINIPRODUCTION_BASE_URL",
            "DOMINO_MINIPRODUCTION_CLIENT_STATE_DIR",
            "DOMINO_MINIPRODUCTION_POPULATION_SIZE"
        ) `
        -Job $processJob
    $script:serverProcess = $serverProcess
    Write-ActiveRunMetadata -Phase "WAITING_FOR_SERVER"

    $readinessDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $readinessDeadline) {
        if (-not (Test-ProcessAlive $serverProcess)) {
            throw "O servidor encerrou antes da prontidão."
        }
        if (Test-HttpReady -Uri $readinessUrl) {
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-HttpReady -Uri $readinessUrl)) {
        throw "O servidor não ficou pronto em 90 segundos."
    }

    $populationEnvironment = @{
        DOMINO_MINIPRODUCTION_BASE_URL = $baseUrl
        DOMINO_MINIPRODUCTION_CLIENT_STATE_DIR = (
            Join-Path $runDirectory "clients"
        )
        DOMINO_MINIPRODUCTION_POPULATION_SIZE = $PopulationSize.ToString()
        DOMINO_MINIPRODUCTION_POLL_INTERVAL_MILLIS = "750"
        DOMINO_SYNTHETIC_PROVISIONING_SECRET =
            $syntheticProvisioningSecret
    }
    $populationProcess = Start-OwnedProcess `
        -Launcher $populationLauncher `
        -Name "population" `
        -EnvironmentValues $populationEnvironment `
        -EnvironmentVariablesToRemove @(
            "DOMINO_SERVER_ENVIRONMENT",
            "DOMINO_SERVER_PORT",
            "DOMINO_SESSION_SIGNING_SECRET",
            "DOMINO_SERVER_STATE_FILE",
            "DOMINO_AUTO_FILL_BOTS_AFTER_TWO_HUMANS"
        ) `
        -Job $processJob
    $script:populationProcess = $populationProcess
    Write-ActiveRunMetadata -Phase "PROVISIONING_POPULATION"

    $populationLog = Join-Path $runDirectory "population.stdout.log"
    $populationDeadline = (Get-Date).AddMinutes(3)
    $populationStarted = $false
    while ((Get-Date) -lt $populationDeadline) {
        if (-not (Test-ProcessAlive $populationProcess)) {
            throw "A população encerrou durante o provisionamento."
        }
        if (Test-Path -LiteralPath $populationLog -PathType Leaf) {
            $populationStarted = [bool](Select-String `
                -LiteralPath $populationLog `
                -Pattern "POPULATION_STARTED" `
                -SimpleMatch `
                -Quiet)
        }
        if ($populationStarted) {
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $populationStarted) {
        throw "A população não confirmou início em três minutos."
    }

    $startedAt = Get-Date
    $endsAt = $startedAt.AddMinutes($DurationMinutes)
    $script:startedAt = $startedAt
    $script:endsAt = $endsAt
    Write-ActiveRunMetadata -Phase "RUNNING"
    Write-Host "RESULT=RUNNING"
    Write-Host "RUN_ID=$runId"
    Write-Host "BASE_URL=$baseUrl"
    Write-Host "ENDS_AT=$($endsAt.ToString('o'))"
    Write-Host "GOOGLE_LOGIN_CONFIGURED=$googleLoginConfigured"

    $nextHeartbeat = Get-Date
    $readinessFailureCount = 0
    while ($true) {
        if (Test-Path -LiteralPath $stopFile -PathType Leaf) {
            $stopReason = "EXPLICIT_STOP"
            break
        }
        if ((Get-Date) -ge $endsAt) {
            $stopReason = "DURATION_COMPLETE"
            break
        }
        if (-not (Test-ProcessAlive $serverProcess)) {
            throw "O servidor encerrou durante o ciclo."
        }
        if (-not (Test-ProcessAlive $populationProcess)) {
            throw "A população encerrou durante o ciclo."
        }

        if ((Get-Date) -ge $nextHeartbeat) {
            $ready = Test-HttpReady -Uri $readinessUrl
            if ($ready) {
                $readinessFailureCount = 0
            } else {
                $readinessFailureCount++
            }
            $heartbeat = [ordered]@{
                runId = $runId
                observedAt = (Get-Date).ToString("o")
                serverAlive = $true
                populationAlive = $true
                ready = $ready
                consecutiveReadinessFailures = $readinessFailureCount
                endsAt = $endsAt.ToString("o")
            }
            Write-JsonAtomically -Value $heartbeat -Path (
                Join-Path $runDirectory "heartbeat.json"
            )
            if ($readinessFailureCount -ge 3) {
                throw "O servidor permaneceu sem prontidão por três verificações."
            }
            $nextHeartbeat = (Get-Date).AddSeconds(10)
        }

        Start-Sleep -Milliseconds 500
    }

    $result = "PASS"
} catch {
    $failure = $_
    $stopReason = "RUNTIME_FAILURE"
} finally {
    try {
        if ($null -ne $activeRunFile -and (
            Test-Path -LiteralPath $activeRunFile -PathType Leaf
        )) {
            Write-ActiveRunMetadata -Phase "STOPPING"
        }
    } catch {
        if ($null -eq $failure) {
            $failure = $_
            $result = "FAIL"
        }
    }

    Stop-OwnedProcessTree $populationProcess
    Stop-OwnedProcessTree $serverProcess
    if ($null -ne $processJob) {
        $processJob.Dispose()
    }

    if ($null -ne $runDirectory -and (
        Test-Path -LiteralPath $runDirectory -PathType Container
    )) {
        $summaryError = $null
        if ($null -ne $failure) {
            $summaryError = $failure.Exception.Message
        }
        $summary = [ordered]@{
            result = $result
            runId = $runId
            stopReason = $stopReason
            startedAt = $startedAt.ToString("o")
            endedAt = (Get-Date).ToString("o")
            plannedEndAt = $endsAt.ToString("o")
            durationMinutes = $DurationMinutes
            populationSize = $PopulationSize
            baseUrl = $baseUrl
            googleLoginConfigured = $googleLoginConfigured
            error = $summaryError
            serverStatePreserved = $true
            credentialsExcludedFromEvidence = $true
        }
        Write-JsonAtomically -Value $summary -Path (
            Join-Path $runDirectory "run-summary.json"
        )

        $documentsDirectory = [Environment]::GetFolderPath(
            [Environment+SpecialFolder]::MyDocuments
        )
        $evidenceDirectory = Join-Path $documentsDirectory (
            "DominoPernambucano-evidence"
        )
        $null = New-Item -ItemType Directory -Path $evidenceDirectory -Force
        $evidenceStage = Join-Path $runDirectory "evidence-stage"
        $null = New-Item -ItemType Directory -Path $evidenceStage -Force
        $evidenceNames = @(
            "build.log",
            "server.stdout.log",
            "server.stderr.log",
            "population.stdout.log",
            "population.stderr.log",
            "run-metadata.json",
            "heartbeat.json",
            "run-summary.json"
        )
        foreach ($name in $evidenceNames) {
            $source = Join-Path $runDirectory $name
            if (Test-Path -LiteralPath $source -PathType Leaf) {
                Copy-Item -LiteralPath $source -Destination (
                    Join-Path $evidenceStage $name
                ) -Force
            }
        }

        $evidenceZip = Join-Path $evidenceDirectory (
            "Domino-PE-autonomous-miniproduction-A3-runtime-$runId.zip"
        )
        if (Test-Path -LiteralPath $evidenceZip) {
            Remove-Item -LiteralPath $evidenceZip -Force
        }
        Compress-Archive -Path (Join-Path $evidenceStage "*") `
            -DestinationPath $evidenceZip -CompressionLevel Optimal
        Remove-Item -LiteralPath $evidenceStage -Recurse -Force
    }

    if ($null -ne $activeRunFile -and (
        Test-Path -LiteralPath $activeRunFile -PathType Leaf
    )) {
        Remove-Item -LiteralPath $activeRunFile -Force
    }
    if ($null -ne $runtimeLock) {
        $runtimeLock.Dispose()
    }

    if ($null -ne $evidenceZip) {
        Write-Host "EVIDENCE_ZIP=$evidenceZip"
    }
}

if ($null -ne $failure) {
    throw $failure
}

Write-Host "RESULT=PASS"
Write-Host "STOP_REASON=$stopReason"
