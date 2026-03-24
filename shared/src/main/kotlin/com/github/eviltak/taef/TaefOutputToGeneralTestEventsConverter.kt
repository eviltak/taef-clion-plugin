package com.github.eviltak.taef

import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.execution.testing.CidrFromTagInLineToGeneralTestEventsConverter
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor
import com.github.eviltak.taef.output.TaefStreamParser
import com.github.eviltak.taef.output.TaefTestEvent
import com.github.eviltak.taef.output.TestResult as TaefTestResult

/**
 * Converts TE.exe console output into SMRunner test tree events.
 *
 * Extends [CidrFromTagInLineToGeneralTestEventsConverter] which provides:
 * - Node ID tracking via [myTestNameStack] / [getCurrentNodeId] / [getLocationFromId]
 * - [process] to feed service messages through the SMRunner pipeline
 * - Process-finished message buffering and deferred output
 *
 * Suite lifecycle is tracked via [currentSuiteName]. On each test start,
 * if the suite changed, the old suite is closed and a new one opened.
 * The last open suite is closed on Summary or process termination.
 *
 * Test tree structure: root → suite (class) → test (method).
 * Node IDs: `"0/SuiteName/TestName"`. Locations: `"SuiteName/TestName"`.
 */
class TaefOutputToGeneralTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
    processor: CidrTestEventProcessor,
    console: ExecutionConsole
) : CidrFromTagInLineToGeneralTestEventsConverter(
    testFrameworkName, consoleProperties, processor, console
) {
    private val streamParser = TaefStreamParser()
    private var currentSuiteName: String? = null
    private var frameworkAttached = false

    override fun processLine(outputType: Key<*>, text: String) {
        when (val events = streamParser.feedLine(text.trimEnd())) {
            emptyList<TaefTestEvent>() -> processor?.onUncapturedOutput(text, outputType)
            else -> events.forEach { processEvent(it) }
        }
    }

    internal fun processOutput(text: String, outputType: Key<*>, visitor: jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor): Boolean =
        processServiceMessages(text, outputType, visitor)

    private fun processEvent(event: TaefTestEvent) {
        when (event) {
            is TaefTestEvent.TestStarted -> {
                ensureFrameworkAttached()
                handleSuiteTransition(event.id.suiteName)
                openTest(event.id.testName)
            }
            is TaefTestEvent.TestFinished -> closeTest(event.id.testName, event.result)
            is TaefTestEvent.TestOutput -> emitOutput(event)
            is TaefTestEvent.Summary -> closeSuiteIfOpen()
        }
    }

    private fun ensureFrameworkAttached() {
        if (!frameworkAttached) {
            processor?.onTestsReporterAttached()
            frameworkAttached = true
        }
    }

    private fun handleSuiteTransition(suiteName: String) {
        if (suiteName != currentSuiteName) {
            closeSuiteIfOpen()
            openSuite(suiteName)
        }
    }

    private fun openSuite(name: String) {
        val parentId = currentNodeId
        myTestNameStack.push(name)
        myTestResultStack.push(TestResult.create(true, 0))
        process(myEventProcessor.suiteStarted(name, parentId, currentNodeId, locationFromId, null))
        currentSuiteName = name
    }

    private fun closeSuiteIfOpen() {
        if (currentSuiteName != null) {
            val nodeId = currentNodeId
            if (myTestNameStack.isNotEmpty()) {
                val result = myTestResultStack.pop()
                myTestNameStack.pop()
                if (myTestResultStack.isNotEmpty()) {
                    myTestResultStack.peek().update(result.success, result.durationInMs.toLong())
                }
            }
            process(myEventProcessor.suiteFinished(currentSuiteName!!, nodeId))
            currentSuiteName = null
        }
    }

    private fun openTest(testName: String) {
        val parentId = currentNodeId
        myTestNameStack.push(testName)
        myTestResultStack.push(TestResult.create(true, 0))
        process(myEventProcessor.testStarted(testName, parentId, currentNodeId, locationFromId, null))
    }

    private fun closeTest(testName: String, result: TaefTestResult) {
        val nodeId = currentNodeId
        val success = result == TaefTestResult.PASSED
        val ignored = result == TaefTestResult.SKIPPED || result == TaefTestResult.BLOCKED

        if (myTestNameStack.isNotEmpty()) {
            val testResult = myTestResultStack.pop()
            myTestNameStack.pop()
            if (myTestResultStack.isNotEmpty()) {
                myTestResultStack.peek().update(success, testResult.durationInMs.toLong())
            }
        }

        process(
            if (ignored) {
                val reason = if (result == TaefTestResult.BLOCKED) "Blocked" else "Skipped"
                myEventProcessor.testFinished(testName, nodeId, "0", reason, true, true)
            } else {
                myEventProcessor.testFinished(testName, nodeId, "0", success)
            }
        )
    }

    private fun emitOutput(event: TaefTestEvent.TestOutput) {
        val nodeId = currentNodeId
        val text = event.text + "\n"
        process(
            if (event.isError) {
                myEventProcessor.testErrOut(event.id.testName, nodeId, text)
            } else {
                myEventProcessor.testStdOut(event.id.testName, nodeId, text)
            }
        )
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        closeSuiteIfOpen()
        super.flushBufferOnProcessTermination(exitCode)
    }
}
