package com.github.eviltak.taef

import com.intellij.execution.configurations.ConfigurationFactory
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.execution.CMakeTargetRunConfigurationBinder
import com.jetbrains.cidr.cpp.execution.testing.CMakeTestRunConfiguration
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement
import com.jetbrains.cidr.execution.testing.CidrTestWithScopeElementsRunConfigurationProducer

/**
 * Enables right-click → Run/Debug on TAEF test gutter icons.
 *
 * The base class handles all the context logic:
 * - Uses [TaefLanguageSupport] to extract the test element from the clicked PSI
 * - Resolves the CMake target via [CMakeTargetRunConfigurationBinder]
 * - Creates/matches [TaefRunConfiguration] instances
 */
class TaefRunConfigurationProducer : CidrTestWithScopeElementsRunConfigurationProducer<
    CMakeConfiguration,
    CMakeTarget,
    CMakeTestRunConfiguration,
    CidrTestScopeElement
>(
    CMakeTargetRunConfigurationBinder.INSTANCE,
    TaefLanguageSupport.getInstance()
        ?: error("No TaefLanguageSupport registered; requires classic or Nova language support module")
) {
    override fun getConfigurationFactory(): ConfigurationFactory =
        TaefConfigurationType.getInstance().factory

    override fun isTestTarget(target: CMakeTarget): Boolean {
        return target.buildConfigurations.any {
            it.targetType == CMakeConfiguration.TargetType.MODULE_LIBRARY ||
                it.targetType == CMakeConfiguration.TargetType.SHARED_LIBRARY
        }
    }

    override fun doSetupConfigurationFromContext(
        configuration: CMakeTestRunConfiguration,
        context: com.intellij.execution.actions.ConfigurationContext,
        sourceElement: com.intellij.openapi.util.Ref<com.intellij.psi.PsiElement>
    ): Boolean {
        val result = super.doSetupConfigurationFromContext(configuration, context, sourceElement)
        if (!result) return false

        if (configuration is TaefRunConfiguration) {
            // Set nameFilter from testPattern (set by base class from
            // buildQualifiedTestPath via scope.getPattern())
            configuration.testData.testPattern?.takeIf { it.isNotBlank() }?.let {
                configuration.nameFilter = it
            }

            // The framework does not clone the template on first use, and
            // setupTarget overwrites executableData with the CMake target.
            // Restore settings from the template.
            val template = com.intellij.execution.RunManager.getInstance(configuration.project)
                .getConfigurationTemplate(configurationFactory)
                .configuration as? TaefRunConfiguration
            if (template != null) {
                configuration.executableData = template.executableData
                if (configuration.nameFilter.isBlank()) configuration.nameFilter = template.nameFilter
                if (configuration.selectQuery.isBlank()) configuration.selectQuery = template.selectQuery
                configuration.inproc = template.inproc
                configuration.additionalTeArgs = template.additionalTeArgs
            }
        }
        return true
    }
}
