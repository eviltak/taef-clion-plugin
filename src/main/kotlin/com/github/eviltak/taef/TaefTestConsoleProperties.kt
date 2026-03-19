package com.github.eviltak.taef

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.jetbrains.cidr.execution.testing.CidrAbstractTestConsoleProperties
import java.util.regex.Pattern

/**
 * SMRunner console properties for TAEF test runs.
 *
 * Creates the output-to-events converter that parses TE.exe output
 * into structured test tree events.
 */
class TaefTestConsoleProperties(
    config: RunConfiguration,
    executor: Executor,
    target: ExecutionTarget
) : CidrAbstractTestConsoleProperties(config, TaefTestConstants.PROTOCOL_PREFIX, executor, target) {

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties
    ): OutputToGeneralTestEventsConverter =
        TaefOutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties)

    override fun getTestLocator(): SMTestLocator = TaefTestLocator.INSTANCE

    override fun getAssertionPattern(): Pattern =
        Pattern.compile("\\bVERIFY_\\w+\\b")
}
