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

    // --- TaefTestFramework positive cases ---
    // Macros are defined inline so OCMacroCall PSI is created without
    // needing CMake include resolution.

    companion object {
        // Inline TAEF header with the markers validateTaefElement looks for.
        // isTaefMacro accepts unresolved macros (can't resolve → accept),
        // so defining them inline with correct markers works.
        private val TAEF_HEADER = """
            #define TAEF_TEST_METHOD(name) void name()
            #define TEST_METHOD(name) TAEF_TEST_METHOD(name)
            #define BEGIN_TEST_METHOD(name) TAEF_TEST_METHOD(name)
            namespace WEX { namespace TestExecution {
                template<typename T> struct TestClassFactory {};
            }}
            #define TEST_CLASS(className) \
                static WEX::TestExecution::TestClassFactory<className> s_TestClassFactory
            #define BEGIN_TEST_CLASS(className) TEST_CLASS(className)
        """.trimIndent()
    }

    private fun createTaefFile(testBody: String): com.intellij.psi.PsiFile =
        myFixture.configureByText("TaefTest.cpp", "$TAEF_HEADER\n$testBody")

    private fun findMacroCall(file: com.intellij.psi.PsiFile, macroName: String): com.intellij.psi.PsiElement? {
        var found: com.intellij.psi.PsiElement? = null
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is com.jetbrains.cidr.lang.psi.OCMacroCall &&
                    element.macroReferenceElement?.name == macroName) {
                    found = element
                    return
                }
                super.visitElement(element)
            }
        })
        return found
    }

    fun testDetectsTestMethodMacro() {
        val file = createTaefFile("TEST_METHOD(MyTest);")
        val macro = findMacroCall(file, "TEST_METHOD")
        assertNotNull("Should find TEST_METHOD OCMacroCall in PSI", macro)
        assertTrue(framework.isTestMethodOrFunction(null, macro!!, project))
        assertFalse(framework.isTestClassOrStruct(null, macro, project))
    }

    fun testDetectsTestClassMacro() {
        val file = createTaefFile("TEST_CLASS(MyClass);")
        val macro = findMacroCall(file, "TEST_CLASS")
        assertNotNull("Should find TEST_CLASS OCMacroCall in PSI", macro)
        assertTrue(framework.isTestClassOrStruct(null, macro!!, project))
        assertFalse(framework.isTestMethodOrFunction(null, macro, project))
    }

    // Note: findTestObject and isAvailable delegate to the base class which
    // requires importable macros (via include resolution). Inline #defines
    // are not sufficient. These are verified via manual testing.

    fun testGetTestLineMarkInfoForTestMethod() {
        val file = createTaefFile("TEST_METHOD(MyTest);")
        val macro = findMacroCall(file, "TEST_METHOD")
        assertNotNull("Should find TEST_METHOD", macro)
        val info = framework.getTestLineMarkInfo(macro!!)
        assertNotNull("Should return line mark info", info)
        assertFalse("TEST_METHOD should not be a suite", info!!.isSuite)
    }

    fun testGetTestLineMarkInfoForTestClass() {
        val file = createTaefFile("TEST_CLASS(MyClass);")
        val macro = findMacroCall(file, "TEST_CLASS")
        assertNotNull("Should find TEST_CLASS", macro)
        val info = framework.getTestLineMarkInfo(macro!!)
        assertNotNull("Should return line mark info", info)
        assertTrue("TEST_CLASS should be a suite", info!!.isSuite)
    }
}
