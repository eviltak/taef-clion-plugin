package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefCommandLineBuilder] — verifies TE.exe command line
 * construction from [TaefCommandLineParams].
 */
class TaefCommandLineBuilderTest {

    private val baseParams = TaefCommandLineParams(
        teExePath = "C:\\tools\\te.exe",
        testDllPath = "C:\\tests\\MyTest.dll",
    )

    // --- Basic invocation ---

    @Test
    fun basicInvocation() {
        val cmd = TaefCommandLineBuilder.build(baseParams)
        assertEquals("C:\\tools\\te.exe", cmd.exePath)
        assertEquals("C:\\tests\\MyTest.dll", cmd.parametersList.list[0])
    }

    // --- /name: filter ---

    @Test
    fun nameFilterAppended() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(nameFilter = "SampleTestClass::TestMethodPass"))
        assertTrue(cmd.parametersList.list.contains("/name:SampleTestClass::TestMethodPass"))
    }

    // --- /select: query ---

    @Test
    fun selectQueryAppendedWithQuotes() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(selectQuery = "@Owner='testowner'"))
        assertTrue(cmd.parametersList.list.contains("/select:\"@Owner='testowner'\""))
    }

    // --- /inproc ---

    @Test
    fun inprocAppended() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(inproc = true))
        assertTrue(cmd.parametersList.list.contains("/inproc"))
    }

    @Test
    fun inprocOmittedWhenFalse() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(inproc = false))
        assertFalse(cmd.parametersList.list.contains("/inproc"))
    }

    // --- Additional args split ---

    @Test
    fun additionalArgsSplitAndAppended() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(additionalArgs = "/parallel /logOutput:High"))
        assertTrue(cmd.parametersList.list.contains("/parallel"))
        assertTrue(cmd.parametersList.list.contains("/logOutput:High"))
    }

    // --- Blank filters omitted ---

    @Test
    fun blankNameFilterOmitted() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(nameFilter = "  "))
        assertFalse(cmd.parametersList.list.any { it.startsWith("/name:") })
    }

    @Test
    fun blankSelectQueryOmitted() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(selectQuery = ""))
        assertFalse(cmd.parametersList.list.any { it.startsWith("/select:") })
    }

    // --- Missing TE.exe throws ---

    @Test(expected = IllegalArgumentException::class)
    fun missingTeExePathThrows() {
        TaefCommandLineBuilder.build(baseParams.copy(teExePath = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullTeExePathThrows() {
        TaefCommandLineBuilder.build(baseParams.copy(teExePath = null))
    }

    // --- Missing DLL throws ---

    @Test(expected = IllegalArgumentException::class)
    fun missingDllPathThrows() {
        TaefCommandLineBuilder.build(baseParams.copy(testDllPath = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullDllPathThrows() {
        TaefCommandLineBuilder.build(baseParams.copy(testDllPath = null))
    }

    // --- Combined ---

    @Test
    fun allParamsCombined() {
        val cmd = TaefCommandLineBuilder.build(TaefCommandLineParams(
            teExePath = "C:\\te.exe",
            testDllPath = "C:\\test.dll",
            nameFilter = "*Foo*",
            selectQuery = "@Priority=1",
            workingDirectory = "C:\\work",
            inproc = true,
            additionalArgs = "/parallel",
        ))
        val params = cmd.parametersList.list
        assertEquals("C:\\te.exe", cmd.exePath)
        assertEquals("C:\\test.dll", params[0])
        assertTrue(params.contains("/name:*Foo*"))
        assertTrue(params.contains("/select:\"@Priority=1\""))
        assertTrue(params.contains("/inproc"))
        assertTrue(params.contains("/parallel"))
        assertEquals("C:\\work", cmd.workDirectory?.path)
    }
}
