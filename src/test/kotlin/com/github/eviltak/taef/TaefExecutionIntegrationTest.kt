package com.github.eviltak.taef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

/**
 * Platform integration tests for TaefRunConfiguration.
 * Tests config creation, TAEF field persistence, and command-line building
 * using a real Project instance (lightweight — no CMake workspace needed).
 */
class TaefExecutionIntegrationTest : BasePlatformTestCase() {

    // --- Configuration creation ---

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

    // --- Framework detector constants ---

    fun testDetectorConstants() {
        val detector = TaefTestFrameworkDetector()
        assertEquals(TaefTestConstants.HEADER_PATTERN, detector.testHeaderName)
        assertEquals(TaefTestConstants.ALL_TEST_MACROS, detector.testMacros)
    }

    // --- TAEF fields persistence round-trip ---

    fun testTaefFieldsPersistence() {
        val config = createConfig()
        config.nameFilter = "*Foo*"
        config.selectQuery = "@Priority=1"
        config.inproc = true
        config.additionalTeArgs = "/parallel"

        val element = Element("configuration")
        config.writeExternal(element)

        val restored = createConfig()
        restored.readExternal(element)

        assertEquals("*Foo*", restored.nameFilter)
        assertEquals("@Priority=1", restored.selectQuery)
        assertTrue(restored.inproc)
        assertEquals("/parallel", restored.additionalTeArgs)
    }

    fun testTaefFieldsPersistence_defaults() {
        val config = createConfig()

        val element = Element("configuration")
        config.writeExternal(element)

        val restored = createConfig()
        restored.readExternal(element)

        assertEquals("", restored.nameFilter)
        assertEquals("", restored.selectQuery)
        assertFalse(restored.inproc)
        assertEquals("", restored.additionalTeArgs)
    }

    // --- buildTaefArgs ---

    fun testBuildTaefArgs_allFields() {
        val config = createConfig()
        config.nameFilter = "*Foo*"
        config.selectQuery = "@Priority=1"
        config.inproc = true
        config.additionalTeArgs = "/parallel"

        val args = config.buildTaefArgs()
        assertTrue(args.contains("/name:*Foo*"))
        assertTrue(args.contains("/select:\"@Priority=1\""))
        assertTrue(args.contains("/inproc"))
        assertTrue(args.contains("/parallel"))
    }

    fun testBuildTaefArgs_empty() {
        val config = createConfig()
        assertEquals("", config.buildTaefArgs())
    }

    fun testBuildTaefArgs_nameFilterOnly() {
        val config = createConfig()
        config.nameFilter = "*Bar*"
        assertEquals("/name:*Bar*", config.buildTaefArgs())
    }

    fun testBuildTaefArgs_inprocOnly() {
        val config = createConfig()
        config.inproc = true
        assertEquals("/inproc", config.buildTaefArgs())
    }

    // --- TaefCommandLineBuilder ---

    fun testCommandLineBuilder_fullParams() {
        val params = TaefCommandLineParams(
            teExePath = "C:\\tools\\te.exe",
            testDllPath = "C:\\tests\\test.dll",
            nameFilter = "*Foo*",
            selectQuery = "@Priority=1",
            inproc = true,
            additionalArgs = "/parallel",
            workingDirectory = "C:\\work"
        )
        val cmd = TaefCommandLineBuilder.build(params)
        assertEquals("C:\\tools\\te.exe", cmd.exePath)
        assertTrue(cmd.parametersList.parameters.contains("C:\\tests\\test.dll"))
        assertTrue(cmd.parametersList.parameters.contains("/name:*Foo*"))
        assertTrue(cmd.parametersList.parameters.contains("/inproc"))
    }

    fun testCommandLineBuilder_missingTeExe() {
        val params = TaefCommandLineParams(teExePath = null, testDllPath = "C:\\test.dll")
        try {
            TaefCommandLineBuilder.build(params)
            fail("Should throw for missing TE.exe")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("TE.exe"))
        }
    }

    fun testCommandLineBuilder_missingDll() {
        val params = TaefCommandLineParams(teExePath = "C:\\te.exe", testDllPath = null)
        try {
            TaefCommandLineBuilder.build(params)
            fail("Should throw for missing DLL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("DLL"))
        }
    }

    // --- Helpers ---

    private fun createConfig(): TaefRunConfiguration {
        val configType = TaefConfigurationType()
        return configType.factory.createTemplateConfiguration(project) as TaefRunConfiguration
    }
}
