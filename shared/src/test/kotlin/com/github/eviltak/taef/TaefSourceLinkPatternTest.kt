package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the TAEF source link regex pattern.
 * Verifies that [TaefTestConsoleProperties.SOURCE_LINK_PATTERN] correctly
 * extracts file path (group 1) and line number (group 2) from TE.exe error output.
 */
class TaefSourceLinkPatternTest {

    private val pattern = TaefTestConsoleProperties.SOURCE_LINK_PATTERN

    @Test
    fun matchesFullPatternWithFunctionField() {
        val line = "Error: Verify: AreEqual(42, 0) - Values (42, 0) [File: C:\\dev\\src\\Tests.cpp, Function: SampleTestClass::TestMethodFail, Line: 83]"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertEquals("C:\\dev\\src\\Tests.cpp", m.group(1).trim())
        assertEquals("83", m.group(2))
    }

    @Test
    fun matchesPatternWithoutFunctionField() {
        val line = "Error: something [File: test.cpp, Line: 20]"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertEquals("test.cpp", m.group(1).trim())
        assertEquals("20", m.group(2))
    }

    @Test
    fun returnsNullForNonMatchingLine() {
        val line = "SampleTestClass::TestMethodPass - verifying equality."
        assertFalse(pattern.matcher(line).find())
    }

    @Test
    fun returnsNullForEmptyLine() {
        assertFalse(pattern.matcher("").find())
    }

    @Test
    fun extractsWindowsAbsolutePath() {
        val line = "[File: C:\\Users\\dev\\project\\src\\SampleTests.cpp, Line: 42]"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertEquals("C:\\Users\\dev\\project\\src\\SampleTests.cpp", m.group(1).trim())
        assertEquals("42", m.group(2))
    }

    @Test
    fun extractsUnixPath() {
        val line = "[File: /home/user/src/tests.cpp, Function: Foo::Bar, Line: 1]"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertEquals("/home/user/src/tests.cpp", m.group(1).trim())
        assertEquals("1", m.group(2))
    }

    @Test
    fun matchesPatternEmbeddedInErrorLine() {
        val line = "Error: Verify: AreEqual(left + right, expected) - Values (0, 1) [File: C:\\dev\\taef-clion-plugin\\testData\\sampleProject\\src\\SampleTests.cpp, Function: DataDrivenClass::TestAddition, Line: 127]"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertTrue(m.group(1).trim().endsWith("SampleTests.cpp"))
        assertEquals("127", m.group(2))
    }

    @Test
    fun highlightCoversEntireBracketExpression() {
        val line = "text before [File: test.cpp, Line: 5] text after"
        val m = pattern.matcher(line)
        assertTrue(m.find())
        assertEquals("[File: test.cpp, Line: 5]", line.substring(m.start(), m.end()))
    }
}
