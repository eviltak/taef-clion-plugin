package com.github.eviltak.taef

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.util.Key
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Execution tests that verify the full run configuration pipeline produces
 * correct TE.exe invocations and output, using mock_te.cmd.
 *
 * Plain JUnit (no BasePlatformTestCase) to avoid platform teardown side effects.
 * Tests create TaefCommandLineParams directly — the same data path as
 * TaefRunConfiguration.options → TaefCommandLineState.buildCommandLine().
 */
class TaefExecutionTest {

    private lateinit var mockTeExe: String
    private val testDll = "FakeTests.dll"

    @Before
    fun setUp() {
        val mockPath = findMockTe()
        assumeTrue("mock_te.cmd not found", mockPath != null)
        mockTeExe = mockPath!!.absolutePath
    }

    @Test
    fun executeAllTests() {
        val result = execute(TaefCommandLineParams(teExePath = mockTeExe, testDllPath = testDll))

        assertTrue("Should contain StartGroup", result.stdout.contains("StartGroup:"))
        assertTrue("Should contain Passed", result.stdout.contains("[Passed]"))
        assertTrue("Should contain Failed", result.stdout.contains("[Failed]"))
        assertTrue("Should contain Skipped", result.stdout.contains("[Skipped]"))
        assertTrue("Should contain Blocked", result.stdout.contains("[Blocked]"))
        assertTrue("Should contain Summary", result.stdout.contains("Summary: Total=4"))
        assertEquals("Exit code should be 1 (has failures)", 1, result.exitCode)
    }

    @Test
    fun executeWithNameFilter() {
        val result = execute(
            TaefCommandLineParams(
                teExePath = mockTeExe, testDllPath = testDll, nameFilter = "*Pass*",
            )
        )

        assertTrue("Should contain passing test", result.stdout.contains("TestMethodPass"))
        assertFalse("Should NOT contain failing test", result.stdout.contains("TestMethodFail"))
        assertTrue("Summary should show Total=1", result.stdout.contains("Total=1"))
        assertEquals("Exit code should be 0", 0, result.exitCode)
    }

    @Test
    fun executeWithListFlag() {
        val result = execute(
            TaefCommandLineParams(
                teExePath = mockTeExe, testDllPath = testDll, additionalArgs = "/list",
            )
        )

        assertTrue("Should list tests", result.stdout.contains("SampleNamespace::SampleTestClass::TestMethodPass"))
        assertFalse("Should NOT contain StartGroup", result.stdout.contains("StartGroup:"))
        assertEquals("Exit code should be 0", 0, result.exitCode)
    }

    @Test
    fun executeWithInproc() {
        val result = execute(
            TaefCommandLineParams(
                teExePath = mockTeExe, testDllPath = testDll, inproc = true,
            )
        )

        assertTrue("Should produce output", result.stdout.contains("StartGroup:"))
    }

    @Test
    fun outputContainsErrorWithLocation() {
        val result = execute(TaefCommandLineParams(teExePath = mockTeExe, testDllPath = testDll))

        assertTrue("Should contain file location", result.stdout.contains("[File: SampleTests.cpp, Function:"))
        assertTrue("Should contain line number", result.stdout.contains("Line: 35]"))
    }

    @Test
    fun outputContainsLogMessages() {
        val result = execute(TaefCommandLineParams(teExePath = mockTeExe, testDllPath = testDll))

        assertTrue("Should have log lines", result.stdout.contains("Log: Starting test execution..."))
        assertTrue("Should have log lines", result.stdout.contains("Log: Verifying expected result."))
    }

    // --- Helpers ---

    private data class ProcessResult(val stdout: String, val stderr: String, val exitCode: Int)

    private fun execute(params: TaefCommandLineParams): ProcessResult {
        val cmd = TaefCommandLineBuilder.build(params)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val latch = CountDownLatch(1)
        var exitCode = -1

        val handler = OSProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when (outputType.toString()) {
                    "stdout" -> stdout.append(event.text)
                    "stderr" -> stderr.append(event.text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                exitCode = event.exitCode
                latch.countDown()
            }
        })

        handler.startNotify()
        assertTrue("Process should complete within 30s", latch.await(30, TimeUnit.SECONDS))
        return ProcessResult(stdout.toString(), stderr.toString(), exitCode)
    }

    private fun findMockTe(): File? {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            val candidate = File(dir, "testData/mockTe/mock_te.cmd")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
