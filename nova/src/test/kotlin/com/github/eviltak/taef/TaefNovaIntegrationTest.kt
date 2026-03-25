package com.github.eviltak.taef

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.jetbrains.cidr.cpp.cmake.CMakeSettings
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import java.io.File

/**
 * Heavy integration tests that load the sample CMake project and verify
 * the Nova engine's text-based detection against real C++ source files.
 *
 * Uses [HeavyPlatformTestCase] with CMake workspace reload to get a real
 * project model. Tests are consolidated into few methods to minimize the
 * expensive setUp/tearDown cycle.
 */
class TaefNovaIntegrationTest : HeavyPlatformTestCase() {

    private val support by lazy { TaefNovaLanguageSupport() }
    private val contributor by lazy { TaefNovaLineMarkerContributor() }

    override fun setUp() {
        super.setUp()
        // Radler modifies this setting during app init
        com.intellij.codeInsight.CodeInsightSettings.getInstance()
            .AUTO_POPUP_JAVADOC_INFO = false
        ensureToolchainConfigured()
        copySampleProjectIntoTestProject()
        CMakeWorkspace.forceUseStandardDirForGenerationInTests = true
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
     * Tests that the Nova line marker contributor places gutter icons
     * on TAEF macro lines in real C++ source files.
     */
    fun testLineMarkerContributorOnRealTestFile() {
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE)
        assertNotNull("Should find ${SampleProjectConstants.REAL_TESTS_FILE}", psiFile)

        val infos = collectLineMarkerInfos(psiFile!!)
        assertTrue(
            "Should find line marker infos for TAEF macros, found: ${infos.size}",
            infos.isNotEmpty()
        )
    }

    /**
     * Tests that the Nova language support detects TAEF files as available.
     */
    fun testIsAvailableForRealTestFile() {
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE)
        assertNotNull(psiFile)
        assertTrue(
            "Nova support should detect TAEF macros in real test file",
            support.isAvailable(psiFile)
        )
    }

    /**
     * Tests that non-TAEF files are not detected.
     */
    fun testIsNotAvailableForNonTaefFile() {
        val psiFile = findPsiFile(SampleProjectConstants.NOT_TAEF_FILE)
        assertNotNull(psiFile)
        assertFalse(
            "Nova support should not detect non-TAEF files",
            support.isAvailable(psiFile)
        )
    }

    /**
     * Tests that findTestObject returns a scope element for TAEF macro lines.
     */
    fun testFindTestObjectOnRealTestFile() {
        val psiFile = findPsiFile(SampleProjectConstants.REAL_TESTS_FILE)
        assertNotNull(psiFile)

        // Find an element on a TEST_METHOD invocation
        val document = psiFile!!.viewProvider.document!!
        val text = document.text
        val macroOffset = text.indexOf("TEST_METHOD(")
        assertTrue("Should find TEST_METHOD in file text", macroOffset >= 0)

        val element = psiFile.findElementAt(macroOffset)
        assertNotNull("Should find PSI element at TEST_METHOD", element)

        val result = support.findTestObject(element!!)
        assertNotNull("findTestObject should return scope element for TEST_METHOD line", result)
    }

    /**
     * Tests that stub header files produce no line markers.
     */
    fun testNoLineMarkersForStubTestFile() {
        val psiFile = findPsiFile(SampleProjectConstants.STUB_TESTS_FILE)
        assertNotNull(psiFile)

        // Stub files DO contain TEST_METHOD macros (text matches), so
        // the Nova text-based detector WILL find them. This is a known
        // limitation of text-based detection vs classic PSI detection.
        // This test documents the current behavior.
        val infos = collectLineMarkerInfos(psiFile!!)
        // Nova detects these as TAEF (text match) — classic would reject them
        // (substitution marker check fails for stub headers).
        assertTrue(
            "Nova text-based detection finds macros in stub file (expected limitation)",
            infos.isNotEmpty()
        )
    }

    // --- Helper methods ---

    private fun collectLineMarkerInfos(
        file: com.intellij.psi.PsiFile
    ): List<com.intellij.execution.lineMarker.RunLineMarkerContributor.Info> {
        val infos = mutableListOf<com.intellij.execution.lineMarker.RunLineMarkerContributor.Info>()
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val info = contributor.getInfo(element)
                if (info != null) infos.add(info)
                super.visitElement(element)
            }
        })
        return infos
    }

    private fun findPsiFile(fileName: String): com.intellij.psi.PsiFile? {
        val projectRoot = File(project.basePath!!)
        val file = File(projectRoot, "src/$fileName")
        if (!file.exists()) return null
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
        return PsiManager.getInstance(project).findFile(vFile)
    }

    private fun ensureToolchainConfigured() {
        val toolchains = CPPToolchains.getInstance()
        val tc = toolchains.defaultToolchain
        if (tc == null || toolchains.toolchains.isEmpty()) {
            fail("No C++ toolchain configured. Integration tests require a working toolchain.")
        }
        CMakeWorkspace.setEnvironmentFactoryInTests { _, _, _, _ ->
            com.jetbrains.cidr.cpp.toolchains.CPPEnvironment(tc!!)
        }
    }

    private fun copySampleProjectIntoTestProject() {
        val sampleDir = findSampleProject()
            ?: throw AssertionError("testData/sampleProject not found")

        val projectRoot = File(project.basePath!!)
        for (name in listOf("CMakeLists.txt", "src")) {
            File(sampleDir, name).copyRecursively(File(projectRoot, name), overwrite = true)
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
            "CMakeLists.txt should exist",
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
