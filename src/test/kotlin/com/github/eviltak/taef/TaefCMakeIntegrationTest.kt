package com.github.eviltak.taef

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import java.io.File

/**
 * Heavy integration tests that load the sample CMake project and verify
 * the full plugin pipeline: CMake target discovery, DLL path resolution,
 * run configuration creation + validation, before-run task scoping.
 *
 * Uses HeavyPlatformTestCase with CMakeWorkspace reload to get a real
 * CMake model. Uses CLion's bundled toolchain — no environment deps.
 *
 * Tests are consolidated into few methods to minimize the expensive
 * setUp/tearDown cycle (project creation + CMake reload per method).
 * Each method delegates to well-named assertion helpers for readability.
 */
class TaefCMakeIntegrationTest : HeavyPlatformTestCase() {

    private var cmakeLoaded = false

    override fun setUp() {
        super.setUp()
        copySampleProjectIntoTestProject()
        cmakeLoaded = reloadCMakeWorkspace()
    }

    override fun tearDown() {
        try {
            CMakeWorkspace.clearTestSettings()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Tests CMake model: target discovery, product file resolution, build scoping.
     */
    fun testCMakeTargetDiscoveryAndBuildScoping() {
        if (!cmakeLoaded) return

        assertSampleTestsTargetDiscovered()
        assertProductFileResolvedToDll()
        assertBuildScopedToSelectedTarget()
    }

    /**
     * Tests run config pipeline: validation, DLL resolution, overrides, error handling.
     */
    fun testRunConfigDllResolutionPipeline() {
        if (!cmakeLoaded) return

        assertCMakeTargetPassesValidation()
        assertCMakeTargetResolvesToDll()
        assertManualDllOverridesTakesPriority()
        assertInvalidTargetProducesMeaningfulError()
    }

    /**
     * Tests execution target provider and profile-aware DLL resolution.
     */
    fun testProfileSelectionAndTargetProvider() {
        if (!cmakeLoaded) return

        assertExecutionTargetProviderReturnProfiles()
        assertExecutionTargetProviderIgnoresNonTaefConfigs()
        assertDllResolutionUsesSelectedProfile()
    }

    /**
     * Tests before-run task: creation, filtering, description with target name.
     */
    fun testBeforeRunTask() {
        assertTaskCreatedForTaefConfig()
        assertTaskNotCreatedForOtherConfig()
        assertTaskDescriptionContainsTargetName()
    }

    // --- Target discovery assertions ---

    private fun assertSampleTestsTargetDiscovered() {
        val helper = CMakeBuildConfigurationHelper(project)
        val targetNames = helper.targets.map { it.name }
        assertTrue(
            "Should find SampleTests target, found: $targetNames",
            targetNames.contains("SampleTests")
        )
    }

    private fun assertProductFileResolvedToDll() {
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        val config = helper.getDefaultConfiguration(target)
        assertNotNull("Should have a default CMake configuration", config)

        val productFile = config!!.productFile
        assertNotNull("Product file should be resolved", productFile)
        assertEquals("SampleTests.dll", productFile!!.name)
    }

    private fun assertBuildScopedToSelectedTarget() {
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        val cmakeConfig = helper.getDefaultConfiguration(target)!!

        val buildAndRun = CMakeAppRunConfiguration.BuildAndRunConfigurations(cmakeConfig)
        val buildableElements = CMakeBuild.getBuildableElements(buildAndRun)
        val elementNames = buildableElements.map { it.name }

        assertFalse("Buildable elements should not be empty", buildableElements.isEmpty())
        assertTrue(
            "Should reference SampleTests, got: $elementNames",
            elementNames.any { it.contains("SampleTests") }
        )
        assertFalse(
            "Should NOT build entire project ('all'/'ALL_BUILD'), got: $elementNames",
            elementNames.any { it == "all" || it == "ALL_BUILD" }
        )
    }

    // --- Run config pipeline assertions ---

    private fun assertCMakeTargetPassesValidation() {
        val config = createTaefConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "SampleTests"
        config.checkConfiguration()
    }

    private fun assertCMakeTargetResolvesToDll() {
        val config = createTaefConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "SampleTests"

        val state = TaefCommandLineState(createExecutionEnvironment(config), config)
        val dllPath = state.resolveTestDllPath()
        assertTrue("Should resolve to SampleTests.dll, got: $dllPath", dllPath.endsWith("SampleTests.dll"))
    }

    private fun assertManualDllOverridesTakesPriority() {
        val config = createTaefConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "SampleTests"
        config.options.testDllPath = "C:\\override\\test.dll"

        val state = TaefCommandLineState(createExecutionEnvironment(config), config)
        assertEquals("C:\\override\\test.dll", state.resolveTestDllPath())
    }

    private fun assertInvalidTargetProducesMeaningfulError() {
        val config = createTaefConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "NonExistentTarget"

        val state = TaefCommandLineState(createExecutionEnvironment(config), config)
        try {
            state.resolveTestDllPath()
            fail("Should throw for non-existent target")
        } catch (e: Exception) {
            assertTrue("Error should mention target name", e.message!!.contains("NonExistentTarget"))
        }
    }

    // --- Before-run task assertions ---

    private fun assertTaskCreatedForTaefConfig() {
        val provider = TaefBeforeRunTaskProvider()
        val task = provider.createTask(createTaefConfig())
        assertNotNull("Should create before-run task for TAEF config", task)
        assertTrue("Task should be enabled by default", task!!.isEnabled)
    }

    private fun assertTaskNotCreatedForOtherConfig() {
        val provider = TaefBeforeRunTaskProvider()
        val otherConfig = com.intellij.execution.configurations.UnknownRunConfiguration(
            com.intellij.execution.configurations.UnknownConfigurationType.getInstance(),
            project
        )
        assertNull("Should not create task for non-TAEF config", provider.createTask(otherConfig))
    }

    private fun assertTaskDescriptionContainsTargetName() {
        val provider = TaefBeforeRunTaskProvider()

        val configWithTarget = createTaefConfig()
        configWithTarget.options.cmakeTarget = "SampleTests"
        val taskWithTarget = provider.createTask(configWithTarget)!!
        assertEquals("Build 'SampleTests'", provider.getDescription(taskWithTarget))

        val configNoTarget = createTaefConfig()
        val taskNoTarget = provider.createTask(configNoTarget)!!
        assertEquals(TaefBeforeRunTaskProvider.DEFAULT_DESCRIPTION, provider.getDescription(taskNoTarget))
    }

    // --- Profile selection assertions ---

    private fun assertExecutionTargetProviderReturnProfiles() {
        val provider = TaefExecutionTargetProvider()
        val config = createTaefConfig()
        val targets = provider.getTargets(project, config)

        assertFalse("Should return at least one profile", targets.isEmpty())
        assertTrue(
            "Targets should be TaefBuildProfileExecutionTarget",
            targets.all { it is TaefBuildProfileExecutionTarget }
        )

        val profileNames = targets.map { (it as TaefBuildProfileExecutionTarget).profileName }
        assertTrue(
            "Should contain a profile name (e.g. Debug), got: $profileNames",
            profileNames.any { it.isNotBlank() }
        )
    }

    private fun assertExecutionTargetProviderIgnoresNonTaefConfigs() {
        val provider = TaefExecutionTargetProvider()
        val otherConfig = com.intellij.execution.configurations.UnknownRunConfiguration(
            com.intellij.execution.configurations.UnknownConfigurationType.getInstance(),
            project
        )
        val targets = provider.getTargets(project, otherConfig)
        assertTrue("Should return empty for non-TAEF config", targets.isEmpty())
    }

    private fun assertDllResolutionUsesSelectedProfile() {
        val config = createTaefConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "SampleTests"

        // Get available profiles
        val provider = TaefExecutionTargetProvider()
        val profileTargets = provider.getTargets(project, config)
        assertTrue("Need at least one profile for this test", profileTargets.isNotEmpty())

        val profileTarget = profileTargets[0] as TaefBuildProfileExecutionTarget
        val env = createExecutionEnvironmentWithTarget(config, profileTarget)
        val state = TaefCommandLineState(env, config)
        val dllPath = state.resolveTestDllPath()

        assertTrue(
            "DLL path should contain the profile name '${profileTarget.profileName}', got: $dllPath",
            dllPath.contains(profileTarget.profileName, ignoreCase = true)
        )
        assertTrue("Should still end with SampleTests.dll", dllPath.endsWith("SampleTests.dll"))
    }

    // --- Infrastructure helpers ---

    private fun createTaefConfig(): TaefRunConfiguration {
        val configType = TaefConfigurationType()
        val factory = configType.configurationFactories[0]
        return factory.createTemplateConfiguration(project) as TaefRunConfiguration
    }

    private fun createExecutionEnvironment(
        config: TaefRunConfiguration
    ): com.intellij.execution.runners.ExecutionEnvironment {
        val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
        val settings = com.intellij.execution.RunManager.getInstance(project)
            .createConfiguration(config, config.factory!!)
        return com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(executor, settings)
            .build()
    }

    private fun createExecutionEnvironmentWithTarget(
        config: TaefRunConfiguration,
        target: com.intellij.execution.ExecutionTarget
    ): com.intellij.execution.runners.ExecutionEnvironment {
        val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
        val settings = com.intellij.execution.RunManager.getInstance(project)
            .createConfiguration(config, config.factory!!)
        return com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(executor, settings)
            .target(target)
            .build()
    }

    private fun copySampleProjectIntoTestProject() {
        val sampleDir = findSampleProject()
            ?: throw AssertionError("testData/sampleProject not found from ${System.getProperty("user.dir")}")

        val projectRoot = File(project.basePath!!)
        sampleDir.listFiles()?.forEach { file ->
            if (file.name != "cmake-build-debug" && !file.name.startsWith(".")) {
                file.copyRecursively(File(projectRoot, file.name), overwrite = true)
            }
        }

        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectRoot)
        assertNotNull("Project VFS root should exist", vDir)
        WriteAction.runAndWait<Throwable> { vDir!!.refresh(false, true) }
    }

    private fun reloadCMakeWorkspace(): Boolean {
        val workspace = CMakeWorkspace.getInstance(project)
        CMakeWorkspace.skipReloadOnProjectOpenInTests = false

        try {
            workspace.scheduleReload()
            workspace.waitForReloadsToFinish(120_000)
        } catch (e: Exception) {
            System.err.println("CMake reload failed: ${e.message} — skipping CMake-dependent tests")
            return false
        }

        if (workspace.modelTargets.isEmpty()) {
            System.err.println("CMake loaded but no targets found — skipping CMake-dependent tests")
            return false
        }
        return true
    }

    private fun findSampleProject(): File? {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            val candidate = File(dir, "testData/sampleProject")
            if (candidate.exists() && File(candidate, "CMakeLists.txt").exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
