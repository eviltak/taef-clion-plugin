#include <WexTestClass.h>

// Uses the stub header (from stubs/ include dir).
// The plugin should NOT detect these as TAEF tests because the stub macros
// lack TAEF-internal identifiers (TAEF_TEST_METHOD, TestClassFactory, etc.)
// even though the header is named WexTestClass.h.

class StubTestClass
{
    TEST_CLASS(StubTestClass);

    TEST_METHOD(StubTestMethod);
};

void StubTestClass::StubTestMethod()
{
    VERIFY_IS_TRUE(true);
}

class AnotherStubClass
{
    BEGIN_TEST_CLASS(AnotherStubClass)
    END_TEST_CLASS()

    BEGIN_TEST_METHOD(AnotherStubMethod)
    END_TEST_METHOD()
};

void AnotherStubClass::AnotherStubMethod()
{
    VERIFY_ARE_EQUAL(1, 1);
}
