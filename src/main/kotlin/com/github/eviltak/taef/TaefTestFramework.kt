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
import com.jetbrains.cidr.lang.OCTestLineMarkInfo
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCMacroCall
import com.jetbrains.cidr.lang.symbols.OCSymbol

/**
 * PSI-level TAEF test framework. Provides gutter run/debug icons and test
 * element detection by analyzing C++ macro invocations.
 *
 * Detection uses a three-step approach:
 * 1. Fast pre-filter: match macro names (TEST_METHOD, BEGIN_TEST_METHOD, etc.)
 * 2. Header origin: verify the macro is defined in WexTestClass.h (not a
 *    same-named macro from another framework or stub)
 * 3. Substitution markers: verify the macro body contains TAEF-internal
 *    identifiers (TAEF_TEST_METHOD, TAEF_TestMethodIndexOffset, etc.)
 *
 * Steps 2 and 3 share the same resolveToSymbol() call, so the combined
 * cost is negligible over either check alone.
 */
class TaefTestFramework : CidrTestWithScopeElementsAndGeneratorFramework(
    TaefTestConstants.PROTOCOL_PREFIX, OCMacroCall::class.java
) {

    companion object {
        fun getInstance(): TaefTestFramework = findExtension(TaefTestFramework::class.java)

        // TAEF-internal identifiers that appear in the macro substitution.
        // These are stable ABI markers that distinguish real TAEF macros from
        // identically-named macros in other frameworks.
        private val TAEF_METHOD_MARKERS = listOf("TAEF_TEST_METHOD")
        private val TAEF_CLASS_MARKERS = listOf("TAEF_TestMethodIndexOffset", "TestClassFactory")
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
        val name = getMacroName(element) ?: return false
        if (name !in TaefTestConstants.TEST_METHOD_MACROS) return false
        return isTaefMacro(element, TAEF_METHOD_MARKERS, project)
    }

    override fun isTestClassOrStruct(
        symbol: OCSymbol?, element: PsiElement?, project: Project
    ): Boolean {
        if (element == null) return false
        val name = getMacroName(element) ?: return false
        if (name !in TaefTestConstants.TEST_CLASS_MACROS) return false
        return isTaefMacro(element, TAEF_CLASS_MARKERS, project)
    }

    override fun getTestLineMarkInfo(element: PsiElement?): OCTestLineMarkInfo? {
        if (element == null) return null
        val macroName = getMacroName(element) ?: return null
        if (macroName !in TaefTestConstants.ALL_TEST_MACROS) return null

        val testName = getFirstArgText(element) ?: return null
        val isSuite = macroName in TaefTestConstants.TEST_CLASS_MACROS
        val url = "${TaefTestConstants.PROTOCOL_PREFIX}://$testName"

        return object : OCTestLineMarkInfo {
            override fun getUrlInTestTree(): String = url
            override fun isSuite(): Boolean = isSuite
        }
    }

    override fun getGenerator(): Function<CidrTestScopeElementImpl, CidrTestScopeElementImpl.AbstractPropertiesGenerator> =
        Function { impl ->
            object : CidrTestScopeElementImpl.AbstractPropertiesGenerator() {
                override fun getTestPath(): String = impl.id ?: ""
                override fun isTest(): Boolean = true
                override fun getConfigurationName(): String = impl.id ?: ""
                override fun getPattern(): String = "*${impl.id.orEmpty()}*"
                override fun isPatternLike(): Boolean = true
            }
        }

    override fun extractTest(element: PsiElement): CidrTestScopeElement? {
        val macroName = getMacroName(element) ?: return null
        if (macroName !in TaefTestConstants.ALL_TEST_MACROS) return null

        val testName = getFirstArgText(element) ?: return null
        val isSuite = macroName in TaefTestConstants.TEST_CLASS_MACROS

        return createTestScopeElementForSuiteAndTest(
            if (isSuite) testName else "",
            if (isSuite) "" else testName
        )
    }

    override fun createTestObjectsDirectly(file: PsiFile): Map<String, CidrTestScopeElement> {
        if (file !is OCFile) return emptyMap()
        val result = mutableMapOf<String, CidrTestScopeElement>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val test = extractTest(element)
                if (test != null) {
                    val id = test.id ?: return
                    result[id] = test
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

    /**
     * Checks whether a macro invocation is a real TAEF macro using two checks:
     * 1. Header origin: the macro must be defined in WexTestClass.h
     * 2. Substitution markers: the macro body must contain TAEF-internal identifiers
     *
     * Both checks share the same resolveToSymbol() call so the combined cost
     * is negligible over either check alone.
     *
     * Falls back to accepting the macro if resolution fails (graceful
     * degradation — better to show a false gutter icon than miss a real test).
     */
    private fun isTaefMacro(element: PsiElement, markers: List<String>, project: Project): Boolean {
        if (element !is OCMacroCall) return false
        val macroSymbol = element.resolveToSymbol() ?: return true // can't resolve → accept

        // Check 1: header origin — macro must come from WexTestClass.h
        val containingFile = macroSymbol.getContainingOCFile(project)
        if (containingFile != null) {
            val fileName = containingFile.virtualFile?.name
            if (fileName != null && fileName != TaefTestConstants.HEADER_NAME) {
                return false
            }
        }

        // Check 2: substitution markers — macro body must contain TAEF internals
        val substitution = macroSymbol.substitution ?: return true // no body → accept
        return markers.any { substitution.contains(it) }
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
