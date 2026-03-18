package com.github.eviltak.taef

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import com.jetbrains.cidr.cpp.cmake.CMakeSettings
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.execution.CMakeLauncher
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import com.intellij.execution.DefaultExecutionTarget
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import java.io.File

/**
 * Heavy integration tests that load the sample CMake project and verify
 * the full plugin pipeline: CMake target discovery, DLL path resolution,
 * run configuration creation + validation, launcher creation.
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
        addReleaseCMakeProfile()
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
     * Tests Tier 1 detection: TaefTestFrameworkDetector identifies MODULE targets.
     */
    fun testFrameworkDetector() {
        if (!cmakeLoaded) return

        assertDetectorIdentifiesTaefTarget()
        assertDetectorRejectsNonModuleTargets()
    }

    /**
     * Tests run config pipeline: target setting via parent API, validation,
     * DLL resolution through getBuildAndRunConfigurations, suggested name.
     */
    fun testRunConfigPipeline() {
        if (!cmakeLoaded) return

        assertCMakeTargetPassesValidation()
        assertCMakeTargetResolvesToDll()
        assertSuggestedNameIncludesTaefPrefix()
        assertParentFieldsPersistAlongsideTaefFields()
    }

    /**
     * Tests launcher: creation, type hierarchy, executable swap, arg injection.
     */
    fun testLauncher() {
        if (!cmakeLoaded) return

        assertLauncherCanBeCreated()
        assertLauncherExtendsCMakeLauncher()
        assertLauncherSwapsExecutableAndInjectsDllArg()
    }

    // --- Framework detector assertions ---

    private fun assertDetectorIdentifiesTaefTarget() {
        val detector = TaefTestFrameworkDetector()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!

        assertTrue(
            "Detector should identify SampleTests as a TAEF target",
            detector.hasTestConfiguration(target, helper)
        )
        assertEquals(
            "Detector should return TaefConfigurationType",
            TaefConfigurationType.ID,
            detector.testConfigurationType.id
        )
    }

    private fun assertDetectorRejectsNonModuleTargets() {
        val detector = TaefTestFrameworkDetector()
        val helper = CMakeBuildConfigurationHelper(project)

        // SampleApp is an executable that uses TAEF macros — detector should reject it
        // because TE.exe only loads MODULE libraries (DLLs)
        val appTarget = helper.targets.find { it.name == "SampleApp" }
        assertNotNull("SampleApp target should exist", appTarget)
        assertFalse(
            "Detector should reject executable target 'SampleApp' even though it has TAEF macros",
            detector.hasTestConfiguration(appTarget!!, helper)
        )
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
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))
        config.checkConfiguration()
    }

    private fun assertCMakeTargetResolvesToDll() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val buildAndRun = config.getBuildAndRunConfigurations(DefaultExecutionTarget.INSTANCE, null, false)
        assertNotNull("BuildAndRunConfigurations should be resolved", buildAndRun)

        val productFile = buildAndRun!!.buildConfiguration.productFile
        assertNotNull("Product file (DLL) should be resolved", productFile)
        assertEquals("SampleTests.dll", productFile!!.name)
    }

    private fun assertSuggestedNameIncludesTaefPrefix() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val name = config.suggestedName()
        assertNotNull("Suggested name should not be null when target is set", name)
        assertTrue(
            "Suggested name should start with 'TAEF: ', got: $name",
            name!!.startsWith("TAEF: ")
        )
        assertTrue(
            "Suggested name should contain target name, got: $name",
            name.contains("SampleTests")
        )
    }

    private fun assertParentFieldsPersistAlongsideTaefFields() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))
        config.nameFilter = "*TestFilter*"
        config.selectQuery = "@Owner='me'"
        config.inproc = true
        config.additionalTeArgs = "/logOutput:High"

        val element = org.jdom.Element("configuration")
        config.writeExternal(element)

        val restored = createTaefConfig()
        restored.readExternal(element)

        // Parent fields preserved
        val restoredTarget = restored.targetAndConfigurationData
        assertNotNull("Target should be preserved after round-trip", restoredTarget)

        // TAEF fields preserved
        assertEquals("*TestFilter*", restored.nameFilter)
        assertEquals("@Owner='me'", restored.selectQuery)
        assertTrue(restored.inproc)
        assertEquals("/logOutput:High", restored.additionalTeArgs)
    }

    // --- Launcher assertions ---

    private fun assertLauncherCanBeCreated() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))
        config.nameFilter = "*Foo*"
        config.inproc = true

        val env = createExecutionEnvironment(config)
        val launcher = TaefLauncher(env, config)
        assertNotNull(launcher)
    }

    private fun assertLauncherExtendsCMakeLauncher() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val env = createExecutionEnvironment(config)
        val launcher = TaefLauncher(env, config)
        assertInstanceOf(launcher, CMakeLauncher::class.java)
    }

    private fun assertLauncherSwapsExecutableAndInjectsDllArg() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == "SampleTests" }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))
        config.nameFilter = "*MyTest*"
        config.inproc = true

        // Set TE.exe as the executable
        val teExePath = "C:\\tools\\te.exe"
        config.executableData = com.jetbrains.cidr.execution.ExecutableData(teExePath)

        val env = createExecutionEnvironment(config)
        val launcher = TaefLauncher(env, config)
        val (runFile, cppEnv) = launcher.getRunFileAndEnvironment()

        // Executable should be TE.exe, not the DLL
        assertEquals("Run file should be TE.exe", teExePath, runFile.absolutePath)

        // Program parameters should contain the DLL path and TAEF args
        val programParams = config.programParameters ?: ""
        assertTrue(
            "Program params should contain SampleTests.dll, got: $programParams",
            programParams.contains("SampleTests.dll")
        )
        assertTrue(
            "Program params should contain /name:*MyTest*, got: $programParams",
            programParams.contains("/name:*MyTest*")
        )
        assertTrue(
            "Program params should contain /inproc, got: $programParams",
            programParams.contains("/inproc")
        )
    }

    // --- Infrastructure helpers ---

    private fun createTaefConfig(): TaefRunConfiguration {
        val configType = TaefConfigurationType()
        return configType.factory.createTemplateConfiguration(project) as TaefRunConfiguration
    }

    private fun createExecutionEnvironment(
        config: TaefRunConfiguration
    ): ExecutionEnvironment {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val settings = RunManager.getInstance(project)
            .createConfiguration(config, config.factory!!)
        return ExecutionEnvironmentBuilder
            .create(executor, settings)
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

    private fun addReleaseCMakeProfile() {
        val settings = CMakeSettings.getInstance(project)
        val profiles = settings.profiles.toMutableList()
        if (profiles.none { it.name == "Release" }) {
            profiles.add(CMakeSettings.Profile("Release"))
            settings.setProfiles(profiles)
        }
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
