#include <WexTestClass.h>

// Sample TAEF test file demonstrating all macro patterns the plugin should detect.

BEGIN_TEST_CLASS(SampleTestClass)
    TEST_CLASS_PROPERTY(L"Component", L"SampleComponent")
END_TEST_CLASS()

// Simple test method — should get a gutter run icon
TEST_METHOD(TestMethodPass)
{
    int expected = 42;
    int actual = 42;
    VERIFY_ARE_EQUAL(expected, actual);
}

// Extended test method with metadata
BEGIN_TEST_METHOD(TestMethodFail)
    TEST_METHOD_PROPERTY(L"Owner", L"testowner")
    TEST_METHOD_PROPERTY(L"Priority", L"1")
END_TEST_METHOD()
{
    // This test intentionally fails
    VERIFY_ARE_EQUAL(42, 0);
}

TEST_METHOD(TestMethodSkip)
{
    // Skipped via runtime condition
    VERIFY_IS_TRUE(true);
}

// Fixtures
TEST_CLASS_SETUP(ClassSetup)
{
    return true;
}

TEST_CLASS_CLEANUP(ClassCleanup)
{
    return true;
}

// A second test class in the same file
TEST_CLASS(AnotherTestClass);

TEST_METHOD(TestBlocked)
{
    VERIFY_IS_TRUE(false);
}
