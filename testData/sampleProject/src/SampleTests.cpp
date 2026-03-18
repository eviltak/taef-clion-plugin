#include <WexTestClass.h>

// Sample TAEF test file using real TAEF class structure.
// See https://learn.microsoft.com/en-us/windows-hardware/drivers/taef/authoring-tests-in-c--

class SampleTestClass
{
    BEGIN_TEST_CLASS(SampleTestClass)
        TEST_CLASS_PROPERTY(L"Component", L"SampleComponent")
    END_TEST_CLASS()

    TEST_CLASS_SETUP(ClassSetup) { return true; }
    TEST_CLASS_CLEANUP(ClassCleanup) { return true; }

    TEST_METHOD(TestMethodPass)
    {
        VERIFY_ARE_EQUAL(42, 42);
    }

    BEGIN_TEST_METHOD(TestMethodFail)
        TEST_METHOD_PROPERTY(L"Owner", L"testowner")
        TEST_METHOD_PROPERTY(L"Priority", L"1")
    END_TEST_METHOD()
    {
        VERIFY_ARE_EQUAL(42, 0);
    }

    TEST_METHOD(TestMethodSkip)
    {
        VERIFY_IS_TRUE(true);
    }
};

namespace TestNamespace
{
    class AnotherTestClass
    {
        TEST_CLASS(AnotherTestClass);

        TEST_METHOD(TestBlocked)
        {
            VERIFY_IS_TRUE(false);
        }
    };
}
