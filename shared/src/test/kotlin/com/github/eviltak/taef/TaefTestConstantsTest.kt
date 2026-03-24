package com.github.eviltak.taef

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaefTestConstants] — verify macro sets, header patterns,
 * and protocol values are consistent.
 */
class TaefTestConstantsTest {

    @Test
    fun headerNameIsWexTestClass() {
        assertEquals("WexTestClass.h", TaefTestConstants.HEADER_NAME)
    }

    @Test
    fun headerPatternMatchesHeaderName() {
        val regex = Regex(TaefTestConstants.HEADER_PATTERN)
        assertTrue(regex.containsMatchIn(TaefTestConstants.HEADER_NAME))
    }

    @Test
    fun headerPatternRejectsWrongName() {
        val regex = Regex(TaefTestConstants.HEADER_PATTERN)
        assertFalse(regex.containsMatchIn("SomeOtherHeader.h"))
        assertFalse(regex.containsMatchIn("WexTestClassXh"))
    }

    @Test
    fun allTestMacrosIsUnionOfMethodAndClass() {
        assertEquals(
            TaefTestConstants.TEST_METHOD_MACROS + TaefTestConstants.TEST_CLASS_MACROS,
            TaefTestConstants.ALL_TEST_MACROS
        )
    }

    @Test
    fun macroSetsAreDisjoint() {
        val overlap = TaefTestConstants.TEST_METHOD_MACROS.intersect(TaefTestConstants.TEST_CLASS_MACROS)
        assertTrue("Method and class macros should not overlap", overlap.isEmpty())
    }

    @Test
    fun protocolPrefixIsTaef() {
        assertEquals("taef", TaefTestConstants.PROTOCOL_PREFIX)
    }
}
