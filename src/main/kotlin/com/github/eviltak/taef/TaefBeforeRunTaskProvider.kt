package com.github.eviltak.taef

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import javax.swing.Icon

private val BEFORE_RUN_TASK_ID: Key<TaefBuildBeforeRunTask> = Key.create("TaefBuildBeforeRunTask")

class TaefBuildBeforeRunTask :
    BeforeRunTask<TaefBuildBeforeRunTask>(BEFORE_RUN_TASK_ID) {
    init {
        isEnabled = true
    }
}

class TaefBeforeRunTaskProvider : BeforeRunTaskProvider<TaefBuildBeforeRunTask>() {

    override fun getId(): Key<TaefBuildBeforeRunTask> = BEFORE_RUN_TASK_ID
    override fun getName(): String = "Build TAEF Test DLL"
    override fun getIcon(): Icon = AllIcons.Actions.Compile
    override fun getDescription(task: TaefBuildBeforeRunTask): String =
        "Build TAEF test DLL via CMake"

    override fun createTask(runConfiguration: RunConfiguration): TaefBuildBeforeRunTask? {
        if (runConfiguration is TaefRunConfiguration) {
            return TaefBuildBeforeRunTask()
        }
        return null
    }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: TaefBuildBeforeRunTask
    ): Boolean {
        // TODO: Phase 4 — trigger CMake build of the test DLL target
        // For now, return true to let the run proceed without building
        return true
    }
}
