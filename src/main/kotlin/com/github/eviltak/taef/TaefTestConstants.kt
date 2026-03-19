package com.github.eviltak.taef

/**
 * TAEF test framework constants: header names, macro names, and protocol identifiers.
 */
object TaefTestConstants {
    const val HEADER_PATTERN = "WexTestClass[.]h"
    const val HEADER_NAME = "WexTestClass.h"
    const val PROTOCOL_PREFIX = "taef"
    const val PATTERN_SEPARATOR = "*"

    const val TEST_METHOD = "TEST_METHOD"
    const val BEGIN_TEST_METHOD = "BEGIN_TEST_METHOD"
    const val TEST_CLASS = "TEST_CLASS"
    const val BEGIN_TEST_CLASS = "BEGIN_TEST_CLASS"

    val TEST_METHOD_MACROS: Set<String> = setOf(TEST_METHOD, BEGIN_TEST_METHOD)
    val TEST_CLASS_MACROS: Set<String> = setOf(TEST_CLASS, BEGIN_TEST_CLASS)
    val ALL_TEST_MACROS: Set<String> = TEST_METHOD_MACROS + TEST_CLASS_MACROS
}
