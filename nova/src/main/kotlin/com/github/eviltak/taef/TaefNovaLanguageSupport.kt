package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
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
        val text = file?.viewProvider?.document?.text ?: return false
        return TaefNovaDetector.FILE_PATTERN.containsMatchIn(text)
    }

    override fun getProtocolPrefix(): String = TaefTestConstants.PROTOCOL_PREFIX

    override fun getPatternSeparatorInCommandLine(): String = TaefTestConstants.PATTERN_SEPARATOR

    override fun getProjectSourcesScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.projectScope(project)
}

/**
 * Shared text-based TAEF macro detection for Nova.
 * Used by both [TaefNovaLanguageSupport] and [TaefNovaLineMarkerContributor].
 */
object TaefNovaDetector {

    data class TaefElementInfo(val testName: String, val isSuite: Boolean)

    /** Matches TAEF macro invocations: `TEST_METHOD(Name)`, `BEGIN_TEST_CLASS(Name)`, etc. */
    private val MACRO_PATTERN = Regex(
        """(TEST_METHOD|BEGIN_TEST_METHOD|TEST_CLASS|BEGIN_TEST_CLASS)\s*\(\s*(\w+)"""
    )

    /** Quick check for any TAEF macro in a file's text. */
    val FILE_PATTERN = Regex(
        """(?:TEST_METHOD|BEGIN_TEST_METHOD|TEST_CLASS|BEGIN_TEST_CLASS)\s*\("""
    )

    /**
     * Detects a TAEF macro at the given PsiElement's line.
     * Returns null if the element's line doesn't contain a TAEF macro.
     */
    fun detect(element: PsiElement): TaefElementInfo? {
        val document = element.containingFile?.viewProvider?.document ?: return null
        val offset = element.textRange?.startOffset ?: return null
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        val match = MACRO_PATTERN.find(lineText) ?: return null
        val macroName = match.groupValues[1]
        val testName = match.groupValues[2]
        val isSuite = macroName in TaefTestConstants.TEST_CLASS_MACROS
        return TaefElementInfo(testName, isSuite)
    }
}
