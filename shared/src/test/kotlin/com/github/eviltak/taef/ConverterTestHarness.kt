package com.github.eviltak.taef

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.project.Project
import jetbrains.buildServer.messages.serviceMessages.*
import org.junit.Assert.assertEquals

/**
 * Test harness that wraps [TaefOutputToGeneralTestEventsConverter] with a
 * recording [ServiceMessageVisitor]. Feed lines in, then verify received
 * messages using semantic comparison (messageName + attributes).
 */
class ConverterTestHarness(project: Project) : ServiceMessageVisitor {
    val received = mutableListOf<ServiceMessage>()

    private val converter: TaefOutputToGeneralTestEventsConverter = TaefTestUtil.createConverter(project)

    fun processLine(line: String): Boolean =
        converter.processOutput(line, ProcessOutputType.STDOUT, this)

    fun processLines(vararg lines: String) {
        for (line in lines) processLine(line)
    }

    /**
     * Flush any open suite by feeding a summary line, then verify received
     * messages match the expected sequence built by the nested DSL.
     */
    fun verify(block: Verifier.() -> Unit) {
        processLine("Summary: Total=0, Passed=0, Failed=0, Blocked=0, Not Run=0, Skipped=0")
        val verifier = Verifier()
        verifier.block()
        assertEquals(verifier.expected, received.map { it.toPair() })
    }

    // --- Verifier DSL ---

    sealed class TestFinish {
        data object Pass : TestFinish()
        data object Fail : TestFinish()
        data class Ignored(val reason: String) : TestFinish()
    }

    class Verifier {
        val expected = mutableListOf<Pair<String, Map<String, String>>>()
        private val nodeStack = mutableListOf<String>()

        private fun currentNodeId() = "0" + nodeStack.joinToString("") { "/$it" }
        private fun location() = nodeStack.joinToString("/")

        fun suite(name: String, block: SuiteScope.() -> Unit) {
            val parentId = currentNodeId()
            nodeStack.add(name)
            expected.add(
                TestSuiteStarted(name).toPairWith(
                    "locationHint" to "${TaefTestConstants.PROTOCOL_PREFIX}://${location()}",
                    "nodeId" to currentNodeId(),
                    "parentNodeId" to parentId
                )
            )
            SuiteScope().block()
            expected.add(TestSuiteFinished(name).toPairWith("nodeId" to currentNodeId()))
            nodeStack.removeLast()
        }

        inner class SuiteScope {
            fun pass() = TestFinish.Pass
            fun fail() = TestFinish.Fail
            fun ignored(reason: String) = TestFinish.Ignored(reason)

            fun test(name: String, finish: TestFinish = pass(), block: TestScope.() -> Unit = {}) {
                val parentId = currentNodeId()
                nodeStack.add(name)
                val nodeId = currentNodeId()
                expected.add(
                    TestStarted(name, false, "${TaefTestConstants.PROTOCOL_PREFIX}://${location()}").toPairWith(
                        "nodeId" to nodeId,
                        "parentNodeId" to parentId
                    )
                )
                TestScope(name).block()
                nodeStack.removeLast()
                when (finish) {
                    is TestFinish.Pass ->
                        expected.add(TestFinished(name, 0).toPairWith("nodeId" to nodeId))
                    is TestFinish.Fail -> {
                        expected.add(TestFailed(name, "").toPairWith("nodeId" to nodeId))
                        expected.add(TestFinished(name, 0).toPairWith("nodeId" to nodeId))
                    }
                    is TestFinish.Ignored -> {
                        expected.add(TestIgnored(name, finish.reason).toPairWith("nodeId" to nodeId))
                        expected.add(TestFinished(name, 0).toPairWith("nodeId" to nodeId))
                    }
                }
            }
        }

        inner class TestScope(private val testName: String) {
            fun stdout(text: String) {
                expected.add(TestStdOut(testName, text + "\n").toPairWith("nodeId" to currentNodeId()))
            }
            fun stderr(text: String) {
                expected.add(TestStdErr(testName, text + "\n").toPairWith("nodeId" to currentNodeId()))
            }
        }
    }

    companion object {
        private fun ServiceMessage.toPair(): Pair<String, Map<String, String>> =
            messageName to attributes

        private fun ServiceMessage.toPairWith(vararg extra: Pair<String, String>): Pair<String, Map<String, String>> =
            messageName to (attributes + extra)
    }

    override fun visitTestStarted(m: TestStarted) { received.add(m) }
    override fun visitTestFinished(m: TestFinished) { received.add(m) }
    override fun visitTestFailed(m: TestFailed) { received.add(m) }
    override fun visitTestIgnored(m: TestIgnored) { received.add(m) }
    override fun visitTestStdOut(m: TestStdOut) { received.add(m) }
    override fun visitTestStdErr(m: TestStdErr) { received.add(m) }
    override fun visitTestSuiteStarted(m: TestSuiteStarted) { received.add(m) }
    override fun visitTestSuiteFinished(m: TestSuiteFinished) { received.add(m) }
    override fun visitPublishArtifacts(m: PublishArtifacts) { received.add(m) }
    override fun visitProgressMessage(m: ProgressMessage) { received.add(m) }
    override fun visitProgressStart(m: ProgressStart) { received.add(m) }
    override fun visitProgressFinish(m: ProgressFinish) { received.add(m) }
    override fun visitBuildNumber(m: BuildNumber) { received.add(m) }
    override fun visitBuildStatus(m: BuildStatus) { received.add(m) }
    override fun visitBuildStatisticValue(m: BuildStatisticValue) { received.add(m) }
    override fun visitMessageWithStatus(m: Message) { received.add(m) }
    override fun visitServiceMessage(m: ServiceMessage) { received.add(m) }
    override fun visitBlockOpened(m: BlockOpened) { received.add(m) }
    override fun visitBlockClosed(m: BlockClosed) { received.add(m) }
    override fun visitCompilationStarted(m: CompilationStarted) { received.add(m) }
    override fun visitCompilationFinished(m: CompilationFinished) { received.add(m) }
}
