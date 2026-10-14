param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.kitt\signing\acceptance.keystore'),
    [string]$ApkPath = '',
    [string]$ExpectedApplicationId = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# The same certificate as the installed 0.3.5.1 runtimecheck APK.
$expected = '3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba'
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw 'KITT canonical signing keystore missing. See docs/SIGNING_UNIFICATION.md. Do not generate a replacement.'
}
$keytool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\keytool.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\keytool.exe'
} else {
    (Get-Command 'keytool.exe' -ErrorAction Stop).Source
}
# Existing KITT acceptance signing uses the standard Android Debug key, alias and password.
# The path is private to the user's machine; never commit its bytes to GitHub.
$details = (& $keytool '-J-Duser.language=en' '-J-Duser.country=US' -list -v -keystore $KeystorePath -storepass android -alias androiddebugkey 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the original KITT signing key; check path and key metadata.' }
if ($details -notmatch '(?im)Entry type:\s*PrivateKeyEntry\b') {
    throw 'KITT signing keystore must contain the original private key, not just a public certificate.'
}
$match = [regex]::Match($details, '(?im)\bSHA256:\s*([A-F0-9:]{64,})\b')
if (-not $match.Success) { throw 'Cannot read SHA256 certificate fingerprint from keytool.' }
$actual = $match.Groups[1].Value.Replace(':', '').ToLowerInvariant()
if ($actual -ne $expected) {
    throw "KITT signing certificate mismatch: expected $expected but found $actual. DO NOT uninstall the installed app."
}
Write-Output "PASS: canonical KITT keystore certificate SHA256=$actual"

if ($ApkPath) {
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK file does not exist: $ApkPath" }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to validate APK signatures.' }
    $signer = Join-Path $sdk 'build-tools\35.0.0\apksigner.bat'
    if (-not (Test-Path -LiteralPath $signer)) { throw 'Android SDK build-tools 35.0.0 apksigner missing.' }
    # Windows PowerShell 5 turns native stderr into ErrorRecords. Capture an expected
    # rejection before restoring Stop, then fail closed on the native exit code.
    $taskErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $apkDetails = (& $signer verify --print-certs $ApkPath 2>&1) -join "`n"
        $taskSignerExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskErrorPreference }
    if ($taskSignerExit -ne 0) { throw 'APK signature verification failed.' }
    $apkMatches = [regex]::Matches($apkDetails, '(?im)Signer #\d+ certificate SHA-256 digest:\s*([A-F0-9:]{64,})\b')
    if ($apkMatches.Count -ne 1) { throw 'APK must have exactly one signer: the original KITT certificate.' }
    $apkMatch = $apkMatches[0]
    $apkCert = $apkMatch.Groups[1].Value.Replace(':', '').ToLowerInvariant()
    if ($apkCert -ne $expected) {
        throw "APK signature mismatch: expected $expected but found $apkCert. DO NOT install over existing runtimecheck."
    }
    Write-Output "PASS: APK certificate SHA256=$apkCert"
    $aapt = Join-Path $sdk 'build-tools\35.0.0\aapt.exe'
    $badging = (& $aapt dump badging $ApkPath 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK package metadata.' }
    $package = [regex]::Match($badging, "(?m)^package: name='([^']+)' versionCode='(\d*)' versionName='([^']*)'")
    if (-not $package.Success) { throw 'Cannot parse APK package metadata.' }
    $applicationId = $package.Groups[1].Value
    if ($ExpectedApplicationId -and $applicationId -ne $ExpectedApplicationId) {
        throw "APK applicationId mismatch: expected $ExpectedApplicationId but found $applicationId."
    }
    Write-Output "PASS: APK applicationId=$applicationId versionCode=$($package.Groups[2].Value) versionName=$($package.Groups[3].Value)"
} elseif ($ExpectedApplicationId) {
    throw 'ExpectedApplicationId requires ApkPath.'
}
