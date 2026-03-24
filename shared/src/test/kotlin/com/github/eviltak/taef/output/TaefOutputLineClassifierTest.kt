package com.github.eviltak.taef.output

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefOutputLineClassifier] — stateless classification
 * of individual TE.exe output lines.
 */
class TaefOutputLineClassifierTest {

    @Test
    fun startGroup() {
        assertEquals(
            TaefOutputLine.StartGroup("SampleTestClass::TestMethodPass"),
            TaefOutputLineClassifier.classify("StartGroup: SampleTestClass::TestMethodPass")
        )
    }

    @Test
    fun startGroupWithNamespace() {
        assertEquals(
            TaefOutputLine.StartGroup("TestNamespace::NamespacedTestClass::TestInNamespace"),
            TaefOutputLineClassifier.classify("StartGroup: TestNamespace::NamespacedTestClass::TestInNamespace")
        )
    }

    @Test
    fun startGroupDataDriven() {
        assertEquals(
            TaefOutputLine.StartGroup("DataDrivenClass::TestAddition#0"),
            TaefOutputLineClassifier.classify("StartGroup: DataDrivenClass::TestAddition#0")
        )
    }

    @Test
    fun endGroupPassed() {
        assertEquals(
            TaefOutputLine.EndGroup("C::Test", TestResult.PASSED),
            TaefOutputLineClassifier.classify("EndGroup: C::Test [Passed]")
        )
    }

    @Test
    fun endGroupFailed() {
        assertEquals(
            TaefOutputLine.EndGroup("C::Test", TestResult.FAILED),
            TaefOutputLineClassifier.classify("EndGroup: C::Test [Failed]")
        )
    }

    @Test
    fun endGroupBlocked() {
        assertEquals(
            TaefOutputLine.EndGroup("C::Test", TestResult.BLOCKED),
            TaefOutputLineClassifier.classify("EndGroup: C::Test [Blocked]")
        )
    }

    @Test
    fun endGroupSkipped() {
        assertEquals(
            TaefOutputLine.EndGroup("C::Test", TestResult.SKIPPED),
            TaefOutputLineClassifier.classify("EndGroup: C::Test [Skipped]")
        )
    }

    @Test
    fun endGroupNoResult() {
        assertEquals(
            TaefOutputLine.Ignored,
            TaefOutputLineClassifier.classify("EndGroup: C::Test")
        )
    }

    @Test
    fun errorLine() {
        assertEquals(
            TaefOutputLine.Error("Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Line: 10]"),
            TaefOutputLineClassifier.classify("Error: Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Line: 10]")
        )
    }

    @Test
    fun testBlockedLine() {
        assertEquals(
            TaefOutputLine.TestBlocked("TAEF: Setup fixture 'BlockedTestClass::SetupThatFails' for the scope 'BlockedTestClass' returned 'false'."),
            TaefOutputLineClassifier.classify("TestBlocked: TAEF: Setup fixture 'BlockedTestClass::SetupThatFails' for the scope 'BlockedTestClass' returned 'false'.")
        )
    }

    @Test
    fun summaryLine() {
        assertEquals(
            TaefOutputLine.Summary(total = 12, passed = 4, failed = 3, blocked = 4, notRun = 0, skipped = 1),
            TaefOutputLineClassifier.classify("Summary: Total=12, Passed=4, Failed=3, Blocked=4, Not Run=0, Skipped=1")
        )
    }

    @Test
    fun bareText() {
        assertEquals(
            TaefOutputLine.Text("SampleTestClass::TestMethodPass - verifying equality."),
            TaefOutputLineClassifier.classify("SampleTestClass::TestMethodPass - verifying equality.")
        )
    }

    @Test
    fun verifyLine() {
        assertEquals(
            TaefOutputLine.Text("Verify: AreEqual(42, 42)"),
            TaefOutputLineClassifier.classify("Verify: AreEqual(42, 42)")
        )
    }

    @Test
    fun warningLine() {
        assertEquals(
            TaefOutputLine.Text("Warning: resource usage is high."),
            TaefOutputLineClassifier.classify("Warning: resource usage is high.")
        )
    }

    @Test
    fun taefDataLine() {
        assertEquals(
            TaefOutputLine.Text("TAEF: Data[Expected]: 3"),
            TaefOutputLineClassifier.classify("TAEF: Data[Expected]: 3")
        )
    }

    @Test
    fun emptyLine() {
        assertEquals(TaefOutputLine.Ignored, TaefOutputLineClassifier.classify(""))
    }

    @Test
    fun blankLine() {
        assertEquals(TaefOutputLine.Ignored, TaefOutputLineClassifier.classify("   "))
    }

    @Test
    fun taefHeader() {
        assertEquals(
            TaefOutputLine.Text("Test Authoring and Execution Framework v10.99k for x64"),
            TaefOutputLineClassifier.classify("Test Authoring and Execution Framework v10.99k for x64")
        )
    }
}
