package com.github.eviltak.taef.output

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefStreamParser] — verifies stateful parsing of TE.exe
 * output into exact [TaefTestEvent] sequences.
 *
 * All tests compare the full event list for exact equality — no partial
 * matching. Test data is based on real TE.exe v10.99k output.
 */
class TaefStreamParserTest {

    // --- TaefTestId parsing ---

    @Test
    fun parseIdSimple() {
        assertEquals(TaefTestId("C", "Test"), TaefTestId.parse("C::Test"))
    }

    @Test
    fun parseIdNamespaced() {
        assertEquals(TaefTestId("NS::Class", "Method"), TaefTestId.parse("NS::Class::Method"))
    }

    @Test
    fun parseIdDataDriven() {
        assertEquals(TaefTestId("Class", "Test#0"), TaefTestId.parse("Class::Test#0"))
    }

    @Test
    fun parseIdNoSuite() {
        assertEquals(TaefTestId("", "Test"), TaefTestId.parse("Test"))
    }

    // --- Result types ---

    @Test
    fun passingTest() {
        assertEvents("""
            StartGroup: C::TestPass
            Verify: AreEqual(42, 42)
            EndGroup: C::TestPass [Passed]
        """,
            TaefTestEvent.TestStarted(id("C", "TestPass")),
            TaefTestEvent.TestOutput(id("C", "TestPass"), "Verify: AreEqual(42, 42)", isError = false),
            TaefTestEvent.TestFinished(id("C", "TestPass"), TestResult.PASSED),
        )
    }

    @Test
    fun failingTestWithVerifyError() {
        assertEvents("""
            StartGroup: C::TestFail
            Error: Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Function: C::TestFail, Line: 83]
            EndGroup: C::TestFail [Failed]
        """,
            TaefTestEvent.TestStarted(id("C", "TestFail")),
            TaefTestEvent.TestOutput(id("C", "TestFail"), "Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Function: C::TestFail, Line: 83]", isError = true),
            TaefTestEvent.TestFinished(id("C", "TestFail"), TestResult.FAILED),
        )
    }

    @Test
    fun failingTestWithLogError() {
        assertEvents("""
            StartGroup: C::Test
            Error: non-fatal error for testing.
            EndGroup: C::Test [Failed]
        """,
            TaefTestEvent.TestStarted(id("C", "Test")),
            TaefTestEvent.TestOutput(id("C", "Test"), "non-fatal error for testing.", isError = true),
            TaefTestEvent.TestFinished(id("C", "Test"), TestResult.FAILED),
        )
    }

    @Test
    fun skippedTest() {
        assertEvents("""
            StartGroup: C::TestSkip
            C::TestSkip - skipping test.
            EndGroup: C::TestSkip [Skipped]
        """,
            TaefTestEvent.TestStarted(id("C", "TestSkip")),
            TaefTestEvent.TestOutput(id("C", "TestSkip"), "C::TestSkip - skipping test.", isError = false),
            TaefTestEvent.TestFinished(id("C", "TestSkip"), TestResult.SKIPPED),
        )
    }

    @Test
    fun blockedTest() {
        assertEvents("""
            StartGroup: C::TestBlocked
            EndGroup: C::TestBlocked [Blocked]
        """,
            TaefTestEvent.TestStarted(id("C", "TestBlocked")),
            TaefTestEvent.TestFinished(id("C", "TestBlocked"), TestResult.BLOCKED),
        )
    }

    // --- Output line types ---

    @Test
    fun bareTextCapturedAsStdout() {
        assertEvents("""
            StartGroup: C::Test
            SampleTestClass::TestMethodPass - verifying equality.
            EndGroup: C::Test [Passed]
        """,
            TaefTestEvent.TestStarted(id("C", "Test")),
            TaefTestEvent.TestOutput(id("C", "Test"), "SampleTestClass::TestMethodPass - verifying equality.", isError = false),
            TaefTestEvent.TestFinished(id("C", "Test"), TestResult.PASSED),
        )
    }

    @Test
    fun warningCapturedAsStdout() {
        assertEvents("""
            StartGroup: C::Test
            Warning: resource usage is high.
            EndGroup: C::Test [Passed]
        """,
            TaefTestEvent.TestStarted(id("C", "Test")),
            TaefTestEvent.TestOutput(id("C", "Test"), "Warning: resource usage is high.", isError = false),
            TaefTestEvent.TestFinished(id("C", "Test"), TestResult.PASSED),
        )
    }

    @Test
    fun taefDataLinesCapturedAsStdout() {
        assertEvents("""
            StartGroup: DataClass::Test#0
            TAEF: Data[Expected]: 3
            TAEF: Data[Left]: 1
            EndGroup: DataClass::Test#0 [Passed]
        """,
            TaefTestEvent.TestStarted(id("DataClass", "Test#0")),
            TaefTestEvent.TestOutput(id("DataClass", "Test#0"), "TAEF: Data[Expected]: 3", isError = false),
            TaefTestEvent.TestOutput(id("DataClass", "Test#0"), "TAEF: Data[Left]: 1", isError = false),
            TaefTestEvent.TestFinished(id("DataClass", "Test#0"), TestResult.PASSED),
        )
    }

    // --- TestBlocked context ---

    @Test
    fun testBlockedMessageCapturedAsErrorContext() {
        assertEvents("""
            TestBlocked: TAEF: Setup fixture 'BlockedTestClass::SetupThatFails' for the scope 'BlockedTestClass' returned 'false'.
            StartGroup: BlockedTestClass::TestBlockedBySetup
            EndGroup: BlockedTestClass::TestBlockedBySetup [Blocked]
        """,
            TaefTestEvent.TestStarted(id("BlockedTestClass", "TestBlockedBySetup")),
            TaefTestEvent.TestOutput(id("BlockedTestClass", "TestBlockedBySetup"),
                "TAEF: Setup fixture 'BlockedTestClass::SetupThatFails' for the scope 'BlockedTestClass' returned 'false'.", isError = true),
            TaefTestEvent.TestFinished(id("BlockedTestClass", "TestBlockedBySetup"), TestResult.BLOCKED),
        )
    }

    // --- Summary ---

    @Test
    fun summaryParsed() {
        assertEvents(
            "Summary: Total=12, Passed=4, Failed=3, Blocked=4, Not Run=0, Skipped=1",
            TaefTestEvent.Summary(total = 12, passed = 4, failed = 3, blocked = 4, notRun = 0, skipped = 1),
        )
    }

    // --- Lines outside blocks ---

    @Test
    fun outsideBlockLinesIgnored() {
        assertEvents("""
            Test Authoring and Execution Framework v10.99k for x64
            ModuleSetup - initializing module.
            SampleTestClass::ClassSetup - initializing class.
            StartGroup: C::Test
            EndGroup: C::Test [Passed]
            SampleTestClass::MethodCleanup - cleaning up test.
        """,
            TaefTestEvent.TestStarted(id("C", "Test")),
            TaefTestEvent.TestFinished(id("C", "Test"), TestResult.PASSED),
        )
    }

    // --- Edge cases ---

    @Test
    fun dataDrivenTestNameWithHashSuffix() {
        assertEvents("""
            StartGroup: DataDrivenClass::TestAddition#2
            Error: Verify: AreEqual(left + right, expected) - Values (0, 1)
            EndGroup: DataDrivenClass::TestAddition#2 [Failed]
        """,
            TaefTestEvent.TestStarted(id("DataDrivenClass", "TestAddition#2")),
            TaefTestEvent.TestOutput(id("DataDrivenClass", "TestAddition#2"),
                "Verify: AreEqual(left + right, expected) - Values (0, 1)", isError = true),
            TaefTestEvent.TestFinished(id("DataDrivenClass", "TestAddition#2"), TestResult.FAILED),
        )
    }

    @Test
    fun multipleErrorLinesAccumulated() {
        assertEvents("""
            StartGroup: C::Test
            Error: First error [File: a.cpp, Line: 10]
            Error: Second error [File: a.cpp, Line: 20]
            EndGroup: C::Test [Failed]
        """,
            TaefTestEvent.TestStarted(id("C", "Test")),
            TaefTestEvent.TestOutput(id("C", "Test"),
                "First error [File: a.cpp, Line: 10]\nSecond error [File: a.cpp, Line: 20]", isError = true),
            TaefTestEvent.TestFinished(id("C", "Test"), TestResult.FAILED),
        )
    }

    @Test
    fun orphanEndGroupIgnored() {
        assertEvents("EndGroup: C::Test [Passed]")
    }

    @Test
    fun namespacedTestClass() {
        assertEvents("""
            StartGroup: TestNamespace::NamespacedTestClass::TestInNamespace
            EndGroup: TestNamespace::NamespacedTestClass::TestInNamespace [Passed]
        """,
            TaefTestEvent.TestStarted(id("TestNamespace::NamespacedTestClass", "TestInNamespace")),
            TaefTestEvent.TestFinished(id("TestNamespace::NamespacedTestClass", "TestInNamespace"), TestResult.PASSED),
        )
    }

    // --- Helpers ---

    private fun id(suite: String, test: String) = TaefTestId(suite, test)

    private fun assertEvents(output: String, vararg expected: TaefTestEvent) {
        val actual = TaefStreamParser().parseAll(output.trimIndent())
        assertEquals(expected.toList(), actual)
    }
}
