package com.github.eviltak.taef

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class TaefSettingsEditor(private val project: Project) : SettingsEditor<TaefRunConfiguration>() {

    companion object {
        private val LOG = Logger.getInstance(TaefSettingsEditor::class.java)
        private fun fileDescriptor() = FileChooserDescriptor(true, false, false, false, false, false)
        private fun folderDescriptor() = FileChooserDescriptor(false, true, false, false, false, false)
    }

    private val teExePathField = TextFieldWithBrowseButton()
    private val cmakeTargetCombo = ComboBox<CMakeTarget>()
    private val testDllPathField = TextFieldWithBrowseButton()
    private val nameFilterField = JBTextField()
    private val selectQueryField = JBTextField()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val inprocCheckbox = JBCheckBox("Run in-process (/inproc)")
    private val additionalArgsField = JBTextField()
    private val panel: JPanel

    init {
        teExePathField.addBrowseFolderListener(project, fileDescriptor())
        testDllPathField.addBrowseFolderListener(project, fileDescriptor())
        workingDirectoryField.addBrowseFolderListener(project, folderDescriptor())

        setupCmakeTargetCombo()

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("TE.exe path:", teExePathField)
            .addLabeledComponent("CMake target:", cmakeTargetCombo)
            .addLabeledComponent("Test DLL override:", testDllPathField)
            .addLabeledComponent("Name filter (/name:):", nameFilterField)
            .addLabeledComponent("Select query (/select:):", selectQueryField)
            .addLabeledComponent("Working directory:", workingDirectoryField)
            .addComponent(inprocCheckbox)
            .addLabeledComponent("Additional arguments:", additionalArgsField)
            .panel
    }

    private fun setupCmakeTargetCombo() {
        cmakeTargetCombo.renderer = SimpleListCellRenderer.create("(none)") { it.name }

        val model = DefaultComboBoxModel<CMakeTarget>()
        model.addElement(null)
        try {
            val helper = CMakeBuildConfigurationHelper(project)
            helper.targets.forEach { model.addElement(it) }
        } catch (e: Exception) {
            LOG.info("CMake workspace not available, target combo will be empty", e)
        }
        cmakeTargetCombo.model = model
        cmakeTargetCombo.addActionListener { fireEditorStateChanged() }
    }

    override fun resetEditorFrom(config: TaefRunConfiguration) {
        val opts = config.options
        teExePathField.text = opts.teExePath ?: ""
        testDllPathField.text = opts.testDllPath ?: ""
        nameFilterField.text = opts.nameFilter ?: ""
        selectQueryField.text = opts.selectQuery ?: ""
        workingDirectoryField.text = opts.workingDirectory ?: ""
        inprocCheckbox.isSelected = opts.inproc
        additionalArgsField.text = opts.additionalArgs ?: ""

        val targetName = opts.cmakeTarget
        if (!targetName.isNullOrBlank()) {
            for (i in 0 until cmakeTargetCombo.itemCount) {
                val item = cmakeTargetCombo.getItemAt(i)
                if (item?.name == targetName) {
                    cmakeTargetCombo.selectedIndex = i
                    break
                }
            }
        } else {
            cmakeTargetCombo.selectedIndex = 0
        }
    }

    override fun applyEditorTo(config: TaefRunConfiguration) {
        val opts = config.options
        opts.teExePath = teExePathField.text
        opts.testDllPath = testDllPathField.text
        opts.nameFilter = nameFilterField.text
        opts.selectQuery = selectQueryField.text
        opts.workingDirectory = workingDirectoryField.text
        opts.inproc = inprocCheckbox.isSelected
        opts.additionalArgs = additionalArgsField.text
        opts.cmakeTarget = (cmakeTargetCombo.selectedItem as? CMakeTarget)?.name ?: ""
    }

    override fun createEditor(): JComponent = panel
}
