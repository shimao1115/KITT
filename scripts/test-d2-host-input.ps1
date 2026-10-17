# Process-level stdin contract test. Fake Gradle only: no identities, signing or GitHub processes.
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskOutput = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $taskOutput) { throw 'Use a new isolated test directory.' }
New-Item -ItemType Directory -Path (Join-Path $taskOutput 'scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'd2-pack.ps1') -Destination (Join-Path $taskOutput 'scripts/d2-pack.ps1')
@'
@echo off
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0fake-gradle.ps1"
exit /b %errorlevel%
'@ | Set-Content -LiteralPath (Join-Path $taskOutput 'gradlew.bat') -Encoding ascii
@'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
$taskLine = [Console]::In.ReadLine()
$taskLine | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'received.txt') -Encoding utf8
Write-Output 'status=FAKE_STDIN_CAPTURED'
Write-Output 'exit_code=0'
'@ | Set-Content -LiteralPath (Join-Path $taskOutput 'fake-gradle.ps1') -Encoding utf8
$taskWrapper = Join-Path $taskOutput 'scripts/d2-pack.ps1'
$taskHash = 'b' * 64
$taskLine = "可以发布 $taskHash"
$taskArguments = @{Command='publish';Commit=('a' * 40);ReviewHash=$taskHash;Output=(Join-Path $taskOutput 'unused')}
$taskEncodingBefore = $global:OutputEncoding.CodePage
$taskLine | & $taskWrapper @taskArguments | Out-Null
if ((Get-Content -LiteralPath (Join-Path $taskOutput 'received.txt') -Raw).Trim() -cne $taskLine) { throw 'UTF8 stdin changed.' }
$taskReceipt = (Get-FileHash -LiteralPath (Join-Path $taskOutput 'received.txt')).Hash
foreach ($taskInvalid in @(@('SIMULATED_TEST_ONLY'), @("可以发布 $('c' * 64)"), @($taskLine, $taskLine))) {
    $taskRejected = $false
    try { $taskInvalid | & $taskWrapper @taskArguments | Out-Null } catch {
        if ($_.Exception.Message -notlike '*one actual user decision*') { throw }
        $taskRejected = $true
    }
    if (-not $taskRejected) { throw 'Invalid piped decision was accepted.' }
    if ((Get-FileHash -LiteralPath (Join-Path $taskOutput 'received.txt')).Hash -ne $taskReceipt) { throw 'Refusal reached fake process.' }
}
# Gradle exit 0 is insufficient: the wrapper must also reject a CLI refusal.
$taskFake = Join-Path $taskOutput 'fake-gradle.ps1'
(Get-Content -LiteralPath $taskFake -Raw).Replace("exit_code=0", "exit_code=3") | Set-Content -LiteralPath $taskFake -Encoding utf8
$taskCliRejected = $false
try { $taskLine | & $taskWrapper @taskArguments | Out-Null } catch {
    if ($_.Exception.Message -notlike '*Candidate refused*') { throw }
    $taskCliRejected = $true
}
if (-not $taskCliRejected) { throw 'CLI refusal was swallowed.' }
if ($global:OutputEncoding.CodePage -ne $taskEncodingBefore) { throw 'Host encoding was not restored.' }
[ordered]@{utf8_single_decision_forwarded=$true;simulated_flag_wrong_hash_and_multiple_lines_refused=$true;cli_refusal_propagated=$true;host_encoding_restored=$true;fake_process_only=$true;real_user_approval_recorded=$false;real_upload_performed=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'result.json') -Encoding utf8
Write-Output 'PASS: one UTF8 stdin decision; invalid/multiple decisions and CLI refusal rejected; fake process only.'
