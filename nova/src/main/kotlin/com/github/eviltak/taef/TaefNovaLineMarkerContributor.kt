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
 * Registered in `taef-nova.xml` behind an optional dependency on
 * `org.jetbrains.plugins.clion.radler`.
 */
class TaefNovaLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
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
