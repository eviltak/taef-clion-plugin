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

    // --- CidrTestEventProcessor produces messages for all event types ---

    fun testServiceMessagesForAllEventTypes() {
        val p = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
        assertTrue("testStarted", p.testStarted("M", "taef://C/M").isNotEmpty())
        assertTrue("testFinished(pass)", p.testFinished("M", "", false).isNotEmpty())
        assertTrue("testFinished(fail)", p.testFinished("M", "", true).isNotEmpty())
        assertTrue("testIgnored",
            p.testUnitIgnore("M", "0/C/M", "Skipped").joinToString { it.toString() }.contains("testIgnored"))
        assertTrue("testStdErr",
            p.testErrOut("M", "0/C/M", "error").joinToString { it.toString() }.contains("testStdErr"))
        assertTrue("testStdOut",
            p.testStdOut("M", "0/C/M", "log").joinToString { it.toString() }.contains("testStdOut"))
    }

    // --- Converter: result types ---

    fun testConverterPassingTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Passed]")
        h.verify {
            suite("C") {
                test("Test") {}
            }
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
            suite("C") {
                test("Test", fail()) { stderr("Verify failed") }
            }
        }
    }

    fun testConverterSkippedTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Skipped]")
        h.verify {
            suite("C") {
                test("Test", ignored("Skipped")) {}
            }
        }
    }

    fun testConverterBlockedTest() {
        val h = ConverterTestHarness(project)
        h.processLines("StartGroup: C::Test", "EndGroup: C::Test [Blocked]")
        h.verify {
            suite("C") {
                test("Test", ignored("Blocked")) {}
            }
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
            suite("C") {
                test("Test") {
                    stdout("Comment text here.")
                    stdout("Verify: AreEqual(42, 42)")
                    stdout("Warning: something")
                    stdout("TAEF: Data[Value]: 1")
                }
            }
        }
    }

    // --- Converter: non-test lines return true (all stdout handled) ---

    fun testConverterReturnsTrueForNonTestLines() {
        val h = ConverterTestHarness(project)
        assertTrue(h.processLine("Test Authoring and Execution Framework v10.99k"))
        assertTrue(h.processLine(""))
        assertTrue(h.processLine("SampleTestClass::ClassSetup - initializing."))
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
            suite("C") {
                test("Test", fail()) {
                    stdout("About to fail.")
                    stdout("Warning: resource high.")
                    stderr("Verify: AreEqual(42, 0) - Values (42, 0) [File: test.cpp, Line: 10]")
                }
            }
        }
    }

    // --- Converter: edge cases ---

    fun testConverterOrphanEndGroupIgnored() {
        val h = ConverterTestHarness(project)
        h.processLines("EndGroup: C::Test [Passed]")
        h.verify { /* no messages — orphan EndGroup is ignored */ }
    }

    fun testConverterMultipleTestsSameSuite() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: A::Test1",
            "EndGroup: A::Test1 [Passed]",
            "StartGroup: A::Test2",
            "Error: fail",
            "EndGroup: A::Test2 [Failed]"
        )
        h.verify {
            suite("A") {
                test("Test1") {}
                test("Test2", fail()) { stderr("fail") }
            }
        }
    }

    fun testConverterSuiteTransition() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: A::Test1",
            "EndGroup: A::Test1 [Passed]",
            "StartGroup: B::Test2",
            "EndGroup: B::Test2 [Passed]"
        )
        h.verify {
            suite("A") { test("Test1") {} }
            suite("B") { test("Test2") {} }
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
            suite("C") { test("Test") {} }
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
            suite("DataClass") {
                test("Test#0") {
                    stdout("TAEF: Data[Value]: 1")
                    stdout("Verify: AreEqual(1, 1)")
                }
                test("Test#1", fail()) {
                    stdout("TAEF: Data[Value]: 99")
                    stderr("Verify: AreEqual(99, 1)")
                }
            }
        }
    }

    fun testConverterNamespacedSuite() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "StartGroup: NS::Class::Method",
            "EndGroup: NS::Class::Method [Passed]"
        )
        h.verify {
            suite("NS::Class") { test("Method") {} }
        }
    }

    fun testConverterBlockedTestWithContext() {
        val h = ConverterTestHarness(project)
        h.processLines(
            "TestBlocked: TAEF: Setup fixture 'Blocked::Setup' returned 'false'.",
            "StartGroup: Blocked::Test",
            "EndGroup: Blocked::Test [Blocked]"
        )
        h.verify {
            suite("Blocked") {
                test("Test", ignored("Blocked")) {
                    stderr("TAEF: Setup fixture 'Blocked::Setup' returned 'false'.")
                }
            }
        }
    }
}
