cmake_minimum_required(VERSION 3.20)
project(TaefSampleTests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

# Real TAEF DLL using the header with TAEF-internal markers (taef/WexTestClass.h).
# The plugin's dual detection should ACCEPT tests in this target.
add_library(SampleTests MODULE
    src/SampleTests.cpp
)
target_include_directories(SampleTests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/taef)
set_target_properties(SampleTests PROPERTIES SUFFIX ".dll" PREFIX "")

# A non-test executable target — uses TAEF macros but TE.exe only loads DLLs,
# so the framework detector should reject it.
add_executable(SampleApp src/SampleApp.cpp)
target_include_directories(SampleApp PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/taef)

# Stub TAEF DLL using the header WITHOUT TAEF-internal markers (stubs/WexTestClass.h).
# The plugin's dual detection should REJECT tests in this target
# (header name matches, but substitution markers are missing).
add_library(StubTests MODULE
    src/StubTests.cpp
)
target_include_directories(StubTests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/stubs)
set_target_properties(StubTests PROPERTIES SUFFIX ".dll" PREFIX "")
