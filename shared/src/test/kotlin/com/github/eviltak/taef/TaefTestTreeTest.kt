package com.github.eviltak.taef

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests that verify the actual SMTestProxy tree structure built from
 * TE.exe output. Feeds output through the converter → processor → proxy tree,
 * then asserts on node names, parenting, result states, and output.
 */
class TaefTestTreeTest : BasePlatformTestCase() {

    private fun buildTree(vararg lines: String): SMTestProxy.SMRootTestProxy {
        val root = SMTestProxy.SMRootTestProxy()
        val processor = GeneralToSMTRunnerEventsConvertor(project, root, TaefTestConstants.PROTOCOL_PREFIX)
        val converter = TaefTestUtil.createConverter(project)
        converter.setProcessor(processor)
        converter.startTesting()

        for (line in lines) {
            converter.process("$line\n", ProcessOutputTypes.STDOUT)
        }
        converter.process("Summary: Total=0, Passed=0, Failed=0, Blocked=0, Not Run=0, Skipped=0\n", ProcessOutputTypes.STDOUT)
        processor.onFinishTesting()
        return root
    }

    // --- Tree structure ---

    fun testSingleTestTreeStructure() {
        val root = buildTree(
            "StartGroup: SampleTestClass::TestMethodPass",
            "EndGroup: SampleTestClass::TestMethodPass [Passed]"
        )
        assertEquals("Root should have 1 suite child", 1, root.children.size)

        val suite = root.children[0]
        assertEquals("SampleTestClass", suite.name)
        assertTrue("Suite node should be marked as suite", suite.isSuite)
        assertEquals("Suite should have 1 test child", 1, suite.children.size)

        val test = suite.children[0]
        assertEquals("TestMethodPass", test.name)
        assertFalse("Test node should not be suite", test.isSuite)
        assertTrue("Test should be leaf", test.isLeaf)
    }

    fun testMultipleTestsSameSuite() {
        val root = buildTree(
            "StartGroup: C::Test1",
            "EndGroup: C::Test1 [Passed]",
            "StartGroup: C::Test2",
            "EndGroup: C::Test2 [Failed]"
        )
        assertEquals(1, root.children.size)
        val suite = root.children[0]
        assertEquals("C", suite.name)
        assertEquals("Suite should have 2 test children", 2, suite.children.size)
        assertEquals("Test1", suite.children[0].name)
        assertEquals("Test2", suite.children[1].name)
    }

    fun testSuiteTransitionCreatesSeparateBranches() {
        val root = buildTree(
            "StartGroup: A::Test1",
            "EndGroup: A::Test1 [Passed]",
            "StartGroup: B::Test2",
            "EndGroup: B::Test2 [Passed]"
        )
        assertEquals("Root should have 2 suite children", 2, root.children.size)
        assertEquals("A", root.children[0].name)
        assertEquals("B", root.children[1].name)
        assertEquals(1, root.children[0].children.size)
        assertEquals(1, root.children[1].children.size)
    }

    fun testSuiteReopeningAfterTransition() {
        val root = buildTree(
            "StartGroup: A::Test1",
            "EndGroup: A::Test1 [Passed]",
            "StartGroup: B::Test2",
            "EndGroup: B::Test2 [Passed]",
            "StartGroup: A::Test3",
            "EndGroup: A::Test3 [Passed]"
        )
        // A is closed when B starts, then reopened — should create a second A node
        val suiteNames = root.children.map { it.name }
        assertEquals(listOf("A", "B", "A"), suiteNames)
    }

    fun testDataDrivenTestsGroupedUnderSuite() {
        val root = buildTree(
            "StartGroup: DataClass::Test#0",
            "EndGroup: DataClass::Test#0 [Passed]",
            "StartGroup: DataClass::Test#1",
            "EndGroup: DataClass::Test#1 [Passed]",
            "StartGroup: DataClass::Test#2",
            "EndGroup: DataClass::Test#2 [Failed]"
        )
        assertEquals(1, root.children.size)
        val suite = root.children[0]
        assertEquals("DataClass", suite.name)
        assertEquals(3, suite.children.size)
        assertEquals("Test#0", suite.children[0].name)
        assertEquals("Test#1", suite.children[1].name)
        assertEquals("Test#2", suite.children[2].name)
    }

    fun testNamespacedSuitePreserved() {
        val root = buildTree(
            "StartGroup: NS::Class::Method",
            "EndGroup: NS::Class::Method [Passed]"
        )
        assertEquals(1, root.children.size)
        assertEquals("NS::Class", root.children[0].name)
        assertEquals("Method", root.children[0].children[0].name)
    }

    // --- Test results ---

    fun testPassedTestIsFinal() {
        val root = buildTree(
            "StartGroup: C::Test",
            "EndGroup: C::Test [Passed]"
        )
        assertTrue("Passed test should be final", root.children[0].children[0].isFinal)
    }

    fun testFailedTestIsFinal() {
        val root = buildTree(
            "StartGroup: C::Test",
            "Error: something broke",
            "EndGroup: C::Test [Failed]"
        )
        assertTrue("Failed test should be final", root.children[0].children[0].isFinal)
    }

    fun testSkippedTestIsFinal() {
        val root = buildTree(
            "StartGroup: C::Test",
            "EndGroup: C::Test [Skipped]"
        )
        val test = root.children[0].children[0]
        assertTrue("Skipped test should be final", test.isFinal)
        assertTrue("Skipped test should be ignored", test.isIgnored)
    }

    fun testBlockedTestIsFinal() {
        val root = buildTree(
            "StartGroup: C::Test",
            "EndGroup: C::Test [Blocked]"
        )
        val test = root.children[0].children[0]
        assertTrue("Blocked test should be final", test.isFinal)
        assertTrue("Blocked test should be ignored", test.isIgnored)
    }

    // --- Node parenting ---

    fun testTestParentIsSuite() {
        val root = buildTree(
            "StartGroup: MySuite::MyTest",
            "EndGroup: MySuite::MyTest [Passed]"
        )
        val suite = root.children[0]
        val test = suite.children[0]
        assertSame("Test's parent should be the suite", suite, test.parent)
    }

    fun testSuiteParentIsRoot() {
        val root = buildTree(
            "StartGroup: MySuite::MyTest",
            "EndGroup: MySuite::MyTest [Passed]"
        )
        assertSame("Suite's parent should be root", root, root.children[0].parent)
    }

    // --- Empty / edge cases ---

    fun testEmptyOutputProducesEmptyTree() {
        val root = buildTree()
        assertEquals(0, root.children.size)
    }

    fun testOrphanEndGroupProducesEmptyTree() {
        val root = buildTree("EndGroup: C::Test [Passed]")
        assertEquals(0, root.children.size)
    }
}
