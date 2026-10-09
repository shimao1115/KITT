param([switch]$Offline, [string]$SigningKeystore = '')
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
    $taskGradle = Get-Content -LiteralPath app\build.gradle.kts -Raw
    $taskVersion = [regex]::Match($taskGradle, 'versionName = "([^"]+)"').Groups[1].Value
    if (-not $taskVersion) { throw 'Cannot read APK version.' }
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
        $taskMinimum = if ($taskVariant -eq 'Debug') { 314 } else { 301 }
        if ($taskTests -lt $taskMinimum -or $taskFailures -gt 0 -or $taskSkipped -gt 0) { throw "$taskVariant reports do not establish the complete $taskMinimum+ passing suite." }
        $taskCounts[$taskVariant] = [ordered]@{ tests=$taskTests; failures=$taskFailures; skipped=$taskSkipped }
    }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\kitt-v0-debug.apk -Force
    $taskKeystore = if ($SigningKeystore) { [IO.Path]::GetFullPath($SigningKeystore) } else { Join-Path $env:USERPROFILE '.android\debug.keystore' }
    if (-not (Test-Path -LiteralPath $taskKeystore)) { throw 'Existing debug keystore is required for the acceptance Release APK.' }
    if ($SigningKeystore) {
        & $taskSigner sign --ks $taskKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false --out artifacts\kitt-v0-debug.apk app\build\outputs\apk\debug\app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Debug acceptance re-signing failed.' }
        & $taskSigner verify --verbose artifacts\kitt-v0-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Debug acceptance signature verification failed.' }
    }
    & $taskSigner sign --ks $taskKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false --out artifacts\kitt-v0-release.apk app\build\outputs\apk\release\app-release-unsigned.apk
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance Release APK signing failed.' }
    & $taskSigner verify --verbose artifacts\kitt-v0-release.apk
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance Release APK signature verification failed.' }
    Copy-Item -LiteralPath artifacts\kitt-v0-debug.apk -Destination "artifacts\kitt-v$taskVersion-debug.apk" -Force
    Copy-Item -LiteralPath artifacts\kitt-v0-release.apk -Destination "artifacts\kitt-v$taskVersion-release.apk" -Force
    foreach ($taskReport in @('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt', 'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt')) {
        Copy-Item -LiteralPath (Join-Path app\build\acceptance $taskReport) -Destination (Join-Path artifacts $taskReport) -Force
    }
    $taskHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-debug.apk -Algorithm SHA256).Hash
    $taskReleaseHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-release.apk -Algorithm SHA256).Hash
    @("$taskHash  kitt-v$taskVersion-debug.apk", "$taskReleaseHash  kitt-v$taskVersion-release.apk") | Set-Content -LiteralPath artifacts\SHA256.txt -Encoding utf8
    [ordered]@{
        version=$taskVersion; product='沿途'; build='PASS (Debug/Release)'; variants=$taskCounts; lint='PASS (Debug/Release)'; signature='PASS; Release uses existing debug acceptance key'
        simulation='PASS with deterministic/Fake Provider and fake Voice; production Context/Journey/Director'
        route='29 coarse points: Chengdu Xindu to Mianyang Anzhou Jushui; 100km/h / 16x'
        apk="kitt-v$taskVersion-debug.apk"; sha256=$taskHash; releaseApk="kitt-v$taskVersion-release.apk"; releaseSha256=$taskReleaseHash
        deviceAcceptance='V0.3.4 phone smoke results are recorded separately in docs/ACCEPTANCE_RUNTIME_STATUS.md; this script does not run phone or field tests. Issue #14 still requires field acceptance.'
        runtimeStatus='PASS: truthful request lifecycle/elapsed/priority, location and network/VPN signals, unverified/Fake isolation, cancellation/timeout/configuration/re-entry, queued TTS, phone portrait/landscape + tablet layout. No active ping, diagnostic panel or timeline.'
        locationFallback='PASS: selector + Robolectric GPS/network listener regression, monotonic age, bridge/unknown, jump filtering, coarse landmark/stale guards, coordinate-only enrichment. Wi-Fi/cellular/VPN field behavior requires vivo acceptance.'
        liveProvider='Credential-backed Overview/Topic/Director/TTS results are recorded separately in docs/ACCEPTANCE_STAGED_RESEARCH.md; this script does not run live-provider acceptance.'
        chineseASR='System recogniser still fails ERROR_CLIENT before ready; the bundled offline Vosk Chinese model is the working path on this device.'
        localResearch='Overview -> Topic regression PASS; this script does not run credential-backed search. Current phone evidence is in docs/ACCEPTANCE_STAGED_RESEARCH.md.'
        silenceHotfix='PASS: pending research does not globally gate Director/simulation; failed/ready opportunities retained; user priority/stale protections; transient ChatGPT backoff does not slide.'
        xinduBenchmark='NOT RUN BY THIS SCRIPT: fresh production-provider phone discovery is recorded separately in docs/ACCEPTANCE_STAGED_RESEARCH.md.'
    } | ConvertTo-Json | Set-Content -LiteralPath artifacts\verification.json -Encoding utf8
    Write-Output "PASS: Debug $($taskCounts.Debug.tests) + Release $($taskCounts.Release.tests) tests; install artifacts\kitt-v0-debug.apk"
} finally { Pop-Location }
