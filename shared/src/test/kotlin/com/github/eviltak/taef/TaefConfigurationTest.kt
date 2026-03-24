package com.github.eviltak.taef

import com.intellij.openapi.options.SettingsEditor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.cpp.execution.testing.CMakeTestLauncher
import org.jdom.Element

/**
 * Lightweight platform tests for run configuration, persistence, settings
 * editor, and launcher wiring.
 */
class TaefConfigurationTest : BasePlatformTestCase() {

    // --- Launcher wiring ---

    fun testCreateLauncherExtendsCMakeTestLauncher() {
        val config = TaefTestUtil.createConfig(project)
        val env = TaefTestUtil.createEnv(project, config)
        val launcher = config.createLauncher(env)
        assertInstanceOf(launcher, TaefLauncher::class.java)
        assertInstanceOf(launcher, CMakeTestLauncher::class.java)
    }

    // --- TAEF fields persistence ---

    fun testTaefFieldsPersistence() {
        val config = TaefTestUtil.createConfig(project)
        config.nameFilter = "SampleTestClass::TestMethodPass"
        config.selectQuery = "@Priority=1"
        config.inproc = true
        config.additionalTeArgs = "/parallel"

        val element = Element("configuration")
        config.writeExternal(element)

        val restored = TaefTestUtil.createConfig(project)
        restored.readExternal(element)

        assertEquals("SampleTestClass::TestMethodPass", restored.nameFilter)
        assertEquals("@Priority=1", restored.selectQuery)
        assertTrue(restored.inproc)
        assertEquals("/parallel", restored.additionalTeArgs)
    }

    // --- Default values ---

    fun testDefaultValues() {
        val config = TaefTestUtil.createConfig(project)
        assertEquals("", config.nameFilter)
        assertEquals("", config.selectQuery)
        assertFalse(config.inproc)
        assertEquals("", config.additionalTeArgs)
    }

    // --- Settings editor round-trip ---

    fun testSettingsEditorRoundTrip() {
        val config = TaefTestUtil.createConfig(project)
        config.nameFilter = "*Foo*"
        config.selectQuery = "@Owner='me'"
        config.inproc = true
        config.additionalTeArgs = "/logOutput:High"

        // The editor is generic over CMakeAppRunConfiguration (from parent class),
        // but TaefRunConfiguration IS-A CMakeAppRunConfiguration so this is safe.
        @Suppress("UNCHECKED_CAST")
        val editor = config.configurationEditor as SettingsEditor<TaefRunConfiguration>
        editor.resetFrom(config)
        val roundTripped = TaefTestUtil.createConfig(project)
        editor.applyTo(roundTripped)

        assertEquals("*Foo*", roundTripped.nameFilter)
        assertEquals("@Owner='me'", roundTripped.selectQuery)
        assertTrue(roundTripped.inproc)
        assertEquals("/logOutput:High", roundTripped.additionalTeArgs)
    }

    // --- buildTaefArgs ---

    fun testBuildTaefArgsWithNameFilter() {
        val config = TaefTestUtil.createConfig(project)
        config.nameFilter = "SampleTestClass::TestMethodPass"
        assertTrue(config.buildTaefArgs().contains("/name:SampleTestClass::TestMethodPass"))
    }

    fun testBuildTaefArgsEmpty() {
        val config = TaefTestUtil.createConfig(project)
        assertEquals("", config.buildTaefArgs())
    }

    fun testConsolePropertiesHasLocator() {
        val props = TaefTestUtil.createConsoleProperties(project)
        assertInstanceOf(props.testLocator, TaefTestLocator::class.java)
    }
}
