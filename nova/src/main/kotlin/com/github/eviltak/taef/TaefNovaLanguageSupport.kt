package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.rider.cpp.fileType.lexer.CppTokenTypes
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement
import com.jetbrains.cidr.execution.testing.CidrTestScopeElementImpl

/**
 * Text-based [TaefLanguageSupport] for CLion Nova.
 *
 * Uses regex matching on document text rather than classic C++ PSI to
 * detect TAEF test macros. Shares detection logic with
 * [TaefNovaLineMarkerContributor] via [TaefNovaDetector].
 *
 * Registered as the [TaefLanguageSupport] applicationService in
 * `taef-nova.xml` when the Radler (Nova) engine is active.
 */
class TaefNovaLanguageSupport : TaefLanguageSupport {

    override fun findTestObject(element: PsiElement): CidrTestScopeElement? {
        val info = TaefNovaDetector.detect(element) ?: return null
        val pattern = if (info.isSuite) "${info.testName}::*" else info.testName
        return CidrTestScopeElementImpl.createTestScopeWithPatternAndConfigurationName(
            info.testName, pattern, { element }
        )
    }

    override fun isAvailable(file: PsiFile?): Boolean {
        if (file == null) return false
        if (!TaefNovaDetector.fileIncludesTaefHeader(file)) return false
        return TaefTargetUtil.isInDllTarget(file)
    }

    override fun getProtocolPrefix(): String = TaefTestConstants.PROTOCOL_PREFIX

    override fun getPatternSeparatorInCommandLine(): String = TaefTestConstants.PATTERN_SEPARATOR

    override fun getProjectSourcesScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.projectScope(project)
}

/**
 * Token-based TAEF macro detection for Nova.
 * Used by both [TaefNovaLanguageSupport] and [TaefNovaLineMarkerContributor].
 *
 * Uses Nova's C++ lexer token types ([CppTokenTypes]) instead of regex
 * for more robust detection — won't match macros inside comments or strings.
 */
object TaefNovaDetector {

    data class TaefElementInfo(val testName: String, val isSuite: Boolean)

    /**
     * Checks whether the file includes WexTestClass.h by scanning the PSI
     * tree for HEADER_PATH_LITERAL tokens containing the header name.
     */
    fun fileIncludesTaefHeader(file: PsiFile): Boolean {
        var found = false
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (found) return
                if (element.node?.elementType == CppTokenTypes.HEADER_PATH_LITERAL &&
                    element.text.contains(TaefTestConstants.HEADER_NAME)) {
                    found = true
                    return
                }
                super.visitElement(element)
            }
        })
        return found
    }

    /**
     * Detects a TAEF macro invocation at the given PsiElement.
     *
     * The element must be an IDENTIFIER token with a TAEF macro name,
     * followed (after optional whitespace) by LPAR. The first IDENTIFIER
     * inside the parens is extracted as the test name.
     *
     * Returns null if the element is not a TAEF macro call or if the
     * file doesn't include WexTestClass.h.
     */
    fun detect(element: PsiElement): TaefElementInfo? {
        if (element.node?.elementType != CppTokenTypes.IDENTIFIER) return null
        val macroName = element.text ?: return null
        if (macroName !in TaefTestConstants.ALL_TEST_MACROS) return null

        var sibling = element.nextSibling
        while (sibling != null && CppTokenTypes.WHITESPACES.contains(sibling.node?.elementType)) {
            sibling = sibling.nextSibling
        }
        if (sibling?.node?.elementType != CppTokenTypes.LPAR) return null

        var arg = sibling.nextSibling
        while (arg != null && CppTokenTypes.WHITESPACES.contains(arg.node?.elementType)) {
            arg = arg.nextSibling
        }
        if (arg?.node?.elementType != CppTokenTypes.IDENTIFIER) return null
        val testName = arg.text ?: return null

        val file = element.containingFile ?: return null
        if (!fileIncludesTaefHeader(file)) return null

        val isSuite = macroName in TaefTestConstants.TEST_CLASS_MACROS
        return TaefElementInfo(testName, isSuite)
    }
}
