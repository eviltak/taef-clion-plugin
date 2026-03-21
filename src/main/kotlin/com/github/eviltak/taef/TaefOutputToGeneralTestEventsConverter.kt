package com.github.eviltak.taef

import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor
import com.github.eviltak.taef.output.TaefStreamParser
import com.github.eviltak.taef.output.TaefTestEvent
import com.github.eviltak.taef.output.TaefTestId
import com.github.eviltak.taef.output.TestResult

/**
 * Converts TE.exe console output into SMRunner ##teamcity service messages.
 *
 * Called per-line by the SMRunner framework. Maintains a [TaefStreamParser]
 * instance for state across calls (current test, accumulated errors).
 */
class TaefOutputToGeneralTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

    private val eventProcessor = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
    private val streamParser = TaefStreamParser()

    public override fun processServiceMessages(text: String, outputType: Key<*>, visitor: jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor): Boolean {
        val events = streamParser.feedLine(text.trimEnd())
        if (events.isEmpty()) return false

        for (event in events) {
            for (msg in convertEvent(event)) {
                super.processServiceMessages(msg.toString(), outputType, visitor)
            }
        }
        return true
    }

    private fun convertEvent(event: TaefTestEvent): List<ServiceMessageBuilder> = when (event) {
        is TaefTestEvent.TestStarted -> fqn(event.id).let { name ->
            eventProcessor.testStarted(name, name)
        }

        is TaefTestEvent.TestFinished -> fqn(event.id).let { name ->
            when (event.result) {
                TestResult.PASSED -> eventProcessor.testFinished(name, "0", true)
                TestResult.FAILED -> eventProcessor.testFinished(name, "0", false)
                TestResult.SKIPPED, TestResult.BLOCKED -> eventProcessor.testUnitIgnore(
                    name, name,
                    if (event.result == TestResult.BLOCKED) "Blocked" else "Skipped"
                )
            }
        }

        is TaefTestEvent.TestOutput -> fqn(event.id).let { name ->
            if (event.isError) {
                eventProcessor.testErrOut(name, event.text)
            } else {
                eventProcessor.testStdOut(name, name, event.text)
            }
        }

        is TaefTestEvent.Summary -> emptyList()
    }

    private fun fqn(id: TaefTestId): String =
        if (id.suiteName.isEmpty()) id.testName else "${id.suiteName}::${id.testName}"
}
