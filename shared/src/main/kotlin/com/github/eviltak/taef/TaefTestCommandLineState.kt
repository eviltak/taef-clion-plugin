package com.github.eviltak.taef

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.Function
import com.jetbrains.cidr.execution.CidrLauncher
import com.jetbrains.cidr.execution.testing.CidrRerunFailedTestsAction
import com.jetbrains.cidr.execution.testing.CidrRerunFailedTestsActionEx
import com.jetbrains.cidr.execution.testing.CidrTestCommandLineState
import com.jetbrains.cidr.execution.testing.CidrTestRunConfiguration
import com.jetbrains.cidr.execution.testing.CidrTestScope
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement
import com.jetbrains.cidr.execution.testing.CidrTestScopeElementImpl

/**
 * Command line state for TAEF test execution. Extends [CidrTestCommandLineState]
 * to get SMRunner console integration (test tree, rerun actions, etc.)
 * instead of a plain text console.
 */
class TaefTestCommandLineState(
    configuration: CidrTestRunConfiguration,
    launcher: CidrLauncher,
    failedTests: CidrTestScope?,
    environment: ExecutionEnvironment,
    executor: Executor
) : CidrTestCommandLineState<CidrTestRunConfiguration>(
    configuration, launcher, environment, executor, failedTests,
    { CidrTestScope.createEmptyTestScope(TaefTestConstants.PATTERN_SEPARATOR) }
) {
    override fun doCreateRerunFailedTestsAction(
        consoleView: SMTRunnerConsoleView
    ): CidrRerunFailedTestsAction =
        CidrRerunFailedTestsActionEx(consoleView, PROXY_TO_TEST_PATTERN, this)

    override fun createTestScopeElement(suite: String?, test: String?): CidrTestScopeElement {
        val pattern = if (!test.isNullOrEmpty()) "$suite::$test" else "$suite::*"
        return CidrTestScopeElementImpl.createTestScopeWithPatternAndConfigurationName(
            pattern, pattern, { null }
        )
    }

    companion object {
        private const val PROTOCOL_SCHEME = "${TaefTestConstants.PROTOCOL_PREFIX}://"

        /**
         * Extracts the TAEF test pattern from a failed test proxy's location URL.
         * URL format: `taef://Namespace::Class::Method` → pattern: `Namespace::Class::Method`
         */
        private val PROXY_TO_TEST_PATTERN = Function<Pair<AbstractTestProxy, Project>, String> { pair ->
            val url = pair.first.locationUrl
            if (url != null && url.startsWith(PROTOCOL_SCHEME)) {
                url.substring(PROTOCOL_SCHEME.length)
            } else {
                pair.first.name
            }
        }
    }
}
