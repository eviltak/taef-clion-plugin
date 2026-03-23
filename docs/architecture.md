# TAEF CLion Plugin — Architecture

## Extension Points (plugin.xml registration)

```
com.intellij.configurationType       → TaefConfigurationType
com.intellij.runConfigurationProducer → TaefRunConfigurationProducer
com.intellij.consoleFilterProvider    → TaefConsoleFilterProvider
cidr.testFrameworkDetector           → TaefTestFrameworkDetector
cidr.lang.testFramework              → TaefTestFramework
```

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        IDE Platform                              │
│                                                                  │
│  ┌─── PSI Detection ───┐  ┌─── Run Config ──────────────────┐  │
│  │                      │  │                                  │  │
│  │  TaefTestFramework   │  │  TaefConfigurationType           │  │
│  │   ├ validateElement  │  │   └ factory → TaefRunConfig      │  │
│  │   ├ getTestLineMarkInfo│ │                                  │  │
│  │   ├ extractTest      │  │  TaefRunConfiguration            │  │
│  │   └ buildQualifiedPath│ │   ├ nameFilter, selectQuery,    │  │
│  │                      │  │   │ inproc, additionalTeArgs     │  │
│  │  TaefTestFramework-  │  │   ├ createLauncher → TaefLaunch │  │
│  │    Detector          │  │   ├ buildTaefArgs               │  │
│  │   └ hasTestConfig    │  │   └ suggestedName               │  │
│  │                      │  │                                  │  │
│  └──────────────────────┘  │  TaefTestRunConfigurationData    │  │
│                            │   ├ createState → TestCmdState   │  │
│  ┌─── Producer ─────────┐  │   ├ createTestConsoleProperties  │  │
│  │                      │  │   └ suggestedName                │  │
│  │  TaefRunConfig-      │  │                                  │  │
│  │    Producer          │  │  TaefSettingsEditor               │  │
│  │   ├ isTestTarget     │  │   ├ resetEditorFrom              │  │
│  │   ├ doSetupConfig    │  │   └ applyEditorTo                │  │
│  │   └ configFactory    │  │                                  │  │
│  └──────────────────────┘  └──────────────────────────────────┘  │
│                                                                  │
│  ┌─── Execution ────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  TaefTestCommandLineState (extends CidrTestCmdState)     │   │
│  │   ├ doCreateRerunFailedTestsAction                       │   │
│  │   ├ buildRerunPattern                                    │   │
│  │   └ createTestScopeElement                               │   │
│  │                                                          │   │
│  │  TaefLauncher (extends CMakeTestLauncher)                │   │
│  │   └ getRunFileAndEnvironment                             │   │
│  │     ├ resolves DLL from CMake target product file        │   │
│  │     ├ resolves TE.exe from executableData                │   │
│  │     └ injects DLL + TAEF args into program parameters    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─── Output → Test Tree ───────────────────────────────────┐   │
│  │                                                          │   │
│  │  TaefTestConsoleProperties                               │   │
│  │   ├ createTestEventsConverter → Converter                │   │
│  │   ├ getTestLocator → TaefTestLocator                     │   │
│  │   ├ getAssertionPattern → SOURCE_LINK_PATTERN            │   │
│  │   └ SOURCE_LINK_PATTERN matches [File: ..., Line: N]     │   │
│  │                                                          │   │
│  │  TaefOutputToGeneralTestEventsConverter                  │   │
│  │   (extends CidrFromTagInLineToGeneralTestEventsConverter)│   │
│  │   └ processLine(outputType, text)                        │   │
│  │     ├ TaefStreamParser.feedLine(line) → events           │   │
│  │     ├ processEvent → suite/test stack management         │   │
│  │     ├ process(myEventProcessor.testStarted/Finished/...) │   │
│  │     └ uncaptured lines → processor.onUncapturedOutput    │   │
│  │                                                          │   │
│  │  output/TaefOutputLineClassifier (stateless)             │   │
│  │   └ classify(line) → TaefOutputLine                      │   │
│  │     ├ StartGroup, EndGroup, Error, Text                  │   │
│  │     ├ TestBlocked, Summary, Ignored                      │   │
│  │     └ prefix matching (no regex except Summary)          │   │
│  │                                                          │   │
│  │  output/TaefStreamParser (stateful)                      │   │
│  │   └ feedLine(line) → List<TaefTestEvent>                 │   │
│  │     ├ tracks currentTest, errorLines, blockedContext      │   │
│  │     ├ accumulates Error: lines, flushes on EndGroup      │   │
│  │     └ buffers TestBlocked context for next test          │   │
│  │                                                          │   │
│  │  output/TaefTestId                                       │   │
│  │   └ parse(fqn) splits on last :: → (suite, test)         │   │
│  │                                                          │   │
│  │  TaefConsoleFilterProvider + TaefSourceLinkFilter         │   │
│  │   └ [File: path, Line: N] → clickable link in all panes  │   │
│  │                                                          │   │
│  │  TaefTestLocator (stub)                                  │   │
│  │   └ getLocation(protocol, path) → List<Location>         │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─── Shared ───────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  TaefTestConstants                                       │   │
│  │   ├ PROTOCOL_PREFIX = "taef"                             │   │
│  │   ├ ALL_TEST_MACROS, TEST_METHOD_MACROS, etc.            │   │
│  │   └ HEADER_NAME, HEADER_PATTERN                          │   │
│  │                                                          │   │
│  │  TaefCommandLineBuilder (pure, no IDE deps)              │   │
│  │   └ build(TaefCommandLineParams) → GeneralCommandLine    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

```
Source file (.cpp)
  ↓ PSI indexing
TaefTestFramework.validateTaefElement
  ↓ macro name + header + substitution + target type
TaefTestFramework.getTestLineMarkInfo → gutter icon (url = taef://Class::Method)
TaefTestFramework.extractTest → CidrTestScopeElement (pattern + configName)
  ↓ user clicks gutter icon
TaefRunConfigurationProducer.doSetupConfigurationFromContext
  ↓ creates TaefRunConfiguration with nameFilter, target, template settings
User clicks Run
  ↓
TaefTestRunConfigurationData.createState → TaefTestCommandLineState
TaefRunConfiguration.createLauncher → TaefLauncher
  ↓ CMakeTestLauncher.createProcess:
  ↓   getRunFileAndEnvironment (TE.exe + DLL args)
  ↓   createConsoleBuilder → SMRunner console
TE.exe process runs, writes stdout
  ↓ line by line via CidrFromTagInLine.processServiceMessages
TaefOutputToGeneralTestEventsConverter.processLine
  ↓ TaefStreamParser.feedLine(line) → TaefTestEvent list
  ↓ processEvent → suite/test node stack management
  ↓ process(myEventProcessor.testStarted/Finished/...) → ##teamcity messages
  ↓ uncaptured lines → onUncapturedOutput → Console Output tab
SMRunner test tree populated (root → suite → test)
  Node IDs: 0/SuiteName/TestName
  Locations: SuiteName/TestName → taef://SuiteName/TestName
```

## Test Tree Structure

```
root
├── SampleTestClass           (suite, node ID: 0/SampleTestClass)
│   ├── TestMethodPass        (test, node ID: 0/SampleTestClass/TestMethodPass)
│   ├── TestMethodFail        (test, failed)
│   └── TestMethodSkip        (test, ignored)
├── DataDrivenClass           (suite)
│   ├── TestAddition#0        (test, data row 0)
│   ├── TestAddition#1        (test, data row 1)
│   └── TestAddition#2        (test, data row 2)
└── NS::NamespacedClass       (suite, colons in name are safe)
    └── TestInNamespace       (test)
```

## Key Design Decisions

### Why CidrFromTagInLineToGeneralTestEventsConverter?
Extends `CidrOutputToGeneralTestEventsConverterBase` (not the sibling
`CidrOutputToGeneralTestEventsConverter`). Provides:
- `myTestNameStack` / `myTestResultStack` for node ID management
- `getCurrentNodeId()` / `getLocationFromId()` for tree paths
- `process()` which calls `super.processServiceMessages` (invokespecial) to
  route ##teamcity messages through the SMRunner pipeline
- `processServiceMessages` intercepts ALL stdout/stderr → calls `processLine`
- `flushBufferOnProcessTermination` buffers "Process finished" for deferred output

### Why CMakeTestLauncher, not CMakeLauncher?
`CMakeTestLauncher.createConsoleBuilder()` overrides `CidrConsoleBuilder.createConsole()`
to delegate to `CidrTestCommandLineState.createConsole(builder)`, which creates an
`SMTRunnerConsoleView`. Without this, `createConsole()` returns a plain `ConsoleViewImpl`,
and `createRestartAction()` throws `ClassCastException`.

### Why ConsoleFilterProvider AND getAssertionPattern?
`getAssertionPattern()` (via `addStackTraceFilter`) only applies to the main Console
Output tab. `ConsoleFilterProvider` applies globally including per-test output panes
in the SMRunner tree. Both use the same `SOURCE_LINK_PATTERN` regex.
