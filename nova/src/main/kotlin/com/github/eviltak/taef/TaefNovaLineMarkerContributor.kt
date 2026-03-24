package com.github.eviltak.taef

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

/**
 * Provides TAEF test gutter icons on CLion Nova where the classic C/C++
 * PSI engine (`cidr.lang`) is not available.
 *
 * Detection is text-based via [TaefNovaDetector]: matches TAEF macro
 * identifier tokens and verifies a parenthesized argument follows. Less
 * precise than the classic [TaefTestFramework] which validates macro
 * origin and substitution markers, but sufficient for gutter icon
 * placement.
 *
 * Registered in `taef-nova.xml` behind an optional dependency on
 * `org.jetbrains.plugins.clion.radler`.
 */
class TaefNovaLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element is PsiWhiteSpace || element is PsiComment) return null

        // Only trigger on identifier tokens matching TAEF macro names
        val text = element.text ?: return null
        if (text !in TaefTestConstants.ALL_TEST_MACROS) return null

        // Use shared detector to verify full macro pattern on this line
        val info = TaefNovaDetector.detect(element) ?: return null

        val icon = if (info.isSuite) AllIcons.RunConfigurations.TestState.Run_run
                   else AllIcons.RunConfigurations.TestState.Run
        val label = if (info.isSuite) "Run Test Class" else "Run Test Method"

        return Info(
            icon,
            ExecutorAction.getActions(0),
            { label }
        )
    }
}
