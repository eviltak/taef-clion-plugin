package com.github.eviltak.taef

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.jetbrains.cidr.execution.testing.CidrAbstractTestConsoleProperties
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor
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
        TaefOutputToGeneralTestEventsConverter(
            testFrameworkName,
            consoleProperties,
            CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX),
            console
        )

    override fun getTestLocator(): SMTestLocator = TaefTestLocator.INSTANCE

    override fun getAssertionPattern(): Pattern = SOURCE_LINK_PATTERN

    companion object {
        /**
         * Matches TAEF error source references: `[File: path, Function: name, Line: N]`
         * or `[File: path, Line: N]`. Group 1 = file path, Group 2 = line number.
         * Used by [CidrAbstractTestConsoleProperties] to create clickable source links.
         */
        internal val SOURCE_LINK_PATTERN: Pattern = Pattern.compile(
            """\[File:\s*([^,\]]+)(?:,\s*Function:\s*[^,]+)?,\s*Line:\s*(\d+)]"""
        )
    }
}
