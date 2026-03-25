package com.github.eviltak.taef

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the classic engine module: [TaefTestFramework] PSI-based
 * detection and [TaefClassicLanguageSupport] delegation.
 *
 * These tests run with `com.intellij.cidr.lang` on the classpath
 * (no radler), verifying classic-only functionality.
 */
class TaefClassicEngineTest : BasePlatformTestCase() {

    private lateinit var framework: TaefTestFramework
    private lateinit var support: TaefClassicLanguageSupport

    override fun setUp() {
        super.setUp()
        framework = TaefTestFramework()
        support = TaefClassicLanguageSupport(framework)
    }

    // --- TaefClassicLanguageSupport delegation ---

    fun testProtocolPrefix() {
        assertEquals(TaefTestConstants.PROTOCOL_PREFIX, support.protocolPrefix)
    }

    fun testPatternSeparator() {
        assertEquals(TaefTestConstants.PATTERN_SEPARATOR, support.patternSeparatorInCommandLine)
    }

    fun testProjectSourcesScopeNotNull() {
        assertNotNull(support.getProjectSourcesScope(project))
    }

    fun testIsAvailableReturnsFalseForNullFile() {
        assertFalse(support.isAvailable(null))
    }

    fun testIsAvailableReturnsFalseForNonTaefFile() {
        val file = myFixture.configureByText("plain.cpp", "int main() { return 0; }")
        assertFalse(support.isAvailable(file))
    }

    fun testFindTestObjectReturnsNullForNonTaefElement() {
        val file = myFixture.configureByText("plain.cpp", "int x = 42;")
        val element = file.findElementAt(0)!!
        assertNull(support.findTestObject(element))
    }

    // --- TaefTestFramework negative cases ---

    fun testIsNotTestMethodForPlainCode() {
        val file = myFixture.configureByText("plain.cpp", "int x = 42;")
        val element = file.findElementAt(0)!!
        assertFalse(framework.isTestMethodOrFunction(null, element, project))
    }

    fun testIsNotTestClassForPlainCode() {
        val file = myFixture.configureByText("plain.cpp", "int x = 42;")
        val element = file.findElementAt(0)!!
        assertFalse(framework.isTestClassOrStruct(null, element, project))
    }

    fun testExtractTestReturnsNullForPlainCode() {
        val file = myFixture.configureByText("plain.cpp", "int x = 42;")
        val element = file.findElementAt(0)!!
        assertNull(support.findTestObject(element))
    }

    fun testGetTestLineMarkInfoReturnsNullForPlainCode() {
        val file = myFixture.configureByText("plain.cpp", "int x = 42;")
        val element = file.findElementAt(0)!!
        assertNull(framework.getTestLineMarkInfo(element))
    }

    fun testNullElementHandling() {
        assertFalse(framework.isTestMethodOrFunction(null, null, project))
        assertFalse(framework.isTestClassOrStruct(null, null, project))
        assertNull(framework.getTestLineMarkInfo(null))
    }

    // Note: Positive PSI detection tests (TEST_METHOD parsed as OCMacroCall)
    // require C++ include resolution which needs a full CMake project
    // configuration not available in BasePlatformTestCase. Positive cases
    // are verified via manual testing with the sample project in testData/.
}
