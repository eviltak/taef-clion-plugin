package com.github.eviltak.taef

import com.intellij.openapi.progress.ProgressManager
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.execution.CidrBuildConfiguration
import com.jetbrains.cidr.execution.CidrBuildConfigurationHelper
import com.jetbrains.cidr.execution.CidrBuildTarget
import com.jetbrains.cidr.execution.testing.CidrTestFrameworkDetector

/**
 * Fast detection: identifies CMake targets that contain TAEF tests.
 *
 * Scans source files for WexTestClass.h include and TAEF macro names.
 * Unlike the default fastFilterTestSource fallback (which only runs for
 * EXECUTABLE targets), this explicitly handles MODULE targets since TAEF
 * tests are DLLs.
 */
class TaefTestFrameworkDetector : CidrTestFrameworkDetector {

    override fun getTestConfigurationType() = TaefConfigurationType.getInstance()

    override fun getTestHeaderName(): String = TaefTestConstants.HEADER_PATTERN

    override fun getTestMacros(): Collection<String> = TaefTestConstants.ALL_TEST_MACROS

    override fun hasTestConfiguration(
        target: CidrBuildTarget<*>,
        helper: CidrBuildConfigurationHelper<out CidrBuildConfiguration, out CidrBuildTarget<*>>
    ): Boolean {
        if (target !is CMakeTarget || helper !is CMakeBuildConfigurationHelper) return false
        ProgressManager.checkCanceled()

        val config = helper.getDefaultConfiguration(target) ?: return false
        val targetType = config.targetType

        // TAEF tests are MODULE libraries (DLLs), not executables
        if (targetType != CMakeConfiguration.TargetType.MODULE_LIBRARY &&
            targetType != CMakeConfiguration.TargetType.SHARED_LIBRARY
        ) return false

        val sources = config.sources
        if (sources.isEmpty()) return false

        // Processor.process returns false to accept a file for scanning
        val result = CidrTestFrameworkDetector.fastFilterTestSource(
            sources,
            MAX_FILES_TO_CHECK,
            HEADER_CONTEXT_LENGTH
        ) { false }

        return result != null
    }

    companion object {
        private const val MAX_FILES_TO_CHECK = 100
        private const val HEADER_CONTEXT_LENGTH = 4096
    }
}
