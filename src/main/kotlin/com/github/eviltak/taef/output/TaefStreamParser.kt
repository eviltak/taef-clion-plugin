package com.github.eviltak.taef.output

/**
 * Stateful parser that processes TE.exe output lines one at a time,
 * maintaining context (current test, accumulated errors, blocked context)
 * across calls.
 *
 * Used by:
 * - Batch parsing: feed all lines, read [events] at the end
 * - Per-line converter: call [feedLine] per line, use returned events
 */
class TaefStreamParser {
    val events = mutableListOf<TaefTestEvent>()

    var currentTest: TaefTestId? = null
        private set
    private val errorLines = mutableListOf<String>()
    private val blockedContext = mutableListOf<String>()

    /** Feed a single raw line. Returns events emitted for this line only. */
    fun feedLine(line: String): List<TaefTestEvent> {
        val before = events.size
        process(TaefOutputLineClassifier.classify(line))
        return events.subList(before, events.size).toList()
    }

    /** Batch-parse a full output string. */
    fun parseAll(output: String): List<TaefTestEvent> = parseAll(output.lineSequence())

    /** Batch-parse a sequence of lines. */
    fun parseAll(lines: Sequence<String>): List<TaefTestEvent> {
        for (line in lines) feedLine(line)
        return events
    }

    private fun process(type: TaefOutputLine) {
        when (type) {
            is TaefOutputLine.StartGroup -> {
                val id = TaefTestId.parse(type.name)
                currentTest = id
                errorLines.clear()
                events.add(TaefTestEvent.TestStarted(id))
                if (blockedContext.isNotEmpty()) {
                    events.add(TaefTestEvent.TestOutput(id, blockedContext.joinToString("\n"), isError = true))
                    blockedContext.clear()
                }
            }

            is TaefOutputLine.EndGroup -> {
                if (currentTest != null) {
                    val id = currentTest!!
                    if (errorLines.isNotEmpty()) {
                        events.add(TaefTestEvent.TestOutput(id, errorLines.joinToString("\n"), isError = true))
                        errorLines.clear()
                    }
                    events.add(TaefTestEvent.TestFinished(id, type.result))
                    currentTest = null
                }
            }

            is TaefOutputLine.Error -> {
                if (currentTest != null) errorLines.add(type.text)
            }

            is TaefOutputLine.Text -> {
                if (currentTest != null) {
                    events.add(TaefTestEvent.TestOutput(currentTest!!, type.text, isError = false))
                }
            }

            is TaefOutputLine.TestBlocked -> blockedContext.add(type.text)

            is TaefOutputLine.Summary -> events.add(TaefTestEvent.Summary(
                type.total, type.passed, type.failed, type.blocked, type.notRun, type.skipped
            ))

            is TaefOutputLine.Ignored -> {}
        }
    }
}
