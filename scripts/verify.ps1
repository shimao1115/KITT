param([switch]$Offline, [string]$SigningKeystore = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $taskKeystore = Join-Path $env:USERPROFILE '.kitt\signing\acceptance.keystore'
    if ($SigningKeystore -and ([IO.Path]::GetFullPath($SigningKeystore) -ne [IO.Path]::GetFullPath($taskKeystore))) {
        throw 'Pass only the project canonical keystore; do not sign acceptance builds with another certificate.'
    }
    & (Join-Path $PSScriptRoot 'check-kitt-signing.ps1')
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
        $taskMinimum = if ($taskVariant -eq 'Debug') { 395 } else { 382 }
        if ($taskTests -lt $taskMinimum -or $taskFailures -gt 0 -or $taskSkipped -gt 0) { throw "$taskVariant reports do not establish the complete $taskMinimum+ passing suite." }
        $taskCounts[$taskVariant] = [ordered]@{ tests=$taskTests; failures=$taskFailures; skipped=$taskSkipped }
    }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\kitt-v0-debug.apk -Force
    # Debug is signed by the canonical project signingConfig inside Gradle.
    # Never re-sign a random local-debug APK and present it as a phone-upgrade candidate.
    & (Join-Path $PSScriptRoot 'check-kitt-signing.ps1') -ApkPath 'artifacts\kitt-v0-debug.apk' -ExpectedApplicationId 'com.kitt.reader'
    & $taskSigner sign --ks $taskKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false --out artifacts\kitt-v0-release.apk app\build\outputs\apk\release\app-release-unsigned.apk
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance Release APK signing failed.' }
    & $taskSigner verify --verbose artifacts\kitt-v0-release.apk
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance Release APK signature verification failed.' }
    & (Join-Path $PSScriptRoot 'check-kitt-signing.ps1') -ApkPath 'artifacts\kitt-v0-release.apk' -ExpectedApplicationId 'com.kitt.reader'
    Copy-Item -LiteralPath artifacts\kitt-v0-debug.apk -Destination "artifacts\kitt-v$taskVersion-debug.apk" -Force
    Copy-Item -LiteralPath artifacts\kitt-v0-release.apk -Destination "artifacts\kitt-v$taskVersion-release.apk" -Force
    foreach ($taskReport in @('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt', 'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt', 'area-stability.txt', 'research-cache.txt', 'narration-memory.txt')) {
        Copy-Item -LiteralPath (Join-Path app\build\acceptance $taskReport) -Destination (Join-Path artifacts $taskReport) -Force
    }
    $taskHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-debug.apk -Algorithm SHA256).Hash
    $taskReleaseHash = (Get-FileHash -LiteralPath artifacts\kitt-v0-release.apk -Algorithm SHA256).Hash
    @("$taskHash  kitt-v$taskVersion-debug.apk", "$taskReleaseHash  kitt-v$taskVersion-release.apk") | Set-Content -LiteralPath artifacts\SHA256.txt -Encoding utf8
    [ordered]@{
        version=$taskVersion; product='沿途'; build='PASS (Debug/Release)'; variants=$taskCounts; lint='PASS (Debug/Release)'; signature='PASS; Debug and Release use canonical KITT signing certificate'
        simulation='PASS with deterministic/Fake Provider and fake Voice; production Context/Journey/Director'
        route='29 coarse points: Chengdu Xindu to Mianyang Anzhou Jushui; 100km/h / 16x'
        apk="kitt-v$taskVersion-debug.apk"; sha256=$taskHash; releaseApk="kitt-v$taskVersion-release.apk"; releaseSha256=$taskReleaseHash
        deviceAcceptance='NOT RUN BY THIS SCRIPT: no phone installation, downgrade, data clear or credential access. Historical V0.3.5.1 phone results are recorded in HANDOFF.md; Issues #14/#15 remain separate.'
        areaStability='PASS: 600s fresh-position publication, 120s empty/error/8s timeout refresh, reliable replacement, 450m/100kmh movement, stale/gaps/provider switch/coarse/LAST_KNOWN/restart/late result and unavailable Geocoder regression. Synthetic measurements, not real-device service evidence.'
        runtimeStatus='PASS: truthful request lifecycle/elapsed/priority, location and network/VPN signals, unverified/Fake isolation, cancellation/timeout/configuration/re-entry, queued TTS, phone portrait/landscape + tablet layout. No active ping, diagnostic panel or timeline.'
        locationFallback='PASS: selector + Robolectric GPS/network listener regression, monotonic age, bridge/unknown, jump filtering, coarse landmark/stale guards, coordinate-only enrichment. Wi-Fi/cellular/VPN field behavior requires vivo acceptance.'
        liveProvider='Credential-backed Overview/Topic/Director/TTS results are recorded separately in docs/ACCEPTANCE_STAGED_RESEARCH.md; this script does not run live-provider acceptance.'
        chineseASR='System recogniser still fails ERROR_CLIENT before ready; the bundled offline Vosk Chinese model is the working path on this device.'
        localResearch='Overview -> Topic + persistent sourced evidence regression PASS. Synthetic district/town/Topic: first journey fetches 1/1/1; reconstructed manager/store with unavailable research fetches 0/0/0, Director judges again. NOT real Provider/device process-restart acceptance; see docs/ACCEPTANCE_RESEARCH_CACHE.md.'
        narrationMemory='PASS: session-only object/fact coverage and queued/start/completed/failure/cancel/interruption callbacks; automatic repeated coverage suppressed, new facts/objects/chapters/landmarks eligible; more/replay/ordinary question/image and legacy recovery. Synthetic labels and fake Director cannot prove real semantic quality or human hearing.'
        silenceHotfix='PASS: pending research does not globally gate Director/simulation; failed/ready opportunities retained; user priority/stale protections; transient ChatGPT backoff does not slide.'
        xinduBenchmark='NOT RUN BY THIS SCRIPT: fresh production-provider phone discovery is recorded separately in docs/ACCEPTANCE_STAGED_RESEARCH.md.'
    } | ConvertTo-Json | Set-Content -LiteralPath artifacts\verification.json -Encoding utf8
    Write-Output "PASS: Debug $($taskCounts.Debug.tests) + Release $($taskCounts.Release.tests) tests; standard com.kitt.reader artifacts verified. The installed phone app requires the separate runtimecheck package."
} finally { Pop-Location }
