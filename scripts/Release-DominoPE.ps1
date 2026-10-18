#requires -Version 5.1
[CmdletBinding()]
param(
    [int]$VersionCode = 36,
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Resolve-Executable([string]$Path) {
    Assert-That (Test-Path -LiteralPath $Path -PathType Leaf) "Arquivo nao encontrado: $Path"
    return (Resolve-Path -LiteralPath $Path).Path
}
function Read-Config {
    $path = Join-Path $HOME '.domino-pernambucano\release\production.json'
    Assert-That (Test-Path -LiteralPath $path -PathType Leaf) 'CONFIG_RELEASE_AUSENTE'
    $cfg = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    Assert-That ($cfg.schemaVersion -eq 1) 'CONFIG_SCHEMA_NAO_SUPORTADO'
    foreach ($field in @('googleWebClientId','admobAppId','admobBannerUnitId','admobInterstitialUnitId','uploadKeystore','uploadKeyAlias','uploadCertSha256')) {
        Assert-That (-not [string]::IsNullOrWhiteSpace([string]$cfg.$field)) "CONFIG_CAMPO_AUSENTE=$field"
    }
    return $cfg
}
function Assert-Equal([string]$Actual, [string]$Expected, [string]$Name) {
    Assert-That ($Actual -ceq $Expected) "VALOR_DIVERGENTE=$Name"
}
function Get-DexValues([string]$Package, [string]$Regex) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($Package)
    $matches = New-Object 'System.Collections.Generic.List[string]'
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '(^|/)classes[0-9]*\.dex$' -and $entry.FullName -notmatch '(^|/)classes\.dex$') { continue }
            $stream = $entry.Open()
            $mem = New-Object IO.MemoryStream
            try {
                $stream.CopyTo($mem)
                $body = [Text.Encoding]::ASCII.GetString($mem.ToArray())
                foreach ($hit in [regex]::Matches($body, $Regex)) { $matches.Add($hit.Value) }
            } finally { $stream.Dispose(); $mem.Dispose() }
        }
    } finally { $archive.Dispose() }
    return @($matches | Sort-Object -Unique)
}
function Check-IdsInPackage([string]$Package, $Cfg) {
    $oauth = @(Get-DexValues $Package '[0-9]{10,}-[a-zA-Z0-9]+\.apps\.googleusercontent\.com')
    Assert-That ($oauth.Count -eq 1) "OAUTH_QUANTIDADE_INVALIDA=$($oauth.Count)"
    Assert-Equal $oauth[0] $Cfg.googleWebClientId 'OAUTH_NO_ARTEFATO'
    $ads = @(Get-DexValues $Package 'ca-app-pub-[0-9]{16}[~/][0-9]{10}')
    foreach ($value in @($Cfg.admobBannerUnitId, $Cfg.admobInterstitialUnitId)) {
        Assert-That ($ads -ccontains $value) 'ADMOB_UNIDADE_AUSENTE_NO_ARTEFATO'
    }
    Write-Host "IDS_ARTEFATO_PASS=$([IO.Path]::GetFileName($Package))"
}

$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gradleFile = Resolve-Executable (Join-Path $repo 'app\build.gradle.kts')
$cfg = Read-Config
Assert-That ($VersionCode -gt 0) 'VERSION_CODE_INVALIDO'
$gradleText = [IO.File]::ReadAllText($gradleFile)
$match = [regex]::Matches($gradleText, '(?m)^\s*versionCode\s*=\s*(\d+)\s*$')
Assert-That ($match.Count -eq 1) 'VERSION_CODE_NAO_UNICO'
Assert-That ([int]$match[0].Groups[1].Value -eq $VersionCode) 'VERSION_CODE_DIVERGENTE'
Assert-That ($cfg.googleWebClientId -match '^[0-9]+-[A-Za-z0-9]+\.apps\.googleusercontent\.com$') 'OAUTH_INVALIDO'
Assert-That ($cfg.admobAppId -match '^ca-app-pub-[0-9]{16}~[0-9]{10}$') 'ADMOB_APP_INVALIDO'
Assert-That ($cfg.admobBannerUnitId -match '^ca-app-pub-[0-9]{16}/[0-9]{10}$') 'ADMOB_BANNER_INVALIDO'
Assert-That ($cfg.admobInterstitialUnitId -match '^ca-app-pub-[0-9]{16}/[0-9]{10}$') 'ADMOB_INTERSTITIAL_INVALIDO'
Assert-That ($cfg.admobBannerUnitId -cne $cfg.admobInterstitialUnitId) 'ADMOB_UNIDADES_REPETIDAS'
Assert-That ($cfg.admobAppId -notmatch 'ca-app-pub-0000000000000000') 'ADMOB_PLACEHOLDER'
$keystore = Resolve-Executable $cfg.uploadKeystore
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
Assert-That (Test-Path -LiteralPath (Join-Path $sdk 'platform-tools\adb.exe') -PathType Leaf) 'ANDROID_SDK_AUSENTE'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

# Live backend authority. Do not print secrets or environment dump.
$sshKey = Resolve-Executable (Join-Path $HOME '.ssh\lightsail-sa-east-1.pem')
$backendId = @(& ssh -o BatchMode=yes -o ConnectTimeout=12 -i $sshKey ubuntu@56.124.45.216 'sudo -n docker exec domino-server printenv DOMINO_GOOGLE_WEB_CLIENT_ID')
Assert-That ($LASTEXITCODE -eq 0 -and $backendId.Count -eq 1) 'BACKEND_OAUTH_CONSULTA_FALHOU'
Assert-Equal ([string]$cfg.googleWebClientId) (([string]$backendId[0]).Trim()) 'OAUTH_VS_BACKEND'
Write-Host 'CONFIG_E_BACKEND_PASS'

$env:DOMINO_GOOGLE_WEB_CLIENT_ID = [string]$cfg.googleWebClientId
$env:DOMINO_ADMOB_APP_ID = [string]$cfg.admobAppId
$env:DOMINO_ADMOB_BANNER_UNIT_ID = [string]$cfg.admobBannerUnitId
$env:DOMINO_ADMOB_INTERSTITIAL_UNIT_ID = [string]$cfg.admobInterstitialUnitId
$env:DOMINO_UPLOAD_STORE_FILE = $keystore
$env:DOMINO_UPLOAD_KEY_ALIAS = [string]$cfg.uploadKeyAlias

if (-not $Build) {
    Write-Host "PREFLIGHT_PASS versionCode=$VersionCode"
    Write-Host 'Para compilar APK e AAB, execute novamente com -Build (senhas DOMINO_UPLOAD_* devem existir na sessao).'
    exit 0
}
foreach ($secret in @('DOMINO_UPLOAD_STORE_PASSWORD','DOMINO_UPLOAD_KEY_PASSWORD')) {
    Assert-That (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($secret,'Process'))) "SENHA_AUSENTE=$secret"
}

# keytool supports -storepass:env and does not echo the password or place it in process arguments.
$keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source)
if (-not $keytool) {
    $keytool = Join-Path ${env:ProgramFiles} 'Android\Android Studio\jbr\bin\keytool.exe'
}
$keytool = Resolve-Executable $keytool
$keyInfo = @(& $keytool -list -v -keystore $keystore -alias $cfg.uploadKeyAlias '-storepass:env' DOMINO_UPLOAD_STORE_PASSWORD 2>&1)
Assert-That ($LASTEXITCODE -eq 0) 'KEYSTORE_VERIFICACAO_FALHOU'
$shaLines = @($keyInfo | ForEach-Object { [string]$_ } | Where-Object { $_ -match 'SHA256:\s*([0-9A-Fa-f:]+)' })
Assert-That ($shaLines.Count -eq 1) 'CERTIFICADO_SHA_NAO_LOCALIZADO'
$sha = ([regex]::Match($shaLines[0], 'SHA256:\s*([0-9A-Fa-f:]+)')).Groups[1].Value.Replace(':','').ToLowerInvariant()
Assert-Equal $sha (([string]$cfg.uploadCertSha256).ToLowerInvariant()) 'ASSINATURA_UPLOAD'
Write-Host 'ASSINATURA_UPLOAD_PASS'

$gradle = Resolve-Executable (Join-Path $repo 'gradlew.bat')
Push-Location $repo
try {
    & $gradle :app:assembleRelease :app:bundleRelease --no-daemon --console=plain
    Assert-That ($LASTEXITCODE -eq 0) 'GRADLE_BUILD_FAIL'
} finally { Pop-Location }
$apk = Resolve-Executable (Join-Path $repo 'app\build\outputs\apk\release\app-release.apk')
$aab = Resolve-Executable (Join-Path $repo 'app\build\outputs\bundle\release\app-release.aab')
$signer = Get-ChildItem (Join-Path $sdk 'build-tools') -Filter apksigner.bat -Recurse -File |
    Sort-Object { [version]$_.Directory.Name } -Descending | Select-Object -First 1 -ExpandProperty FullName
$signer = Resolve-Executable $signer
$signResult = @(& $signer verify --print-certs $apk)
Assert-That ($LASTEXITCODE -eq 0) 'APK_ASSINATURA_INVALIDA'
$shaApk = @($signResult | Where-Object { $_ -match 'certificate SHA-256 digest:\s*([0-9a-f]+)' })
Assert-That ($shaApk.Count -ge 1) 'APK_CERTIFICADO_AUSENTE'
$actualApk = [regex]::Match($shaApk[0], 'digest:\s*([0-9a-f]+)').Groups[1].Value
Assert-Equal $actualApk (([string]$cfg.uploadCertSha256).ToLowerInvariant()) 'APK_CERTIFICADO'
Check-IdsInPackage $apk $cfg
Check-IdsInPackage $aab $cfg

# jarsigner is an independent signature check of the AAB upload artifact.
$jarsigner = Join-Path (Split-Path $keytool -Parent) 'jarsigner.exe'
$jarsigner = Resolve-Executable $jarsigner
& $jarsigner -verify $aab | Out-Null
Assert-That ($LASTEXITCODE -eq 0) 'AAB_ASSINATURA_INVALIDA'
Write-Host "RELEASE_ARTIFACTS_PASS versionCode=$VersionCode"
Write-Host "APK=$apk"
Write-Host "AAB=$aab"
Write-Host 'PUBLICACAO_NAO_EXECUTADA'
