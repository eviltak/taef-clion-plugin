package com.github.eviltak.taef

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [TaefNovaLineMarkerContributor] text-based TAEF macro detection.
 *
 * The contributor triggers on identifier tokens matching TAEF macro names
 * and verifies a parenthesized argument follows. Designed for Nova but
 * testable with any PSI that tokenizes identifiers.
 */
class TaefNovaLineMarkerContributorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        com.intellij.codeInsight.CodeInsightSettings.getInstance()
            .AUTO_POPUP_JAVADOC_INFO = false
    }

    private val contributor = TaefNovaLineMarkerContributor()

    private fun getInfoAtCaret(content: String): Any? {
        val fullContent = "#include <WexTestClass.h>\n$content"
        myFixture.configureByText("test.cpp", fullContent)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        return element?.let { contributor.getInfo(it) }
    }

    // --- Positive detection ---

    fun testDetectsTestMethod() {
        assertNotNull("TEST_METHOD should produce a gutter icon",
            getInfoAtCaret("<caret>TEST_METHOD(MyTest);"))
    }

    fun testDetectsBeginTestMethod() {
        assertNotNull("BEGIN_TEST_METHOD should produce a gutter icon",
            getInfoAtCaret("<caret>BEGIN_TEST_METHOD(MyTest)"))
    }

    fun testDetectsTestClass() {
        assertNotNull("TEST_CLASS should produce a gutter icon",
            getInfoAtCaret("<caret>TEST_CLASS(MyClass)"))
    }

    fun testDetectsBeginTestClass() {
        assertNotNull("BEGIN_TEST_CLASS should produce a gutter icon",
            getInfoAtCaret("<caret>BEGIN_TEST_CLASS(MyClass)"))
    }

    fun testDetectsWithLeadingWhitespace() {
        assertNotNull("Indented TEST_METHOD should produce a gutter icon",
            getInfoAtCaret("    <caret>TEST_METHOD(MyTest);"))
    }

    fun testDetectsWithSpaceBeforeParen() {
        assertNotNull("TEST_METHOD with space before paren should produce a gutter icon",
            getInfoAtCaret("<caret>TEST_METHOD (MyTest);"))
    }

    // --- Negative detection ---

    fun testIgnoresPlainCode() {
        assertNull("Plain identifier should not produce a gutter icon",
            getInfoAtCaret("<caret>int x = 42;"))
    }

    fun testIgnoresUnknownMacro() {
        assertNull("Non-TAEF macro should not produce a gutter icon",
            getInfoAtCaret("<caret>SOME_OTHER_MACRO(Foo);"))
    }

    fun testIgnoresMacroNameWithoutParens() {
        assertNull("Macro name without parens should not match",
            getInfoAtCaret("<caret>TEST_METHOD"))
    }

    fun testIgnoresArgElement() {
        // Caret on the argument, not the macro name
        assertNull("Argument identifier should not produce a gutter icon",
            getInfoAtCaret("TEST_METHOD(<caret>MyTest);"))
    }
}
