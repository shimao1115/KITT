param([switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        throw 'Set JAVA_HOME to JDK 17 before building.'
    }
    $taskArguments = @('assembleDebug', 'assembleRelease', 'testDebugUnitTest', 'testReleaseUnitTest', 'lintDebug', 'lintRelease', '--console=plain')
    if ($Offline) { $taskArguments += '--offline' }
    & .\gradlew.bat @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Build/test/lint failed; no acceptance package was produced.' }
    $taskSdk = $env:ANDROID_HOME
    if (-not $taskSdk) { $taskSdk = $env:ANDROID_SDK_ROOT }
    $taskSigner = Join-Path $taskSdk 'build-tools\35.0.0\apksigner.bat'
    & $taskSigner verify --verbose app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $taskCounts = [ordered]@{}
    foreach ($taskVariant in @('Debug', 'Release')) {
        $taskReports = "app\build\test-results\test${taskVariant}UnitTest"
        $taskSuites = Get-ChildItem -LiteralPath $taskReports -Filter 'TEST-*.xml' | ForEach-Object {
            ([xml](Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8)).testsuite
        }
        $taskTests = [int](($taskSuites | Measure-Object tests -Sum).Sum)
        $taskFailures = [int](($taskSuites | Measure-Object failures -Sum).Sum) + [int](($taskSuites | Measure-Object errors -Sum).Sum)
        $taskSkipped = [int](($taskSuites | Measure-Object skipped -Sum).Sum)
        if ($taskTests -lt 1 -or $taskFailures -gt 0 -or $taskSkipped -gt 0) { throw "$taskVariant reports do not establish a complete passing suite." }
        $taskCounts[$taskVariant] = [ordered]@{ tests=$taskTests; failures=$taskFailures; skipped=$taskSkipped }
    }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\kitt-v0-debug.apk -Force
    foreach ($taskReport in @('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt', 'landmark-simulation.txt')) {
        Copy-Item -LiteralPath (Join-Path app\build\acceptance $taskReport) -Destination (Join-Path artifacts $taskReport) -Force
    }
    $taskHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-debug.apk -Algorithm SHA256).Hash
    "$taskHash  kitt-v0-debug.apk" | Set-Content -LiteralPath artifacts\SHA256.txt -Encoding utf8
    [ordered]@{
        version='0.2.0'; build='PASS (Debug/Release)'; variants=$taskCounts; lint='PASS (Debug/Release)'; signature='PASS'
        simulation='PASS with deterministic/Fake Provider and fake Voice; production Context/Journey/Director'
        route='29 coarse points: Chengdu Xindu to Mianyang Anzhou Jushui; 100km/h / 16x'
        apk='kitt-v0-debug.apk'; sha256=$taskHash
        deviceAcceptance='DEFERRED TO COMBINED PHONE ACCEPTANCE: docs/COMBINED_PHONE_ACCEPTANCE.md. This batch accessed no phone.'
        liveProvider='Real provider narrative/image quality DEFERRED; historical M1.1-M1.4 phone evidence preserved in HANDOFF.md.'
        chineseASR='Explicitly out of scope for this batch and combined acceptance.'
    } | ConvertTo-Json | Set-Content -LiteralPath artifacts\verification.json -Encoding utf8
    Write-Output "PASS: Debug $($taskCounts.Debug.tests) + Release $($taskCounts.Release.tests) tests; install artifacts\kitt-v0-debug.apk"
} finally { Pop-Location }
