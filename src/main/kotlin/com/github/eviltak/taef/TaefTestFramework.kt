package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.Function
import com.jetbrains.cidr.execution.testing.CidrTestFrameworkVersion
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement
import com.jetbrains.cidr.execution.testing.CidrTestScopeElementImpl
import com.jetbrains.cidr.execution.testing.CidrTestWithScopeElementsAndGeneratorFramework
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.execution.CMakeTargetRunConfigurationBinder
import com.jetbrains.cidr.lang.OCTestLineMarkInfo
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCMacroCall
import com.jetbrains.cidr.lang.symbols.OCSymbol
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurations

/**
 * PSI-level TAEF test framework. Provides gutter run/debug icons and test
 * element detection by analyzing C++ macro invocations.
 *
 * All detection funnels through [validateTaefElement], which performs:
 * 1. Macro name pre-filter (TEST_METHOD, BEGIN_TEST_CLASS, etc.)
 * 2. Header origin check (macro defined in WexTestClass.h)
 * 3. Substitution marker check (TAEF_TEST_METHOD, TestClassFactory, etc.)
 * 4. CMake target type check (file must belong to MODULE or SHARED library)
 */
class TaefTestFramework : CidrTestWithScopeElementsAndGeneratorFramework(
    TaefTestConstants.PROTOCOL_PREFIX, OCMacroCall::class.java
) {

    companion object {
        fun getInstance(): TaefTestFramework = findExtension(TaefTestFramework::class.java)

        // TAEF-internal identifiers that appear in the macro substitution.
        private val TAEF_METHOD_MARKERS = listOf("TAEF_TEST_METHOD")
        private val TAEF_CLASS_MARKERS = listOf("TAEF_TestMethodIndexOffset", "TestClassFactory")
    }

    /**
     * Result of validating a PSI element as a TAEF test declaration.
     * Null return from [validateTaefElement] means the element is not a valid TAEF test.
     */
    private data class TaefElementInfo(
        val testName: String,
        val isSuite: Boolean,
    )

    /**
     * Single validation gate for all TAEF test detection. Every public detection
     * method delegates here. Checks:
     * 1. Element is an OCMacroCall with a recognized TAEF macro name
     * 2. Macro resolves to a definition in WexTestClass.h
     * 3. Macro substitution contains TAEF-internal markers
     * 4. File belongs to a CMake MODULE or SHARED library target (not executable)
     *
     * Returns null if any check fails.
     */
    private fun validateTaefElement(element: PsiElement): TaefElementInfo? {
        val macroName = getMacroName(element) ?: return null
        if (macroName !in TaefTestConstants.ALL_TEST_MACROS) return null

        val isSuite = macroName in TaefTestConstants.TEST_CLASS_MACROS
        val markers = if (isSuite) TAEF_CLASS_MARKERS else TAEF_METHOD_MARKERS
        if (!isTaefMacro(element, markers, element.project)) return null

        if (!isInDllTarget(element)) return null

        val testName = getFirstArgText(element) ?: return null
        return TaefElementInfo(testName, isSuite)
    }

    override fun getProtocolPrefix(): String = TaefTestConstants.PROTOCOL_PREFIX

    override fun getPatternSeparatorInCommandLine(): String = TaefTestConstants.PATTERN_SEPARATOR

    // isAvailable: inherited from base class. Returns true when
    // createFrameworkVersionDirectly() != NOT_AVAILABLE.
    // We use getFrameworkVersionUsingImportedMacro (same as GTest)
    // to check if TEST_METHOD macro is importable in the file.

    override fun isTestMacroCandidate(element: PsiElement, macroNames: Set<String>): Boolean {
        val name = getMacroName(element) ?: return false
        return name in TaefTestConstants.ALL_TEST_MACROS
    }

    override fun isTestMethodOrFunction(
        symbol: OCSymbol?, element: PsiElement?, project: Project
    ): Boolean {
        if (element == null) return false
        val info = validateTaefElement(element) ?: return false
        return !info.isSuite
    }

    override fun isTestClassOrStruct(
        symbol: OCSymbol?, element: PsiElement?, project: Project
    ): Boolean {
        if (element == null) return false
        val info = validateTaefElement(element) ?: return false
        return info.isSuite
    }

    override fun getTestLineMarkInfo(element: PsiElement?): OCTestLineMarkInfo? {
        if (element == null) return null
        val info = validateTaefElement(element) ?: return null
        val qualifiedPath = buildQualifiedTestPath(element, info)
        val url = "${TaefTestConstants.PROTOCOL_PREFIX}://$qualifiedPath"

        return object : OCTestLineMarkInfo {
            override fun getUrlInTestTree(): String = url
            override fun isSuite(): Boolean = info.isSuite
        }
    }

    override fun getGenerator(): Function<CidrTestScopeElementImpl, CidrTestScopeElementImpl.AbstractPropertiesGenerator> =
        Function { impl -> createDefaultGenerator(impl) }

    override fun extractTest(element: PsiElement): CidrTestScopeElement? {
        val info = validateTaefElement(element) ?: return null

        val qualifiedPath = buildQualifiedTestPath(element, info)
        val pattern = if (info.isSuite) "$qualifiedPath::*" else qualifiedPath

        return CidrTestScopeElementImpl.createTestScopeWithPatternAndConfigurationName(
            qualifiedPath, pattern, { element }
        )
    }

    /**
     * Builds a qualified test path from the PSI context by walking up
     * from the element to find enclosing C++ class and namespace.
     *
     * For a method inside `namespace NS { class MyClass { TEST_METHOD(Foo) } }`:
     *   → `NS::MyClass::Foo`
     * For a suite: `NS::MyClass`
     */
    private fun buildQualifiedTestPath(element: PsiElement, info: TaefElementInfo): String {
        val enclosingParts = mutableListOf<String>()
        val startElement = element.parent;

        var current: PsiElement? = startElement
        while (current != null && current !is PsiFile) {
            if (current is com.jetbrains.cidr.lang.psi.OCCppNamespace || current is com.jetbrains.cidr.lang.psi.OCStruct) {
                val name = (current as? com.intellij.psi.PsiNamedElement)?.name
                if (name != null) enclosingParts.add(0, name)
            }
            current = current.parent
        }

        // For suites (TEST_CLASS), the test name IS the class name —
        // don't duplicate it from the enclosing struct
        if (info.isSuite && enclosingParts.lastOrNull() == info.testName) {
            enclosingParts.removeLastOrNull()
        }

        val qualifiedPrefix = if (enclosingParts.isNotEmpty()) {
            "${enclosingParts.joinToString("::")}::"
        } else {
            ""
        }

        return "$qualifiedPrefix${info.testName}"
    }

    override fun createTestObjectsDirectly(file: PsiFile): Map<String, CidrTestScopeElement> {
        if (file !is OCFile) return emptyMap()
        val result = mutableMapOf<String, CidrTestScopeElement>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val info = validateTaefElement(element)
                if (info != null) {
                    val test = extractTest(element)
                    if (test != null) result[info.testName] = test
                }
                super.visitElement(element)
            }
        })

        return result
    }

    override fun createFrameworkVersionDirectly(file: PsiFile): CidrTestFrameworkVersion =
        getFrameworkVersionUsingImportedMacro(file, TaefTestConstants.TEST_METHOD)
    override fun getFileVersion(file: PsiFile): Long = file.modificationStamp
    override fun createFileTestObjectIfPossible(file: PsiFile): CidrTestScopeElement? = null

    // --- Private validation helpers ---

    private fun createDefaultGenerator(
        impl: CidrTestScopeElementImpl
    ): CidrTestScopeElementImpl.AbstractPropertiesGenerator {
        return object : CidrTestScopeElementImpl.DefaultPropertiesGenerator(impl) {
            override fun getTestPath(): String = formatTestIdFromOwner(myOwner)
            override fun isTest(): Boolean = myOwner.testName.isNullOrEmpty().not()
            override fun getConfigurationName(): String = formatTestIdFromOwner(myOwner)
            override fun getPattern(): String = "*${formatTestIdFromOwner(myOwner)}*"
            override fun isPatternLike(): Boolean = true
        }
    }

    private fun formatTestIdFromOwner(owner: CidrTestScopeElementImpl): String =
        formatTestIdFromOwner(owner.suiteName.orEmpty(), owner.testName.orEmpty())

    private fun formatTestIdFromOwner(suite: String, test: String): String =
        when {
            suite.isNotEmpty() && test.isNotEmpty() -> "$suite::$test"
            suite.isNotEmpty() -> suite
            test.isNotEmpty() -> test
            else -> ""
        }

    /**
     * Checks whether a macro invocation is a real TAEF macro using two checks:
     * 1. Header origin: the macro must be defined in WexTestClass.h
     * 2. Substitution markers: the macro body must contain TAEF-internal identifiers
     *
     * Falls back to accepting the macro if resolution fails (graceful
     * degradation — better to show a false gutter icon than miss a real test).
     */
    private fun isTaefMacro(element: PsiElement, markers: List<String>, project: Project): Boolean {
        if (element !is OCMacroCall) return false
        val macroSymbol = element.resolveToSymbol() ?: return true // can't resolve → accept

        val containingFile = macroSymbol.getContainingOCFile(project)
        if (containingFile != null) {
            val fileName = containingFile.virtualFile?.name
            if (fileName != null && fileName != TaefTestConstants.HEADER_NAME) {
                return false
            }
        }

        val substitution = macroSymbol.substitution ?: return true // no body → accept
        return markers.any { substitution.contains(it) }
    }

    /**
     * Checks whether the element's source file belongs to a CMake MODULE or
     * SHARED library target. TE.exe only loads DLLs, so tests in executables
     * or static libraries are not genuine TAEF tests.
     *
     * Returns true if the target type cannot be determined (graceful degradation).
     */
    private fun isInDllTarget(element: PsiElement): Boolean {
        val file = element.containingFile as? OCFile ?: return true
        val virtualFile = file.virtualFile ?: return true
        val project = element.project

        val resolveConfig = OCResolveConfigurations.getPreselectedConfiguration(
            virtualFile, project
        ) ?: return true // can't resolve → accept

        val target = CMakeTargetRunConfigurationBinder.INSTANCE
            .getTargetFromResolveConfiguration(resolveConfig) ?: return true

        val helper = CMakeBuildConfigurationHelper(project)
        val cmakeConfig = helper.getDefaultConfiguration(target) ?: return true

        return cmakeConfig.targetType == CMakeConfiguration.TargetType.MODULE_LIBRARY ||
            cmakeConfig.targetType == CMakeConfiguration.TargetType.SHARED_LIBRARY
    }

    private fun getMacroName(element: PsiElement): String? {
        if (element !is OCMacroCall) return null
        return element.macroReferenceElement?.name
    }

    private fun getFirstArgText(element: PsiElement): String? {
        if (element !is OCMacroCall) return null
        val args = element.arguments ?: return null
        if (args.isEmpty()) return null
        return args[0].text?.trim()
    }
}
