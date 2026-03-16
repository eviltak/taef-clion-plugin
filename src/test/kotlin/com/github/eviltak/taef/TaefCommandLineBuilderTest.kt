package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

class TaefCommandLineBuilderTest {

    private val baseParams = TaefCommandLineParams(
        teExePath = "C:\\tools\\te.exe",
        testDllPath = "C:\\tests\\MyTest.dll",
    )

    @Test
    fun basicInvocation() {
        val cmd = TaefCommandLineBuilder.build(baseParams)
        assertEquals("C:\\tools\\te.exe", cmd.exePath)
        assertEquals(listOf("C:\\tests\\MyTest.dll"), cmd.parametersList.list)
    }

    @Test
    fun withNameFilter() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(nameFilter = "*MyClass::MyMethod*"))
        assertTrue(cmd.parametersList.list.contains("/name:*MyClass::MyMethod*"))
    }

    @Test
    fun withSelectQuery() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(selectQuery = "@Owner='alias'"))
        assertTrue(cmd.parametersList.list.contains("/select:\"@Owner='alias'\""))
    }

    @Test
    fun withInproc() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(inproc = true))
        assertTrue(cmd.parametersList.list.contains("/inproc"))
    }

    @Test
    fun withoutInproc() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(inproc = false))
        assertFalse(cmd.parametersList.list.contains("/inproc"))
    }

    @Test
    fun withWorkingDirectory() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(workingDirectory = "C:\\workdir"))
        assertEquals("C:\\workdir", cmd.workDirectory?.path)
    }

    @Test
    fun withAdditionalArgs() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(additionalArgs = "/parallel /logOutput:High"))
        val params = cmd.parametersList.list
        assertTrue(params.contains("/parallel"))
        assertTrue(params.contains("/logOutput:High"))
    }

    @Test
    fun additionalArgsWithQuotedSpaces() {
        val cmd = TaefCommandLineBuilder.build(
            baseParams.copy(additionalArgs = "/runas:\"Restricted User\" /parallel")
        )
        val params = cmd.parametersList.list
        assertTrue(params.contains("/runas:Restricted User"))
        assertTrue(params.contains("/parallel"))
    }

    @Test
    fun blankFilterOmitted() {
        val cmd = TaefCommandLineBuilder.build(baseParams.copy(nameFilter = "  "))
        val params = cmd.parametersList.list
        assertFalse(params.any { it.startsWith("/name:") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenTeExeMissing() {
        TaefCommandLineBuilder.build(baseParams.copy(teExePath = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenTeExeNull() {
        TaefCommandLineBuilder.build(baseParams.copy(teExePath = null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenDllMissing() {
        TaefCommandLineBuilder.build(baseParams.copy(testDllPath = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenDllNull() {
        TaefCommandLineBuilder.build(baseParams.copy(testDllPath = null))
    }

    @Test
    fun allParamsCombined() {
        val cmd = TaefCommandLineBuilder.build(
            TaefCommandLineParams(
                teExePath = "C:\\te.exe",
                testDllPath = "C:\\test.dll",
                nameFilter = "*Foo*",
                selectQuery = "@Priority=1",
                workingDirectory = "C:\\work",
                inproc = true,
                additionalArgs = "/parallel",
            )
        )
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
