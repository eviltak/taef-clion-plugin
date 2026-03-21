package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefOutputParser] — verifies parsing of real TE.exe
 * console output into structured [TaefTestEvent]s.
 *
 * Test data is based on actual TE.exe v10.99k output captured in
 * testData/mockTe/fixtures/all-tests.txt.
 */
class TaefOutputParserTest {

    // --- Passing test ---

    @Test
    fun passingTest() {
        val events = parse("""
            StartGroup: SampleTestClass::TestMethodPass
            SampleTestClass::TestMethodPass - verifying equality.
            Verify: AreEqual(42, 42)
            EndGroup: SampleTestClass::TestMethodPass [Passed]
        """)

        assertHasStart(events, "SampleTestClass::TestMethodPass")
        assertHasFinish(events, "SampleTestClass::TestMethodPass", TestResult.PASSED)
    }

    // --- Failing test with verify error ---

    @Test
    fun failingTestWithVerifyError() {
        val events = parse("""
            StartGroup: SampleTestClass::TestMethodFail
            SampleTestClass::TestMethodFail - this will fail.
            Error: Verify: AreEqual(42, 0) - Values (42, 0) [File: SampleTests.cpp, Function: SampleTestClass::TestMethodFail, Line: 83]
            EndGroup: SampleTestClass::TestMethodFail [Failed]
        """)

        assertHasStart(events, "SampleTestClass::TestMethodFail")
        assertHasFinish(events, "SampleTestClass::TestMethodFail", TestResult.FAILED)

        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        assertTrue("Should have error output", errors.isNotEmpty())
        val errorText = errors.joinToString("\n") { it.text }
        assertTrue("Error should contain source location", errorText.contains("File:"))
        assertTrue("Error should contain line number", errorText.contains("Line: 83"))
    }

    // --- Failing test with Log::Error (no verify, no [File:]) ---

    @Test
    fun failingTestWithLogError() {
        val events = parse("""
            StartGroup: C::TestWithLogError
            Error: SampleTestClass::TestMethodWithWarning - non-fatal error for testing.
            EndGroup: C::TestWithLogError [Failed]
        """)

        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        assertTrue("Should capture Log::Error as error output", errors.isNotEmpty())
        assertTrue("Error text should contain the message",
            errors[0].text.contains("non-fatal error"))
    }

    // --- Skipped test ---

    @Test
    fun skippedTest() {
        val events = parse("""
            StartGroup: SampleTestClass::TestMethodSkip
            SampleTestClass::TestMethodSkip - skipping test.
            EndGroup: SampleTestClass::TestMethodSkip [Skipped]
        """)

        assertHasFinish(events, "SampleTestClass::TestMethodSkip", TestResult.SKIPPED)
    }

    // --- Blocked test ---

    @Test
    fun blockedTest() {
        val events = parse("""
            StartGroup: BlockedTestClass::TestBlockedBySetup
            EndGroup: BlockedTestClass::TestBlockedBySetup [Blocked]
        """)

        assertHasFinish(events, "BlockedTestClass::TestBlockedBySetup", TestResult.BLOCKED)
    }

    // --- Log::Comment output (bare text, no prefix) ---

    @Test
    fun bareTextCapturedAsStdout() {
        val events = parse("""
            StartGroup: C::Test
            SampleTestClass::TestMethodPass - verifying equality.
            EndGroup: C::Test [Passed]
        """)

        val stdout = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { !it.isError }
        assertTrue("Bare text should be captured as stdout", stdout.isNotEmpty())
        assertTrue("Should contain the comment text",
            stdout.any { it.text.contains("verifying equality") })
    }

    // --- Verify pass output ---

    @Test
    fun verifyPassCapturedAsStdout() {
        val events = parse("""
            StartGroup: C::Test
            Verify: AreEqual(42, 42)
            EndGroup: C::Test [Passed]
        """)

        val stdout = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { !it.isError }
        assertTrue("Verify: lines should be captured as stdout", stdout.isNotEmpty())
        assertTrue("Should contain verify text",
            stdout.any { it.text.contains("AreEqual(42, 42)") })
    }

    // --- Warning output ---

    @Test
    fun warningCapturedAsStdout() {
        val events = parse("""
            StartGroup: C::Test
            Warning: SampleTestClass::TestMethodWithWarning - resource usage is high.
            EndGroup: C::Test [Failed]
        """)

        val stdout = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { !it.isError }
        assertTrue("Warning: lines should be captured as stdout", stdout.isNotEmpty())
        assertTrue("Should contain warning text",
            stdout.any { it.text.contains("resource usage is high") })
    }

    // --- TAEF Data parameter lines ---

    @Test
    fun taefDataLinesCapturedAsStdout() {
        val events = parse("""
            StartGroup: DataDrivenClass::TestAddition#0
            TAEF: Data[Expected]: 3
            TAEF: Data[Left]: 1
            TAEF: Data[Right]: 2
            Verify: AreEqual(left + right, expected)
            EndGroup: DataDrivenClass::TestAddition#0 [Passed]
        """)

        val stdout = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { !it.isError }
        assertTrue("TAEF: Data lines should be captured",
            stdout.any { it.text.contains("Data[Expected]: 3") })
    }

    // --- TestBlocked message as error context ---

    @Test
    fun testBlockedMessageCapturedAsErrorContext() {
        val events = parse("""
            TestBlocked: TAEF: Setup fixture 'BlockedTestClass::SetupThatFails' for the scope 'BlockedTestClass' returned 'false'.
            StartGroup: BlockedTestClass::TestBlockedBySetup
            EndGroup: BlockedTestClass::TestBlockedBySetup [Blocked]
        """)

        assertHasFinish(events, "BlockedTestClass::TestBlockedBySetup", TestResult.BLOCKED)

        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        assertTrue("TestBlocked message should be captured as error context",
            errors.any { it.text.contains("Setup fixture") })
    }

    // --- Summary line ---

    @Test
    fun summaryParsed() {
        val events = parse("Summary: Total=12, Passed=4, Failed=3, Blocked=4, Not Run=0, Skipped=1")

        val summary = events.filterIsInstance<TaefTestEvent.Summary>()
        assertEquals("Should have one summary", 1, summary.size)
        assertEquals(12, summary[0].total)
        assertEquals(4, summary[0].passed)
        assertEquals(3, summary[0].failed)
        assertEquals(4, summary[0].blocked)
        assertEquals(0, summary[0].notRun)
        assertEquals(1, summary[0].skipped)
    }

    // --- Lines outside blocks are not test output ---

    @Test
    fun outsideBlockLinesNotCapturedAsTestOutput() {
        val events = parse("""
            Test Authoring and Execution Framework v10.99k for x64
            ModuleSetup - initializing module.
            SampleTestClass::ClassSetup - initializing class.
            SampleTestClass::MethodSetup - preparing test.
            StartGroup: C::Test
            EndGroup: C::Test [Passed]
            SampleTestClass::MethodCleanup - cleaning up test.
        """)

        val testOutputs = events.filterIsInstance<TaefTestEvent.TestOutput>()
        assertFalse("Header line should not be test output",
            testOutputs.any { it.text.contains("Execution Framework") })
        assertFalse("Setup log should not be test output",
            testOutputs.any { it.text.contains("ModuleSetup") })
        assertFalse("Cleanup log should not be test output",
            testOutputs.any { it.text.contains("MethodCleanup") })
    }

    // --- Data-driven test names with # suffix ---

    @Test
    fun dataDrivenTestNameParsed() {
        val events = parse("""
            StartGroup: DataDrivenClass::TestAddition#2
            EndGroup: DataDrivenClass::TestAddition#2 [Failed]
        """)

        assertHasStart(events, "DataDrivenClass::TestAddition#2")
        assertHasFinish(events, "DataDrivenClass::TestAddition#2", TestResult.FAILED)
    }

    // --- Multiple error lines accumulated ---

    @Test
    fun multipleErrorLinesAccumulated() {
        val events = parse("""
            StartGroup: C::Test
            Error: First error [File: a.cpp, Line: 10]
            Error: Second error [File: a.cpp, Line: 20]
            EndGroup: C::Test [Failed]
        """)

        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        assertEquals("Multiple errors should be accumulated into one output", 1, errors.size)
        assertTrue("Should contain first error", errors[0].text.contains("First error"))
        assertTrue("Should contain second error", errors[0].text.contains("Second error"))
    }

    // --- Empty blocked test body ---

    @Test
    fun emptyBlockedTestBody() {
        val events = parse("""
            StartGroup: C::Test
            EndGroup: C::Test [Blocked]
        """)

        assertHasStart(events, "C::Test")
        assertHasFinish(events, "C::Test", TestResult.BLOCKED)

        val outputs = events.filterIsInstance<TaefTestEvent.TestOutput>()
        assertTrue("Blocked test with no body should have no test output", outputs.isEmpty())
    }

    // --- Helpers ---

    private fun parse(output: String): List<TaefTestEvent> =
        TaefOutputParser.parse(output.trimIndent())

    private fun assertHasStart(events: List<TaefTestEvent>, name: String) {
        assertTrue("Should have TestStarted for '$name'",
            events.any { it is TaefTestEvent.TestStarted && it.fullyQualifiedName == name })
    }

    private fun assertHasFinish(events: List<TaefTestEvent>, name: String, result: TestResult) {
        assertTrue("Should have TestFinished($result) for '$name'",
            events.any { it is TaefTestEvent.TestFinished && it.fullyQualifiedName == name && it.result == result })
    }
}
