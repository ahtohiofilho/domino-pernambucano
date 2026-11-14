#requires -Version 5.1
<#
  DP v37 cutover controller. Default: guarded READ-ONLY runtime preflight.
  The script itself is copied to root-owned ops storage on the AWS host; no
  Docker container/volume changes occur until explicit -Execute + approval.
  Never print environment values, token values or the content of credentials.
#>
[CmdletBinding()]
param(
    [switch]$Execute,
    [string]$Approval = '',
    [string]$RemoteHost = 'ubuntu@56.124.45.216',
    [string]$SshKey = (Join-Path $HOME '.ssh\lightsail-sa-east-1.pem')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Execute -and $Approval -cne 'AUTORIZO-CUTOVER-SINTETICOS-V37') {
    throw 'AUTORIZACAO_EXPLICITA_NECESSARIA'
}
if (-not $Execute -and $Approval) { throw 'AUTORIZACAO_SEM_EXECUCAO' }
if ($RemoteHost -notmatch '^[A-Za-z0-9_-]+@[A-Za-z0-9.-]+$') { throw 'REMOTE_HOST_INVALID' }
if (-not (Test-Path -LiteralPath $SshKey -PathType Leaf)) { throw 'SSH_KEY_MISSING' }
$source = Join-Path $PSScriptRoot '..\deploy\release\dp_synthetic_cutover.py'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw 'CUTOVER_SCRIPT_MISSING' }
$hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$sourceName = 'cutover-v37-' + $hash.Substring(0, 16) + '.py'
$remoteInbox = '/home/ubuntu/.dp-cutover-upload-' + [guid]::NewGuid().ToString('N') + '.py'
$remoteScript = '/var/backups/domino-pe/ops/' + $sourceName
$scp = (Get-Command scp.exe -ErrorAction Stop).Source
$ssh = (Get-Command ssh.exe -ErrorAction Stop).Source
Write-Host "LOCAL_CUTOVER_SCRIPT_SHA256=$hash"
Write-Host 'COPYING_EXECUTION_PLAN_TO_ROOT_OWNED_STORAGE'
& $scp -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $source ($RemoteHost + ':' + $remoteInbox)
if ($LASTEXITCODE -ne 0) { throw 'CUTOVER_SCRIPT_TRANSFER_FAILED' }

$mode = if ($Execute) { '--execute --approval AUTORIZO-CUTOVER-SINTETICOS-V37' } else { '' }
$linux = @'
set -euo pipefail
sudo -n install -d -m 0700 /var/backups/domino-pe/ops
sudo -n install -m 0500 -o root -g root '__INBOX__' '__REMOTE_SCRIPT__'
rm -f '__INBOX__'
actual=$(sudo -n sha256sum '__REMOTE_SCRIPT__' | awk '{print $1}')
test "$actual" = '__SHA256__' || { echo 'REMOTE_SCRIPT_CHECKSUM_FAILED'; exit 42; }
echo 'CUTOVER_EXECUTION_PLAN_HASH=PASS'
sudo -n python3 '__REMOTE_SCRIPT__' __MODE__
'@
$linux = $linux.Replace('__INBOX__', $remoteInbox).
    Replace('__REMOTE_SCRIPT__', $remoteScript).
    Replace('__SHA256__', $hash).
    Replace('__MODE__', $mode)
$linux = ($linux.Replace([string][char]13 + [string][char]10, [string][char]10).
    Replace([string][char]13, [string][char]10)) + [char]10
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($linux))
$command = "printf '%s' '$encoded' | base64 --decode | bash"
$previous = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $result = @(& $ssh -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $RemoteHost $command 2>&1)
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
}
foreach ($line in $result) { Write-Host ([string]$line) }
if ($exitCode -ne 0) { throw "CUTOVER_CONTROL_FAILED=$exitCode" }
if ($Execute) {
    Write-Host 'CUTOVER_CONTROLLER=EXECUTION_COMPLETED'
} else {
    Write-Host 'CUTOVER_CONTROLLER=READ_ONLY_PREFLIGHT_PASS'
    Write-Host 'DOCKER_CHANGES=NONE'
}
