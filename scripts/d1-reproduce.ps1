# Reproducibility proof for D1: build the same TEST_ONLY sample in two brand-new work trees and compare.
#
# Safety rules:
#   * every run uses a fresh, unpredictable directory name; an existing path is a STOP, never deleted
#   * only work trees created by this run are removed, and only after git confirms they are ours
#   * the key directory is never written to, only read
#
# Run from the repository root. This machine's policy blocks executing .ps1 files directly, so the
# recorded evidence opened the current process first:
#   Set-ExecutionPolicy -Scope Process Bypass -Force
#   & .\scripts\d1-reproduce.ps1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRef = if ($env:KITT_D1_REF) { $env:KITT_D1_REF } else { 'HEAD' }
$taskCommand = if ($env:KITT_D1_COMMAND) { $env:KITT_D1_COMMAND } else { 'build' }
if ($taskCommand -notin @('build', 'prepare')) { throw 'Only build or prepare can be reproduced.' }
$taskKeyDir = if ($env:KITT_D1_KEYDIR) { $env:KITT_D1_KEYDIR } else { Join-Path $env:TEMP 'kitt-d1-keys' }
$taskOutFile = if ($env:KITT_D1_OUT) { $env:KITT_D1_OUT } else { 'artifacts/d1/reproducibility.json' }
# Only set by the guard test, which proves a pre-existing directory is never reused or deleted.
$taskToken = if ($env:KITT_D1_REPRO_TOKEN) { $env:KITT_D1_REPRO_TOKEN } else { [guid]::NewGuid().ToString('N').Substring(0, 12) }
if ($taskToken -notmatch '^[a-zA-Z0-9_-]{1,64}$') { throw 'Invalid reproduction token.' }
$taskRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$taskTempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
Push-Location $taskRoot
$taskCreated = @()
try {
    if ($taskCommand -eq 'build' -and -not (Test-Path (Join-Path $taskKeyDir 'key.json'))) {
        throw "Missing TEST_ONLY key material in $taskKeyDir. Run the d1Pack keygen command first."
    }
    $taskCommit = (& git -C $taskRoot rev-parse $taskRef).Trim()
    if ($LASTEXITCODE -ne 0 -or $taskCommit -notmatch '^[0-9a-f]{40}$') { throw 'Cannot resolve the commit to reproduce.' }
    $taskResults = @()
    foreach ($taskIndex in 1, 2) {
        $taskDirectory = Join-Path $env:TEMP ("kitt-d1-repro-{0}-{1}" -f $taskToken, $taskIndex)
        if (Test-Path -LiteralPath $taskDirectory) {
            throw "Refusing to reuse or delete an existing directory: $taskDirectory"
        }
        # git writes progress to stderr; capture it instead of letting the strict preference throw.
        $taskPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & git -C $taskRoot worktree add --detach $taskDirectory $taskCommit 2>&1 | Out-Null
        $taskAddExit = $LASTEXITCODE
        $ErrorActionPreference = $taskPreference
        if ($taskAddExit -ne 0) { throw "Cannot create a clean work tree at $taskDirectory" }
        $taskCreated += $taskDirectory
        $taskOut = Join-Path $taskDirectory 'artifacts/d1/build'
        $taskOutput = & (Join-Path $taskDirectory 'gradlew.bat') ':app:d1Pack' "-Pd1.command=$taskCommand" `
            "-Pd1.root=$taskDirectory" '-Pd1.sample=fact-only' "-Pd1.keydir=$taskKeyDir" "-Pd1.out=$taskOut" `
            "-Pd1.commit=$taskCommit" '--project-dir' $taskDirectory '--offline' '--console=plain' 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Clean build $taskIndex failed: $($taskOutput | Select-Object -Last 20)" }
        $taskFields = @{}
        foreach ($taskLine in $taskOutput) {
            if ($taskLine -match '^([a-z0-9_]+)=(.*)$') { $taskFields[$Matches[1]] = $Matches[2] }
        }
        if ($taskFields['status'] -ne $(if ($taskCommand -eq 'build') { 'BUILT_TEST_ONLY' } else { 'PREPARED_TEST_ONLY' })) {
            throw 'Clean build did not report the expected TEST_ONLY status.'
        }
        if ($taskCommand -eq 'prepare') {
            $taskRaw = @('candidate.unsigned', 'fact-manifest.json', 'facts.json', 'narrative-manifest.json', 'narratives.json') |
                ForEach-Object { [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $taskOut $_))) }
            if ($taskIndex -eq 1) { $taskFirstRaw = $taskRaw } else {
                if (($taskFirstRaw -join ':') -ne ($taskRaw -join ':')) { throw 'Unsigned bytes differ.' }
            }
            $taskFields['sequence'] = '1'
            $taskFields['container_sha256'] = $taskFields['signed_region_sha256']
            $taskFields['container_bytes'] = (Get-Item -LiteralPath (Join-Path $taskOut 'candidate.unsigned')).Length
        }
        $taskCandidate = if ($taskCommand -eq 'build') { 'pack.kcpk' } else { 'candidate.unsigned' }
        $taskResults += [ordered]@{
            tree = "<REPRO_WORKTREE_$taskIndex>"
            commit = $taskCommit
            status = $taskFields['status']
            sequence = [int]$taskFields['sequence']
            unsigned_sha256 = $taskFields['unsigned_sha256']
            container_sha256 = $taskFields['container_sha256']
            container_bytes = [int]$taskFields['container_bytes']
            pack_sha256 = (Get-FileHash -LiteralPath (Join-Path $taskOut $taskCandidate) -Algorithm SHA256).Hash.ToLower()
        }
    }
    $taskIdentical = ($taskResults[0].unsigned_sha256 -eq $taskResults[1].unsigned_sha256) -and
        ($taskResults[0].container_sha256 -eq $taskResults[1].container_sha256) -and
        ($taskResults[0].pack_sha256 -eq $taskResults[1].pack_sha256) -and
        ($taskResults[0].container_bytes -eq $taskResults[1].container_bytes)
    if (-not $taskIdentical) { throw 'The two clean directories produced different bytes.' }
    $taskReport = [ordered]@{
        generated_at = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        commit = $taskCommit
        sample = 'fact-only'
        key_id = 'test-only-d1-local'
        token = $taskToken
        clean_directories = $taskResults
        unsigned_bytes_identical = $true
        container_bytes_identical = ($taskCommand -eq 'build')
        command = $taskCommand
        signed_region_bytes_identical = ($taskCommand -eq 'prepare')
        builder_project_is_worktree = $true
        physical_two_computers = 'PENDING'
        note = 'Two clean worktrees on one Windows computer. Prepare compares all four raw blocks and the KCPK signed region byte for byte, without keys. Build compares signed container hashes with the same TEST_ONLY key.'
    }
    $taskTarget = Join-Path $taskRoot $taskOutFile
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskTarget) -Force | Out-Null
    $taskReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $taskTarget -Encoding utf8
    $taskReport | ConvertTo-Json -Depth 6
} finally {
    $taskRegistered = @()
    $taskPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $taskList = & git -C $taskRoot worktree list --porcelain 2>$null
    $taskListExit = $LASTEXITCODE
    $ErrorActionPreference = $taskPreference
    if ($taskListExit -eq 0 -and $taskList) {
        $taskRegistered = @($taskList | Where-Object { $_ -like 'worktree *' } | ForEach-Object { $_.Substring(9).Trim() })
    }
    foreach ($taskDirectory in $taskCreated) {
        $taskFull = [IO.Path]::GetFullPath($taskDirectory)
        if ([IO.Path]::GetDirectoryName($taskFull) -ne $taskTempRoot) {
            Write-Warning 'Left a worktree outside this run temporary root in place.'
            continue
        }
        $taskIsOurs = @($taskRegistered | Where-Object { [IO.Path]::GetFullPath($_) -eq $taskFull }).Count -eq 1
        if (-not $taskIsOurs) {
            Write-Warning "Left an unregistered directory in place: $taskDirectory"
            continue
        }
        $taskPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & git -C $taskRoot worktree remove --force $taskFull 2>&1 | Out-Null
        $ErrorActionPreference = $taskPreference
    }
    Pop-Location
}
