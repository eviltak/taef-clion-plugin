#include <WexTestClass.h>

// Sample TAEF test file — compatible with both real TAEF and stub headers.
// Uses out-of-line method definitions for maximum compatibility.
// Exercises all TAEF execution locations and output types the IDE plugin
// needs to support.
// See https://learn.microsoft.com/en-us/windows-hardware/drivers/taef/authoring-tests-in-c--

// --- Module-level setup/cleanup ---

MODULE_SETUP(ModuleSetup);
MODULE_CLEANUP(ModuleCleanup);

bool ModuleSetup()
{
    WEX::Logging::Log::Comment(L"ModuleSetup - initializing module.");
    return true;
}

bool ModuleCleanup()
{
    return true;
}

// --- Class with all fixture types and varied test results ---

class SampleTestClass
{
    BEGIN_TEST_CLASS(SampleTestClass)
        TEST_CLASS_PROPERTY(L"Component", L"SampleComponent")
    END_TEST_CLASS()

    TEST_CLASS_SETUP(ClassSetup);
    TEST_CLASS_CLEANUP(ClassCleanup);
    TEST_METHOD_SETUP(MethodSetup);
    TEST_METHOD_CLEANUP(MethodCleanup);

    TEST_METHOD(TestMethodPass);

    BEGIN_TEST_METHOD(TestMethodFail)
        TEST_METHOD_PROPERTY(L"Owner", L"testowner")
        TEST_METHOD_PROPERTY(L"Priority", L"1")
    END_TEST_METHOD()

    TEST_METHOD(TestMethodSkip);

    TEST_METHOD(TestMethodWithWarning);
};

bool SampleTestClass::ClassSetup()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::ClassSetup - initializing class.");
    return true;
}

bool SampleTestClass::ClassCleanup()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::ClassCleanup - tearing down class.");
    return true;
}

bool SampleTestClass::MethodSetup()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::MethodSetup - preparing test.");
    return true;
}

bool SampleTestClass::MethodCleanup()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::MethodCleanup - cleaning up test.");
    return true;
}

void SampleTestClass::TestMethodPass()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::TestMethodPass - verifying equality.");
    VERIFY_ARE_EQUAL(42, 42);
}

void SampleTestClass::TestMethodFail()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::TestMethodFail - this will fail.");
    VERIFY_ARE_EQUAL(42, 0);
}

void SampleTestClass::TestMethodSkip()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::TestMethodSkip - skipping test.");
    WEX::Logging::Log::Result(WEX::Logging::TestResults::Skipped);
}

void SampleTestClass::TestMethodWithWarning()
{
    WEX::Logging::Log::Comment(L"SampleTestClass::TestMethodWithWarning - testing all log types.");
    WEX::Logging::Log::Warning(L"SampleTestClass::TestMethodWithWarning - resource usage is high.");
    WEX::Logging::Log::Error(L"SampleTestClass::TestMethodWithWarning - non-fatal error for testing.");
    VERIFY_IS_TRUE(true);
}

// --- Class with a setup that returns false — all methods are Blocked ---

class BlockedTestClass
{
    TEST_CLASS(BlockedTestClass);

    TEST_CLASS_SETUP(SetupThatFails);

    TEST_METHOD(TestBlockedBySetup);
};

bool BlockedTestClass::SetupThatFails()
{
    WEX::Logging::Log::Comment(L"BlockedTestClass::SetupThatFails - about to return false.");
    return false;
}

void BlockedTestClass::TestBlockedBySetup()
{
    VERIFY_IS_TRUE(true);
}

// --- Class with a failing method setup — individual test blocked ---

class MethodSetupFailClass
{
    TEST_CLASS(MethodSetupFailClass);

    TEST_METHOD_SETUP(BadMethodSetup);

    TEST_METHOD(TestBlockedByMethodSetup);
    TEST_METHOD(TestAlsoBlockedByMethodSetup);
};

bool MethodSetupFailClass::BadMethodSetup()
{
    return false;
}

void MethodSetupFailClass::TestBlockedByMethodSetup()
{
    VERIFY_IS_TRUE(true);
}

void MethodSetupFailClass::TestAlsoBlockedByMethodSetup()
{
    VERIFY_IS_TRUE(true);
}

// --- Namespaced class with a self-blocked method ---
// Shows Log::Result(Blocked) blocking one method while siblings pass.

namespace TestNamespace
{
    class NamespacedTestClass
    {
        TEST_CLASS(NamespacedTestClass);

        TEST_METHOD(TestInNamespace);
        TEST_METHOD(TestSelfBlocked);
    };

    void NamespacedTestClass::TestInNamespace()
    {
        VERIFY_IS_TRUE(true);
    }

    void NamespacedTestClass::TestSelfBlocked()
    {
        WEX::Logging::Log::Comment(L"TestNamespace::NamespacedTestClass::TestSelfBlocked - dependency unavailable.");
        WEX::Logging::Log::Result(WEX::Logging::TestResults::Blocked);
    }
}

// --- Data-driven test — parameterized via XML DataSource ---
// Each row in TestData.xml runs as a separate test instance.
// TE.exe output shows: DataDrivenClass::TestAddition#0, #1, #2

class DataDrivenClass
{
    TEST_CLASS(DataDrivenClass);

    BEGIN_TEST_METHOD(TestAddition)
        TEST_METHOD_PROPERTY(L"DataSource", L"Table:TestData.xml#AdditionData")
    END_TEST_METHOD()
};

void DataDrivenClass::TestAddition()
{
    int left = 0, right = 0, expected = 0;
    WEX::TestExecution::TestData::TryGetValue(L"Left", left);
    WEX::TestExecution::TestData::TryGetValue(L"Right", right);
    WEX::TestExecution::TestData::TryGetValue(L"Expected", expected);

    VERIFY_ARE_EQUAL(left + right, expected);
}
