param([switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        throw 'Set JAVA_HOME to JDK 17 before building.'
    }
    $taskArguments = @('assembleDebug', 'testDebugUnitTest', 'lintDebug', '--console=plain')
    if ($Offline) { $taskArguments += '--offline' }
    & .\gradlew.bat @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Build/test/lint failed; no acceptance package was produced.' }
    $taskSdk = $env:ANDROID_HOME
    if (-not $taskSdk) { $taskSdk = $env:ANDROID_SDK_ROOT }
    $taskSigner = Join-Path $taskSdk 'build-tools\35.0.0\apksigner.bat'
    & $taskSigner verify --verbose app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $taskSuites = Get-ChildItem app\build\test-results\testDebugUnitTest -Filter 'TEST-*.xml' | ForEach-Object {
        ([xml](Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8)).testsuite
    }
    $taskTests = [int](($taskSuites | Measure-Object tests -Sum).Sum)
    $taskFailures = [int](($taskSuites | Measure-Object failures -Sum).Sum) + [int](($taskSuites | Measure-Object errors -Sum).Sum)
    if ($taskTests -lt 1 -or $taskFailures -gt 0) { throw 'Test reports do not establish a passing suite.' }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\kitt-v0-debug.apk -Force
    Copy-Item -LiteralPath app\build\acceptance\full-simulation.txt -Destination artifacts\full-simulation.txt -Force
    $taskHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-debug.apk -Algorithm SHA256).Hash
    "$taskHash  kitt-v0-debug.apk" | Set-Content -LiteralPath artifacts\SHA256.txt -Encoding utf8
    [ordered]@{
        build='PASS'; tests=$taskTests; failures=$taskFailures; lint='PASS'; signature='PASS'
        simulation='PASS with Fake Provider and fake Voice'; apk='kitt-v0-debug.apk'; sha256=$taskHash
        deviceAcceptance='Remaining: GPS, Chinese TTS/ASR, Android Keystore, lock-screen/OEM behavior'
        liveProvider='Remaining: authorize KITT ChatGPT sign-in and plan usage on device; API-key providers remain optional alternatives'
    } | ConvertTo-Json | Set-Content -LiteralPath artifacts\verification.json -Encoding utf8
    Write-Output "PASS: $taskTests tests; install artifacts\kitt-v0-debug.apk"
} finally { Pop-Location }
