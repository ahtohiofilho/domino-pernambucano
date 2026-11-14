#requires -Version 5.1
<#
  v37 ranked queue gate fix. Read-only Docker preflight by default.
  -Execute requires separate authorization and changes only domino-server.
  Never logs environment values, tokens, signing secrets or full Docker inspect.
#>
[CmdletBinding()]
param(
    [switch]$Execute,
    [string]$Approval,
    [string]$RemoteHost = 'ubuntu@56.124.45.216',
    [string]$SshKey = (Join-Path $HOME '.ssh\lightsail-sa-east-1.pem')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Execute -and $Approval -cne 'AUTORIZO-CORRECAO-ALLOWLIST-V37') {
    throw 'SERVER_CUTOVER_APPROVAL_REQUIRED'
}
if (-not $Execute -and -not [string]::IsNullOrWhiteSpace($Approval)) {
    throw 'SERVER_CUTOVER_APPROVAL_WITHOUT_EXECUTE'
}
if ($RemoteHost -notmatch '^[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+$') {
    throw 'REMOTE_HOST_INVALID'
}
if (-not (Test-Path -LiteralPath $SshKey -PathType Leaf)) {
    throw 'SSH_KEY_NOT_FOUND'
}

$scriptPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\deploy\release\dp_server_v37_allowlist_cutover.py')).Path
$localHash = (Get-FileHash -LiteralPath $scriptPath -Algorithm SHA256).Hash.ToLowerInvariant()
$source = $scriptPath
$remoteInbox = '/tmp/dp_server_v37_allowlist_cutover.py'
$remoteScript = '/var/backups/domino-pe/ops/dp_server_v37_allowlist_cutover.py'
$scp = (Get-Command scp.exe -ErrorAction Stop).Source
$ssh = (Get-Command ssh.exe -ErrorAction Stop).Source

Write-Host "LOCAL_SERVER_CUTOVER_SCRIPT_SHA256=$localHash"
Write-Host 'COPYING_READ_ONLY_PREFLIGHT_CONTROLLER'
& $scp -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $source ($RemoteHost + ':' + $remoteInbox)
if ($LASTEXITCODE -ne 0) {
    throw 'SERVER_CONTROLLER_TRANSFER_FAILED'
}

$mode = if ($Execute) {
    '--execute --approval AUTORIZO-CORRECAO-ALLOWLIST-V37'
} else {
    ''
}
$linux = @'
set -euo pipefail
sudo -n install -d -m 0700 /var/backups/domino-pe/ops
sudo -n install -m 0500 -o root -g root '__INBOX__' '__REMOTE_SCRIPT__'
rm -f '__INBOX__'
actual=$(sudo -n sha256sum '__REMOTE_SCRIPT__' | awk '{print $1}')
test "$actual" = '__SHA256__' || { echo 'SERVER_CONTROLLER_HASH_MISMATCH'; exit 42; }
echo 'SERVER_CONTROLLER_HASH=PASS'
sudo -n python3 '__REMOTE_SCRIPT__' __MODE__
'@
$linux = $linux.Replace('__INBOX__', $remoteInbox).
    Replace('__REMOTE_SCRIPT__', $remoteScript).
    Replace('__SHA256__', $localHash).
    Replace('__MODE__', $mode)
$linux = ($linux.Replace([string][char]13 + [string][char]10, [string][char]10).
    Replace([string][char]13, [string][char]10)) + [char]10
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($linux))
$remoteCommand = "printf '%s' '$encoded' | base64 --decode | bash"

$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $output = @(& $ssh -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $RemoteHost $remoteCommand 2>&1)
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousPreference
}
foreach ($line in $output) {
    Write-Host ([string]$line)
}
if ($exitCode -ne 0) {
    throw "SERVER_ALLOWLIST_CONTROLLER_FAILED=$exitCode"
}
if ($Execute) {
    Write-Host 'SERVER_CUTOVER_CONTROLLER=EXECUTION_COMPLETED'
} else {
    Write-Host 'SERVER_CUTOVER_CONTROLLER=READ_ONLY_PREFLIGHT_PASS'
    Write-Host 'DOCKER_CHANGES=NONE'
}
