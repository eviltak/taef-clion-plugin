package com.github.eviltak.taef

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class TaefConfigurationType : ConfigurationType {

    companion object {
        const val ID = "TaefRunConfiguration"
        const val DISPLAY_NAME = "TAEF Test"
        const val DESCRIPTION = "Run TAEF tests via TE.exe"
        private const val ICON_PATH = "/icons/taef.svg"
    }

    private val factory = TaefConfigurationFactory(this)

    override fun getDisplayName(): String = DISPLAY_NAME
    override fun getConfigurationTypeDescription(): String = DESCRIPTION
    override fun getIcon(): Icon = IconLoader.getIcon(ICON_PATH, TaefConfigurationType::class.java)
    override fun getId(): String = ID
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class TaefConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    companion object {
        const val ID = "TaefConfigurationFactory"
    }

    override fun getId(): String = ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        TaefRunConfiguration(project, this, TaefConfigurationType.DISPLAY_NAME).apply {
            setGeneratedName()
        }

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        TaefRunConfigurationOptions::class.java
}
