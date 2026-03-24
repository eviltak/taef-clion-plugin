package com.github.eviltak.taef.output

/**
 * Stateless classifier for TE.exe output lines.
 * No IntelliJ dependencies — fully unit-testable.
 */
object TaefOutputLineClassifier {

    private val SUMMARY_PATTERN = Regex("""^Summary:\s+Total=(\d+),\s*Passed=(\d+),\s*Failed=(\d+),\s*Blocked=(\d+),\s*Not Run=(\d+),\s*Skipped=(\d+)""")

    private const val START_GROUP_PREFIX = "StartGroup: "
    private const val END_GROUP_PREFIX = "EndGroup: "
    private const val ERROR_PREFIX = "Error: "
    private const val TEST_BLOCKED_PREFIX = "TestBlocked: "

    private val END_GROUP_RESULTS = mapOf(
        "Passed" to TestResult.PASSED,
        "Failed" to TestResult.FAILED,
        "Blocked" to TestResult.BLOCKED,
        "Skipped" to TestResult.SKIPPED,
    )

    fun classify(line: String): TaefOutputLine {
        if (line.startsWith(START_GROUP_PREFIX))
            return TaefOutputLine.StartGroup(line.removePrefix(START_GROUP_PREFIX))

        if (line.startsWith(END_GROUP_PREFIX)) {
            val result = parseEndGroupResult(line.removePrefix(END_GROUP_PREFIX))
            if (result != null) return TaefOutputLine.EndGroup(result.first, result.second)
            return TaefOutputLine.Ignored
        }

        if (line.startsWith(ERROR_PREFIX))
            return TaefOutputLine.Error(line.removePrefix(ERROR_PREFIX))

        if (line.startsWith(TEST_BLOCKED_PREFIX))
            return TaefOutputLine.TestBlocked(line.removePrefix(TEST_BLOCKED_PREFIX))

        val summaryMatch = SUMMARY_PATTERN.find(line)
        if (summaryMatch != null) return TaefOutputLine.Summary(
            total = summaryMatch.groupValues[1].toInt(),
            passed = summaryMatch.groupValues[2].toInt(),
            failed = summaryMatch.groupValues[3].toInt(),
            blocked = summaryMatch.groupValues[4].toInt(),
            notRun = summaryMatch.groupValues[5].toInt(),
            skipped = summaryMatch.groupValues[6].toInt(),
        )

        if (line.isNotBlank()) return TaefOutputLine.Text(line)

        return TaefOutputLine.Ignored
    }

    private fun parseEndGroupResult(text: String): Pair<String, TestResult>? {
        val bracketStart = text.lastIndexOf('[')
        val bracketEnd = text.lastIndexOf(']')
        if (bracketStart < 0 || bracketEnd < 0 || bracketEnd <= bracketStart) return null
        val resultStr = text.substring(bracketStart + 1, bracketEnd)
        val result = END_GROUP_RESULTS[resultStr] ?: return null
        val name = text.substring(0, bracketStart).trimEnd()
        return Pair(name, result)
    }
}
