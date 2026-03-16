# Mock TE.exe — simulates TAEF test execution output for testing.
# Usage: pwsh mock_te.ps1 <dll> [/name:<filter>] [/select:"<query>"] [/inproc] [/list]
#
# Behavior:
#   - /list mode: prints test listing
#   - Normal mode: prints StartGroup/EndGroup output with pass/fail results
#   - Exit code 0 if all pass, 1 if any fail

param(
    [Parameter(Position = 0)]
    [string]$Dll,

    [Parameter(ValueFromRemainingArguments)]
    [string[]]$RemainingArgs
)

if (-not $Dll) {
    Write-Host "Error: No test DLL specified."
    Write-Host "Usage: mock_te.ps1 <test_dll> [/name:<filter>] [/list] [/inproc]"
    exit 1
}

# Parse flags
$listMode = $false
$nameFilter = ""
$inproc = $false

foreach ($arg in $RemainingArgs) {
    if ($arg -eq "/list") { $listMode = $true }
    if ($arg -match "^/name:(.+)$") { $nameFilter = $Matches[1] }
    if ($arg -eq "/inproc") { $inproc = $true }
}

Write-Host ""
Write-Host "Test Authoring and Execution Framework v10.89 for x64 (mock)"
Write-Host ""

# === List mode ===
if ($listMode) {
    Write-Host "SampleNamespace::SampleTestClass"
    Write-Host "    SampleNamespace::SampleTestClass::TestMethodPass"
    Write-Host "    SampleNamespace::SampleTestClass::TestMethodFail"
    Write-Host "    SampleNamespace::SampleTestClass::TestMethodSkip"
    Write-Host "SampleNamespace::AnotherTestClass"
    Write-Host "    SampleNamespace::AnotherTestClass::TestBlocked"
    exit 0
}

# === Normal execution mode ===
Write-Host "StartGroup: SampleNamespace::SampleTestClass::TestMethodPass"
Write-Host "    Log: Starting test execution..."
Write-Host "    Log: Verifying expected result."
Write-Host "EndGroup: SampleNamespace::SampleTestClass::TestMethodPass [Passed]"
Write-Host ""

# If name filter only matches Pass, skip the rest
if ($nameFilter -and $nameFilter -match "Pass") {
    Write-Host ""
    Write-Host "Summary: Total=1, Passed=1, Failed=0, Blocked=0, Not Run=0, Skipped=0"
    exit 0
}

Write-Host "StartGroup: SampleNamespace::SampleTestClass::TestMethodFail"
Write-Host "    Log: Starting failing test..."
Write-Host "    Error: Expected 42 but got 0.  [File: SampleTests.cpp, Function: SampleNamespace::SampleTestClass::TestMethodFail, Line: 35]"
Write-Host "EndGroup: SampleNamespace::SampleTestClass::TestMethodFail [Failed]"
Write-Host ""

Write-Host "StartGroup: SampleNamespace::SampleTestClass::TestMethodSkip"
Write-Host "    Log: Precondition not met, skipping."
Write-Host "EndGroup: SampleNamespace::SampleTestClass::TestMethodSkip [Skipped]"
Write-Host ""

Write-Host "StartGroup: SampleNamespace::AnotherTestClass::TestBlocked"
Write-Host "    Log: Dependency unavailable."
Write-Host "EndGroup: SampleNamespace::AnotherTestClass::TestBlocked [Blocked]"
Write-Host ""

Write-Host "Summary: Total=4, Passed=1, Failed=1, Blocked=1, Not Run=0, Skipped=1"
exit 1
