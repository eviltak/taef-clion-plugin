package com.github.eviltak.taef

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform integration tests for TaefRunConfiguration.
 * Tests config creation, validation, and persistence using a real Project instance.
 */
class TaefRunConfigurationIntegrationTest : BasePlatformTestCase() {

    // --- Configuration creation & validation ---

    fun testConfigurationTypeHasCorrectId() {
        val configType = TaefConfigurationType()
        assertEquals(TaefConfigurationType.ID, configType.id)
        assertEquals(TaefConfigurationType.DISPLAY_NAME, configType.displayName)
    }

    fun testFactoryCreatesRunConfiguration() {
        val config = createConfig()
        assertNotNull(config)
        assertInstanceOf(config, TaefRunConfiguration::class.java)
    }

    fun testCheckConfiguration_validConfig() {
        val config = createConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.testDllPath = "C:\\tests\\test.dll"
        config.checkConfiguration()
    }

    fun testCheckConfiguration_missingTeExe() {
        val config = createConfig()
        config.options.testDllPath = "C:\\tests\\test.dll"
        try {
            config.checkConfiguration()
            fail("Should throw RuntimeConfigurationError")
        } catch (e: RuntimeConfigurationError) {
            assertTrue(e.localizedMessage.contains("TE.exe"))
        }
    }

    fun testCheckConfiguration_missingDllAndTarget() {
        val config = createConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        try {
            config.checkConfiguration()
            fail("Should throw RuntimeConfigurationError")
        } catch (e: RuntimeConfigurationError) {
            assertTrue(e.localizedMessage.contains("CMake target") || e.localizedMessage.contains("DLL"))
        }
    }

    fun testCheckConfiguration_cmakeTargetSuffices() {
        val config = createConfig()
        config.options.teExePath = "C:\\tools\\te.exe"
        config.options.cmakeTarget = "MyTestTarget"
        config.checkConfiguration()
    }

    // --- Suggested name ---

    fun testSuggestedName() {
        val config = createConfig()
        // isGeneratedName requires suggestedName() != null, so false when no target set
        assertNull("Should be null when nothing is set", config.suggestedName())

        config.options.cmakeTarget = "SampleTests"
        assertEquals("SampleTests", config.suggestedName())
        assertTrue("Should be generated name when target is set", config.isGeneratedName)

        config.options.testDllPath = "C:\\path\\to\\test.dll"
        assertEquals("SampleTests", config.suggestedName())

        config.options.cmakeTarget = ""
        assertEquals("C:\\path\\to\\test.dll", config.suggestedName())
    }

    // --- Options persistence round-trip ---

    fun testOptionsPersistence() {
        val config = createConfig()
        config.options.teExePath = "C:\\te.exe"
        config.options.testDllPath = "C:\\test.dll"
        config.options.nameFilter = "*Foo*"
        config.options.selectQuery = "@Priority=1"
        config.options.workingDirectory = "C:\\work"
        config.options.inproc = true
        config.options.additionalArgs = "/parallel"
        config.options.cmakeTarget = "MyTarget"

        val element = org.jdom.Element("configuration")
        config.writeExternal(element)

        val restored = createConfig()
        restored.readExternal(element)

        assertEquals("C:\\te.exe", restored.options.teExePath)
        assertEquals("C:\\test.dll", restored.options.testDllPath)
        assertEquals("*Foo*", restored.options.nameFilter)
        assertEquals("@Priority=1", restored.options.selectQuery)
        assertEquals("C:\\work", restored.options.workingDirectory)
        assertTrue(restored.options.inproc)
        assertEquals("/parallel", restored.options.additionalArgs)
        assertEquals("MyTarget", restored.options.cmakeTarget)
    }

    // --- Helpers ---

    private fun createConfig(): TaefRunConfiguration {
        val configType = TaefConfigurationType()
        val factory = configType.configurationFactories[0]
        return factory.createTemplateConfiguration(project) as TaefRunConfiguration
    }
}
