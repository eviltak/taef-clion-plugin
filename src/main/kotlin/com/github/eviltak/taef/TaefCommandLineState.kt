package com.github.eviltak.taef

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper

class TaefCommandLineState(
    environment: ExecutionEnvironment,
    private val config: TaefRunConfiguration
) : CommandLineState(environment) {

    fun buildCommandLine(): GeneralCommandLine {
        val opts = config.options
        val testDllPath = resolveTestDllPath()
        val params = TaefCommandLineParams(
            teExePath = opts.teExePath,
            testDllPath = testDllPath,
            nameFilter = opts.nameFilter,
            selectQuery = opts.selectQuery,
            workingDirectory = opts.workingDirectory,
            inproc = opts.inproc,
            additionalArgs = opts.additionalArgs,
        )
        return TaefCommandLineBuilder.build(params)
    }

    fun resolveTestDllPath(): String {
        val opts = config.options

        // Manual DLL path takes priority
        opts.testDllPath?.takeIf { it.isNotBlank() }?.let { return it }

        // Resolve from CMake target
        val targetName = opts.cmakeTarget?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("No CMake target selected and no test DLL path specified")

        val helper = CMakeBuildConfigurationHelper(config.project)
        val target = helper.targets.find { it.name == targetName }
            ?: throw ExecutionException("CMake target '$targetName' not found in project")

        val cmakeConfig = helper.getDefaultConfiguration(target)
            ?: throw ExecutionException("No CMake configuration found for target '$targetName'")

        val productFile = cmakeConfig.productFile
            ?: throw ExecutionException("Could not resolve output file for target '$targetName'")

        return productFile.absolutePath
    }

    override fun startProcess(): ProcessHandler {
        val commandLine = buildCommandLine()
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
