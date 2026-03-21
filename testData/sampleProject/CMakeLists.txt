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

# --- Optional: Real TAEF build for generating TE.exe output fixtures ---
#
# When building with MSVC and WDK is installed, this creates a DLL that
# TE.exe can actually load, then runs TE.exe to capture real output into
# fixture files used by mock_te.ps1.
#
# Usage:
#   cmake -B build-msvc -G "Visual Studio 17 2022"
#   cmake --build build-msvc --target generate-fixtures
#
# The fixtures are committed to testData/mockTe/fixtures/ and used by
# mock_te.ps1 to replay real TE.exe output without needing TAEF at runtime.

if(MSVC)
    set(TAEF_SEARCH_DIR "$ENV{ProgramFiles\(x86\)}/Windows Kits/10/Testing")

    find_path(TAEF_INCLUDE_DIR WexTestClass.h
        PATHS "${TAEF_SEARCH_DIR}/Development/inc"
        NO_DEFAULT_PATH)

    find_library(TAEF_TE_COMMON_LIB TE.Common
        PATHS "${TAEF_SEARCH_DIR}/Development/lib/x64"
        NO_DEFAULT_PATH)

    find_library(TAEF_WEX_COMMON_LIB Wex.Common
        PATHS "${TAEF_SEARCH_DIR}/Development/lib/x64"
        NO_DEFAULT_PATH)

    find_library(TAEF_WEX_LOGGER_LIB Wex.Logger
        PATHS "${TAEF_SEARCH_DIR}/Development/lib/x64"
        NO_DEFAULT_PATH)

    find_program(TE_EXE TE.exe
        PATHS "${TAEF_SEARCH_DIR}/Runtimes/TAEF/x64"
        NO_DEFAULT_PATH)

    if(TAEF_INCLUDE_DIR AND TAEF_TE_COMMON_LIB AND TAEF_WEX_COMMON_LIB AND TAEF_WEX_LOGGER_LIB AND TE_EXE)
        message(STATUS "TAEF found — enabling real TAEF build and fixture generation")
        message(STATUS "  Headers: ${TAEF_INCLUDE_DIR}")
        message(STATUS "  TE.exe:  ${TE_EXE}")

        add_library(SampleTests_Taef MODULE src/SampleTests.cpp)
        target_include_directories(SampleTests_Taef PRIVATE ${TAEF_INCLUDE_DIR})
        target_link_libraries(SampleTests_Taef PRIVATE
            ${TAEF_TE_COMMON_LIB}
            ${TAEF_WEX_COMMON_LIB}
            ${TAEF_WEX_LOGGER_LIB})
        set_target_properties(SampleTests_Taef PROPERTIES
            SUFFIX ".dll"
            PREFIX ""
            OUTPUT_NAME "SampleTests_Taef")

        # Copy TestData.xml next to the DLL (required for data-driven tests)
        add_custom_command(TARGET SampleTests_Taef POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                ${CMAKE_CURRENT_SOURCE_DIR}/src/TestData.xml
                $<TARGET_FILE_DIR:SampleTests_Taef>/TestData.xml)

        set(FIXTURE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../mockTe/fixtures")
        file(MAKE_DIRECTORY ${FIXTURE_DIR})

        set(FIXTURE_ALL "${FIXTURE_DIR}/all-tests.txt")
        set(FIXTURE_LIST "${FIXTURE_DIR}/list-tests.txt")

        # Fixtures are regenerated whenever the DLL is rebuilt.
        # add_custom_command with OUTPUT tracks the dependency automatically.
        add_custom_command(
            OUTPUT ${FIXTURE_ALL} ${FIXTURE_LIST}
            COMMENT "Running TE.exe to generate test output fixtures..."
            COMMAND ${TE_EXE} $<TARGET_FILE:SampleTests_Taef> /list > "${FIXTURE_LIST}" || ${CMAKE_COMMAND} -E true
            COMMAND ${TE_EXE} $<TARGET_FILE:SampleTests_Taef> > "${FIXTURE_ALL}" || ${CMAKE_COMMAND} -E true
            DEPENDS SampleTests_Taef
            VERBATIM)

        add_custom_target(generate-fixtures ALL
            DEPENDS ${FIXTURE_ALL} ${FIXTURE_LIST})
    else()
        message(STATUS "TAEF not found — skipping real TAEF build (WDK required)")
        if(NOT TAEF_INCLUDE_DIR)
            message(STATUS "  Missing: WexTestClass.h")
        endif()
        if(NOT TE_EXE)
            message(STATUS "  Missing: TE.exe")
        endif()
    endif()
endif()
