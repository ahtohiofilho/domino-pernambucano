#requires -Version 5.1
<#
  QA ONLY: hotfix 15s + Android v35 post-match recovery.
  Does NOT merge, commit, deploy, publish or touch the current working tree.
  Run from inside the domino-pernambucano repository.
#>
param(
    [string]$RepoRoot = (Get-Location).Path,
    [switch]$Device
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$workDir = Join-Path $env:TEMP ("domino-v35-qa-" + $stamp)
$evidence = Join-Path $env:USERPROFILE ("Downloads\DOMINO-V35-QA-" + $stamp)
$hotfix = Join-Path $workDir 'server'
$android = Join-Path $workDir 'android'
$added = New-Object System.Collections.Generic.List[string]
$failed = $false

New-Item -ItemType Directory -Force -Path $workDir, $evidence | Out-Null
function Invoke-Git {
    param([string[]]$GitArgs)
    & git -C $repo @GitArgs
    if ($LASTEXITCODE -ne 0) { throw ('git failed: ' + ($GitArgs -join ' ')) }
}
function Invoke-Gradle {
    param([string]$Directory, [string]$Name, [string[]]$Tasks)
    $gradlew = Join-Path $Directory 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) { throw ('Missing: ' + $gradlew) }
    $stdout = Join-Path $evidence ($Name + '-stdout.txt')
    $stderr = Join-Path $evidence ($Name + '-stderr.txt')
    Write-Host ('Running ' + $Name)
    $p = Start-Process -FilePath $gradlew -WorkingDirectory $Directory `
        -ArgumentList $Tasks -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if ($p.ExitCode -ne 0) { throw ($Name + ' failed: exit ' + $p.ExitCode) }
    Write-Host ('PASS ' + $Name)
}
try {
    if (-not (Test-Path (Join-Path $repo 'gradlew.bat'))) {
        throw 'Run from the root of domino-pernambucano (gradlew.bat required).'
    }
    Invoke-Git -GitArgs @('fetch', 'origin', 'hotfix/synthetic-fallback-15s', 'feature/v35-postmatch-recovery')
    Invoke-Git -GitArgs @('worktree', 'add', '--detach', $hotfix, 'origin/hotfix/synthetic-fallback-15s')
    [void]$added.Add($hotfix)
    Invoke-Git -GitArgs @('worktree', 'add', '--detach', $android, 'origin/feature/v35-postmatch-recovery')
    [void]$added.Add($android)
    & git -C $hotfix rev-parse HEAD | Out-File (Join-Path $evidence 'server-commit.txt')
    & git -C $android rev-parse HEAD | Out-File (Join-Path $evidence 'android-commit.txt')

    Invoke-Gradle -Directory $hotfix -Name 'server-15s' -Tasks @(
        '--no-daemon', ':server:test',
        '--tests', 'com.ahtohiofilho.dominopernambucano.server.PublicRankedSyntheticFallbackAuthorityTest',
        '--tests', 'com.ahtohiofilho.dominopernambucano.server.PublicRankedSyntheticFallbackRematchIntegrationTest'
    )
    Invoke-Gradle -Directory $android -Name 'v35-unit' -Tasks @(
        '--no-daemon', ':app:testDebugUnitTest',
        '--tests', 'com.ahtohiofilho.dominopernambucano.advertising.AdvertisingFrequencyGateTest',
        '--tests', 'com.ahtohiofilho.dominopernambucano.ui.game.PostMatchRecoveryPolicyTest'
    )
    if ($Device) {
        Invoke-Gradle -Directory $android -Name 'v35-device' -Tasks @(
            '--no-daemon', ':app:connectedDebugAndroidTest',
            '-Pandroid.testInstrumentationRunnerArguments.class=com.ahtohiofilho.dominopernambucano.advertising.PostMatchRecoveryStoreTest'
        )
    }
} catch {
    $failed = $true
    ('FAIL: ' + $_.Exception.Message) | Out-File (Join-Path $evidence 'failure.txt')
    Write-Host ('FAIL: ' + $_.Exception.Message)
} finally {
    foreach ($path in $added) {
        & git -C $repo worktree remove --force $path 2>$null
    }
    $zip = $evidence + '.zip'
    Compress-Archive -Path (Join-Path $evidence '*') -DestinationPath $zip -Force
    Write-Host ('EVIDENCE=' + $zip)
}
if ($failed) { exit 1 }
exit 0
