package com.github.eviltak.taef

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.jetbrains.cidr.execution.testing.CidrTestRunConfiguration
import com.jetbrains.cidr.execution.testing.CidrTestRunConfigurationData
import com.jetbrains.cidr.execution.testing.CidrTestScope

/**
 * Test-specific data for TAEF run configurations.
 *
 * Bridges [TaefRunConfiguration] into the CLion test infrastructure,
 * providing test scope info, command line state creation, and
 * SMRunner console properties for the test results tree.
 */
class TaefTestRunConfigurationData(
    config: CidrTestRunConfiguration
) : CidrTestRunConfigurationData<CidrTestRunConfiguration>(config) {

    override fun getTestingFrameworkId(): String = TaefTestConstants.PROTOCOL_PREFIX

    override fun suggestedName(configName: String?): String {
        val testId = formatTestMethod().ifEmpty { testPattern.orEmpty() }
        val targetName = (myConfiguration as? TaefRunConfiguration)?.suggestNameForTarget()

        return when {
            testId.isNotEmpty() && targetName != null -> "'$testId' in '$targetName'"
            testId.isNotEmpty() -> testId
            targetName != null -> targetName
            configName != null -> configName
            else -> "TAEF Test"
        }
    }

    override fun formatTestMethod(): String {
        val suite = testSuite.orEmpty()
        val name = testName.orEmpty()
        return when {
            suite.isNotEmpty() && name.isNotEmpty() -> "$suite::$name"
            suite.isNotEmpty() -> suite
            name.isNotEmpty() -> name
            else -> ""
        }
    }

    override fun checkData() {
        // No additional validation beyond what CMakeTestRunConfiguration provides
    }

    override fun createState(
        environment: ExecutionEnvironment,
        executor: Executor,
        testScope: CidrTestScope?
    ): CommandLineState? {
        val config = myConfiguration as TaefRunConfiguration
        val launcher = config.createLauncher(environment)
        return TaefTestCommandLineState(config, launcher, testScope, environment, executor)
    }

    override fun createTestConsoleProperties(
        executor: Executor,
        target: ExecutionTarget
    ): SMTRunnerConsoleProperties =
        TaefTestConsoleProperties(myConfiguration, executor, target)
}
