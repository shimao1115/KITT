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
    foreach ($taskReport in @('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt', 'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt')) {
        Copy-Item -LiteralPath (Join-Path app\build\acceptance $taskReport) -Destination (Join-Path artifacts $taskReport) -Force
    }
    $taskHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-debug.apk -Algorithm SHA256).Hash
    "$taskHash  kitt-v0-debug.apk" | Set-Content -LiteralPath artifacts\SHA256.txt -Encoding utf8
    [ordered]@{
        version='0.3.1'; product='沿途'; build='PASS (Debug/Release)'; variants=$taskCounts; lint='PASS (Debug/Release)'; signature='PASS'
        simulation='PASS with deterministic/Fake Provider and fake Voice; production Context/Journey/Director'
        route='29 coarse points: Chengdu Xindu to Mianyang Anzhou Jushui; 100km/h / 16x'
        apk='kitt-v0-debug.apk'; sha256=$taskHash
        deviceAcceptance='Chinese voice input accepted on the vivo V2405A; see docs/ACCEPTANCE_CHINESE_ASR.md. Remaining subjective-content gates stay in docs/COMBINED_PHONE_ACCEPTANCE.md.'
        liveProvider='Credential-backed phone narration/search results are recorded separately in docs/ACCEPTANCE_V03_SILENCE_HOTFIX.md; this script does not establish live-provider success.'
        chineseASR='System recogniser still fails ERROR_CLIENT before ready; the bundled offline Vosk Chinese model is the working path on this device.'
        localResearch='Deterministic regression PASS; this verification script does not run credential-backed hosted search. Current probe status is recorded in docs/ACCEPTANCE_V03_SILENCE_HOTFIX.md.'
        silenceHotfix='PASS: pending research does not globally gate Director/simulation; failed/ready opportunities retained; user priority/stale protections; transient ChatGPT backoff does not slide.'
        xinduBenchmark='NOT VERIFIED BY THIS SCRIPT: fresh production-provider research is required; manual web discovery is separate evidence and does not establish runtime acceptance.'
    } | ConvertTo-Json | Set-Content -LiteralPath artifacts\verification.json -Encoding utf8
    Write-Output "PASS: Debug $($taskCounts.Debug.tests) + Release $($taskCounts.Release.tests) tests; install artifacts\kitt-v0-debug.apk"
} finally { Pop-Location }
