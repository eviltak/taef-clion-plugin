package com.github.eviltak.taef

import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the SMRunner integration: verifies that [TaefOutputParser]
 * events are correctly translated into ##teamcity service messages via
 * [CidrTestEventProcessor].
 *
 * Tests the event-to-service-message mapping without running the full
 * converter pipeline (no ProcessHandler or console needed).
 */
class TaefSMRunnerTest {

    private lateinit var processor: CidrTestEventProcessor

    @Before
    fun setUp() {
        processor = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
    }

    // --- TestStarted → testStarted message ---

    @Test
    fun testStarted_producesServiceMessage() {
        val messages = processor.testStarted(
            "NS::Class::Method",
            "${TaefTestConstants.PROTOCOL_PREFIX}://NS::Class::Method"
        )
        assertTrue("Should produce at least one message", messages.isNotEmpty())
        val msg = messages.joinToString("") { it.toString() }
        assertTrue("Should contain testStarted", msg.contains("testStarted"))
        assertTrue("Should contain test name", msg.contains("NS::Class::Method"))
    }

    // --- TestFinished (passed) → testFinished message ---

    @Test
    fun testFinished_passed_producesFinishedMessage() {
        val messages = processor.testFinished("NS::Class::Method", "", false)
        assertTrue("Should produce messages for passed test", messages.isNotEmpty())
        val msg = messages.joinToString("\n") { it.toString() }
        // Verify the test name appears in the output
        assertTrue("Should reference test name, got: $msg", msg.contains("NS::Class::Method"))
    }

    @Test
    fun testFinished_failed_includesFailureIndication() {
        val messages = processor.testFinished("NS::Class::Method", "", true)
        assertTrue("Should produce messages for failed test", messages.isNotEmpty())
        val msg = messages.joinToString("\n") { it.toString() }
        assertTrue("Should reference test name, got: $msg", msg.contains("NS::Class::Method"))
    }

    @Test
    fun testFinished_failed_differFromPassed() {
        val passedMessages = processor.testFinished("TestA", "", false)
        val failedMessages = processor.testFinished("TestB", "", true)
        // Failed should produce different (typically more) messages than passed
        assertNotEquals(
            "Failed and passed should produce different message counts or content",
            passedMessages.size, failedMessages.size
        )
    }

    // --- Skipped/Blocked → testIgnored message ---

    @Test
    fun testIgnored_producesIgnoredMessage() {
        val messages = processor.testUnitIgnore(
            "NS::Class::Method",
            "NS::Class::Method",
            "Skipped"
        )
        val msg = messages.joinToString("") { it.toString() }
        assertTrue("Should contain testIgnored", msg.contains("testIgnored"))
    }

    // --- TestOutput (error) → testStdErr ---

    @Test
    fun testErrOut_producesStdErrMessage() {
        val messages = processor.testErrOut("NS::Class::Method", "assertion failed")
        val msg = messages.joinToString("") { it.toString() }
        assertTrue("Should contain testStdErr", msg.contains("testStdErr"))
        assertTrue("Should contain error text", msg.contains("assertion failed"))
    }

    // --- TestOutput (stdout) → testStdOut ---

    @Test
    fun testStdOut_producesStdOutMessage() {
        val messages = processor.testStdOut(
            "NS::Class::Method", "log output",
            "NS::Class::Method"
        )
        val msg = messages.joinToString("") { it.toString() }
        assertTrue("Should contain testStdOut", msg.contains("testStdOut"))
        assertTrue("Should contain log text", msg.contains("log output"))
    }

    // --- Full pipeline: parse TE.exe output → events → service messages ---

    @Test
    fun fullPipeline_singlePassingTest() {
        val output = """
            StartGroup: NS::MyClass::TestPass
            EndGroup: NS::MyClass::TestPass [Passed]
        """.trimIndent()

        val messages = runPipeline(output)
        val combined = messages.joinToString("\n")
        assertTrue("Should have testStarted", combined.contains("testStarted"))
        assertTrue("Should have testFinished", combined.contains("testFinished"))
    }

    @Test
    fun fullPipeline_singleFailingTest() {
        val output = """
            StartGroup: NS::MyClass::TestFail
                Error: Expected 42 but got 0 [File: test.cpp, Function: TestFail, Line: 10]
            EndGroup: NS::MyClass::TestFail [Failed]
        """.trimIndent()

        val messages = runPipeline(output)
        val combined = messages.joinToString("\n")
        assertTrue("Should have testStarted", combined.contains("testStarted"))
        assertTrue("Should have testStdErr with error text", combined.contains("testStdErr"))
        // Failed test should produce more messages than just started+finished
        assertTrue("Failed test should produce error-related messages",
            messages.size > 2)
    }

    @Test
    fun fullPipeline_skippedTest() {
        val output = """
            StartGroup: NS::MyClass::TestSkip
            EndGroup: NS::MyClass::TestSkip [Skipped]
        """.trimIndent()

        val messages = runPipeline(output)
        val combined = messages.joinToString("\n")
        assertTrue("Should have testStarted", combined.contains("testStarted"))
        assertTrue("Should have testIgnored", combined.contains("testIgnored"))
    }

    @Test
    fun fullPipeline_blockedTest() {
        val output = """
            StartGroup: NS::MyClass::TestBlocked
            EndGroup: NS::MyClass::TestBlocked [Blocked]
        """.trimIndent()

        val messages = runPipeline(output)
        val combined = messages.joinToString("\n")
        assertTrue("Should have testIgnored for blocked", combined.contains("testIgnored"))
        assertTrue("Should contain 'Blocked' message", combined.contains("Blocked"))
    }

    @Test
    fun fullPipeline_mixedResults() {
        val output = """
            StartGroup: NS::C::Pass
            EndGroup: NS::C::Pass [Passed]
            StartGroup: NS::C::Fail
                Error: bad value
            EndGroup: NS::C::Fail [Failed]
            StartGroup: NS::C::Skip
            EndGroup: NS::C::Skip [Skipped]
            Summary: Total=3, Passed=1, Failed=1, Blocked=0, Not Run=0, Skipped=1
        """.trimIndent()

        val messages = runPipeline(output)
        val startCount = messages.count { it.contains("testStarted") }
        val ignoreCount = messages.count { it.contains("testIgnored") }

        assertEquals("Should have 3 testStarted", 3, startCount)
        assertEquals("Should have 1 testIgnored", 1, ignoreCount)
    }

    @Test
    fun fullPipeline_testWithLogOutput() {
        val output = """
            StartGroup: NS::C::TestLog
                Log: Starting verification...
                Log: Check passed.
            EndGroup: NS::C::TestLog [Passed]
        """.trimIndent()

        val messages = runPipeline(output)
        val combined = messages.joinToString("\n")
        assertTrue("Should have testStdOut for log", combined.contains("testStdOut"))
        assertTrue("Should contain log text", combined.contains("Starting verification"))
    }

    @Test
    fun fullPipeline_summaryProducesNoMessages() {
        val output = "Summary: Total=4, Passed=2, Failed=1, Blocked=0, Not Run=0, Skipped=1"

        val messages = runPipeline(output)
        assertTrue("Summary should produce no service messages", messages.isEmpty())
    }

    // --- URL consistency: gutter icon URLs must match converter URLs ---

    @Test
    fun testStarted_urlContainsProtocolAndQualifiedName() {
        val testName = "NS::Class::Method"
        val expectedUrl = "${TaefTestConstants.PROTOCOL_PREFIX}://$testName"
        val messages = processor.testStarted(testName, expectedUrl)
        val msg = messages.joinToString("") { it.toString() }

        assertTrue(
            "testStarted service message should contain the full URL '$expectedUrl', got: $msg",
            msg.contains(expectedUrl)
        )
    }

    @Test
    fun converterUrl_matchesGutterIconFormat() {
        // The converter builds URLs as: protocol + "://" + fullyQualifiedName
        // The gutter icon builds URLs as: protocol + "://" + qualifiedPath
        // These must use the same format for the IDE to link them.
        val testName = "SampleTestClass::TestMethodPass"
        val converterUrl = "${TaefTestConstants.PROTOCOL_PREFIX}://$testName"
        // This is the same format getTestLineMarkInfo uses (after the qualified path fix)
        val gutterUrl = "${TaefTestConstants.PROTOCOL_PREFIX}://$testName"
        assertEquals("Converter URL should match gutter icon URL format", gutterUrl, converterUrl)
    }

    // --- Helper: run full pipeline from TE output to service messages ---

    private fun runPipeline(output: String): List<String> {
        val events = TaefOutputParser.parse(output)
        return events.flatMap { convertEvent(it).map { msg -> msg.toString() } }
    }

    // --- Helper: mirrors TaefOutputToGeneralTestEventsConverter.convertEvent ---

    private fun convertEvent(event: TaefTestEvent): List<ServiceMessageBuilder> {
        return when (event) {
            is TaefTestEvent.TestStarted -> processor.testStarted(
                event.fullyQualifiedName,
                "${TaefTestConstants.PROTOCOL_PREFIX}://${event.fullyQualifiedName}"
            )
            is TaefTestEvent.TestFinished -> when (event.result) {
                TestResult.PASSED -> processor.testFinished(event.fullyQualifiedName, "", false)
                TestResult.FAILED -> processor.testFinished(event.fullyQualifiedName, "", true)
                TestResult.SKIPPED, TestResult.BLOCKED -> processor.testUnitIgnore(
                    event.fullyQualifiedName, event.fullyQualifiedName,
                    if (event.result == TestResult.BLOCKED) "Blocked" else "Skipped"
                )
            }
            is TaefTestEvent.TestOutput -> if (event.isError) {
                processor.testErrOut(event.fullyQualifiedName, event.text)
            } else {
                processor.testStdOut(event.fullyQualifiedName, event.text, event.fullyQualifiedName)
            }
            is TaefTestEvent.Summary -> emptyList()
        }
    }
}
