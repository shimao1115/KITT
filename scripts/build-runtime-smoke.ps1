param([switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskCopy = Join-Path $taskRoot 'artifacts\runtime-smoke-workspace'
Push-Location $taskRoot
try {
    # Independent package only: do not uninstall a configured app when another machine's key differs.
    $taskFiles = & git -c core.quotepath=false ls-files --cached --others --exclude-standard
    if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate the working source.' }
    foreach ($taskFile in $taskFiles) {
        $taskSource = Join-Path $taskRoot $taskFile
        if (-not (Test-Path -LiteralPath $taskSource -PathType Leaf)) { continue }
        $taskTarget = [IO.Path]::GetFullPath((Join-Path $taskCopy $taskFile))
        if (-not $taskTarget.StartsWith($taskCopy + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Source path escapes smoke workspace.' }
        New-Item -ItemType Directory -Path (Split-Path -Parent $taskTarget) -Force | Out-Null
        Copy-Item -LiteralPath $taskSource -Destination $taskTarget -Force
    }
    $taskBuildFile = Join-Path $taskCopy 'app\build.gradle.kts'
    $taskGradle = Get-Content -LiteralPath $taskBuildFile -Raw
    $taskVersion = [regex]::Match($taskGradle, 'versionName = "([^"]+)"').Groups[1].Value
    if (-not $taskVersion) { throw 'Cannot read smoke version.' }
    $taskGradle.Replace('applicationId = "com.kitt.reader"', 'applicationId = "com.kitt.reader.runtimecheck"') |
        Set-Content -LiteralPath $taskBuildFile -Encoding utf8
    $taskManifest = Join-Path $taskCopy 'app\src\main\AndroidManifest.xml'
    (Get-Content -LiteralPath $taskManifest -Raw).Replace('android:label="沿途"', "android:label=`"沿途 $taskVersion 验收`"") |
        Set-Content -LiteralPath $taskManifest -Encoding utf8
    $taskArguments = @('-p', $taskCopy, 'assembleDebug', 'assembleDebugAndroidTest', '--console=plain')
    if ($Offline) { $taskArguments += '--offline' }
    & .\gradlew.bat @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Independent smoke build failed.' }
    $taskApk = Join-Path $taskRoot "artifacts\kitt-v$taskVersion-runtimecheck.apk"
    Copy-Item -LiteralPath (Join-Path $taskCopy 'app\build\outputs\apk\debug\app-debug.apk') -Destination $taskApk -Force
    Write-Output "Independent package: $taskApk; Fake by default, no access to the original app's account."
    Get-FileHash -LiteralPath $taskApk -Algorithm SHA256
} finally { Pop-Location }
