package com.github.eviltak.taef

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NotNullLazyValue
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType

class TaefConfigurationType : CMakeRunConfigurationType(
    ID, FACTORY_ID, DISPLAY_NAME, DESCRIPTION,
    NotNullLazyValue.volatileLazy { IconLoader.getIcon(ICON_PATH, TaefConfigurationType::class.java) }
) {
    companion object {
        const val ID = "TaefRunConfiguration"
        const val FACTORY_ID = "TaefConfigurationFactory"
        const val DISPLAY_NAME = "TAEF Test"
        const val DESCRIPTION = "Run TAEF tests via TE.exe"
        private const val ICON_PATH = "/icons/taef.svg"
    }

    override fun createEditor(project: Project): SettingsEditor<out CMakeAppRunConfiguration> =
        TaefSettingsEditor(project, getHelper(project))

    override fun createRunConfiguration(
        project: Project,
        configurationFactory: ConfigurationFactory
    ): CMakeAppRunConfiguration =
        TaefRunConfiguration(project, factory, DISPLAY_NAME)
}
