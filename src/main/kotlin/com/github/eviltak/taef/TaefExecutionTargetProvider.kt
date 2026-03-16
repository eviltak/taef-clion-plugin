package com.github.eviltak.taef

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import javax.swing.Icon

/**
 * Execution target representing a CMake build profile (Debug, Release, etc.).
 * Unlike CLion's built-in CMakeBuildProfileExecutionTarget, this one returns
 * true from canRun() for TaefRunConfiguration.
 */
class TaefBuildProfileExecutionTarget(val profileName: String) : ExecutionTarget() {
    override fun getId(): String = "TaefBuildProfile:$profileName"
    override fun getDisplayName(): String = profileName
    override fun getIcon(): Icon? = null
    override fun canRun(configuration: RunConfiguration): Boolean =
        configuration is TaefRunConfiguration
}

/**
 * Provides CMake build profile execution targets in the toolbar dropdown
 * when a TaefRunConfiguration is selected.
 */
class TaefExecutionTargetProvider : ExecutionTargetProvider() {

    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        if (configuration !is TaefRunConfiguration) return emptyList()

        val workspace = CMakeWorkspace.getInstance(project)
        return workspace.modelConfigurationData.map { TaefBuildProfileExecutionTarget(it.configName) }
    }
}
