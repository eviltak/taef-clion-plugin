package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement

/**
 * Classic engine [TaefLanguageSupport] that delegates to [TaefTestFramework]
 * (registered as a `cidr.lang` `testFramework` extension).
 *
 * Exists as a separate class because a single class cannot be registered
 * as both an applicationService and an extension.
 */
class TaefClassicLanguageSupport(
    private val framework: TaefTestFramework = TaefTestFramework.getInstance()
) : TaefLanguageSupport {

    override fun findTestObject(element: PsiElement): CidrTestScopeElement? =
        framework.findTestObject(element)

    override fun isAvailable(file: PsiFile?): Boolean =
        file != null && framework.isAvailable(file)

    override fun getProtocolPrefix(): String =
        framework.protocolPrefix

    override fun getPatternSeparatorInCommandLine(): String =
        framework.patternSeparatorInCommandLine

    override fun getProjectSourcesScope(project: Project): GlobalSearchScope =
        framework.getProjectSourcesScope(project)
}
