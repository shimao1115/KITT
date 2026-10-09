param([ValidatePattern('^[A-Za-z0-9_-]{1,80}$')][string]$Condition = 'current-condition-unconfirmed', [ValidateRange(30,180)][int]$Seconds = 65)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    # The test APK must already be installed, signed like runtimecheck. No settings/radios/data changes.
    $taskRunner = 'com.kitt.reader.runtimecheck.test/com.kitt.reader.HotfixPhoneProbe'
    $taskInstalled = & adb shell pm list instrumentation
    if ($LASTEXITCODE -ne 0 -or -not ($taskInstalled -match [regex]::Escape($taskRunner))) {
        throw 'Build with scripts/build-runtime-smoke.ps1 -Offline, then adb install -r its app-debug-androidTest.apk. Complete any vivo fingerprint prompt.'
    }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    & adb shell am instrument -w -e mode fused-location -e condition $Condition -e seconds $Seconds $taskRunner |
        Tee-Object -FilePath artifacts\fused-probe.log
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation did not complete.' }
    if ((Get-Content -LiteralPath artifacts\fused-probe.log -Raw) -notmatch 'INSTRUMENTATION_RESULT: outcome=MEASURED') {
        throw 'This run did not produce a completed measurement. Do not reuse a cached result.'
    }
    $taskReport = & adb shell run-as com.kitt.reader.runtimecheck cat cache/fused-location-phone.json
    if ($LASTEXITCODE -ne 0) { throw 'No completed native report was produced.' }
    $taskJson = $taskReport -join "`n"
    $taskValue = $taskJson | ConvertFrom-Json
    if ($taskValue.condition -ne $Condition -or $taskValue.phase_seconds -ne $Seconds) {
        throw 'The cached report belongs to another probe. Inspect fused-probe.log; do not treat a previous run as new evidence.'
    }
    $taskJson | Set-Content -LiteralPath artifacts\fused-location-phone.json -Encoding utf8
    if ($taskValue.outcome -ne 'MEASURED') { throw "Probe outcome: $($taskValue.outcome)" }
    Write-Output 'Measured only. Review foreground coverage, raw provider labels, mirrored measurements and extra fresh seconds before making an enablement decision.'
} finally { Pop-Location }
