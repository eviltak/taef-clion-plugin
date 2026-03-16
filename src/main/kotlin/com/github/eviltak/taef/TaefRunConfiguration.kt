package com.github.eviltak.taef

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.CidrRunProfile

class TaefRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<TaefRunConfigurationOptions>(project, factory, name), CidrRunProfile {

    public override fun getOptions(): TaefRunConfigurationOptions =
        super.getOptions() as TaefRunConfigurationOptions

    override fun suggestedName(): String? =
        options.cmakeTarget?.takeIf { it.isNotBlank() }
            ?: options.testDllPath?.takeIf { it.isNotBlank() }

    override fun getConfigurationEditor(): SettingsEditor<TaefRunConfiguration> =
        TaefSettingsEditor(project)

    override fun checkConfiguration() {
        if (options.teExePath.isNullOrBlank()) {
            throw RuntimeConfigurationError("TE.exe path is not specified")
        }
        if (options.cmakeTarget.isNullOrBlank() && options.testDllPath.isNullOrBlank()) {
            throw RuntimeConfigurationError("Select a CMake target or specify a test DLL path")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): CidrCommandLineState =
        CidrCommandLineState(environment, TaefLauncher(environment, this))
}
