package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.GridBag
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfigurationSettingsEditor
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings editor for TAEF run configurations. Extends the CMake Application
 * editor (which provides target, executable, and profile selectors) and adds
 * TAEF-specific fields below.
 *
 * The program arguments panel is hidden since TAEF args are managed via
 * dedicated fields and auto-generated at execution time.
 */
class TaefSettingsEditor(
    project: Project,
    helper: CMakeBuildConfigurationHelper
) : CMakeAppRunConfigurationSettingsEditor(project, helper) {

    private val nameFilterField = JBTextField()
    private val selectQueryField = JBTextField()
    private val inprocCheckbox = JBCheckBox("Run in-process (/inproc)")
    private val additionalArgsField = JBTextField()

    override fun getTargetComboTitle(): String = "Test DLL target"

    override fun createEditorInner(panel: JPanel, gridBag: GridBag) {
        super.createEditorInner(panel, gridBag)

        // Hide program arguments panel - TAEF args are managed via our fields
        myCommonProgramParameters?.isVisible = false

        panel.add(JLabel("Name filter (/name:):"), gridBag.nextLine().next())
        panel.add(nameFilterField, gridBag.next().coverLine())

        panel.add(JLabel("Select query (/select:):"), gridBag.nextLine().next())
        panel.add(selectQueryField, gridBag.next().coverLine())

        panel.add(inprocCheckbox, gridBag.nextLine().next().coverLine())

        panel.add(JLabel("Additional TE.exe arguments:"), gridBag.nextLine().next())
        panel.add(additionalArgsField, gridBag.next().coverLine())
    }

    override fun resetEditorFrom(config: CMakeAppRunConfiguration) {
        super.resetEditorFrom(config)
        val taefConfig = config as TaefRunConfiguration
        nameFilterField.text = taefConfig.nameFilter
        selectQueryField.text = taefConfig.selectQuery
        inprocCheckbox.isSelected = taefConfig.inproc
        additionalArgsField.text = taefConfig.additionalTeArgs
    }

    override fun applyEditorTo(config: CMakeAppRunConfiguration) {
        super.applyEditorTo(config)
        val taefConfig = config as TaefRunConfiguration
        taefConfig.nameFilter = nameFilterField.text
        taefConfig.selectQuery = selectQueryField.text
        taefConfig.inproc = inprocCheckbox.isSelected
        taefConfig.additionalTeArgs = additionalArgsField.text
    }
}