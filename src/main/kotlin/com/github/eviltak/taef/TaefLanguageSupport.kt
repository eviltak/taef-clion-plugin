package com.github.eviltak.taef

import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.cidr.execution.testing.CidrTestRunConfigurationLanguageSupport

/**
 * Engine-agnostic service interface for TAEF language support.
 *
 * The classic engine provides an implementation backed by [TaefTestFramework]
 * (registered in `taef-lang.xml`). The Nova engine provides a text-based
 * implementation (registered in `taef-nova.xml`).
 *
 * This indirection allows [TaefRunConfigurationProducer] to be loaded on
 * both engines without eagerly pulling in classes that require the
 * disabled engine.
 */
interface TaefLanguageSupport : CidrTestRunConfigurationLanguageSupport {
    companion object {
        fun getInstance(): TaefLanguageSupport? =
            ApplicationManager.getApplication().getService(TaefLanguageSupport::class.java)
    }
}
