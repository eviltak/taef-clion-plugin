package com.github.eviltak.taef

/**
 * Constants for the sample project used in integration tests.
 * Keeps target names, file names, and expected values in one place.
 */
object SampleProjectConstants {
    // CMake targets
    const val REAL_TESTS_TARGET = "SampleTests"
    const val STUB_TESTS_TARGET = "StubTests"
    const val APP_TARGET = "SampleApp"

    // Source files
    const val REAL_TESTS_FILE = "SampleTests.cpp"
    const val STUB_TESTS_FILE = "StubTests.cpp"
    const val NOT_TAEF_FILE = "NotTaefTests.cpp"

    // Expected test names in SampleTests.cpp — line numbers are verified
    // dynamically by searching the file text (see assertGutterIconInfoForRealTests)
    val EXPECTED_SUITES = setOf("SampleTestClass", "AnotherTestClass")
    val EXPECTED_METHODS = setOf("TestMethodPass", "TestMethodFail", "TestMethodSkip", "TestBlocked")

    // Qualified test paths (used for URL matching and nameFilter assertions)
    val EXPECTED_SUITE_PATHS = mapOf(
        "SampleTestClass" to "SampleTestClass",
        "AnotherTestClass" to "TestNamespace::AnotherTestClass"
    )
    val EXPECTED_METHOD_PATHS = mapOf(
        "TestMethodPass" to "SampleTestClass::TestMethodPass",
        "TestMethodFail" to "SampleTestClass::TestMethodFail",
        "TestMethodSkip" to "SampleTestClass::TestMethodSkip",
        "TestBlocked" to "TestNamespace::AnotherTestClass::TestBlocked"
    )

    // Test names in StubTests.cpp (stub header — detection should reject all of these)
    val STUB_TEST_METHODS = setOf("StubTestMethod", "AnotherStubMethod")
    val STUB_TEST_CLASSES = setOf("StubTestClass", "AnotherStubClass")
}
