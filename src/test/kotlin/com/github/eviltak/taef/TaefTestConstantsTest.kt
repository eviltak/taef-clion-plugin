package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefTestConstants] — verify macro sets, header patterns,
 * and protocol values are consistent.
 */
class TaefTestConstantsTest {

    @Test
    fun headerName_isWexTestClass() {
        assertEquals("WexTestClass.h", TaefTestConstants.HEADER_NAME)
    }

    @Test
    fun headerPattern_matchesHeaderName() {
        val regex = Regex(TaefTestConstants.HEADER_PATTERN)
        assertTrue(regex.containsMatchIn(TaefTestConstants.HEADER_NAME))
    }

    @Test
    fun headerPattern_rejectsWrongName() {
        val regex = Regex(TaefTestConstants.HEADER_PATTERN)
        assertFalse(regex.containsMatchIn("WexTestClassXh"))
        assertFalse(regex.containsMatchIn("SomeOtherHeader.h"))
    }

    @Test
    fun testMethodMacros_containsExpectedEntries() {
        assertTrue(TaefTestConstants.TEST_METHOD_MACROS.contains("TEST_METHOD"))
        assertTrue(TaefTestConstants.TEST_METHOD_MACROS.contains("BEGIN_TEST_METHOD"))
        assertEquals(2, TaefTestConstants.TEST_METHOD_MACROS.size)
    }

    @Test
    fun testClassMacros_containsExpectedEntries() {
        assertTrue(TaefTestConstants.TEST_CLASS_MACROS.contains("TEST_CLASS"))
        assertTrue(TaefTestConstants.TEST_CLASS_MACROS.contains("BEGIN_TEST_CLASS"))
        assertEquals(2, TaefTestConstants.TEST_CLASS_MACROS.size)
    }

    @Test
    fun allTestMacros_isUnionOfMethodAndClassMacros() {
        val expectedUnion = TaefTestConstants.TEST_METHOD_MACROS + TaefTestConstants.TEST_CLASS_MACROS
        assertEquals(expectedUnion, TaefTestConstants.ALL_TEST_MACROS)
    }

    @Test
    fun allTestMacros_hasCorrectSize() {
        assertEquals(4, TaefTestConstants.ALL_TEST_MACROS.size)
    }

    @Test
    fun protocolPrefix_isTaef() {
        assertEquals("taef", TaefTestConstants.PROTOCOL_PREFIX)
    }

    @Test
    fun patternSeparator_isStar() {
        assertEquals("*", TaefTestConstants.PATTERN_SEPARATOR)
    }

    @Test
    fun macroSets_areDisjoint() {
        val intersection = TaefTestConstants.TEST_METHOD_MACROS.intersect(TaefTestConstants.TEST_CLASS_MACROS)
        assertTrue("Method and class macros should not overlap: $intersection", intersection.isEmpty())
    }
}
