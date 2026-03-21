# MockTe — replays captured TE.exe output from fixture files.
#
# Functions:
#   Invoke-MockTe     — main entry point, dispatches to list/run modes
#   Read-Fixture      — reads a fixture file, returns lines
#   Select-TestGroups — parses fixture into test groups, filters by /name: glob
#   Format-Summary    — computes summary line from filtered results
#   Get-ExitCode      — returns 0 if all pass, non-zero count of non-passing

function Read-Fixture {
    param(
        [Parameter(Mandatory)]
        [string]$FixtureDir,

        [Parameter(Mandatory)]
        [ValidateSet('all-tests', 'list-tests')]
        [string]$Name
    )

    $path = Join-Path $FixtureDir "$Name.txt"
    if (-not (Test-Path $path)) {
        throw "Fixture file not found: $path (run cmake --build <dir> --target generate-fixtures)"
    }
    return @(Get-Content $path)
}

function Select-TestGroups {
    param(
        [string[]]$Lines,

        [string]$NameFilter
    )

    if ($NameFilter) {
        $escaped = [regex]::Escape($NameFilter)
        $pattern = '^' + $escaped.Replace('\*', '.*').Replace('\?', '.') + '$'
    } else {
        $pattern = '.*'
    }

    $header = @()
    $groups = @()
    $currentGroup = $null
    $interGroupLines = @()
    $headerDone = $false

    foreach ($line in $Lines) {
        if (-not $headerDone) {
            if ($line -match '^\s*StartGroup:\s+(.+)$') {
                $headerDone = $true
            } else {
                $header += $line
                continue
            }
        }

        if ($line -match '^\s*StartGroup:\s+(.+)$') {
            $currentGroup = @{
                Name = $Matches[1]
                PreLines = @() + $interGroupLines
                Lines = @($line)
                Result = $null
            }
            $interGroupLines = @()
            continue
        }

        if ($line -match '^\s*EndGroup:\s+(.+?)\s+\[(Passed|Failed|Blocked|Skipped)\]') {
            if ($currentGroup) {
                $currentGroup.Lines += $line
                $currentGroup.Result = $Matches[2]
                $groups += $currentGroup
                $currentGroup = $null
            }
            $interGroupLines = @()
            continue
        }

        if ($currentGroup) {
            $currentGroup.Lines += $line
        } else {
            if ($line.Trim()) {
                $interGroupLines += $line
            }
        }
    }

    if ($NameFilter) {
        $groups = @($groups | Where-Object { $_.Name -match $pattern })
    }

    return @{
        Header = $header
        Groups = $groups
    }
}

function Format-Summary {
    param(
        [array]$Groups = @()
    )

    $passed = @($Groups | Where-Object { $_.Result -eq 'Passed' }).Count
    $failed = @($Groups | Where-Object { $_.Result -eq 'Failed' }).Count
    $blocked = @($Groups | Where-Object { $_.Result -eq 'Blocked' }).Count
    $skipped = @($Groups | Where-Object { $_.Result -eq 'Skipped' }).Count
    $total = $Groups.Count

    return "Summary: Total=$total, Passed=$passed, Failed=$failed, Blocked=$blocked, Not Run=0, Skipped=$skipped"
}

function Get-ExitCode {
    param(
        [array]$Groups = @()
    )

    $nonPassing = @($Groups | Where-Object { $_.Result -ne 'Passed' })
    if ($nonPassing.Count -gt 0) { return $nonPassing.Count } else { return 0 }
}

function Invoke-MockTe {
    param(
        [Parameter(Mandatory)]
        [string]$FixtureDir,

        [string]$Dll,

        [switch]$List,

        [string]$NameFilter,

        [switch]$Inproc
    )

    if ($List) {
        $lines = Read-Fixture -FixtureDir $FixtureDir -Name 'list-tests'
        return @{ Output = $lines; ExitCode = 0 }
    }

    $lines = Read-Fixture -FixtureDir $FixtureDir -Name 'all-tests'
    $parsed = Select-TestGroups -Lines $lines -NameFilter $NameFilter

    $output = @()
    $output += $parsed.Header
    foreach ($g in $parsed.Groups) {
        $output += $g.PreLines
        $output += $g.Lines
        $output += ''
    }
    $output += Format-Summary -Groups $parsed.Groups

    return @{
        Output = $output
        ExitCode = Get-ExitCode -Groups $parsed.Groups
    }
}

Export-ModuleMember -Function Invoke-MockTe, Read-Fixture, Select-TestGroups, Format-Summary, Get-ExitCode
