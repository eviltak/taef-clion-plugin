package com.github.eviltak.taef

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.execution.testing.CidrTestFrameworkDetector

/**
 * Tests that all TAEF extension points are correctly registered and
 * discoverable by the IDE, and that registered components have correct
 * metadata.
 */
class TaefExtensionPointTest : BasePlatformTestCase() {

    // --- Registration ---

    fun testConfigurationTypeRegistered() {
        val configType = ConfigurationTypeUtil.findConfigurationType(TaefConfigurationType::class.java)
        assertNotNull("TaefConfigurationType should be registered", configType)
    }

    fun testRunConfigurationProducerRegistered() {
        val producers = RunConfigurationProducer.getProducers(project)
        assertTrue("TaefRunConfigurationProducer should be registered",
            producers.any { it is TaefRunConfigurationProducer })
    }

    fun testTestFrameworkDetectorRegistered() {
        val detectors = CidrTestFrameworkDetector.EP_NAME.extensionList
        assertTrue("TaefTestFrameworkDetector should be registered",
            detectors.any { it is TaefTestFrameworkDetector })
    }

    // --- Configuration type metadata ---

    fun testConfigurationTypeId() {
        val configType = ConfigurationTypeUtil.findConfigurationType(TaefConfigurationType::class.java)
        assertEquals("TaefRunConfiguration", configType.id)
    }

    fun testConfigurationTypeDisplayName() {
        val configType = ConfigurationTypeUtil.findConfigurationType(TaefConfigurationType::class.java)
        assertEquals("TAEF Test", configType.displayName)
    }

    fun testConfigurationFactoryCreatesTaefRunConfiguration() {
        val configType = ConfigurationTypeUtil.findConfigurationType(TaefConfigurationType::class.java)
        val factory = configType.configurationFactories.first()
        val config = factory.createTemplateConfiguration(project)
        assertInstanceOf(config, TaefRunConfiguration::class.java)
    }

    // --- Detector metadata ---

    fun testDetectorTestHeaderPattern() {
        val detector = CidrTestFrameworkDetector.EP_NAME.extensionList
            .filterIsInstance<TaefTestFrameworkDetector>().first()
        assertEquals(TaefTestConstants.HEADER_PATTERN, detector.testHeaderName)
    }

    fun testDetectorTestMacros() {
        val detector = CidrTestFrameworkDetector.EP_NAME.extensionList
            .filterIsInstance<TaefTestFrameworkDetector>().first()
        val macros = detector.testMacros.toSet()
        assertTrue("TEST_METHOD" in macros)
        assertTrue("BEGIN_TEST_METHOD" in macros)
        assertTrue("TEST_CLASS" in macros)
        assertTrue("BEGIN_TEST_CLASS" in macros)
    }

    fun testDetectorReturnsCorrectConfigurationType() {
        val detector = CidrTestFrameworkDetector.EP_NAME.extensionList
            .filterIsInstance<TaefTestFrameworkDetector>().first()
        assertInstanceOf(detector.testConfigurationType, TaefConfigurationType::class.java)
    }

    // --- Console filter provider ---

    fun testConsoleFilterProviderRegistered() {
        val providers = ConsoleFilterProvider.FILTER_PROVIDERS.extensionList
        assertTrue("TaefConsoleFilterProvider should be registered",
            providers.any { it is TaefConsoleFilterProvider })
    }
}
