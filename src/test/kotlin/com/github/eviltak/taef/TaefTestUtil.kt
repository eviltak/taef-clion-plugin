package com.github.eviltak.taef

import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.testing.CidrTestEventProcessor
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shared helpers for platform tests that need TaefRunConfiguration,
 * ExecutionEnvironment, or TaefTestConsoleProperties.
 */
object TaefTestUtil {

    fun createConfig(project: Project): TaefRunConfiguration {
        val configType = TaefConfigurationType()
        return configType.factory.createTemplateConfiguration(project) as TaefRunConfiguration
    }

    fun createEnv(project: Project, config: TaefRunConfiguration): ExecutionEnvironment {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val settings = RunManager.getInstance(project).createConfiguration(config, config.factory!!)
        return ExecutionEnvironmentBuilder.create(executor, settings).build()
    }

    fun createConsoleProperties(project: Project): TaefTestConsoleProperties {
        val config = createConfig(project)
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val target = DefaultExecutionTarget.INSTANCE
        return config.testData!!.createTestConsoleProperties(executor, target) as TaefTestConsoleProperties
    }

    fun createConverter(project: Project): TaefOutputToGeneralTestEventsConverter {
        val props = createConsoleProperties(project)
        return TaefOutputToGeneralTestEventsConverter(
            TaefTestConstants.PROTOCOL_PREFIX,
            props,
            CidrTestEventProcessor(TaefTestConstants.PROTOCOL_PREFIX),
            MockConsole
        )
    }

    internal object MockConsole : ExecutionConsole {
        override fun getComponent(): JComponent = JPanel()
        override fun getPreferredFocusableComponent(): JComponent = component
        override fun dispose() {}
    }
}
