BeforeAll {
    Import-Module "$PSScriptRoot\MockTe.psm1" -Force
    $script:FixtureDir = "$PSScriptRoot\fixtures"

    # Inline test data for unit tests (independent of fixture files)
    $script:SampleOutput = @(
        'Test Authoring and Execution Framework v10.99k for x64'
        'Module setup comment.'
        'Class setup comment.'
        ''
        'StartGroup: MyClass::TestPass'
        'Verify: AreEqual(1, 1)'
        'EndGroup: MyClass::TestPass [Passed]'
        'Method cleanup.'
        ''
        'StartGroup: MyClass::TestFail'
        'Error: Verify: AreEqual(1, 0) - Values (1, 0) [File: test.cpp, Function: MyClass::TestFail, Line: 10]'
        'EndGroup: MyClass::TestFail [Failed]'
        ''
        'StartGroup: MyClass::TestSkip'
        'EndGroup: MyClass::TestSkip [Skipped]'
        'TestBlocked: TAEF: Setup returned false.'
        ''
        'StartGroup: Blocked::Test1'
        'EndGroup: Blocked::Test1 [Blocked]'
        ''
        'StartGroup: NS::Other::TestOK'
        'Verify: IsTrue(true)'
        'EndGroup: NS::Other::TestOK [Passed]'
        ''
        'StartGroup: DataClass::ParamTest#0'
        'TAEF: Data[Value]: 1'
        'Verify: AreEqual(1, 1)'
        'EndGroup: DataClass::ParamTest#0 [Passed]'
        ''
        'StartGroup: DataClass::ParamTest#1'
        'TAEF: Data[Value]: 99'
        'Error: Verify: AreEqual(99, 1) [File: test.cpp, Line: 20]'
        'EndGroup: DataClass::ParamTest#1 [Failed]'
        ''
        'Summary: Total=7, Passed=3, Failed=2, Blocked=1, Not Run=0, Skipped=1'
    )
}

Describe 'Read-Fixture' {
    It 'reads all-tests fixture' {
        $lines = Read-Fixture -FixtureDir $script:FixtureDir -Name 'all-tests'
        $lines | Should -Not -BeNullOrEmpty
        ($lines -join "`n") | Should -Match 'StartGroup:'
    }

    It 'reads list-tests fixture' {
        $lines = Read-Fixture -FixtureDir $script:FixtureDir -Name 'list-tests'
        $lines | Should -Not -BeNullOrEmpty
    }

    It 'throws for missing fixture' {
        { Read-Fixture -FixtureDir 'C:\nonexistent' -Name 'all-tests' } | Should -Throw '*not found*'
    }
}

Describe 'Select-TestGroups' {
    It 'extracts all groups when no filter' {
        $result = Select-TestGroups -Lines $script:SampleOutput
        $result.Groups.Count | Should -Be 7
    }

    It 'extracts header lines before first StartGroup' {
        $result = Select-TestGroups -Lines $script:SampleOutput
        ($result.Header -join "`n") | Should -Match 'Test Authoring and Execution Framework'
        ($result.Header -join "`n") | Should -Match 'Module setup comment'
    }

    It 'preserves result per group' {
        $result = Select-TestGroups -Lines $script:SampleOutput
        @($result.Groups | Where-Object { $_.Result -eq 'Passed' }).Count | Should -Be 3
        @($result.Groups | Where-Object { $_.Result -eq 'Failed' }).Count | Should -Be 2
        @($result.Groups | Where-Object { $_.Result -eq 'Blocked' }).Count | Should -Be 1
        @($result.Groups | Where-Object { $_.Result -eq 'Skipped' }).Count | Should -Be 1
    }

    It 'captures inter-group lines as PreLines' {
        $result = Select-TestGroups -Lines $script:SampleOutput
        $blocked = $result.Groups | Where-Object { $_.Name -eq 'Blocked::Test1' }
        ($blocked.PreLines -join "`n") | Should -Match 'Setup returned false'
    }

    It 'captures test output inside group Lines' {
        $result = Select-TestGroups -Lines $script:SampleOutput
        $pass = $result.Groups | Where-Object { $_.Name -eq 'MyClass::TestPass' }
        ($pass.Lines -join "`n") | Should -Match 'Verify: AreEqual'
    }

    It 'filters by exact name' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'MyClass::TestPass'
        $result.Groups.Count | Should -Be 1
        $result.Groups[0].Name | Should -Be 'MyClass::TestPass'
    }

    It 'filters by class glob' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'MyClass::*'
        $result.Groups.Count | Should -Be 3
    }

    It 'filters by namespace glob' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'NS::*'
        $result.Groups.Count | Should -Be 1
        $result.Groups[0].Name | Should -Match 'TestOK'
    }

    It 'returns empty for no matches' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter '*NonExistent*'
        $result.Groups.Count | Should -Be 0
    }

    It 'filters data-driven test by base name glob' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'DataClass::ParamTest*'
        $result.Groups.Count | Should -Be 2
    }

    It 'filters data-driven test by specific index' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'DataClass::ParamTest#0'
        $result.Groups.Count | Should -Be 1
        $result.Groups[0].Result | Should -Be 'Passed'
    }

    It 'captures TAEF data parameters inside group' {
        $result = Select-TestGroups -Lines $script:SampleOutput -NameFilter 'DataClass::ParamTest#0'
        ($result.Groups[0].Lines -join "`n") | Should -Match 'TAEF: Data\[Value\]: 1'
    }
}

Describe 'Format-Summary' {
    It 'formats all result types' {
        $groups = @(
            @{ Result = 'Passed' },
            @{ Result = 'Failed' },
            @{ Result = 'Blocked' },
            @{ Result = 'Skipped' }
        )
        Format-Summary -Groups $groups | Should -Be 'Summary: Total=4, Passed=1, Failed=1, Blocked=1, Not Run=0, Skipped=1'
    }

    It 'handles empty groups' {
        Format-Summary -Groups @() | Should -Be 'Summary: Total=0, Passed=0, Failed=0, Blocked=0, Not Run=0, Skipped=0'
    }
}

Describe 'Get-ExitCode' {
    It 'returns 0 when all pass' {
        Get-ExitCode -Groups @(@{ Result = 'Passed' }, @{ Result = 'Passed' }) | Should -Be 0
    }

    It 'returns count of non-passing' {
        Get-ExitCode -Groups @(@{ Result = 'Passed' }, @{ Result = 'Failed' }, @{ Result = 'Blocked' }) | Should -Be 2
    }
}

Describe 'Invoke-MockTe — full run (fixtures)' {
    It 'replays all tests' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll'
        $output = $result.Output -join "`n"
        $output | Should -Match 'StartGroup:'
        $output | Should -Match 'Summary: Total='
    }

    It 'returns non-zero exit code when failures exist' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll'
        $result.ExitCode | Should -BeGreaterThan 0
    }
}

Describe 'Invoke-MockTe — /list (fixtures)' {
    It 'replays list fixture without StartGroup' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll' -List
        $output = $result.Output -join "`n"
        $output | Should -Not -Match 'StartGroup:'
        $result.ExitCode | Should -Be 0
    }
}

Describe 'Invoke-MockTe — /name: filter (fixtures)' {
    It 'includes only matching tests' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll' -NameFilter '*Pass*'
        $output = $result.Output -join "`n"
        $output | Should -Match 'Passed'
        $output | Should -Not -Match '\[Failed\]'
        $result.ExitCode | Should -Be 0
    }

    It 'includes PreLines for blocked tests' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll' -NameFilter 'BlockedTestClass::*'
        ($result.Output -join "`n") | Should -Match 'Setup fixture'
    }

    It 'returns empty summary for no matches' {
        $result = Invoke-MockTe -FixtureDir $script:FixtureDir -Dll 'test.dll' -NameFilter '*NonExistent*'
        ($result.Output -join "`n") | Should -Match 'Total=0'
        $result.ExitCode | Should -Be 0
    }
}
