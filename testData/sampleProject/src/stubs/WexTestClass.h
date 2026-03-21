#pragma once

// Stub TAEF macros that do NOT contain TAEF-internal identifiers.
// The plugin's dual detection should REJECT these because:
// 1. The header IS named WexTestClass.h (passes header origin check)
// 2. But the substitutions do NOT contain TAEF_TEST_METHOD,
//    TAEF_TestMethodIndexOffset, or TestClassFactory (fails marker check)

#define TEST_CLASS(className) \
    public: \
        static int stub_marker

#define BEGIN_TEST_CLASS(className) \
    public: \
        static int stub_marker

#define END_TEST_CLASS() ;

#define TEST_CLASS_PROPERTY(name, value)

#define TEST_METHOD(methodName) \
    void methodName()

#define BEGIN_TEST_METHOD(methodName) \
    void methodName()

#define END_TEST_METHOD() ;

#define TEST_METHOD_PROPERTY(name, value)

#define MODULE_SETUP(funcName)         bool funcName()
#define MODULE_CLEANUP(funcName)       bool funcName()
#define TEST_CLASS_SETUP(funcName)     bool funcName()
#define TEST_CLASS_CLEANUP(funcName)   bool funcName()
#define TEST_METHOD_SETUP(funcName)    bool funcName()
#define TEST_METHOD_CLEANUP(funcName)  bool funcName()

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
