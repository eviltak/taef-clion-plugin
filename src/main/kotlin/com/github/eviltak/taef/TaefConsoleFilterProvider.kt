package com.github.eviltak.taef

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.LazyFileHyperlinkInfo
import com.intellij.openapi.project.Project
import java.util.regex.Pattern

/**
 * Registers [TaefSourceLinkFilter] for all project consoles.
 * The filter only activates on lines matching the TAEF error pattern.
 */
class TaefConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(TaefSourceLinkFilter(project))
}

/**
 * Console filter that makes TAEF error source references clickable.
 *
 * Matches patterns like:
 * - `[File: C:\dev\src\Tests.cpp, Function: Class::Method, Line: 83]`
 * - `[File: C:\dev\src\Tests.cpp, Line: 83]` (no Function field)
 */
class TaefSourceLinkFilter(private val project: Project) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val m = TaefTestConsoleProperties.SOURCE_LINK_PATTERN.matcher(line)
        if (!m.find()) return null

        val filePath = m.group(1).trim()
        val lineNum = m.group(2).toIntOrNull() ?: return null
        val documentLine = maxOf(lineNum - 1, 0)

        val offset = entireLength - line.length
        return Filter.Result(
            offset + m.start(),
            offset + m.end(),
            LazyFileHyperlinkInfo(project, filePath, documentLine, 0)
        )
    }
}
