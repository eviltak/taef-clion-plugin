package com.github.eviltak.taef

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.cpp.execution.CMakeLauncher
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import java.io.File

/**
 * Launcher for TAEF tests. Extends CMakeLauncher to get full CLion debug
 * support, but injects the test DLL path as the first program argument
 * and appends TAEF-specific flags.
 *
 * The CMake target's product file (test DLL) is resolved by the parent class.
 * The executable (TE.exe) is set via ExecutableData on the run configuration.
 */
class TaefLauncher(
    environment: ExecutionEnvironment,
    private val config: TaefRunConfiguration
) : CMakeLauncher(environment, config) {

    override fun getRunFileAndEnvironment(): Pair<File, CPPEnvironment> {
        val (parentRunFile, cppEnv) = super.getRunFileAndEnvironment()

        // Resolve the test DLL from the CMake target's product file.
        // If that's not available (e.g. manual config), use parentRunFile.
        val buildAndRun = config.getBuildAndRunConfigurations(
            executionEnvironment.executionTarget, null, false
        )
        val dllFile = buildAndRun?.buildConfiguration?.productFile ?: parentRunFile

        // Inject the DLL as the first program argument, followed by TAEF flags
        val taefArgs = config.buildTaefArgs()
        val fullArgs = if (taefArgs.isNotBlank()) {
            "${dllFile.absolutePath} $taefArgs"
        } else {
            dllFile.absolutePath
        }
        config.setProgramParameters(fullArgs)

        // The actual executable is TE.exe, set via ExecutableData on the config.
        val teExePath = config.executableData?.path
            ?: throw ExecutionException("TE.exe path is not configured (set Executable in run configuration)")

        return Pair(File(teExePath), cppEnv)
    }
}
