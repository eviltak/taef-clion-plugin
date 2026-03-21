#pragma once

// Realistic TAEF header stub for integration testing.
// Contains the internal macro identifiers that the real WexTestClass.h expands
// to, so the plugin's dual detection (header origin + substitution markers)
// recognizes these as genuine TAEF tests.
//
// This is NOT the actual Windows SDK header — it provides just enough structure
// for CLion's preprocessor to produce the expected substitution text.

namespace WEX { namespace TestExecution {
    template<typename T> struct TestClassFactory {};

    struct Verify {
        static bool AreEqual(int a, int b) { return a == b; }
        static bool IsTrue(bool val) { return val; }
        static bool IsFalse(bool val) { return !val; }
    };
}}

// Cross-platform export macro for exposing test methods in the DLL symbol table.
// Allows mock_te.ps1 to discover tests by reading exported symbols from the DLL.
#ifdef _WIN32
  #define TAEF_EXPORT __declspec(dllexport)
#else
  #define TAEF_EXPORT __attribute__((visibility("default")))
#endif

// Internal markers that the real TAEF header expands TEST_METHOD into.
// The plugin checks for "TAEF_TEST_METHOD" in the substitution text.
#define TAEF_TEST_METHOD(methodName) \
    TAEF_EXPORT void methodName()

// TEST_METHOD expands through TAEF_TEST_METHOD — the plugin detects this.
#define TEST_METHOD(methodName) \
    TAEF_TEST_METHOD(methodName)

#define BEGIN_TEST_METHOD(methodName) \
    TAEF_TEST_METHOD(methodName)

#define END_TEST_METHOD() ;

// TEST_CLASS is used inside a class body to register it with TAEF.
// In real TAEF, it generates TestClassFactory and TAEF_TestMethodIndexOffset.
#define TEST_CLASS(className) \
    public: \
        static int TAEF_TestMethodIndexOffset; \
        static WEX::TestExecution::TestClassFactory<className> s_TestClassFactory;

// BEGIN_TEST_CLASS/END_TEST_CLASS are the block form for adding properties.
#define BEGIN_TEST_CLASS(className) \
    public: \
        static int TAEF_TestMethodIndexOffset; \
        static WEX::TestExecution::TestClassFactory<className> s_TestClassFactory;

#define END_TEST_CLASS()

#define TEST_CLASS_PROPERTY(name, value)
#define TEST_METHOD_PROPERTY(name, value)

#define MODULE_SETUP(funcName)         bool funcName()
#define MODULE_CLEANUP(funcName)       bool funcName()
#define TEST_CLASS_SETUP(funcName)     bool funcName()
#define TEST_CLASS_CLEANUP(funcName)   bool funcName()
#define TEST_METHOD_SETUP(funcName)    bool funcName()
#define TEST_METHOD_CLEANUP(funcName)  bool funcName()

#define VERIFY_ARE_EQUAL(a, b) WEX::TestExecution::Verify::AreEqual(a, b)
#define VERIFY_IS_TRUE(val)    WEX::TestExecution::Verify::IsTrue(val)
#define VERIFY_IS_FALSE(val)   WEX::TestExecution::Verify::IsFalse(val)

// Logging API stubs — in real TAEF these are in Wex.Logger.dll.
// Stub implementations are no-ops; real output comes from TE.exe fixtures.
namespace WEX { namespace Logging {
    namespace TestResults {
        enum Result { Passed, Failed, Skipped, Blocked };
    }

    struct Log {
        static void Comment(const wchar_t*) {}
        static void Warning(const wchar_t*) {}
        static void Error(const wchar_t*) {}
        static void Result(TestResults::Result) {}
    };
}}

// TestData stub — in real TAEF this reads parameters from XML DataSource.
// Stub always returns false (value not found); real values come from TE.exe.
namespace WEX { namespace TestExecution {
    struct TestData {
        static bool TryGetValue(const wchar_t*, int& value) { value = 0; return false; }
        static bool TryGetValue(const wchar_t*, const wchar_t*& value) { value = L""; return false; }
    };
}}
