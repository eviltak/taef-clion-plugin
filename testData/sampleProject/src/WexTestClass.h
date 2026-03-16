#pragma once

// Stub TAEF macros for sample/test purposes.
// In real code, #include <WexTestClass.h> from the TAEF SDK.

#define TEST_CLASS(className) \
    class className

#define BEGIN_TEST_CLASS(className) \
    class className {

#define END_TEST_CLASS() \
    };

#define TEST_CLASS_PROPERTY(name, value)

#define TEST_METHOD(methodName) \
    void methodName()

#define BEGIN_TEST_METHOD(methodName) \
    void methodName()

#define END_TEST_METHOD()

#define TEST_METHOD_PROPERTY(name, value)

#define MODULE_SETUP(funcName)       bool funcName()
#define MODULE_CLEANUP(funcName)     bool funcName()
#define TEST_CLASS_SETUP(funcName)   bool funcName()
#define TEST_CLASS_CLEANUP(funcName) bool funcName()
#define TEST_METHOD_SETUP(funcName)  bool funcName()
#define TEST_METHOD_CLEANUP(funcName) bool funcName()

// Stub Verify macros
namespace WEX { namespace TestExecution {
    struct Verify {
        static bool AreEqual(int a, int b) { return a == b; }
        static bool IsTrue(bool val) { return val; }
        static bool IsFalse(bool val) { return !val; }
    };
}}

#define VERIFY_ARE_EQUAL(a, b) WEX::TestExecution::Verify::AreEqual(a, b)
#define VERIFY_IS_TRUE(val)    WEX::TestExecution::Verify::IsTrue(val)
#define VERIFY_IS_FALSE(val)   WEX::TestExecution::Verify::IsFalse(val)
