# Reproducibility proof for D1: build the same TEST_ONLY sample in two clean work trees and compare.
# Run from the repository root (Windows blocks executing .ps1 files under the default policy, so the
# documented invocation pipes the file into Invoke-Expression, which is what the evidence used):
#   Invoke-Expression (Get-Content -Raw scripts/d1-reproduce.ps1)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRef = if ($env:KITT_D1_REF) { $env:KITT_D1_REF } else { 'HEAD' }
$taskKeyDir = if ($env:KITT_D1_KEYDIR) { $env:KITT_D1_KEYDIR } else { Join-Path $env:TEMP 'kitt-d1-keys' }
$taskOutFile = if ($env:KITT_D1_OUT) { $env:KITT_D1_OUT } else { 'artifacts/d1/reproducibility.json' }
$taskRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
Push-Location $taskRoot
$taskWorktrees = @()
try {
    if (-not (Test-Path (Join-Path $taskKeyDir 'key.json'))) {
        throw "Missing TEST_ONLY key material in $taskKeyDir. Run the d1Pack keygen command first."
    }
    $taskCommit = (& git -C $taskRoot rev-parse $taskRef).Trim()
    if ($LASTEXITCODE -ne 0 -or $taskCommit -notmatch '^[0-9a-f]{40}$') { throw 'Cannot resolve the commit to reproduce.' }
    $taskResults = @()
    foreach ($taskIndex in 1, 2) {
        $taskDirectory = Join-Path $env:TEMP ("kitt-d1-repro-{0}-{1}" -f $taskCommit.Substring(0, 8), $taskIndex)
        if (Test-Path $taskDirectory) { Remove-Item -LiteralPath $taskDirectory -Recurse -Force }
        & git -C $taskRoot worktree add --detach $taskDirectory $taskCommit | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot create a clean work tree at $taskDirectory" }
        $taskWorktrees += $taskDirectory
        $taskOut = Join-Path $taskDirectory 'artifacts/d1/build'
        $taskOutput = & (Join-Path $taskDirectory 'gradlew.bat') ':app:d1Pack' '-Pd1.command=build' `
            "-Pd1.root=$taskDirectory" '-Pd1.sample=fact-only' "-Pd1.keydir=$taskKeyDir" "-Pd1.out=$taskOut" `
            "-Pd1.commit=$taskCommit" '--offline' '--console=plain' 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Clean build $taskIndex failed: $($taskOutput | Select-Object -Last 20)" }
        $taskFields = @{}
        foreach ($taskLine in $taskOutput) {
            if ($taskLine -match '^([a-z_]+)=(.*)$') { $taskFields[$Matches[1]] = $Matches[2] }
        }
        if ($taskFields['status'] -ne 'BUILT_TEST_ONLY') { throw "Clean build $taskIndex did not report BUILT_TEST_ONLY" }
        $taskResults += [ordered]@{
            tree = $taskDirectory
            commit = $taskCommit
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
    foreach ($taskDirectory in $taskWorktrees) {
        & git -C $taskRoot worktree remove --force $taskDirectory 2>$null | Out-Null
    }
    Pop-Location
}
