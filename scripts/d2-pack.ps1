# Thin wrapper; publish is the real production path and requires the one reviewed stdin decision.
param(
    [Parameter(Mandatory=$true)][ValidateSet('prepare','build','verify','release-preview','release-dry-run','audit-newdu','identity-init','production-prepare','publish')][string]$Command,
    [string]$Commit,
    [string]$InputDirectory = 'tools/d1/sample/fact-only',
    [string]$KeyDirectory,
    [string]$Output,
    [string]$Pack,
    [string]$Area,
    [string]$FactVersion,
    [string]$NarrativeVersion,
    [int]$Sequence = 1,
    [int]$MinimumSequence = 1,
    [int]$AppCode = 20,
    [string]$KeyId = 'test-only-d1-local',
    [string]$Confirmation,
    [string]$ReviewHash
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskArguments = @(':app:d1Pack', "-Pd1.command=$Command", "-Pd1.root=$taskRoot",
    "-Pd1.appcode=$AppCode", "-Pd1.sequence=$Sequence", "-Pd1.minsequence=$MinimumSequence")
if ($Command -in @('prepare', 'build', 'release-preview', 'release-dry-run', 'production-prepare', 'publish')) {
    if ($Commit -notmatch '^[0-9a-f]{40}$') { throw 'Pass a fixed 40-character commit, not HEAD or a branch name.' }
    $taskArguments += "-Pd1.commit=$Commit"
}
if ($Command -in @('prepare', 'build', 'release-dry-run', 'production-prepare', 'publish')) {
    if (-not $Output) { throw 'Pass an unused output directory.' }
    $taskArguments += @("-Pd1.input=$InputDirectory", "-Pd1.out=$([IO.Path]::GetFullPath($Output))", "-Pd1.keyid=$KeyId")
}
if ($Command -in @('identity-init', 'production-prepare', 'publish') -and $KeyDirectory) {
    $taskArguments += "-Pd1.keydir=$([IO.Path]::GetFullPath($KeyDirectory))"
}
if ($Command -eq 'publish') {
    if ($Confirmation) { throw 'Production consent must come from the single stdin decision, not a simulated flag.' }
    if ($ReviewHash -notmatch '^[0-9a-f]{64}$') { throw 'Pass the reviewed signed-region SHA256.' }
    $taskArguments += "-Pd1.reviewhash=$ReviewHash"
}
if ($Command -in @('build', 'verify', 'release-preview', 'release-dry-run')) {
    if (-not $KeyDirectory) { throw 'Pass an external TEST_ONLY key directory (public-only for verification).' }
    $taskArguments += "-Pd1.keydir=$([IO.Path]::GetFullPath($KeyDirectory))"
}
if ($Command -eq 'release-dry-run') {
    if ($Confirmation -ne 'SIMULATED_TEST_ONLY' -or $ReviewHash -notmatch '^[0-9a-f]{64}$') {
        throw 'This run permits only explicit SIMULATED_TEST_ONLY confirmation of the reviewed signed-region hash.'
    }
    $taskArguments += @("-Pd1.confirmation=$Confirmation", "-Pd1.reviewhash=$ReviewHash")
}
if ($Command -in @('verify', 'release-preview')) {
    if (-not $Pack) { throw 'Pass the signed candidate file.' }
    $taskArguments += "-Pd1.pack=$([IO.Path]::GetFullPath($Pack))"
}
if ($Command -eq 'release-preview') {
    if (-not $Area -or -not $FactVersion -or -not $NarrativeVersion -or -not $Output) {
        throw 'Pass the intended area, both versions and a new preview JSON filename.'
    }
    $taskArguments += @("-Pd1.area=$Area", "-Pd1.factversion=$FactVersion",
        "-Pd1.narrativeversion=$NarrativeVersion", "-Pd1.json=$([IO.Path]::GetFullPath($Output))")
}
Push-Location $taskRoot
try {
    # Gradle intentionally ignores CLI exit values; propagate the CLI's exit_code explicitly.
    $ErrorActionPreference = 'Continue'
    $taskFields = @{}
    & (Join-Path $taskRoot 'gradlew.bat') @taskArguments --offline --console=plain 2>&1 | ForEach-Object {
        $taskLine = "$_"
        if ($taskLine -match '^([a-z][a-z0-9_]*)=(.*)$') {
            $taskFields[$Matches[1]] = $Matches[2]
            Write-Output $taskLine
        } elseif ($Command -eq 'publish') { Write-Host $taskLine }
    }
    $taskGradleExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($taskGradleExit -ne 0 -or -not $taskFields.ContainsKey('exit_code')) {
        throw 'Local Gradle/CLI execution failed; no publication is permitted.'
    }
    if ($taskFields['exit_code'] -ne '0') { throw 'Candidate refused by the local CLI; no publication is permitted.' }
} finally { Pop-Location }
