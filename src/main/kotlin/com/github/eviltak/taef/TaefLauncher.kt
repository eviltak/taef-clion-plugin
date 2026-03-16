package com.github.eviltak.taef

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.createDriverConfiguration
import com.jetbrains.cidr.execution.CidrLauncher
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.toolchains.EnvironmentProblems

/**
 * Launcher for TAEF test runs. Provides both run and debug support.
 *
 * Debug support resolves the CPPEnvironment from the selected CMake profile
 * in the toolbar (via the TaefBuildProfileExecutionTarget), respecting
 * per-profile toolchain and debugger settings. Falls back to the default
 * toolchain if no profile is selected.
 */
class TaefLauncher(
    private val environment: ExecutionEnvironment,
    private val config: TaefRunConfiguration
) : CidrLauncher() {

    override fun getProject(): Project = config.project

    override fun createProcess(state: CommandLineState): ProcessHandler {
        val commandLine = buildCommandLine()
        return ProcessHandlerFactory.getInstance().createProcessHandler(commandLine)
    }

    override fun createDebugProcess(state: CommandLineState, session: XDebugSession): XDebugProcess {
        val commandLine = buildCommandLine()
        val cppEnvironment = resolveEnvironment()
        val driverConfig = createDriverConfiguration(project, cppEnvironment)
        val runParameters = TrivialRunParameters(driverConfig, commandLine)
        return CidrLocalDebugProcess(runParameters, session, state.consoleBuilder)
    }

    private fun resolveProfileName(): String? =
        (environment.executionTarget as? TaefBuildProfileExecutionTarget)?.profileName

    private fun resolveEnvironment(): com.jetbrains.cidr.cpp.toolchains.CPPEnvironment {
        val profileName = resolveProfileName()
        if (profileName != null) {
            val workspace = CMakeWorkspace.getInstance(project)
            val profileInfo = workspace.getCMakeProfileInfoByName(profileName)
            if (profileInfo != null) {
                return profileInfo.getEnvironmentSafe(false)
            }
        }

        // Fall back to default toolchain
        val toolchain = CPPToolchains.getInstance().defaultToolchain
            ?: throw ExecutionException("No C++ toolchain configured")
        val problems = EnvironmentProblems()
        return CPPToolchains.createCPPEnvironment(project, toolchain, problems, false)
            ?: throw ExecutionException("Failed to create C++ environment")
    }

    private fun buildCommandLine(): GeneralCommandLine {
        val cmdLineState = TaefCommandLineState(environment, config)
        return cmdLineState.buildCommandLine()
    }
}
