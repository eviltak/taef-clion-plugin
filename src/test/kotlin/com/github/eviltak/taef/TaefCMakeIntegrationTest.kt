package com.github.eviltak.taef

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.jetbrains.cidr.cpp.cmake.CMakeSettings
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.execution.CMakeLauncher
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import com.intellij.execution.DefaultExecutionTarget
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.lang.psi.OCFile
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

    override fun setUp() {
        super.setUp()
        ensureToolchainConfigured()
        copySampleProjectIntoTestProject()

        // Put CMake build dir under the project temp dir (in VFS allowed roots)
        CMakeWorkspace.forceUseStandardDirForGenerationInTests = true

        // Enable C++ symbol table building in tests (disabled by default)
        com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.setShouldBuildTablesInTests(
            com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties(
                com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties.SymbolsKind.ONLY_USED,
                false, false
            )
        )

        // Ensure symbol tables are treated as loaded so macro resolution works
        com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.forceSymbolsLoadedInTests(true)

        addReleaseCMakeProfile()
        reloadCMakeWorkspace()
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
        assertSampleTestsTargetDiscovered()
        assertProductFileResolvedToDll()
        assertBuildScopedToSelectedTarget()
    }

    /**
     * Tests CMake target detection: TaefTestFrameworkDetector identifies MODULE targets.
     */
    fun testFrameworkDetector() {
        assertDetectorIdentifiesTaefTarget()
        assertDetectorRejectsNonModuleTargets()
    }

    /**
     * Tests run config pipeline: target setting via parent API, validation,
     * DLL resolution through getBuildAndRunConfigurations, suggested name.
     */
    fun testRunConfigPipeline() {
        assertCMakeTargetPassesValidation()
        assertCMakeTargetResolvesToDll()
        assertSuggestedNameIncludesTaefPrefix()
        assertParentFieldsPersistAlongsideTaefFields()
    }

    /**
     * Tests launcher: creation, type hierarchy, executable swap, arg injection.
     */
    fun testLauncher() {
        assertLauncherCanBeCreated()
        assertLauncherExtendsCMakeLauncher()
        assertLauncherSwapsExecutableAndInjectsDllArg()
    }

    /**
     * Tests PSI-level detection: TaefTestFramework identifies test elements
     * in files using the real TAEF header, and rejects tests in files using
     * a stub header that lacks TAEF internal markers.
     */
    fun testPsiDetection() {
        assertRealHeaderFileIsAvailable()
        assertStubHeaderFileIsAvailable()
        assertNotTaefFileIsUnavailable()
        assertRealHeaderTestObjectsDetected()
        assertStubHeaderTestObjectsRejected()
        assertGutterIconInfoForRealTests()
        assertNoGutterIconInfoForNotTaefFile()
    }

    // --- Framework detector assertions ---

    private fun assertDetectorIdentifiesTaefTarget() {
        val detector = TaefTestFrameworkDetector()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!

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
        val appTarget = helper.targets.find { it.name == SampleProjectConstants.APP_TARGET }
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
            targetNames.contains(SampleProjectConstants.REAL_TESTS_TARGET)
        )
    }

    private fun assertProductFileResolvedToDll() {
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        val config = helper.getDefaultConfiguration(target)
        assertNotNull("Should have a default CMake configuration", config)

        val productFile = config!!.productFile
        assertNotNull("Product file should be resolved", productFile)
        assertEquals("${SampleProjectConstants.REAL_TESTS_TARGET}.dll", productFile!!.name)
    }

    private fun assertBuildScopedToSelectedTarget() {
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        val cmakeConfig = helper.getDefaultConfiguration(target)!!

        val buildAndRun = CMakeAppRunConfiguration.BuildAndRunConfigurations(cmakeConfig)
        val buildableElements = CMakeBuild.getBuildableElements(buildAndRun)

        assertFalse("Buildable elements should not be empty", buildableElements.isEmpty())
        assertFalse(
            "Should NOT build entire project ('all'/'ALL_BUILD'), got: ${buildableElements.map { it.name }}",
            buildableElements.any { it.name == "all" || it.name == "ALL_BUILD" }
        )
    }

    // --- Run config pipeline assertions ---

    private fun assertCMakeTargetPassesValidation() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))
        config.checkConfiguration()
    }

    private fun assertCMakeTargetResolvesToDll() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val buildAndRun = config.getBuildAndRunConfigurations(DefaultExecutionTarget.INSTANCE, null, false)
        assertNotNull("BuildAndRunConfigurations should be resolved", buildAndRun)

        val productFile = buildAndRun!!.buildConfiguration.productFile
        assertNotNull("Product file (DLL) should be resolved", productFile)
        assertEquals("${SampleProjectConstants.REAL_TESTS_TARGET}.dll", productFile!!.name)
    }

    private fun assertSuggestedNameIncludesTaefPrefix() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val name = config.suggestedName()
        assertNotNull("Suggested name should not be null when target is set", name)
        assertTrue(
            "Suggested name should start with 'TAEF: ', got: $name",
            name!!.startsWith("TAEF: ")
        )
        assertTrue(
            "Suggested name should contain target name, got: $name",
            name.contains(SampleProjectConstants.REAL_TESTS_TARGET)
        )
    }

    private fun assertParentFieldsPersistAlongsideTaefFields() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
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
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
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
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
        config.setTargetAndConfigurationData(BuildTargetAndConfigurationData(target, null as String?))

        val env = createExecutionEnvironment(config)
        val launcher = TaefLauncher(env, config)
        assertInstanceOf(launcher, CMakeLauncher::class.java)
    }

    private fun assertLauncherSwapsExecutableAndInjectsDllArg() {
        val config = createTaefConfig()
        val helper = CMakeBuildConfigurationHelper(project)
        val target = helper.targets.find { it.name == SampleProjectConstants.REAL_TESTS_TARGET }!!
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
            "Program params should contain ${SampleProjectConstants.REAL_TESTS_TARGET}.dll, got: $programParams",
            programParams.contains("${SampleProjectConstants.REAL_TESTS_TARGET}.dll")
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

    // --- PSI detection assertions ---

    private fun assertRealHeaderFileIsAvailable() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE)
        assertNotNull("${SampleProjectConstants.REAL_TESTS_FILE} should exist as PsiFile", psiFile)
        assertTrue(
            "Framework should be available for ${SampleProjectConstants.REAL_TESTS_FILE} (includes WexTestClass.h)",
            framework.isAvailable(psiFile)
        )
    }

    private fun assertStubHeaderFileIsAvailable() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.STUB_TESTS_FILE)
        assertNotNull("${SampleProjectConstants.STUB_TESTS_FILE} should exist as PsiFile", psiFile)
        assertTrue(
            "Framework should be available for ${SampleProjectConstants.STUB_TESTS_FILE} (includes WexTestClass.h by name)",
            framework.isAvailable(psiFile)
        )
    }

    private fun assertNotTaefFileIsUnavailable() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.NOT_TAEF_FILE)
        assertNotNull("${SampleProjectConstants.NOT_TAEF_FILE} should exist as PsiFile", psiFile)
        assertFalse(
            "Framework should NOT be available for ${SampleProjectConstants.NOT_TAEF_FILE} (no WexTestClass.h include)",
            framework.isAvailable(psiFile)
        )
    }

    /**
     * Collects test objects from a file, triggering the test list update first
     * since the indexed cache may not be populated in the test environment.
     */
    private fun collectTestObjectsFromFile(
        framework: TaefTestFramework,
        psiFile: com.jetbrains.cidr.lang.psi.OCFile
    ): Map<String, com.jetbrains.cidr.execution.testing.CidrTestScopeElement> {
        framework.updateTestsListOrScheduleUpdateIfCannotWait(psiFile)
        return framework.collectTestObjects(psiFile)
    }

    private fun assertRealHeaderTestObjectsDetected() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE)
        assertNotNull(psiFile)

        val testObjects = collectTestObjectsFromFile(framework, psiFile!!)
        assertTrue(
            "Real TAEF file should have detected test objects, got: ${testObjects.keys}",
            testObjects.isNotEmpty()
        )
    }

    private fun assertStubHeaderTestObjectsRejected() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.STUB_TESTS_FILE)
        assertNotNull(psiFile)

        val testObjects = collectTestObjectsFromFile(framework, psiFile!!)
        assertTrue(
            "Stub TAEF file should have NO detected test objects (missing TAEF markers), got: ${testObjects.keys}",
            testObjects.isEmpty()
        )
    }

    private fun assertGutterIconInfoForRealTests() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE) ?: return
        val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile)
        assertNotNull("Should have document for line number lookups", document)
        val fileText = psiFile.text ?: return

        data class GutterEntry(val url: String, val isSuite: Boolean, val line: Int)

        val gutterEntries = mutableListOf<GutterEntry>()
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val info = framework.getTestLineMarkInfo(element)
                if (info != null) {
                    val line = document!!.getLineNumber(element.textOffset) + 1
                    gutterEntries.add(GutterEntry(info.urlInTestTree, info.isSuite, line))
                }
                super.visitElement(element)
            }
        })

        // Verify suites: check names and that line numbers match the source text
        val suites = gutterEntries.filter { it.isSuite }
        for (name in SampleProjectConstants.EXPECTED_SUITES) {
            val match = suites.find { it.url == "${TaefTestConstants.PROTOCOL_PREFIX}://$name" }
            assertNotNull("Should find suite gutter icon for '$name'", match)

            val expectedLine = findMacroLine(fileText, name,
                TaefTestConstants.TEST_CLASS_MACROS.toList())
            assertNotNull("Should find macro declaration for suite '$name' in source", expectedLine)
            assertEquals(
                "Suite '$name' gutter icon should be on the line where it's declared",
                expectedLine, match!!.line
            )
        }

        // Verify test methods: check names and that line numbers match the source text
        val methods = gutterEntries.filter { !it.isSuite }
        for (name in SampleProjectConstants.EXPECTED_METHODS) {
            val match = methods.find { it.url == "${TaefTestConstants.PROTOCOL_PREFIX}://$name" }
            assertNotNull("Should find method gutter icon for '$name'", match)

            val expectedLine = findMacroLine(fileText, name,
                TaefTestConstants.TEST_METHOD_MACROS.toList())
            assertNotNull("Should find macro declaration for method '$name' in source", expectedLine)
            assertEquals(
                "Method '$name' gutter icon should be on the line where it's declared",
                expectedLine, match!!.line
            )
        }
    }

    /**
     * Finds the 1-based line number where a macro invocation like `MACRO_NAME(testName)`
     * appears in the given file text. Returns null if not found.
     */
    private fun findMacroLine(fileText: String, testName: String, macroNames: List<String>): Int? {
        val lines = fileText.lines()
        for ((index, line) in lines.withIndex()) {
            for (macro in macroNames) {
                if (line.contains("$macro($testName)")) {
                    return index + 1
                }
            }
        }
        return null
    }

    private fun assertNoGutterIconInfoForNotTaefFile() {
        val framework = TaefTestFramework()
        val psiFile = findPsiFile(SampleProjectConstants.NOT_TAEF_FILE) ?: return

        val gutterInfos = mutableListOf<com.jetbrains.cidr.lang.OCTestLineMarkInfo>()
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val info = framework.getTestLineMarkInfo(element)
                if (info != null) gutterInfos.add(info)
                super.visitElement(element)
            }
        })

        assertTrue(
            "Non-TAEF file should produce NO gutter icon info, got ${gutterInfos.size} entries",
            gutterInfos.isEmpty()
        )
    }

    private fun findPsiFile(fileName: String): OCFile? {
        val projectRoot = File(project.basePath!!)
        val srcDir = File(projectRoot, "src")
        val file = srcDir.listFiles()?.find { it.name == fileName } ?: return null
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
        return PsiManager.getInstance(project).findFile(vFile) as? OCFile
    }

    // --- Infrastructure helpers ---

    private fun ensureToolchainConfigured() {
        val toolchains = CPPToolchains.getInstance()
        val tc = toolchains.defaultToolchain
        if (tc == null || toolchains.toolchains.isEmpty()) {
            fail("No C++ toolchain detected. CLion integration tests require a working " +
                "C++ compiler (MSVC, GCC, or Clang) and CMake on the system.")
        }

        // Provide CPPEnvironment so CMake generation properly configures PATH for the toolchain
        CMakeWorkspace.setEnvironmentFactoryInTests { _, _, _, _ ->
            com.jetbrains.cidr.cpp.toolchains.CPPEnvironment(tc!!)
        }
    }

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
        val filesToCopy = listOf("CMakeLists.txt", "src")
        for (name in filesToCopy) {
            val src = File(sampleDir, name)
            if (src.exists()) {
                src.copyRecursively(File(projectRoot, name), overwrite = true)
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

    private fun reloadCMakeWorkspace() {
        val workspace = CMakeWorkspace.getInstance(project)
        CMakeWorkspace.skipReloadOnProjectOpenInTests = false

        val projectRoot = java.nio.file.Paths.get(project.basePath!!)
        assertTrue(
            "CMakeLists.txt should exist at ${projectRoot.resolve("CMakeLists.txt")}",
            projectRoot.resolve("CMakeLists.txt").toFile().exists()
        )

        val stateElement = CMakeWorkspace.createStateElement(projectRoot)
        workspace.loadState(stateElement)

        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Modal(project, "Loading CMake workspace", false) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    workspace.load(true)
                }
            }
        )
        workspace.waitForReloadsToFinish(120_000)
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)

        assertFalse(
            "CMake workspace should discover targets. Default toolchain: " +
                "${CPPToolchains.getInstance().defaultToolchain?.name ?: "none"}. " +
                "Model project dir: ${workspace.modelProjectDir}",
            workspace.modelTargets.isEmpty()
        )
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
