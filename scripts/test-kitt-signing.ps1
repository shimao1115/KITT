param(
    [Parameter(Mandatory = $true)][string]$RuntimeApkPath,
    [string]$UnsignedApkPath = 'app\build\outputs\apk\release\app-release-unsigned.apk'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskCheck = Join-Path $PSScriptRoot 'check-kitt-signing.ps1'

# Exercise real keytool/apksigner/aapt gates. No key generation or phone commands.
function Assert-Rejected([string]$Name, [hashtable]$Arguments, [string]$Reason) {
    $taskRejected = $false
    try { & $taskCheck @Arguments | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Reason*") { throw }
        $taskRejected = $true
    }
    if (-not $taskRejected) { throw "Signing gate unexpectedly accepted: $Name" }
    Write-Output "PASS: rejected $Name"
}

& $taskCheck
& $taskCheck -ApkPath $RuntimeApkPath -ExpectedApplicationId 'com.kitt.reader.runtimecheck'
$taskMissing = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString() + '.missing')
Assert-Rejected 'missing key' @{ KeystorePath = $taskMissing } 'keystore missing'
Assert-Rejected 'missing APK' @{ ApkPath = $taskMissing } 'APK file does not exist'
Assert-Rejected 'package constraint without APK' @{ ExpectedApplicationId = 'com.kitt.reader.runtimecheck' } 'requires ApkPath'
Assert-Rejected 'wrong applicationId constraint' @{ ApkPath = $RuntimeApkPath; ExpectedApplicationId = 'com.kitt.reader' } 'applicationId mismatch'
if (-not (Test-Path -LiteralPath $UnsignedApkPath -PathType Leaf)) { throw 'Build the unsigned Release APK before running this regression.' }
Assert-Rejected 'unsigned APK' @{ ApkPath = $UnsignedApkPath } 'APK signature verification failed'

# A public-certificate-only fixture proves a matching fingerprint is insufficient.
# Export only the public certificate; never export, replace or generate a private key.
$taskKeytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$taskPublicCertificate = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString() + '.cer')
$taskCertificateStore = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString() + '.jks')
try {
    & $taskKeytool -exportcert -keystore (Join-Path $env:USERPROFILE '.kitt\signing\acceptance.keystore') -storepass android -alias androiddebugkey -file $taskPublicCertificate 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot export public certificate fixture.' }
    & $taskKeytool -importcert -noprompt -storetype JKS -keystore $taskCertificateStore -storepass android -alias androiddebugkey -file $taskPublicCertificate 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create public certificate fixture.' }
    Assert-Rejected 'matching certificate without private key' @{ KeystorePath = $taskCertificateStore } 'original private key'
} finally {
    foreach ($taskFixture in @($taskPublicCertificate, $taskCertificateStore)) {
        if (Test-Path -LiteralPath $taskFixture) { Remove-Item -LiteralPath $taskFixture }
    }
}
Write-Output 'PASS: signing regression (2 accepted cases, 6 rejected cases).'
