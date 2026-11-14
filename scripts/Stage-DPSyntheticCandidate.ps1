#requires -Version 5.1
<#
  DP: validacao local e staging OPCIONAL da imagem sintética aprovada pela CI.
  Sem -Stage: apenas valida manifest/commit/SHA256. Nao acessa a AWS.
  Com -Stage: transmite tar Docker e executa docker load; NAO inicia/reinicia
  containers, nao altera volumes ou o Google Play. Exige backup preliminar
  previamente verificado e imagem anterior intocada.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArtifactDirectory,
    [switch]$Stage,
    [string]$RemoteHost = 'ubuntu@56.124.45.216',
    [string]$SshKey = (Join-Path $HOME '.ssh\lightsail-sa-east-1.pem'),
    [string]$BackupPath,
    [string]$BackupSha256
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-DP([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}
function Invoke-RemoteReadiness([string]$bash) {
    $linux = (($bash -replace "\r\n", "\n") -replace "\r", "\n") + "\n"
    # PowerShell regex escapes refer to actual carriage returns/newlines below.
    $linux = ($bash.Replace([string][char]13 + [string][char]10, [string][char]10).
        Replace([string][char]13, [string][char]10)) + [char]10
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($linux))
    $command = "printf '%s' '$encoded' | base64 --decode | bash"
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& ssh.exe -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $RemoteHost $command 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    foreach ($line in $output) { Write-Host ([string]$line) }
    Assert-DP ($exitCode -eq 0) "REMOTE_STAGE_GATE_FAILED=$exitCode"
}

$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$directory = (Resolve-Path -LiteralPath $ArtifactDirectory).Path
$manifestPath = Join-Path $directory 'manifest.txt'
$shaFile = Join-Path $directory 'SHA256SUMS'
Assert-DP (Test-Path -LiteralPath $manifestPath -PathType Leaf) 'MANIFEST_MISSING'
Assert-DP (Test-Path -LiteralPath $shaFile -PathType Leaf) 'CHECKSUM_MISSING'

$fields = @{}
foreach ($line in (Get-Content -LiteralPath $manifestPath)) {
    Assert-DP ($line -match '^([A-Z_]+)=(\S+)$') 'MANIFEST_MALFORMED'
    $fields[$Matches[1]] = $Matches[2]
}
$source = [string]$fields['SOURCE_COMMIT']
$imageTag = [string]$fields['IMAGE_TAG']
$imageId = [string]$fields['IMAGE_ID']
$platform = [string]$fields['IMAGE_PLATFORM']
Assert-DP ($source -match '^[0-9a-f]{40}$') 'MANIFEST_SOURCE_INVALID'
Assert-DP ($imageTag -ceq ('dp-synthetic:sha-' + $source.Substring(0, 12))) 'MANIFEST_TAG_INVALID'
Assert-DP ($imageId -match '^sha256:[0-9a-f]{64}$') 'MANIFEST_IMAGE_ID_INVALID'
Assert-DP ($platform -ceq 'linux/amd64') 'MANIFEST_PLATFORM_INVALID'

$localHead = (& git -C $repo rev-parse HEAD).Trim()
Assert-DP ($LASTEXITCODE -eq 0 -and $localHead -ceq $source) 'ARTIFACT_SOURCE_NOT_LOCAL_HEAD'
$filename = 'dp-synthetic-' + $source.Substring(0, 12) + '.docker.tar.gz'
$archive = Join-Path $directory $filename
Assert-DP (Test-Path -LiteralPath $archive -PathType Leaf) 'DOCKER_ARCHIVE_MISSING'
$checksumLine = ((Get-Content -LiteralPath $shaFile) | Where-Object { $_.Trim() })
Assert-DP (@($checksumLine).Count -eq 1) 'CHECKSUM_FORMAT_INVALID'
Assert-DP ($checksumLine -match '^([0-9a-f]{64})\s+(\S+)$') 'CHECKSUM_FORMAT_INVALID'
$expectedArchiveHash = $Matches[1]
Assert-DP ($Matches[2] -ceq $filename) 'CHECKSUM_FILENAME_MISMATCH'
$actualArchiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
Assert-DP ($actualArchiveHash -ceq $expectedArchiveHash) 'ARCHIVE_HASH_MISMATCH'
Write-Host "ARTIFACT_SOURCE=$source"
Write-Host "ARTIFACT_IMAGE_ID=$imageId"
Write-Host "ARTIFACT_PLATFORM=$platform"
Write-Host 'LOCAL_ARTIFACT_VERIFIED=PASS'

if (-not $Stage) {
    Write-Host 'AWS_CHANGES=NONE'
    Write-Host 'Para staging futuro, usar -Stage com -BackupPath e -BackupSha256.'
    exit 0
}

Assert-DP ($RemoteHost -match '^[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+$') 'REMOTE_HOST_INVALID'
Assert-DP (Test-Path -LiteralPath $SshKey -PathType Leaf) 'SSH_KEY_MISSING'
Assert-DP ($BackupPath -match '^/var/backups/domino-pe/synthetic/identities-[a-zA-Z0-9-]+\.json$') 'BACKUP_PATH_INVALID'
Assert-DP ($BackupSha256 -match '^[a-fA-F0-9]{64}$') 'BACKUP_CHECKSUM_INVALID'
$backupHash = $BackupSha256.ToLowerInvariant()
$previousImage = 'sha256:d25988e867448a5eda0878bead1acae7794ac3ca82bc6e9691d56026c0dc1827'
$volumeName = 'synthetic-participant-state'

$preflight = @'
set -euo pipefail
test "$(uname -m)" = 'x86_64'
current=$(sudo -n docker inspect --format '{{.Image}}' domino-synthetic-population)
test "$current" = '__OLD_IMAGE__'
state=$(sudo -n docker inspect --format '{{range .Mounts}}{{if eq .Destination "/state"}}{{.Name}}{{end}}{{end}}' domino-synthetic-population)
test "$state" = '__VOLUME__'
status=$(sudo -n docker inspect --format '{{.State.Status}}' domino-synthetic-population)
test "$status" = 'running'
actual=$(sudo -n sha256sum '__BACKUP_PATH__' | awk '{print $1}')
test "$actual" = '__BACKUP_HASH__'
echo 'STAGE_BASELINE_AND_BACKUP=PASS'
'@
$preflight = $preflight.Replace('__OLD_IMAGE__', $previousImage).
    Replace('__VOLUME__', $volumeName).
    Replace('__BACKUP_PATH__', $BackupPath).
    Replace('__BACKUP_HASH__', $backupHash)
Invoke-RemoteReadiness $preflight

$scp = (Get-Command scp.exe -ErrorAction Stop).Source
$remoteArchive = '/var/tmp/' + $filename
Write-Host 'TRANSPORTING_VERIFIED_ARCHIVE'
& $scp -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $archive ($RemoteHost + ':' + $remoteArchive)
Assert-DP ($LASTEXITCODE -eq 0) 'STAGE_SCP_FAILED'

$load = @'
set -euo pipefail
archive='__REMOTE_ARCHIVE__'
actual=$(sha256sum "$archive" | awk '{print $1}')
test "$actual" = '__ARCHIVE_HASH__'
echo 'REMOTE_ARCHIVE_HASH=PASS'
sudo -n docker load --input "$archive" >/dev/null
actualImage=$(sudo -n docker image inspect --format '{{.Id}}' '__NEW_TAG__')
test "$actualImage" = '__NEW_IMAGE_ID__'
current=$(sudo -n docker inspect --format '{{.Image}}' domino-synthetic-population)
test "$current" = '__OLD_IMAGE__'
state=$(sudo -n docker inspect --format '{{range .Mounts}}{{if eq .Destination "/state"}}{{.Name}}{{end}}{{end}}' domino-synthetic-population)
test "$state" = '__VOLUME__'
echo 'STAGED_IMAGE_ID=PASS'
echo 'EXISTING_CONTAINER_AND_VOLUME=UNCHANGED'
echo 'CUTOVER_EXECUTED=NO'
'@
$load = $load.Replace('__REMOTE_ARCHIVE__', $remoteArchive).
    Replace('__ARCHIVE_HASH__', $expectedArchiveHash).
    Replace('__NEW_TAG__', $imageTag).
    Replace('__NEW_IMAGE_ID__', $imageId).
    Replace('__OLD_IMAGE__', $previousImage).
    Replace('__VOLUME__', $volumeName)
Invoke-RemoteReadiness $load
Write-Host 'STAGING_COMPLETE=PASS'
