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

    private val converter: TaefOutputToGeneralTestEventsConverter

    init {
        val props = TaefTestUtil.createConsoleProperties(project)
        converter = props.createTestEventsConverter(
            TaefTestConstants.PROTOCOL_PREFIX, props
        ) as TaefOutputToGeneralTestEventsConverter
    }

    fun processLine(line: String): Boolean =
        converter.processServiceMessages(line, ProcessOutputType.STDOUT, this)

    fun processLines(vararg lines: String) {
        for (line in lines) processLine(line)
    }

    /**
     * Verify received messages match expected. The lambda builds an expected
     * Verify received messages match expected. Expected messages are constructed
     * from ServiceMessage classes, converted to (messageName, attributes) pairs.
     */
    fun verify(block: Verifier.() -> Unit) {
        val verifier = Verifier()
        verifier.block()
        assertEquals(verifier.expected, received.map { it.toPair() })
    }

    class Verifier {
        val expected = mutableListOf<Pair<String, Map<String, String>>>()

        fun testStarted(name: String) {
            expected.add(TestStarted(name, false, "${TaefTestConstants.PROTOCOL_PREFIX}://$name").toPair())
        }

        fun testFinishedPass(name: String) {
            expected.add(TestFinished(name, 0).toPair())
        }

        fun testFinishedFail(name: String) {
            expected.add(TestFailed(name, "").toPair())
            expected.add(TestFinished(name, 0).toPair())
        }

        fun testIgnored(name: String, reason: String) {
            expected.add(TestIgnored(name, reason).toPairWith("nodeId" to name))
        }

        fun testStdOut(name: String, text: String) {
            expected.add(TestStdOut(name, text).toPairWith("nodeId" to name))
        }

        fun testStdErr(name: String, text: String) {
            expected.add(TestStdErr(name, text).toPair())
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
