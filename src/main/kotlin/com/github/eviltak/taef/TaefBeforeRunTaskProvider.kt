package com.github.eviltak.taef

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import java.util.concurrent.CompletableFuture
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
        if (configuration !is TaefRunConfiguration) return false
        val opts = configuration.options

        // If using manual DLL path, skip the build
        if (!opts.testDllPath.isNullOrBlank()) return true

        val targetName = opts.cmakeTarget?.takeIf { it.isNotBlank() } ?: return true

        val project = configuration.project
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == targetName } ?: return false
        val cmakeConfig = helper.getDefaultConfiguration(target) ?: return false

        val buildAndRun = CMakeAppRunConfiguration.BuildAndRunConfigurations(cmakeConfig)
        val buildableElements = CMakeBuild.getBuildableElements(buildAndRun)

        val taskManager = ProjectTaskManager.getInstance(project)
        val buildTask = taskManager.createBuildTask(true, *buildableElements.toTypedArray())
        val taskContext = ProjectTaskContext(false)

        val future = CompletableFuture<Boolean>()
        taskManager.run(taskContext, buildTask)
            .onSuccess { result ->
                when {
                    result.isAborted -> future.cancel(false)
                    result.hasErrors() -> future.complete(false)
                    else -> future.complete(true)
                }
            }
            .onError { future.completeExceptionally(it) }

        return try {
            future.get()
        } catch (_: Exception) {
            false
        }
    }
}
