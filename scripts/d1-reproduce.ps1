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
$taskKeyDir = if ($env:KITT_D1_KEYDIR) { $env:KITT_D1_KEYDIR } else { Join-Path $env:TEMP 'kitt-d1-keys' }
$taskOutFile = if ($env:KITT_D1_OUT) { $env:KITT_D1_OUT } else { 'artifacts/d1/reproducibility.json' }
# Only set by the guard test, which proves a pre-existing directory is never reused or deleted.
$taskToken = if ($env:KITT_D1_REPRO_TOKEN) { $env:KITT_D1_REPRO_TOKEN } else { [guid]::NewGuid().ToString('N').Substring(0, 12) }
$taskRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
Push-Location $taskRoot
$taskCreated = @()
try {
    if (-not (Test-Path (Join-Path $taskKeyDir 'key.json'))) {
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
        & git -C $taskRoot worktree add --detach $taskDirectory $taskCommit | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot create a clean work tree at $taskDirectory" }
        $taskCreated += $taskDirectory
        $taskOut = Join-Path $taskDirectory 'artifacts/d1/build'
        $taskOutput = & (Join-Path $taskDirectory 'gradlew.bat') ':app:d1Pack' '-Pd1.command=build' `
            "-Pd1.root=$taskDirectory" '-Pd1.sample=fact-only' "-Pd1.keydir=$taskKeyDir" "-Pd1.out=$taskOut" `
            "-Pd1.commit=$taskCommit" '--offline' '--console=plain' 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Clean build $taskIndex failed: $($taskOutput | Select-Object -Last 20)" }
        $taskFields = @{}
        foreach ($taskLine in $taskOutput) {
            if ($taskLine -match '^([a-z0-9_]+)=(.*)$') { $taskFields[$Matches[1]] = $Matches[2] }
        }
        if ($taskFields['status'] -ne 'BUILT_TEST_ONLY') { throw "Clean build $taskIndex did not report BUILT_TEST_ONLY" }
        $taskResults += [ordered]@{
            tree = "<REPRO_WORKTREE_$taskIndex>"
            commit = $taskCommit
            status = $taskFields['status']
            sequence = [int]$taskFields['sequence']
            unsigned_sha256 = $taskFields['unsigned_sha256']
            container_sha256 = $taskFields['container_sha256']
            container_bytes = [int]$taskFields['container_bytes']
            pack_sha256 = (Get-FileHash -LiteralPath (Join-Path $taskOut 'pack.kcpk') -Algorithm SHA256).Hash.ToLower()
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
        container_bytes_identical = $true
        note = 'RSA PKCS#1 v1.5 is deterministic, so with the same TEST_ONLY key the whole container repeats.'
    }
    $taskTarget = Join-Path $taskRoot $taskOutFile
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskTarget) -Force | Out-Null
    $taskReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $taskTarget -Encoding utf8
    $taskReport | ConvertTo-Json -Depth 6
} finally {
    $taskRegistered = @()
    $taskList = & git -C $taskRoot worktree list --porcelain 2>$null
    if ($LASTEXITCODE -eq 0) {
        $taskRegistered = @($taskList | Where-Object { $_ -like 'worktree *' } | ForEach-Object { $_.Substring(9).Trim() })
    }
    foreach ($taskDirectory in $taskCreated) {
        $taskFull = [IO.Path]::GetFullPath($taskDirectory)
        $taskIsOurs = @($taskRegistered | Where-Object { [IO.Path]::GetFullPath($_) -eq $taskFull }).Count -eq 1
        if (-not $taskIsOurs) {
            Write-Warning "Left an unregistered directory in place: $taskDirectory"
            continue
        }
        & git -C $taskRoot worktree remove --force $taskFull 2>$null | Out-Null
    }
    Pop-Location
}
