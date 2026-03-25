package com.github.eviltak.taef

import com.intellij.psi.PsiFile
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeBuildConfigurationHelper
import com.jetbrains.cidr.cpp.execution.CMakeTargetRunConfigurationBinder
import com.jetbrains.cidr.execution.CidrResolveConfigurationProvider

/**
 * Engine-agnostic utility for checking whether a file belongs to a CMake
 * MODULE or SHARED library target (i.e. a DLL that TE.exe can load).
 *
 * Resolves the file's CMake target via [CidrResolveConfigurationProvider]
 * (which both classic and Nova engines implement) and
 * [CMakeTargetRunConfigurationBinder].
 */
object TaefTargetUtil {

    /**
     * Returns true if the file belongs to a MODULE_LIBRARY or SHARED_LIBRARY
     * CMake target, or if the target type cannot be determined (graceful degradation).
     */
    fun isInDllTarget(file: PsiFile): Boolean {
        val resolveConfig = CidrResolveConfigurationProvider.EP_NAME.extensionList
            .firstNotNullOfOrNull { provider ->
                provider.getAllResolveConfigurationsForFile(file, null)?.firstOrNull()
            } ?: return true

        val target = CMakeTargetRunConfigurationBinder.INSTANCE
            .getTargetFromResolveConfiguration(resolveConfig) ?: return true

        val config = CMakeBuildConfigurationHelper(file.project)
            .getDefaultConfiguration(target) ?: return true

        return config.targetType == CMakeConfiguration.TargetType.MODULE_LIBRARY ||
            config.targetType == CMakeConfiguration.TargetType.SHARED_LIBRARY
    }
}
