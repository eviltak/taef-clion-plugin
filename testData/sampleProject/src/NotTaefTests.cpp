#include "SomeOtherFramework.h"

// This file uses macros with the same names as TAEF, but is NOT a TAEF test.
// The plugin should NOT show gutter icons here because WexTestClass.h is not included.

TEST_CLASS(LooksLikeTaefButIsNot);

TEST_METHOD(MethodThatIsNotTaef)
{
    // Some other framework's test
    EXPECT_TRUE(true);
}

BEGIN_TEST_CLASS(AnotherFakeClass)
END_TEST_CLASS()

BEGIN_TEST_METHOD(AnotherFakeMethod)
END_TEST_METHOD()
{
    EXPECT_EQ(1, 1);
}
