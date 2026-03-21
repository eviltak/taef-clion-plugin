# Mock TE.exe — replays captured TE.exe output from fixture files.
# Usage: pwsh mock_te.ps1 <dll> [/name:<filter>] [/select:"<query>"] [/inproc] [/list]
#
# See MockTe.psm1 for the implementation and MockTe.Tests.ps1 for tests.

param(
    [Parameter(Position = 0)]
    [string]$Dll,

    [Parameter(ValueFromRemainingArguments)]
    [string[]]$RemainingArgs
)

Import-Module "$PSScriptRoot\MockTe.psm1" -Force

if (-not $Dll) {
    [Console]::Error.WriteLine("Error: No test DLL specified.")
    [Console]::Error.WriteLine("Usage: mock_te.ps1 <test_dll> [/name:<filter>] [/list] [/inproc]")
    exit 1
}

$listMode = $false
$nameFilter = ""
$inproc = $false

foreach ($arg in $RemainingArgs) {
    if ($arg -eq "/list") { $listMode = $true }
    if ($arg -match "^/name:(.+)$") { $nameFilter = $Matches[1] }
    if ($arg -eq "/inproc") { $inproc = $true }
}

$params = @{
    FixtureDir = Join-Path $PSScriptRoot "fixtures"
    Dll = $Dll
}
if ($listMode) { $params.List = $true }
if ($nameFilter) { $params.NameFilter = $nameFilter }
if ($inproc) { $params.Inproc = $true }

$result = Invoke-MockTe @params
$result.Output | ForEach-Object { [Console]::Out.WriteLine($_) }
exit $result.ExitCode
