package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefOutputParser] — the TE.exe output state machine.
 * Pure logic, no IntelliJ Platform dependencies.
 */
class TaefOutputParserTest {

    @Test
    fun parse_singlePassedTest() {
        val output = """
            StartGroup: NS::MyClass::TestPass
            EndGroup: NS::MyClass::TestPass [Passed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        assertEquals(2, events.size)
        assertIs<TaefTestEvent.TestStarted>(events[0])
        assertIs<TaefTestEvent.TestFinished>(events[1])
        assertEquals("NS::MyClass::TestPass", (events[0] as TaefTestEvent.TestStarted).fullyQualifiedName)
        assertEquals(TestResult.PASSED, (events[1] as TaefTestEvent.TestFinished).result)
    }

    @Test
    fun parse_singleFailedTest() {
        val output = """
            StartGroup: NS::MyClass::TestFail
                Error: Expected 1 but got 2  [File: Test.cpp, Function: TestFail, Line: 42]
            EndGroup: NS::MyClass::TestFail [Failed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>().first()

        assertEquals(1, errors.size)
        assertTrue(errors[0].text.contains("Expected 1 but got 2"))
        assertTrue(errors[0].text.contains("File: Test.cpp"))
        assertEquals(TestResult.FAILED, finished.result)
    }

    @Test
    fun parse_blockedTest() {
        val output = """
            StartGroup: NS::MyClass::TestBlocked
            EndGroup: NS::MyClass::TestBlocked [Blocked]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>().first()
        assertEquals(TestResult.BLOCKED, finished.result)
    }

    @Test
    fun parse_skippedTest() {
        val output = """
            StartGroup: NS::MyClass::TestSkipped
            EndGroup: NS::MyClass::TestSkipped [Skipped]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>().first()
        assertEquals(TestResult.SKIPPED, finished.result)
    }

    @Test
    fun parse_multipleTestsMixedResults() {
        val output = """
            StartGroup: A::B::Test1
            EndGroup: A::B::Test1 [Passed]
            StartGroup: A::B::Test2
                Error: assertion failed
            EndGroup: A::B::Test2 [Failed]
            StartGroup: A::B::Test3
            EndGroup: A::B::Test3 [Skipped]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>()
        assertEquals(3, finished.size)
        assertEquals(TestResult.PASSED, finished[0].result)
        assertEquals(TestResult.FAILED, finished[1].result)
        assertEquals(TestResult.SKIPPED, finished[2].result)
    }

    @Test
    fun parse_nestedNamespace() {
        val output = """
            StartGroup: Deep::Nested::Namespace::ClassName::MethodName
            EndGroup: Deep::Nested::Namespace::ClassName::MethodName [Passed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val started = events.filterIsInstance<TaefTestEvent.TestStarted>().first()
        assertEquals("Deep::Nested::Namespace::ClassName::MethodName", started.fullyQualifiedName)
    }

    @Test
    fun parse_logMessages() {
        val output = """
            StartGroup: NS::MyClass::TestWithLogs
                Log: Starting test execution...
                Log: Verifying expected result.
            EndGroup: NS::MyClass::TestWithLogs [Passed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val logs = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { !it.isError }
        assertEquals(2, logs.size)
        assertEquals("Starting test execution...", logs[0].text)
        assertEquals("Verifying expected result.", logs[1].text)
    }

    @Test
    fun parse_errorWithFileLocation() {
        val output = """
            StartGroup: NS::MyClass::TestError
                Error: Verify failed  [File: SampleTests.cpp, Function: TestError, Line: 35]
            EndGroup: NS::MyClass::TestError [Failed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        assertEquals(1, errors.size)
        assertTrue(errors[0].text.contains("File: SampleTests.cpp"))
        assertTrue(errors[0].text.contains("Line: 35"))
    }

    @Test
    fun parse_multipleErrorLines() {
        val output = """
            StartGroup: NS::MyClass::TestMultiError
                Error: First error  [File: Test.cpp, Function: F, Line: 10]
                Error: Second error  [File: Test.cpp, Function: F, Line: 20]
            EndGroup: NS::MyClass::TestMultiError [Failed]
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val errors = events.filterIsInstance<TaefTestEvent.TestOutput>().filter { it.isError }
        // Multiple error lines are accumulated into a single TestOutput
        assertEquals(1, errors.size)
        assertTrue(errors[0].text.contains("First error"))
        assertTrue(errors[0].text.contains("Second error"))
    }

    @Test
    fun parse_summaryLine() {
        val output = "Summary: Total=5, Passed=3, Failed=1, Blocked=0, Not Run=0, Skipped=1"

        val events = TaefOutputParser.parse(output)
        val summary = events.filterIsInstance<TaefTestEvent.Summary>().first()
        assertEquals(5, summary.total)
        assertEquals(3, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.blocked)
        assertEquals(0, summary.notRun)
        assertEquals(1, summary.skipped)
    }

    @Test
    fun parse_emptyOutput() {
        val events = TaefOutputParser.parse("")
        assertTrue(events.isEmpty())
    }

    @Test
    fun parse_unexpectedLines() {
        val output = """
            Some random text before tests
            StartGroup: NS::MyClass::Test1
                Log: actual log
            EndGroup: NS::MyClass::Test1 [Passed]
            More random text between tests
            Summary: Total=1, Passed=1, Failed=0, Blocked=0, Not Run=0, Skipped=0
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val started = events.filterIsInstance<TaefTestEvent.TestStarted>()
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>()
        assertEquals(1, started.size)
        assertEquals(1, finished.size)
        assertEquals(TestResult.PASSED, finished[0].result)
    }

    @Test
    fun parse_classGroupsIgnored() {
        // TE.exe also emits StartGroup/EndGroup for classes, not just methods.
        // These have no [Passed] etc. suffix on EndGroup, so they won't match
        // the EndGroup pattern and are effectively ignored.
        val output = """
            StartGroup: NS::MyClass
            StartGroup: NS::MyClass::Test1
            EndGroup: NS::MyClass::Test1 [Passed]
            EndGroup: NS::MyClass
        """.trimIndent()

        val events = TaefOutputParser.parse(output)
        val started = events.filterIsInstance<TaefTestEvent.TestStarted>()
        val finished = events.filterIsInstance<TaefTestEvent.TestFinished>()
        // Class-level StartGroup is captured but class-level EndGroup is not
        // (no [Result] suffix), which is correct behavior
        assertEquals(2, started.size) // class + method
        assertEquals(1, finished.size) // only method
    }

    // --- Helpers ---

    private inline fun <reified T> assertIs(value: Any?) {
        assertTrue("Expected ${T::class.simpleName} but got ${value?.javaClass?.simpleName}", value is T)
    }
}
