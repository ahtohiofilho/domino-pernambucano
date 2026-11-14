#requires -Version 5.1
# Regression for the release DEX scanner under Windows PowerShell 5.1.
# The test never reads production.json, certificates or upload credentials.
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$releaseScript = Join-Path $repo 'scripts\Release-DominoPE.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $releaseScript, [ref]$tokens, [ref]$parseErrors
)
if ($parseErrors.Count -ne 0) { throw 'RELEASE_SCRIPT_PARSE_FAILED' }
$functionAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -ceq 'Get-DexValues'
}, $true)
if (-not $functionAst) { throw 'GET_DEX_VALUES_NOT_FOUND' }

# Import only the scanner function: do not execute the release script.
. ([scriptblock]::Create($functionAst.Extent.Text))

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$folder = Join-Path $env:TEMP ('dp-v37-dex-scan-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$package = Join-Path $folder 'synthetic.apk'
try {
    $zip = [IO.Compression.ZipFile]::Open($package, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $expectedOAuth = '123456789012-abcdef.apps.googleusercontent.com'
        $expectedAd = 'ca-app-pub-1234567890123456/1234567890'
        foreach ($name in @('classes.dex','assets/ignored.txt')) {
            $entry = $zip.CreateEntry($name)
            $stream = $entry.Open()
            try {
                $body = if ($name -eq 'classes.dex') {
                    "other $expectedOAuth $expectedAd $expectedOAuth"
                } else {
                    'ca-app-pub-9999999999999999/9999999999'
                }
                $bytes = [Text.Encoding]::ASCII.GetBytes($body)
                $stream.Write($bytes, 0, $bytes.Length)
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }

    $oauth = @(Get-DexValues $package '[0-9]{10,}-[a-zA-Z0-9]+\.apps\.googleusercontent\.com')
    if ($oauth.Count -ne 1 -or $oauth[0] -cne $expectedOAuth) {
        throw 'DEX_OAUTH_SCAN_REGRESSION'
    }
    $ads = @(Get-DexValues $package 'ca-app-pub-[0-9]{16}[~/][0-9]{10}')
    if ($ads.Count -ne 1 -or $ads[0] -cne $expectedAd) {
        throw 'DEX_ADMOB_SCAN_REGRESSION'
    }
    Write-Host 'RELEASE_DEX_SCAN_POWERSHELL51=PASS'
} finally {
    Remove-Item -LiteralPath $folder -Recurse -Force -ErrorAction SilentlyContinue
}
