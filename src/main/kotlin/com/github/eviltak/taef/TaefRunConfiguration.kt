package com.github.eviltak.taef

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.execution.CidrCommandLineState
import org.jdom.Element

/**
 * TAEF run configuration based on CMakeAppRunConfiguration.
 *
 * The CMake target selects the test DLL to build. TE.exe is specified via
 * the Executable field (supports both CMake executable targets and manual paths).
 * The DLL path is auto-prepended to program arguments at execution time.
 *
 * TAEF-specific fields (name filter, select query, inproc, additional args)
 * are serialized separately and injected into the command line.
 */
class TaefRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : CMakeAppRunConfiguration(project, factory, name) {

    var nameFilter: String = ""
    var selectQuery: String = ""
    var inproc: Boolean = false
    var additionalTeArgs: String = ""

    override fun suggestedName(): String? {
        val targetName = suggestNameForTarget() ?: return null
        return "TAEF: $targetName"
    }

    override fun getConfigurationEditor(): SettingsEditor<out CMakeAppRunConfiguration> =
        TaefSettingsEditor(project, getHelper())

    override fun getState(executor: Executor, environment: ExecutionEnvironment): CidrCommandLineState =
        CidrCommandLineState(environment, TaefLauncher(environment, this))

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        val taefElement = Element(TAEF_ELEMENT)
        taefElement.setAttribute(ATTR_NAME_FILTER, nameFilter)
        taefElement.setAttribute(ATTR_SELECT_QUERY, selectQuery)
        taefElement.setAttribute(ATTR_INPROC, inproc.toString())
        taefElement.setAttribute(ATTR_ADDITIONAL_ARGS, additionalTeArgs)
        element.addContent(taefElement)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        val taefElement = element.getChild(TAEF_ELEMENT) ?: return
        nameFilter = taefElement.getAttributeValue(ATTR_NAME_FILTER, "")
        selectQuery = taefElement.getAttributeValue(ATTR_SELECT_QUERY, "")
        inproc = taefElement.getAttributeValue(ATTR_INPROC, "false").toBoolean()
        additionalTeArgs = taefElement.getAttributeValue(ATTR_ADDITIONAL_ARGS, "")
    }

    /** Build TAEF-specific arguments (without DLL path — that's prepended by launcher). */
    fun buildTaefArgs(): String {
        val args = mutableListOf<String>()
        if (nameFilter.isNotBlank()) args.add("/name:$nameFilter")
        if (selectQuery.isNotBlank()) args.add("/select:\"$selectQuery\"")
        if (inproc) args.add("/inproc")
        if (additionalTeArgs.isNotBlank()) args.add(additionalTeArgs)
        return args.joinToString(" ")
    }

    companion object {
        private const val TAEF_ELEMENT = "taef-settings"
        private const val ATTR_NAME_FILTER = "name-filter"
        private const val ATTR_SELECT_QUERY = "select-query"
        private const val ATTR_INPROC = "inproc"
        private const val ATTR_ADDITIONAL_ARGS = "additional-args"
    }
}
