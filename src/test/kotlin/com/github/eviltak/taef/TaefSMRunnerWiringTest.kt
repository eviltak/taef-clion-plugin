package com.github.eviltak.taef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor

/**
 * Platform tests for SMRunner wiring: console properties, converter,
 * locator, and the full line-to-service-message chain.
 *
 * Converter tests use [ConverterTestHarness] to feed TE.exe output lines
 * through the real converter and compare received service messages against
 * what [CidrTestEventProcessor] would produce for the same events.
 */
class TaefSMRunnerWiringTest : BasePlatformTestCase() {

    // --- Console properties wiring ---

    fun testConsolePropertiesCreatesConverter() {
        val props = TaefTestUtil.createConsoleProperties(project)
        val converter = props.createTestEventsConverter(TaefTestConstants.PROTOCOL_PREFIX, props)
        assertInstanceOf(converter, TaefOutputToGeneralTestEventsConverter::class.java)
    }

    fun testConsolePropertiesHasLocator() {
        val props = TaefTestUtil.createConsoleProperties(project)
        assertInstanceOf(props.testLocator, TaefTestLocator::class.java)
    }

    // --- CidrTestEventProcessor produces messages for all event types ---

    fun testServiceMessagesForAllEventTypes() {
        val p = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
        assertTrue("testStarted", p.testStarted("C::M", "taef://C::M").isNotEmpty())
        assertTrue("testFinished(pass)", p.testFinished("C::M", "", false).isNotEmpty())
        assertTrue("testFinished(fail)", p.testFinished("C::M", "", true).isNotEmpty())
        assertTrue("testIgnored",
            p.testUnitIgnore("C::M", "C::M", "Skipped").joinToString { it.toString() }.contains("testIgnored"))
        assertTrue("testStdErr",
            p.testErrOut("C::M", "error").joinToString { it.toString() }.contains("testStdErr"))
        assertTrue("testStdOut",
            p.testStdOut("C::M", "log", "C::M").joinToString { it.toString() }.contains("testStdOut"))
    }

    // --- URLs in testStarted match taef:// format ---

    fun testServiceMessageUrlFormat() {
        val p = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
        for (name in listOf(
            "SampleTestClass::TestMethodPass",
            "TestNamespace::NamespacedTestClass::TestInNamespace",
            "DataDrivenClass::TestAddition#0",
        )) {
            val url = "${TaefTestConstants.PROTOCOL_PREFIX}://$name"
            val text = p.testStarted(name, url).joinToString("") { it.toString() }
            assertTrue("URL '$url' should appear in message", text.contains(url))
        }
    }

    // --- Converter: result types ---

    fun testConverterPassingTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Passed]")
        h.verify {
            testStarted("C::Test")
            testFinishedPass("C::Test")
        }
    }

    fun testConverterFailingTest() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: C::Test",
            "Error: Verify failed",
            "EndGroup: C::Test [Failed]"
        )
        h.verify {
            testStarted("C::Test")
            testStdErr("C::Test", "Verify failed")
            testFinishedFail("C::Test")
        }
    }

    fun testConverterSkippedTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Skipped]")
        h.verify {
            testStarted("C::Test")
            testIgnored("C::Test", "Skipped")
        }
    }

    fun testConverterBlockedTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Blocked]")
        h.verify {
            testStarted("C::Test")
            testIgnored("C::Test", "Blocked")
        }
    }

    // --- Converter: stdout line types ---

    fun testConverterCapturesAllStdoutLineTypes() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: C::Test",
            "Comment text here.",
            "Verify: AreEqual(42, 42)",
            "Warning: something",
            "TAEF: Data[Value]: 1",
            "EndGroup: C::Test [Passed]"
        )
        h.verify {
            testStarted("C::Test")
            testStdOut("C::Test", "Comment text here.")
            testStdOut("C::Test", "Verify: AreEqual(42, 42)")
            testStdOut("C::Test", "Warning: something")
            testStdOut("C::Test", "TAEF: Data[Value]: 1")
            testFinishedPass("C::Test")
        }
    }

    // --- Converter: non-test lines ---

    fun testConverterReturnsFalseForNonTestLines() {
        val h = ConverterTestHarness(project)
        assertFalse(h.processLine("Test Authoring and Execution Framework v10.99k"))
        assertFalse(h.processLine(""))
        assertFalse(h.processLine("SampleTestClass::ClassSetup - initializing."))
        h.verify { /* no messages expected */ }
    }

    // --- Converter: full chain with mixed output ---

    fun testConverterFullFailingTestWithAllOutputTypes() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: C::Test",
            "About to fail.",
            "Warning: resource high.",
            "Error: Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Line: 10]",
            "EndGroup: C::Test [Failed]"
        )
        h.verify {
            testStarted("C::Test")
            testStdOut("C::Test", "About to fail.")
            testStdOut("C::Test", "Warning: resource high.")
            testStdErr("C::Test", "Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Line: 10]")
            testFinishedFail("C::Test")
        }
    }

    // --- Converter: edge cases ---

    fun testConverterOrphanEndGroupIgnored() {
        val h = ConverterTestHarness(project)
        h.processLines("EndGroup: C::Test [Passed]")
        h.verify { /* no messages — orphan EndGroup is ignored */ }
    }

    fun testConverterMultipleTestsSequential() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: A::Test1",
            "EndGroup: A::Test1 [Passed]",
            "StartGroup: A::Test2",
            "Error: fail",
            "EndGroup: A::Test2 [Failed]"
        )
        h.verify {
            testStarted("A::Test1")
            testFinishedPass("A::Test1")
            testStarted("A::Test2")
            testStdErr("A::Test2", "fail")
            testFinishedFail("A::Test2")
        }
    }

    fun testConverterSetupAndCleanupLinesIgnored() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "ModuleSetup - initializing module.",
            "SampleTestClass::ClassSetup - initializing class.",
            "SampleTestClass::MethodSetup - preparing test.",
            "StartGroup: C::Test",
            "EndGroup: C::Test [Passed]",
            "SampleTestClass::MethodCleanup - cleaning up test.",
            "SampleTestClass::ClassCleanup - tearing down class."
        )
        h.verify {
            testStarted("C::Test")
            testFinishedPass("C::Test")
        }
    }

    fun testConverterDataDrivenTestNames() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: DataClass::Test#0",
            "TAEF: Data[Value]: 1",
            "Verify: AreEqual(1, 1)",
            "EndGroup: DataClass::Test#0 [Passed]",
            "StartGroup: DataClass::Test#1",
            "TAEF: Data[Value]: 99",
            "Error: Verify: AreEqual(99, 1)",
            "EndGroup: DataClass::Test#1 [Failed]"
        )
        h.verify {
            testStarted("DataClass::Test#0")
            testStdOut("DataClass::Test#0", "TAEF: Data[Value]: 1")
            testStdOut("DataClass::Test#0", "Verify: AreEqual(1, 1)")
            testFinishedPass("DataClass::Test#0")
            testStarted("DataClass::Test#1")
            testStdOut("DataClass::Test#1", "TAEF: Data[Value]: 99")
            testStdErr("DataClass::Test#1", "Verify: AreEqual(99, 1)")
            testFinishedFail("DataClass::Test#1")
        }
    }
}
