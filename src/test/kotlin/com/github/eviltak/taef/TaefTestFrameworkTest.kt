package com.github.eviltak.taef

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCMacroCall
import com.jetbrains.cidr.lang.psi.OCMacroCallArgument
import com.jetbrains.cidr.lang.psi.OCReferenceElement
import com.jetbrains.cidr.lang.symbols.cpp.OCMacroSymbol
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TaefTestFramework] detection logic.
 *
 * Uses MockK to simulate PSI elements — tests the macro name pre-filter,
 * header origin check, substitution marker check, and null-safety paths.
 */
class TaefTestFrameworkTest {

    private lateinit var framework: TaefTestFramework
    private lateinit var project: Project

    @Before
    fun setUp() {
        framework = TaefTestFramework()
        project = mockk(relaxed = true)
    }

    // --- isAvailable ---

    // isAvailable: not overridden — uses base class which delegates to
    // createFrameworkVersionDirectly → getFrameworkVersionUsingImportedMacro.
    // Tested via integration tests (TaefCMakeIntegrationTest.testPsiDetection).

    // --- isTestMacroCandidate ---

    @Test
    fun isTestMacroCandidate_testMethod_returnsTrue() {
        val macro = createMockMacroCall("TEST_METHOD")
        assertTrue(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_beginTestMethod_returnsTrue() {
        val macro = createMockMacroCall("BEGIN_TEST_METHOD")
        assertTrue(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_testClass_returnsTrue() {
        val macro = createMockMacroCall("TEST_CLASS")
        assertTrue(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_beginTestClass_returnsTrue() {
        val macro = createMockMacroCall("BEGIN_TEST_CLASS")
        assertTrue(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_unknownMacro_returnsFalse() {
        val macro = createMockMacroCall("TEST")
        assertFalse(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_gtestMacro_returnsFalse() {
        val macro = createMockMacroCall("TEST_F")
        assertFalse(framework.isTestMacroCandidate(macro, emptySet()))
    }

    @Test
    fun isTestMacroCandidate_nonMacroElement_returnsFalse() {
        val element = mockk<PsiElement>()
        assertFalse(framework.isTestMacroCandidate(element, emptySet()))
    }

    // --- isTestMethodOrFunction ---

    @Test
    fun isTestMethodOrFunction_nonMacro_returnsFalse() {
        val element = mockk<PsiElement>()
        assertFalse(framework.isTestMethodOrFunction(null, element, project))
    }

    @Test
    fun isTestMethodOrFunction_nullElement_returnsFalse() {
        assertFalse(framework.isTestMethodOrFunction(null, null, project))
    }

    @Test
    fun isTestMethodOrFunction_wrongMacroName_returnsFalse() {
        val macro = createMockMacroCall(TaefTestConstants.TEST_CLASS)
        assertFalse(framework.isTestMethodOrFunction(null, macro, project))
    }

    @Test
    fun isTestMethodOrFunction_resolvedFromWrongHeader_returnsFalse() {
        val macro = createFullyResolvedMacro(
            macroName = TaefTestConstants.TEST_METHOD,
            headerName = "SomeOtherHeader.h",
            substitution = "TAEF_TEST_METHOD(x)"
        )
        assertFalse(framework.isTestMethodOrFunction(null, macro, project))
    }

    @Test
    fun isTestMethodOrFunction_correctHeaderButWrongSubstitution_returnsFalse() {
        val macro = createFullyResolvedMacro(
            macroName = TaefTestConstants.TEST_METHOD,
            headerName = TaefTestConstants.HEADER_NAME,
            substitution = "SOME_OTHER_IMPL(x)"
        )
        assertFalse(framework.isTestMethodOrFunction(null, macro, project))
    }

    @Test
    fun isTestMethodOrFunction_stubHeaderRejected() {
        val macro = createFullyResolvedMacro(
            macroName = TaefTestConstants.TEST_METHOD,
            headerName = "FakeWexTestClass.h",
            substitution = "void testMethod() {}"
        )
        assertFalse(framework.isTestMethodOrFunction(null, macro, project))
    }

    // Positive detection tested via TaefCMakeIntegrationTest.testPsiDetection
    // (requires real CMake workspace for target type validation)

    // --- isTestClassOrStruct ---

    @Test
    fun isTestClassOrStruct_nonMacro_returnsFalse() {
        val element = mockk<PsiElement>()
        assertFalse(framework.isTestClassOrStruct(null, element, project))
    }

    @Test
    fun isTestClassOrStruct_nullElement_returnsFalse() {
        assertFalse(framework.isTestClassOrStruct(null, null, project))
    }

    @Test
    fun isTestClassOrStruct_wrongMacroName_returnsFalse() {
        val macro = createMockMacroCall(TaefTestConstants.TEST_METHOD)
        assertFalse(framework.isTestClassOrStruct(null, macro, project))
    }

    @Test
    fun isTestClassOrStruct_wrongHeader_returnsFalse() {
        val macro = createFullyResolvedMacro(
            macroName = TaefTestConstants.TEST_CLASS,
            headerName = "boost/test/unit_test.hpp",
            substitution = "TAEF_TestMethodIndexOffset"
        )
        assertFalse(framework.isTestClassOrStruct(null, macro, project))
    }

    // Positive detection tested via TaefCMakeIntegrationTest.testPsiDetection

    // --- getTestLineMarkInfo ---

    @Test
    fun getTestLineMarkInfo_testMethod_returnsMethodInfo() {
        val macro = createMockMacroCallWithArg("TEST_METHOD", "MyTestMethod")
        val info = framework.getTestLineMarkInfo(macro)
        assertNotNull(info)
        assertFalse("TEST_METHOD should not be a suite", info!!.isSuite)
        assertEquals("taef://MyTestMethod", info.urlInTestTree)
    }

    @Test
    fun getTestLineMarkInfo_testClass_returnsSuiteInfo() {
        val macro = createMockMacroCallWithArg("TEST_CLASS", "MyTestClass")
        val info = framework.getTestLineMarkInfo(macro)
        assertNotNull(info)
        assertTrue("TEST_CLASS should be a suite", info!!.isSuite)
        assertEquals("taef://MyTestClass", info.urlInTestTree)
    }

    @Test
    fun getTestLineMarkInfo_unknownMacro_returnsNull() {
        val macro = createMockMacroCallWithArg("TEST_F", "Foo")
        assertNull(framework.getTestLineMarkInfo(macro))
    }

    @Test
    fun getTestLineMarkInfo_nullElement_returnsNull() {
        assertNull(framework.getTestLineMarkInfo(null))
    }

    @Test
    fun getTestLineMarkInfo_noArgs_returnsNull() {
        val macro = createMockMacroCall(TaefTestConstants.TEST_METHOD)
        every { macro.arguments } returns null
        assertNull(framework.getTestLineMarkInfo(macro))
    }

    @Test
    fun getTestLineMarkInfo_stubHeader_returnsNull() {
        val macro = createFullyResolvedMacroWithArg(
            macroName = TaefTestConstants.TEST_METHOD,
            argText = "FakeTest",
            headerName = TaefTestConstants.HEADER_NAME,
            substitution = "void FakeTest()"
        )
        assertNull("Stub macro should not get gutter icon", framework.getTestLineMarkInfo(macro))
    }

    @Test
    fun getTestLineMarkInfo_wrongHeader_returnsNull() {
        val macro = createFullyResolvedMacroWithArg(
            macroName = TaefTestConstants.TEST_METHOD,
            argText = "FakeTest",
            headerName = "OtherFramework.h",
            substitution = "TAEF_TEST_METHOD(FakeTest)"
        )
        assertNull("Macro from wrong header should not get gutter icon", framework.getTestLineMarkInfo(macro))
    }

    @Test
    fun getTestLineMarkInfo_realHeader_returnsInfo() {
        val macro = createFullyResolvedMacroWithArg(
            macroName = TaefTestConstants.TEST_METHOD,
            argText = "RealTest",
            headerName = TaefTestConstants.HEADER_NAME,
            substitution = "TAEF_TEST_METHOD(RealTest)"
        )
        val info = framework.getTestLineMarkInfo(macro)
        assertNotNull("Real TAEF macro should get gutter icon", info)
        assertEquals("${TaefTestConstants.PROTOCOL_PREFIX}://RealTest", info!!.urlInTestTree)
    }

    // --- Protocol and separator ---

    @Test
    fun protocolPrefix_isTaef() {
        assertEquals("taef", framework.protocolPrefix)
    }

    @Test
    fun patternSeparator_isStar() {
        assertEquals("*", framework.patternSeparatorInCommandLine)
    }

    // Graceful degradation (unresolved macros, null files, empty substitution)
    // tested via TaefCMakeIntegrationTest.testPsiDetection which exercises the
    // full validation pipeline in a real CMake workspace.

    // --- Helper methods ---

    private fun createMockMacroCall(macroName: String): OCMacroCall {
        val macro = mockk<OCMacroCall>(relaxed = true)
        val ref = mockk<OCReferenceElement>()
        every { ref.name } returns macroName
        every { macro.macroReferenceElement } returns ref
        every { macro.resolveToSymbol() } returns null
        every { macro.arguments } returns null
        every { macro.project } returns project
        every { macro.parent } returns mockk<PsiFile>(relaxed = true)
        return macro
    }

    private fun createMockMacroCallWithArg(macroName: String, argText: String): OCMacroCall {
        val macro = createMockMacroCall(macroName)
        val arg = mockk<OCMacroCallArgument>()
        every { arg.text } returns argText
        every { macro.arguments } returns listOf(arg)
        return macro
    }

    private fun createFullyResolvedMacro(
        macroName: String,
        headerName: String,
        substitution: String
    ): OCMacroCall {
        val macro = createMockMacroCall(macroName)
        val symbol = mockk<OCMacroSymbol>(relaxed = true)
        val ocFile = mockk<OCFile>()
        val vFile = mockk<VirtualFile>()

        every { macro.resolveToSymbol() } returns symbol
        every { macro.project } returns project
        every { symbol.getContainingOCFile(any()) } returns ocFile
        every { ocFile.virtualFile } returns vFile
        every { vFile.name } returns headerName
        every { symbol.substitution } returns substitution

        return macro
    }

    private fun createFullyResolvedMacroWithArg(
        macroName: String,
        argText: String,
        headerName: String,
        substitution: String
    ): OCMacroCall {
        val macro = createFullyResolvedMacro(macroName, headerName, substitution)
        val arg = mockk<OCMacroCallArgument>()
        every { arg.text } returns argText
        every { macro.arguments } returns listOf(arg)
        return macro
    }
}
