package com.github.eviltak.taef

import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor

/**
 * Converts TE.exe console output into SMRunner ##teamcity service messages
 * for the test results tree.
 *
 * Delegates parsing to [TaefOutputParser] (the existing pure state machine),
 * then translates [TaefTestEvent]s into service messages via
 * [CidrTestEventProcessor].
 */
class TaefOutputToGeneralTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

    private val eventProcessor = CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX)
    private var currentTest: String? = null
    private var myProcessor: GeneralTestEventsProcessor? = null

    override fun setProcessor(processor: GeneralTestEventsProcessor?) {
        super.setProcessor(processor)
        myProcessor = processor
    }

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor): Boolean {
        val events = TaefOutputParser.parse(text.trimEnd())
        if (events.isEmpty()) return false

        for (event in events) {
            val messages = convertEvent(event)
            for (msg in messages) {
                // Build the ##teamcity service message string and pass to visitor
                super.processServiceMessages(msg.toString(), outputType, visitor)
            }
        }
        return true
    }

    private fun convertEvent(event: TaefTestEvent): List<ServiceMessageBuilder> {
        return when (event) {
            is TaefTestEvent.TestStarted -> {
                currentTest = event.fullyQualifiedName
                eventProcessor.testStarted(
                    event.fullyQualifiedName,
                    "${TaefTestConstants.PROTOCOL_PREFIX}://${event.fullyQualifiedName}"
                )
            }

            is TaefTestEvent.TestFinished -> {
                val result = when (event.result) {
                    TestResult.PASSED -> eventProcessor.testFinished(
                        event.fullyQualifiedName, "", false
                    )
                    TestResult.FAILED -> eventProcessor.testFinished(
                        event.fullyQualifiedName, "", true
                    )
                    TestResult.SKIPPED, TestResult.BLOCKED -> eventProcessor.testUnitIgnore(
                        event.fullyQualifiedName,
                        event.fullyQualifiedName,
                        if (event.result == TestResult.BLOCKED) "Blocked" else "Skipped"
                    )
                }
                currentTest = null
                result
            }

            is TaefTestEvent.TestOutput -> {
                if (event.isError) {
                    eventProcessor.testErrOut(event.fullyQualifiedName, event.text)
                } else {
                    eventProcessor.testStdOut(
                        event.fullyQualifiedName, event.text,
                        event.fullyQualifiedName
                    )
                }
            }

            is TaefTestEvent.Summary -> emptyList()
        }
    }
}
