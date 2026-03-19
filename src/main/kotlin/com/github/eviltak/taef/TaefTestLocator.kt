package com.github.eviltak.taef

import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

/**
 * Locates TAEF test source elements from test tree URLs.
 *
 * Resolves `taef://TestName` URLs to PSI locations for "Navigate to Test"
 * functionality in the test results tree.
 */
class TaefTestLocator : SMTestLocator {

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<com.intellij.execution.Location<*>> {
        // TODO: resolve test name to PSI element for navigation
        return emptyList()
    }

    companion object {
        val INSTANCE = TaefTestLocator()
    }
}
