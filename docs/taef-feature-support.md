# TAEF Feature Support — IDE Plugin

Tracks TAEF features relevant to the CLion IDE test plugin. Updated to reflect
current implementation state after parser rewrite and converter refactor.

## Core Features

### Test Detection — Gutter icons and run config creation

The plugin detects TAEF test macros on both CLion C++ engines. The detection
mechanism differs per engine, but the result is the same: gutter icons and
run configurations for test methods and classes.

| Engine  | Mechanism                                          | Module    |
|---------|----------------------------------------------------|-----------|
| Classic | PSI-based (`TaefTestFramework` + `cidr.lang`)      | `classic` |
| Nova    | Text-based (`TaefNovaLineMarkerContributor` + Radler) | `nova` |

#### Supported macros

| Macro                     | Supported |
|---------------------------|-----------|
| `TEST_METHOD(name)`       | ✅         |
| `BEGIN_TEST_METHOD(name)` | ✅         |
| `TEST_CLASS(name)`        | ✅         |
| `BEGIN_TEST_CLASS(name)`  | ✅         |

### Test Results — Populating the test tree

| Result  | TE.exe Output              | Supported | Notes                                                     |
|---------|----------------------------|-----------|-----------------------------------------------------------|
| Passed  | `EndGroup: Name [Passed]`  | ✅         | Default when all verifies pass                            |
| Failed  | `EndGroup: Name [Failed]`  | ✅         | Any verify failure OR `Log::Error()`                      |
| Skipped | `EndGroup: Name [Skipped]` | ✅         | Requires `Log::Result(TestResults::Skipped)` in test body |
| Blocked | `EndGroup: Name [Blocked]` | ✅         | Setup returned false (class or method level)              |

### Console Output — Shown in test tree / failure details

Lines inside a StartGroup/EndGroup block belong to that test.
Lines outside blocks are fixture output (setup/cleanup/blocked messages).

| Line Type           | Format                                                      | Supported | Notes                                      |
|---------------------|-------------------------------------------------------------|-----------|--------------------------------------------|
| Test start          | `StartGroup: NS::Class::Method`                             | ✅         | Opens test node in tree                    |
| Test end            | `EndGroup: NS::Class::Method [Result]`                      | ✅         | Closes test node with result               |
| Verify failure      | `Error: Verify: <call> - Values (...) [File: ..., Line: N]` | ✅         | Captured as stderr                         |
| Log::Error          | `Error: <text>`                                             | ✅         | Captured as stderr (no leading whitespace) |
| Log::Comment        | Bare text (no prefix)                                       | ✅         | Captured as stdout                         |
| Verify pass         | `Verify: AreEqual(a, b)`                                    | ✅         | Captured as stdout                         |
| Log::Warning        | `Warning: <text>`                                           | ✅         | Captured as stdout                         |
| Data-driven params  | `TAEF: Data[Key]: Value`                                    | ✅         | Captured as stdout per row                 |
| Summary             | `Summary: Total=N, Passed=P, ...`                           | ✅         | Triggers suite close                       |
| TestBlocked context | `TestBlocked: TAEF: Setup fixture '...' returned 'false'.`  | ✅         | Buffered, attached as error to next test   |

### Test Tree Structure

| Feature                         | Supported | Notes                                               |
|---------------------------------|-----------|-----------------------------------------------------|
| Suite grouping (Class → Method) | ✅         | Node IDs: `0/SuiteName/TestName`                    |
| Namespaced suites               | ✅         | `NS::Class` as suite name (colons safe in node IDs) |
| Data-driven test names          | ✅         | `Method#0`, `Method#1` as test names                |
| `enteredTheMatrix` lifecycle    | ✅         | Tree shows "Tests passed/failed" not "Terminated"   |
| Output newlines preserved       | ✅         | Each line gets trailing `\n`                        |

### Fixture/Setup Output — Outside StartGroup/EndGroup blocks

| Line Type                | When                           | Supported | Notes                        |
|--------------------------|--------------------------------|-----------|------------------------------|
| Module setup/cleanup log | Before first / after last test | ✅ Ignored | Uncaptured output in console |
| Class setup/cleanup log  | Before/after class methods     | ✅ Ignored | Uncaptured output in console |
| Method setup/cleanup log | Before/after each test         | ✅ Ignored | Uncaptured output in console |
| TAEF version header      | First line                     | ✅ Ignored | Uncaptured output in console |

### Command Line

| Feature                      | Supported | Notes                    |
|------------------------------|-----------|--------------------------|
| DLL path as first arg        | ✅         |                          |
| `/name:<filter>` (glob)      | ✅         |                          |
| `/select:"<query>"`          | ✅         | Passed through to TE.exe |
| `/inproc`                    | ✅         | Passed through to TE.exe |
| Additional args pass-through | ✅         |                          |

## Not Yet Implemented

| Feature                     | Status      | Issue                                                       | Notes                                                                                |
|-----------------------------|-------------|-------------------------------------------------------------|--------------------------------------------------------------------------------------|
| Test locator                | Stub        | [#2](https://github.com/eviltak/taef-clion-plugin/issues/2) | Parse `taef://Suite/Test`, resolve PSI via `TaefTestFramework`                       |
| Test index contributor      | Not started | [#1](https://github.com/eviltak/taef-clion-plugin/issues/1) | `CidrTestIndexContributor` for project-wide test discovery, right-click file/dir run |
| Rerun failed tests          | Deferred    | [#3](https://github.com/eviltak/taef-clion-plugin/issues/3) | Design issues: `/name:` doesn't support OR; need `/select:` with `@Name` queries     |
| Test selection autocomplete | Not started | [#5](https://github.com/eviltak/taef-clion-plugin/issues/5) | Structured editor with test tree dropdown (depends on index contributor)             |
| Auto-detect TE.exe          | Not started |                                                             | Heuristic search: PATH, common locations, NuGet cache                                |
| XML log reconciliation      | Not started |                                                             | `/logOutput:xml` for authoritative results after execution                           |
| Harness exit code decoding  | Not started |                                                             | Bits 24-30 → human-readable error messages                                           |

## Parser Edge Cases

| Scenario                         | Behavior                                                                         | Notes                                                        |
|----------------------------------|----------------------------------------------------------------------------------|--------------------------------------------------------------|
| EndGroup with no StartGroup      | Ignored                                                                          | Orphan EndGroups silently dropped                            |
| User output mimics StartGroup    | Inside block: captured as stdout (safe). Outside: could trigger false StartGroup | Very unlikely in practice                                    |
| TE.exe stderr                    | Not matched → uncaptured output in console                                       | Stderr is for harness failures, not test results             |
| `Error:` on stdout not stderr    | Tagged as `isError=true` → shown as testStdErr in tree                           | Correct — TE.exe writes errors to stdout                     |
| Blank lines inside test block    | Ignored (isNotBlank check)                                                       | Blank lines are visual separators                            |
| Malformed EndGroup (no [Result]) | Ignored                                                                          | Class-level EndGroup in `/list` output has no result bracket |
