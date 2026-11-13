#requires -Version 5.1
<#
  DP v37 read-only preflight. Does not mutate Docker, state or Google Play.
  Never collect or print secrets, tokens, passwords or environment variables.
#>
[CmdletBinding()]
param(
  [string]$RepoRoot = (Join-Path $PSScriptRoot '..'),
  [string]$RemoteHost = 'ubuntu@56.124.45.216',
  [string]$SshKey = (Join-Path $HOME '.ssh\lightsail-sa-east-1.pem'),
  [switch]$SkipRemote
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
if (-not (Test-Path (Join-Path $repo 'gradlew.bat'))) { throw 'REPO_INVALIDO' }
$evidence = Join-Path $HOME ("Downloads\DP-V37-READINESS-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
$report = New-Object System.Collections.Generic.List[string]
function Record([string]$s) { Write-Host $s; [void]$report.Add($s) }
$git = (Get-Command git.exe -ErrorAction Stop).Source
$head = (& $git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'GIT_HEAD_FAIL' }
Record "HEAD=$head"
$status = @(& $git -C $repo status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'GIT_STATUS_FAIL' }
if ($status.Count -gt 0) { Record 'WORKTREE=DIRTY' } else { Record 'WORKTREE=CLEAN' }
$gradle = [IO.File]::ReadAllText((Join-Path $repo 'app\build.gradle.kts'))
$match = [regex]::Match($gradle, '(?m)^\s*versionCode\s*=\s*(\d+)\s*$')
if (-not $match.Success) { throw 'VERSION_CODE_MISSING' }
Record "CANDIDATE_VERSION_CODE=$($match.Groups[1].Value)"
if ($match.Groups[1].Value -ne '37') { throw 'CANDIDATE_NOT_37' }
Record 'PLAY_HIGHEST_VERSION=VERIFY_MANUALLY'
if (-not $SkipRemote) {
  if ($RemoteHost -notmatch '^[A-Za-z0-9_-]+@[A-Za-z0-9.-]+$') { throw 'REMOTE_HOST_INVALID' }
  if (-not (Test-Path -LiteralPath $SshKey -PathType Leaf)) { throw 'SSH_KEY_MISSING' }
  $ssh = (Get-Command ssh.exe -ErrorAction Stop).Source
  $remote = @'
set -eu
echo '=== DP REMOTE READ ONLY ==='
for name in domino-server domino-synthetic-population; do
  sudo -n docker inspect --format 'CONTAINER={{.Name}} IMAGE={{.Config.Image}} IMAGE_ID={{.Image}} STATUS={{.State.Status}} RESTARTS_TOTAL={{.RestartCount}} HEALTH={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "$name"
  sudo -n docker inspect --format 'MOUNTS={{range .Mounts}}{{.Destination}}:{{.Type}};{{end}}' "$name"
done
if sudo -n docker exec domino-synthetic-population test -s /state/synthetic-identities.json; then
  echo 'SYNTHETIC_IDENTITIES=PRESENT_NONEMPTY'
else
  echo 'SYNTHETIC_IDENTITIES=UNCONFIRMED'
fi
curl --fail --silent --show-error --max-time 12 -o /dev/null https://api.dominope.com.br/health
echo 'PUBLIC_HEALTH=PASS'
curl --fail --silent --show-error --max-time 12 -o /dev/null https://api.dominope.com.br/ready
echo 'PUBLIC_READY=PASS'
echo 'REMOTE_READ_ONLY=PASS'
'@
  # Windows PowerShell 5.1 pipes strings to native stdin with Windows-specific
  # encoding/newlines. Sending the here-string to 'bash -s' can corrupt
  # 'set -eu' on its first line. Encode UTF-8 + LF and decode remotely instead.
  # Base64 is data, not a secret, and avoids all shell-quoting/CRLF ambiguity.
  $linuxScript = (($remote -replace "`r`n", "`n") -replace "`r", "`n") + "`n"
  $encoded = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes($linuxScript)
  )
  $decoded = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($encoded)
  )
  if ($decoded -cne $linuxScript -or
      $decoded.Contains("`r") -or
      -not $decoded.StartsWith("set -eu`n", [StringComparison]::Ordinal)) {
    throw 'REMOTE_SCRIPT_ENCODING_INVALID'
  }
  Record 'SSH_SCRIPT_ENCODING=UTF8_LF_BASE64_PASS'
  $remoteCommand = "printf '%s' '$encoded' | base64 --decode | bash"
  # Do not send a PowerShell pipeline into SSH stdin. Capture the native exit
  # status even if SSH emits stderr (PowerShell 5.1 + Stop can throw early).
  $previousErrorActionPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    $output = @(& $ssh -i $SshKey -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=12 $RemoteHost $remoteCommand 2>&1)
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }
  foreach ($item in $output) { Record ([string]$item) }
  if ($exitCode -ne 0) {
    $report | Out-File -LiteralPath $evidence -Encoding UTF8
    throw "REMOTE_PREFLIGHT_FAILED=$exitCode; EVIDENCE=$evidence"
  }
} else { Record 'REMOTE_PREFLIGHT=SKIPPED' }
Record 'PRODUCTION_CHANGES=NONE'
$report | Out-File -LiteralPath $evidence -Encoding UTF8
Write-Host "EVIDENCE=$evidence"
