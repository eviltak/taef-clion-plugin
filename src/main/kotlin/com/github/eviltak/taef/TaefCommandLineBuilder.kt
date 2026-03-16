package com.github.eviltak.taef

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.execution.ParametersListUtil
import java.io.File

data class TaefCommandLineParams(
    val teExePath: String?,
    val testDllPath: String?,
    val nameFilter: String? = null,
    val selectQuery: String? = null,
    val workingDirectory: String? = null,
    val inproc: Boolean = false,
    val additionalArgs: String? = null,
)

object TaefCommandLineBuilder {

    fun build(params: TaefCommandLineParams): GeneralCommandLine {
        val teExe = params.teExePath?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("TE.exe path is not configured")
        val testDll = params.testDllPath?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Test DLL path is not configured")

        return GeneralCommandLine().apply {
            exePath = teExe
            addParameter(testDll)

            params.nameFilter?.takeIf { it.isNotBlank() }?.let {
                addParameter("/name:$it")
            }
            params.selectQuery?.takeIf { it.isNotBlank() }?.let {
                addParameter("/select:\"$it\"")
            }
            if (params.inproc) {
                addParameter("/inproc")
            }
            params.additionalArgs?.takeIf { it.isNotBlank() }?.let {
                addParameters(ParametersListUtil.parse(it))
            }
            params.workingDirectory?.takeIf { it.isNotBlank() }?.let {
                workDirectory = File(it)
            }
        }
    }
}
