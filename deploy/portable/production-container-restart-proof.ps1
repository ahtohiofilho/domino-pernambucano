[CmdletBinding()]
param(
    [string]$RepoRoot =
        "C:\Users\ahtoh\AndroidStudioProjects\DominoPernambucano",
    [string]$ImageName =
        "domino-pernambucano-server:portable-gate"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        $utf8WithoutBom
    )
}

function Invoke-DockerCommand {
    param(
        [string[]]$Arguments,
        [string]$LogPath = "",
        [switch]$ShowOutput,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $hasNativePreference = Test-Path `
        -LiteralPath "variable:PSNativeCommandUseErrorActionPreference"
    $previousNativePreference = $null
    $rawOutput = @()
    $exitCode = -1

    if ($hasNativePreference) {
        $previousNativePreference =
            $PSNativeCommandUseErrorActionPreference
    }

    try {
        $ErrorActionPreference = "Continue"

        if ($hasNativePreference) {
            $PSNativeCommandUseErrorActionPreference = $false
        }

        $rawOutput = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference

        if ($hasNativePreference) {
            $PSNativeCommandUseErrorActionPreference =
                $previousNativePreference
        }
    }

    $lines = @(
        $rawOutput | ForEach-Object {
            $_.ToString()
        } | Where-Object {
            $_ -ne "System.Management.Automation.RemoteException"
        }
    )
    $output = $lines -join [Environment]::NewLine

    if (-not [string]::IsNullOrWhiteSpace($LogPath)) {
        Write-Utf8File -Path $LogPath -Content $output
    }

    if ($ShowOutput -and -not [string]::IsNullOrWhiteSpace($output)) {
        Write-Host $output
    }

    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $safeCommand = "docker " + ($Arguments -join " ")
        throw (
            "Command failed with exit code ${exitCode}: " +
            "${safeCommand}`n${output}"
        )
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )

    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-JsonRequest {
    param(
        [ValidateSet("Get", "Post")]
        [string]$Method,
        [string]$Uri,
        [string]$AccessToken = "",
        [AllowNull()]
        [object]$Body = $null
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        TimeoutSec = 20
    }

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $parameters.Headers = @{
            Authorization = "Bearer $AccessToken"
        }
    }

    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }

    return Invoke-RestMethod @parameters
}

function Test-OwnedContainer {
    param(
        [string]$Name,
        [string]$ExpectedRunId
    )

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return $false
    }

    $result = Invoke-DockerCommand `
        -Arguments @("inspect", $Name) `
        -AllowFailure

    if ($result.ExitCode -ne 0) {
        return $false
    }

    try {
        $inspection = @($result.Output | ConvertFrom-Json)
        if ($inspection.Count -ne 1) {
            return $false
        }

        $labels = $inspection[0].Config.Labels
        if ($null -eq $labels) {
            return $false
        }

        $labelKey =
            "com.ahtohiofilho.dominopernambucano.proof"
        $labelProperty = $labels.PSObject.Properties[$labelKey]
        return (
            $null -ne $labelProperty -and
            [string]$labelProperty.Value -eq $ExpectedRunId
        )
    } catch {
        return $false
    }
}

function New-EvidenceArchive {
    param(
        [string]$EvidenceRoot,
        [string]$EvidenceZip,
        [string]$StateFile,
        [string]$RunId
    )

    $stagingRoot = Join-Path `
        $env:TEMP `
        "domino-container-evidence-$RunId"

    try {
        if (Test-Path -LiteralPath $stagingRoot) {
            Remove-Item -LiteralPath $stagingRoot -Recurse -Force
        }
        New-Item `
            -ItemType Directory `
            -Path $stagingRoot `
            -Force | Out-Null

        Get-ChildItem `
            -LiteralPath $EvidenceRoot `
            -File | ForEach-Object {
                Copy-Item `
                    -LiteralPath $_.FullName `
                    -Destination $stagingRoot
            }

        if (Test-Path -LiteralPath $StateFile -PathType Leaf) {
            $stagingStateRoot = Join-Path $stagingRoot "state"
            New-Item `
                -ItemType Directory `
                -Path $stagingStateRoot `
                -Force | Out-Null
            Copy-Item `
                -LiteralPath $StateFile `
                -Destination $stagingStateRoot
        }

        if (Test-Path -LiteralPath $EvidenceZip -PathType Leaf) {
            Remove-Item -LiteralPath $EvidenceZip -Force
        }
        Compress-Archive `
            -Path (Join-Path $stagingRoot "*") `
            -DestinationPath $EvidenceZip `
            -CompressionLevel Optimal
    } finally {
        if (Test-Path -LiteralPath $stagingRoot) {
            Remove-Item -LiteralPath $stagingRoot -Recurse -Force
        }
    }
}

function Save-ContainerLog {
    param(
        [string]$Name,
        [string]$ExpectedRunId,
        [string]$Path
    )

    if (Test-OwnedContainer `
        -Name $Name `
        -ExpectedRunId $ExpectedRunId) {
        $null = Invoke-DockerCommand `
            -Arguments @("logs", "--timestamps", $Name) `
            -LogPath $Path `
            -AllowFailure
    }
}

function Remove-OwnedContainer {
    param(
        [string]$Name,
        [string]$ExpectedRunId
    )

    if (Test-OwnedContainer `
        -Name $Name `
        -ExpectedRunId $ExpectedRunId) {
        $null = Invoke-DockerCommand `
            -Arguments @("rm", "--force", $Name) `
            -AllowFailure
    }
}

function Wait-ContainerHealth {
    param(
        [string]$Name,
        [string]$BaseUri,
        [int]$TimeoutSeconds = 240
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastError = "No health response."

    while ([DateTime]::UtcNow -lt $deadline) {
        $stateResult = Invoke-DockerCommand `
            -Arguments @(
                "inspect",
                "--format",
                "{{.State.Status}}",
                $Name
            ) `
            -AllowFailure

        if ($stateResult.ExitCode -ne 0) {
            throw "Container not found while waiting for health: $Name"
        }

        $state = $stateResult.Output.Trim()
        if ($state -ne "running") {
            throw "Container $Name stopped before becoming healthy: $state"
        }

        try {
            $health = Invoke-JsonRequest `
                -Method Get `
                -Uri "$BaseUri/health"

            if ([string]$health.status -eq "ok") {
                return
            }

            $lastError = "Unexpected health status: $($health.status)"
        } catch {
            $lastError = $_.Exception.Message
        }

        Start-Sleep -Seconds 1
    }

    throw (
        "Health endpoint did not become ready within " +
        "${TimeoutSeconds}s. Last error: $lastError"
    )
}

function Start-ProofContainer {
    param(
        [string]$Name,
        [string]$RunId,
        [int]$HostPort,
        [string]$DataRoot,
        [string]$EnvironmentFile,
        [string]$Image
    )

    $mount = "type=bind,source=$DataRoot,target=/data"
    $label =
        "com.ahtohiofilho.dominopernambucano.proof=$RunId"
    $result = Invoke-DockerCommand -Arguments @(
        "run",
        "--detach",
        "--name", $Name,
        "--label", $label,
        "--publish", "127.0.0.1:${HostPort}:8080",
        "--mount", $mount,
        "--env-file", $EnvironmentFile,
        "--read-only",
        "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
        "--cap-drop", "ALL",
        "--security-opt", "no-new-privileges:true",
        "--memory", "512m",
        "--cpus", "1.0",
        "--pids-limit", "256",
        "--restart", "no",
        $Image
    )

    $containerId = $result.Output.Trim()
    Assert-Condition `
        -Condition (-not [string]::IsNullOrWhiteSpace($containerId)) `
        -Message "Docker did not return a container ID for $Name."

    return $containerId
}

function Stop-And-RemoveProofContainer {
    param(
        [string]$Name,
        [string]$RunId,
        [string]$LogPath
    )

    Assert-Condition `
        -Condition (Test-OwnedContainer `
            -Name $Name `
            -ExpectedRunId $RunId) `
        -Message "Refusing to stop a container not owned by this run: $Name"

    $null = Invoke-DockerCommand `
        -Arguments @("stop", "--time", "30", $Name)
    Save-ContainerLog `
        -Name $Name `
        -ExpectedRunId $RunId `
        -Path $LogPath
    Remove-OwnedContainer `
        -Name $Name `
        -ExpectedRunId $RunId
}

function Assert-SafeRuntimeLog {
    param(
        [string]$Path,
        [string]$SigningSecret,
        [object[]]$Sessions
    )

    Assert-Condition `
        -Condition (Test-Path -LiteralPath $Path -PathType Leaf) `
        -Message "Container log was not created: $Path"

    $content = [System.IO.File]::ReadAllText($Path)
    Assert-Condition `
        -Condition ($content -match "Application started") `
        -Message "Runtime log does not confirm application startup: $Path"
    Assert-Condition `
        -Condition ($content -notmatch "(?i)No SLF4J providers|NOPLogger|NOP logger") `
        -Message "Runtime log reports a missing or no-op SLF4J provider: $Path"
    Assert-Condition `
        -Condition (-not $content.Contains($SigningSecret)) `
        -Message "Signing secret leaked into runtime log: $Path"

    foreach ($session in $Sessions) {
        Assert-Condition `
            -Condition (-not $content.Contains([string]$session.accessToken)) `
            -Message "Session token leaked into runtime log: $Path"
    }
}

function Protect-EvidenceLog {
    param(
        [string]$Path,
        [string]$SigningSecret,
        [object[]]$Sessions
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return
    }

    $content = [System.IO.File]::ReadAllText($Path)
    $protectedContent = $content

    if (-not [string]::IsNullOrWhiteSpace($SigningSecret)) {
        $protectedContent = $protectedContent.Replace(
            $SigningSecret,
            "[REDACTED_SIGNING_SECRET]"
        )
    }

    foreach ($session in $Sessions) {
        $token = [string]$session.accessToken
        if (-not [string]::IsNullOrWhiteSpace($token)) {
            $protectedContent = $protectedContent.Replace(
                $token,
                "[REDACTED_SESSION_TOKEN]"
            )
        }
    }

    if ($protectedContent -ne $content) {
        Write-Utf8File -Path $Path -Content $protectedContent
    }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runNonce = [Guid]::NewGuid().ToString("N").Substring(0, 8)
$runId = "portable-$stamp-$runNonce"
$firstContainerName = "domino-proof-$stamp-$runNonce-a"
$secondContainerName = "domino-proof-$stamp-$runNonce-b"
$evidenceParent = Join-Path `
    $env:USERPROFILE `
    "Documents\DominoPernambucano-evidence"
$evidenceRoot = Join-Path `
    $evidenceParent `
    "production-container-restart-proof-$stamp-$runNonce"
$evidenceZip = "$evidenceRoot.zip"
$dataRoot = Join-Path $evidenceRoot "state"
$stateFile = Join-Path $dataRoot "authoritative-state.json"
$buildLog = Join-Path $evidenceRoot "docker-build.log"
$firstLog = Join-Path $evidenceRoot "container-first.log"
$secondLog = Join-Path $evidenceRoot "container-second.log"
$reportPath = Join-Path $evidenceRoot "report.txt"
$environmentFile = Join-Path `
    $env:TEMP `
    "domino-container-$stamp-$runNonce.env"

$hostPort = 0
$baseUri = ""
$signingSecret = ""
$secretBytes = $null
$sessions = @()
$firstContainerId = ""
$secondContainerId = ""
$imageId = ""
$roomId = ""
$roomCode = ""
$matchId = ""
$revisionBefore = -1
$revisionAfter = -1
$stateFileBytes = 0
$passed = $false
$failure = ""

New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null

try {
    Assert-Condition `
        -Condition ($null -ne (Get-Command docker -ErrorAction SilentlyContinue)) `
        -Message "Docker command was not found. Install or start Docker Desktop."

    $requiredFiles = @(
        "Dockerfile.server",
        "Dockerfile.server.dockerignore",
        "gradlew",
        "gradlew.bat",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "gradle\wrapper\gradle-wrapper.jar",
        "gradle\wrapper\gradle-wrapper.properties",
        "game-core\build.gradle.kts",
        "server\build.gradle.kts"
    )

    foreach ($relativePath in $requiredFiles) {
        $absolutePath = Join-Path $RepoRoot $relativePath
        Assert-Condition `
            -Condition (Test-Path -LiteralPath $absolutePath -PathType Leaf) `
            -Message "Required project file was not found: $absolutePath"
    }

    $null = Invoke-DockerCommand -Arguments @("info")
    $hostPort = Get-FreeLoopbackPort
    $baseUri = "http://127.0.0.1:$hostPort"

    $secretBytes = New-Object byte[] 48
    $randomNumberGenerator =
        [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomNumberGenerator.GetBytes($secretBytes)
    } finally {
        $randomNumberGenerator.Dispose()
    }
    $signingSecret = [Convert]::ToBase64String($secretBytes)

    $environmentLines = @(
        "DOMINO_SERVER_ENVIRONMENT=production",
        "DOMINO_SERVER_STATE_FILE=/data/authoritative-state.json",
        "DOMINO_SESSION_SIGNING_SECRET=$signingSecret"
    )
    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllLines(
        $environmentFile,
        $environmentLines,
        $utf8WithoutBom
    )

    Write-Host "Building portable server image: $ImageName"
    $null = Invoke-DockerCommand `
        -Arguments @(
            "build",
            "--file", (Join-Path $RepoRoot "Dockerfile.server"),
            "--tag", $ImageName,
            $RepoRoot
        ) `
        -LogPath $buildLog `
        -ShowOutput

    $imageResult = Invoke-DockerCommand -Arguments @(
        "image",
        "inspect",
        "--format",
        "{{.Id}}",
        $ImageName
    )
    $imageId = $imageResult.Output.Trim()

    Write-Host "Starting first container on $baseUri"
    $firstContainerId = Start-ProofContainer `
        -Name $firstContainerName `
        -RunId $runId `
        -HostPort $hostPort `
        -DataRoot $dataRoot `
        -EnvironmentFile $environmentFile `
        -Image $ImageName
    Wait-ContainerHealth `
        -Name $firstContainerName `
        -BaseUri $baseUri

    for ($index = 1; $index -le 4; $index++) {
        $session = Invoke-JsonRequest `
            -Method Post `
            -Uri "$baseUri/sessions/anonymous"
        Assert-Condition `
            -Condition (-not [string]::IsNullOrWhiteSpace(
                [string]$session.playerId
            )) `
            -Message "Session $index did not include a player ID."
        Assert-Condition `
            -Condition (-not [string]::IsNullOrWhiteSpace(
                [string]$session.accessToken
            )) `
            -Message "Session $index did not include an access token."
        $sessions += $session
    }

    $createResult = Invoke-JsonRequest `
        -Method Post `
        -Uri "$baseUri/rooms" `
        -AccessToken ([string]$sessions[0].accessToken) `
        -Body @{
            localPlayerId = [string]$sessions[0].playerId
            playerName = "Portable Gate 1"
        }
    Assert-Condition `
        -Condition ([bool]$createResult.accepted) `
        -Message "The server rejected room creation: $($createResult.reason)"
    Assert-Condition `
        -Condition ($null -ne $createResult.roomSnapshot) `
        -Message "Room creation returned no snapshot."

    $roomId = [string]$createResult.roomSnapshot.roomId
    $roomCode = [string]$createResult.roomSnapshot.roomCode

    for ($index = 1; $index -lt 4; $index++) {
        $joinResult = Invoke-JsonRequest `
            -Method Post `
            -Uri "$baseUri/rooms/join" `
            -AccessToken ([string]$sessions[$index].accessToken) `
            -Body @{
                roomCode = $roomCode
                localPlayerId = [string]$sessions[$index].playerId
                playerName = "Portable Gate $($index + 1)"
            }
        Assert-Condition `
            -Condition ([bool]$joinResult.accepted) `
            -Message (
                "The server rejected player $($index + 1): " +
                "$($joinResult.reason)"
            )
    }

    $roomBefore = Invoke-JsonRequest `
        -Method Get `
        -Uri "$baseUri/rooms/$roomId" `
        -AccessToken ([string]$sessions[0].accessToken)
    Assert-Condition `
        -Condition ([string]$roomBefore.status -eq "IN_MATCH") `
        -Message "Room did not enter IN_MATCH after four human players."
    Assert-Condition `
        -Condition (@($roomBefore.players).Count -eq 4) `
        -Message "Room does not contain exactly four players."

    $matchId = [string]$roomBefore.matchId
    Assert-Condition `
        -Condition (-not [string]::IsNullOrWhiteSpace($matchId)) `
        -Message "Room did not include a match ID."

    $matchBefore = Invoke-JsonRequest `
        -Method Get `
        -Uri "$baseUri/matches/$matchId" `
        -AccessToken ([string]$sessions[0].accessToken)
    $revisionBefore = [long]$matchBefore.revision

    Stop-And-RemoveProofContainer `
        -Name $firstContainerName `
        -RunId $runId `
        -LogPath $firstLog

    Write-Host "Starting second container with the same persistent state"
    $secondContainerId = Start-ProofContainer `
        -Name $secondContainerName `
        -RunId $runId `
        -HostPort $hostPort `
        -DataRoot $dataRoot `
        -EnvironmentFile $environmentFile `
        -Image $ImageName
    Wait-ContainerHealth `
        -Name $secondContainerName `
        -BaseUri $baseUri

    $roomAfter = Invoke-JsonRequest `
        -Method Get `
        -Uri "$baseUri/rooms/$roomId" `
        -AccessToken ([string]$sessions[0].accessToken)
    $matchAfter = Invoke-JsonRequest `
        -Method Get `
        -Uri "$baseUri/matches/$matchId" `
        -AccessToken ([string]$sessions[0].accessToken)
    $revisionAfter = [long]$matchAfter.revision

    Assert-Condition `
        -Condition ([string]$roomAfter.roomId -eq $roomId) `
        -Message "Room ID changed after container restart."
    Assert-Condition `
        -Condition ([string]$roomAfter.roomCode -eq $roomCode) `
        -Message "Room code changed after container restart."
    Assert-Condition `
        -Condition ([string]$roomAfter.status -eq "IN_MATCH") `
        -Message "Room status was not restored after container restart."
    Assert-Condition `
        -Condition (@($roomAfter.players).Count -eq 4) `
        -Message "Player list was not restored after container restart."
    Assert-Condition `
        -Condition ([string]$roomAfter.matchId -eq $matchId) `
        -Message "Room match ID changed after container restart."
    Assert-Condition `
        -Condition ([string]$matchAfter.matchId -eq $matchId) `
        -Message "Match ID changed after container restart."
    Assert-Condition `
        -Condition ([string]$matchAfter.roomId -eq $roomId) `
        -Message "Match room ID changed after container restart."
    Assert-Condition `
        -Condition ($revisionAfter -ge $revisionBefore) `
        -Message "Match revision regressed after container restart."

    Stop-And-RemoveProofContainer `
        -Name $secondContainerName `
        -RunId $runId `
        -LogPath $secondLog

    Assert-Condition `
        -Condition (Test-Path -LiteralPath $stateFile -PathType Leaf) `
        -Message "Persistent state file was not created: $stateFile"
    $stateFileBytes = (Get-Item -LiteralPath $stateFile).Length
    Assert-Condition `
        -Condition ($stateFileBytes -gt 0) `
        -Message "Persistent state file is empty."

    Assert-SafeRuntimeLog `
        -Path $firstLog `
        -SigningSecret $signingSecret `
        -Sessions $sessions
    Assert-SafeRuntimeLog `
        -Path $secondLog `
        -SigningSecret $signingSecret `
        -Sessions $sessions

    $passed = $true
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne (Get-Command docker -ErrorAction SilentlyContinue)) {
        Save-ContainerLog `
            -Name $firstContainerName `
            -ExpectedRunId $runId `
            -Path $firstLog
        Save-ContainerLog `
            -Name $secondContainerName `
            -ExpectedRunId $runId `
            -Path $secondLog
        Remove-OwnedContainer `
            -Name $firstContainerName `
            -ExpectedRunId $runId
        Remove-OwnedContainer `
            -Name $secondContainerName `
            -ExpectedRunId $runId
    }

    if (Test-Path -LiteralPath $stateFile -PathType Leaf) {
        $stateFileBytes = (Get-Item -LiteralPath $stateFile).Length
    }

    if (Test-Path -LiteralPath $environmentFile -PathType Leaf) {
        Remove-Item -LiteralPath $environmentFile -Force
    }

    Protect-EvidenceLog `
        -Path $firstLog `
        -SigningSecret $signingSecret `
        -Sessions $sessions
    Protect-EvidenceLog `
        -Path $secondLog `
        -SigningSecret $signingSecret `
        -Sessions $sessions

    if ($null -ne $secretBytes) {
        [Array]::Clear($secretBytes, 0, $secretBytes.Length)
    }
    $signingSecret = ""

    $resultText = if ($passed) { "PASS" } else { "FAIL" }
    $reportLines = @(
        "GATE: production-container-restart-proof",
        "RESULT: $resultText",
        "EXECUTED_AT: $stamp",
        "REPO: $RepoRoot",
        "IMAGE: $ImageName",
        "IMAGE_ID: $imageId",
        "HOST_PORT: $hostPort",
        "FIRST_CONTAINER_ID: $firstContainerId",
        "SECOND_CONTAINER_ID: $secondContainerId",
        "STATE_FILE: $stateFile",
        "STATE_FILE_BYTES: $stateFileBytes",
        "ROOM_ID: $roomId",
        "MATCH_ID: $matchId",
        "REVISION_BEFORE: $revisionBefore",
        "REVISION_AFTER: $revisionAfter",
        "FAILURE: $failure",
        "",
        "Signing secrets and session tokens are not recorded in this evidence."
    )
    Write-Utf8File `
        -Path $reportPath `
        -Content ($reportLines -join [Environment]::NewLine)

    New-EvidenceArchive `
        -EvidenceRoot $evidenceRoot `
        -EvidenceZip $evidenceZip `
        -StateFile $stateFile `
        -RunId $runId
}

if (-not $passed) {
    throw (
        "Portable container restart proof failed. " +
        "Evidence: $evidenceZip`n$failure"
    )
}

Write-Host "PASS: portable container restart proof"
Write-Host "Evidence: $evidenceZip"
