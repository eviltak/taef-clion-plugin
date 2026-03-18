package com.github.eviltak.taef

/**
 * Structured test event produced by parsing TE.exe output.
 */
sealed class TaefTestEvent {
    data class TestStarted(val fullyQualifiedName: String) : TaefTestEvent()
    data class TestFinished(val fullyQualifiedName: String, val result: TestResult) : TaefTestEvent()
    data class TestOutput(val fullyQualifiedName: String, val text: String, val isError: Boolean) : TaefTestEvent()
    data class Summary(val total: Int, val passed: Int, val failed: Int, val blocked: Int, val notRun: Int, val skipped: Int) : TaefTestEvent()
}

enum class TestResult {
    PASSED, FAILED, BLOCKED, SKIPPED
}

/**
 * Pure state machine that parses TE.exe console output line by line
 * into structured [TaefTestEvent]s.
 *
 * TE.exe output format:
 * ```
 * StartGroup: Namespace::ClassName::MethodName
 *     Log: <comment text>
 *     Error: <error text>  [File: x.cpp, Function: ..., Line: 42]
 * EndGroup: Namespace::ClassName::MethodName [Passed]
 * EndGroup: Namespace::ClassName::MethodName [Failed]
 * EndGroup: Namespace::ClassName::MethodName [Blocked]
 * EndGroup: Namespace::ClassName::MethodName [Skipped]
 *
 * Summary: Total=N, Passed=P, Failed=F, Blocked=B, Not Run=R, Skipped=S
 * ```
 *
 * No IntelliJ Platform dependencies — fully unit-testable.
 */
object TaefOutputParser {

    private val START_GROUP_PATTERN = Regex("""^StartGroup:\s+(.+)$""")
    private val END_GROUP_PATTERN = Regex("""^EndGroup:\s+(.+?)\s+\[(Passed|Failed|Blocked|Skipped)]$""")
    private val LOG_PATTERN = Regex("""^\s+Log:\s+(.+)$""")
    private val ERROR_PATTERN = Regex("""^\s+Error:\s+(.+)$""")
    private val SUMMARY_PATTERN = Regex("""^Summary:\s+Total=(\d+),\s*Passed=(\d+),\s*Failed=(\d+),\s*Blocked=(\d+),\s*Not Run=(\d+),\s*Skipped=(\d+)""")

    /**
     * Parse a sequence of TE.exe output lines into test events.
     */
    fun parse(lines: Sequence<String>): List<TaefTestEvent> {
        val events = mutableListOf<TaefTestEvent>()
        var currentTest: String? = null
        val errorLines = mutableListOf<String>()

        for (line in lines) {
            val startMatch = START_GROUP_PATTERN.find(line)
            if (startMatch != null) {
                val name = startMatch.groupValues[1]
                currentTest = name
                errorLines.clear()
                events.add(TaefTestEvent.TestStarted(name))
                continue
            }

            val endMatch = END_GROUP_PATTERN.find(line)
            if (endMatch != null) {
                val name = endMatch.groupValues[1]
                val result = when (endMatch.groupValues[2]) {
                    "Passed" -> TestResult.PASSED
                    "Failed" -> TestResult.FAILED
                    "Blocked" -> TestResult.BLOCKED
                    "Skipped" -> TestResult.SKIPPED
                    else -> TestResult.FAILED
                }

                // Flush accumulated error lines as a single error output
                if (errorLines.isNotEmpty()) {
                    events.add(TaefTestEvent.TestOutput(name, errorLines.joinToString("\n"), isError = true))
                    errorLines.clear()
                }

                events.add(TaefTestEvent.TestFinished(name, result))
                currentTest = null
                continue
            }

            if (currentTest != null) {
                val logMatch = LOG_PATTERN.find(line)
                if (logMatch != null) {
                    events.add(TaefTestEvent.TestOutput(currentTest, logMatch.groupValues[1], isError = false))
                    continue
                }

                val errorMatch = ERROR_PATTERN.find(line)
                if (errorMatch != null) {
                    errorLines.add(errorMatch.groupValues[1])
                    continue
                }
            }

            val summaryMatch = SUMMARY_PATTERN.find(line)
            if (summaryMatch != null) {
                events.add(TaefTestEvent.Summary(
                    total = summaryMatch.groupValues[1].toInt(),
                    passed = summaryMatch.groupValues[2].toInt(),
                    failed = summaryMatch.groupValues[3].toInt(),
                    blocked = summaryMatch.groupValues[4].toInt(),
                    notRun = summaryMatch.groupValues[5].toInt(),
                    skipped = summaryMatch.groupValues[6].toInt(),
                ))
                continue
            }

            // Unrecognized lines are silently ignored
        }

        return events
    }

    /**
     * Convenience overload for parsing a string of output.
     */
    fun parse(output: String): List<TaefTestEvent> =
        parse(output.lineSequence())
}
