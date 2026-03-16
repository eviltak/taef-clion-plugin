package com.github.eviltak.taef

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

class TaefConfigurationType : ConfigurationType {

    companion object {
        const val ID = "TaefRunConfiguration"
        const val DISPLAY_NAME = "TAEF Test"
        const val DESCRIPTION = "Run TAEF tests via TE.exe"
    }

    private val factory = TaefConfigurationFactory(this)

    override fun getDisplayName(): String = DISPLAY_NAME
    override fun getConfigurationTypeDescription(): String = DESCRIPTION
    override fun getIcon(): Icon = AllIcons.RunConfigurations.TestState.Run
    override fun getId(): String = ID
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class TaefConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    companion object {
        const val ID = "TaefConfigurationFactory"
    }

    override fun getId(): String = ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        TaefRunConfiguration(project, this, TaefConfigurationType.DISPLAY_NAME)

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        TaefRunConfigurationOptions::class.java
}
