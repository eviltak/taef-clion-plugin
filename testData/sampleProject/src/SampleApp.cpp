// Executable that uses TAEF macros — but TE.exe only loads DLLs (MODULE
// libraries), so the framework detector should reject this target.
#include <WexTestClass.h>

class ExecutableTests {
    TEST_CLASS(ExecutableTests);
    TEST_METHOD(ShouldNotBeDetected);
};

int main() { return 0; }
