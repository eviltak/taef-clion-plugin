cmake_minimum_required(VERSION 3.20)
project(TaefSampleTests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

# In a real TAEF project, these would come from WexTestClass.h via NuGet or SDK.
# For this sample, we provide stub macros in TaefStubs.h.

add_library(SampleTests MODULE
    src/SampleTests.cpp
)

target_include_directories(SampleTests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)

# The output is a .dll that TE.exe loads
set_target_properties(SampleTests PROPERTIES
    SUFFIX ".dll"
    PREFIX ""
)

# A non-test executable target — uses TAEF macros but TE.exe only loads DLLs,
# so the framework detector should reject it.
add_executable(SampleApp src/SampleApp.cpp)
target_include_directories(SampleApp PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
