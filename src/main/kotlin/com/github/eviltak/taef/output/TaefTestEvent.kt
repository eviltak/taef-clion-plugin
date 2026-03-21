package com.github.eviltak.taef.output

/**
 * Identifies a TAEF test by its suite (class) name and test (method) name.
 * Parsed from the fully qualified name in TE.exe output by splitting on the
 * last `::` separator, so namespaced classes are preserved in [suiteName].
 *
 * Examples:
 * - `"SampleTestClass::TestMethodPass"` → suite=`"SampleTestClass"`, test=`"TestMethodPass"`
 * - `"NS::Class::Method"` → suite=`"NS::Class"`, test=`"Method"`
 * - `"DataClass::Test#0"` → suite=`"DataClass"`, test=`"Test#0"`
 */
data class TaefTestId(val suiteName: String, val testName: String) {
    companion object {
        fun parse(fullyQualifiedName: String): TaefTestId {
            val idx = fullyQualifiedName.lastIndexOf("::")
            return if (idx >= 0) {
                TaefTestId(fullyQualifiedName.substring(0, idx), fullyQualifiedName.substring(idx + 2))
            } else {
                TaefTestId("", fullyQualifiedName)
            }
        }
    }
}

/**
 * Events produced by parsing TE.exe output. These are the structured
 * representation that the converter translates into SMRunner service messages.
 */
sealed class TaefTestEvent {
    data class TestStarted(val id: TaefTestId) : TaefTestEvent()
    data class TestFinished(val id: TaefTestId, val result: TestResult) : TaefTestEvent()
    data class TestOutput(val id: TaefTestId, val text: String, val isError: Boolean) : TaefTestEvent()
    data class Summary(val total: Int, val passed: Int, val failed: Int, val blocked: Int, val notRun: Int, val skipped: Int) : TaefTestEvent()
}

enum class TestResult {
    PASSED, FAILED, BLOCKED, SKIPPED
}

/**
 * Classification of a single TE.exe output line. Produced by
 * [TaefOutputLineClassifier], consumed by [TaefStreamParser].
 */
sealed class TaefOutputLine {
    data class StartGroup(val name: String) : TaefOutputLine()
    data class EndGroup(val name: String, val result: TestResult) : TaefOutputLine()
    data class Error(val text: String) : TaefOutputLine()
    data class TestBlocked(val text: String) : TaefOutputLine()
    data class Text(val text: String) : TaefOutputLine()
    data class Summary(val total: Int, val passed: Int, val failed: Int,
                       val blocked: Int, val notRun: Int, val skipped: Int) : TaefOutputLine()
    data object Ignored : TaefOutputLine()
}
