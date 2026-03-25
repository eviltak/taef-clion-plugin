# TAEF CLion Plugin — Architecture

## Module Layout

The plugin is split into three Gradle modules plus a root assembly module:

```
root            plugin.xml — optional depends on cidr.lang (→ taef-lang.xml)
  │                                         and radler    (→ taef-nova.xml)
  ├── shared    engine-agnostic: run config, output parser, launcher, constants
  ├── classic   classic C++ engine: TaefTestFramework (PSI), TaefClassicLanguageSupport
  └── nova      Nova engine: TaefNovaLanguageSupport (text-based), line marker
```

`shared` defines `TaefLanguageSupport`, the engine-agnostic service interface.
Each engine module provides an `applicationService` implementation registered in
its own XML descriptor. At runtime the active engine wins, and all shared code
(producer, launcher, converter) works unchanged via `TaefLanguageSupport.getInstance()`.

## Extension Points

### Root — plugin.xml (always loaded)

```
com.intellij.configurationType       → TaefConfigurationType          (shared)
com.intellij.runConfigurationProducer → TaefRunConfigurationProducer   (shared)
com.intellij.consoleFilterProvider    → TaefConsoleFilterProvider      (shared)
```

### Classic — taef-lang.xml (loaded when cidr.lang is present)

```
com.intellij.applicationService      → TaefClassicLanguageSupport      (classic)
cidr.testFrameworkDetector           → TaefTestFrameworkDetector       (shared)
cidr.lang.testFramework              → TaefTestFramework               (classic)
```

### Nova — taef-nova.xml (loaded when radler is present)

```
com.intellij.applicationService      → TaefNovaLanguageSupport         (nova)
com.intellij.runLineMarkerContributor → TaefNovaLineMarkerContributor  (nova)
```

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                          IDE Platform                                │
│                                                                      │
│  ┌─── Engine Abstraction (shared) ────────────────────────────────┐  │
│  │                                                                │  │
│  │  TaefLanguageSupport (applicationService interface)            │  │
│  │   ├ isTestTarget(file) → Boolean                               │  │
│  │   ├ getTestScopeElement(element) → CidrTestScopeElement?       │  │
│  │   └ getInstance() → whichever engine is active                 │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│        ▲ implemented by                  ▲ implemented by            │
│  ┌─────┴─── Classic Engine ──┐     ┌─────┴─── Nova Engine ───────┐  │
│  │                           │     │                              │  │
│  │  TaefClassicLanguage-     │     │  TaefNovaLanguageSupport     │  │
│  │    Support                │     │   └ text-based detection     │  │
│  │   └ delegates to          │     │     (no PSI / cidr.lang)     │  │
│  │     TaefTestFramework     │     │                              │  │
│  │                           │     │  TaefNovaLineMarker-         │  │
│  │  TaefTestFramework        │     │    Contributor               │  │
│  │   ├ validateElement       │     │   └ gutter icons via         │  │
│  │   ├ getTestLineMarkInfo   │     │     runLineMarkerContributor │  │
│  │   ├ extractTest           │     │                              │  │
│  │   └ buildQualifiedPath    │     │                              │  │
│  │                           │     │                              │  │
│  │  TaefTestFramework-       │     │                              │  │
│  │    Detector               │     │                              │  │
│  │   └ hasTestConfig         │     │                              │  │
│  │                           │     │                              │  │
│  └───────────────────────────┘     └──────────────────────────────┘  │
│                                                                      │
│  ┌─── Run Config (shared) ───────────────────────────────────────┐  │
│  │                                                                │  │
│  │  TaefRunConfigurationProducer                                  │  │
│  │   ├ isTestTarget → TaefLanguageSupport.getInstance()           │  │
│  │   ├ doSetupConfig                                              │  │
│  │   └ configFactory                                              │  │
│  │                                                                │  │
│  │  TaefConfigurationType                                         │  │
│  │   └ factory → TaefRunConfiguration                             │  │
│  │                                                                │  │
│  │  TaefRunConfiguration                                          │  │
│  │   ├ nameFilter, selectQuery, inproc, additionalTeArgs          │  │
│  │   ├ createLauncher → TaefLauncher                              │  │
│  │   ├ buildTaefArgs                                              │  │
│  │   └ suggestedName                                              │  │
│  │                                                                │  │
│  │  TaefTestRunConfigurationData                                  │  │
│  │   ├ createState → TaefTestCommandLineState                     │  │
│  │   ├ createTestConsoleProperties                                │  │
│  │   └ suggestedName                                              │  │
│  │                                                                │  │
│  │  TaefSettingsEditor                                            │  │
│  │   ├ resetEditorFrom                                            │  │
│  │   └ applyEditorTo                                              │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─── Execution (shared) ────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  TaefTestCommandLineState (extends CidrTestCmdState)           │  │
│  │   ├ doCreateRerunFailedTestsAction                             │  │
│  │   ├ buildRerunPattern                                          │  │
│  │   └ createTestScopeElement                                     │  │
│  │                                                                │  │
│  │  TaefLauncher (extends CMakeTestLauncher)                      │  │
│  │   └ getRunFileAndEnvironment                                   │  │
│  │     ├ resolves DLL from CMake target product file              │  │
│  │     ├ resolves TE.exe from executableData                      │  │
│  │     └ injects DLL + TAEF args into program parameters          │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─── Output → Test Tree (shared) ───────────────────────────────┐  │
│  │                                                                │  │
│  │  TaefTestConsoleProperties                                     │  │
│  │   ├ createTestEventsConverter → Converter                      │  │
│  │   ├ getTestLocator → TaefTestLocator                           │  │
│  │   ├ getAssertionPattern → SOURCE_LINK_PATTERN                  │  │
│  │   └ SOURCE_LINK_PATTERN matches [File: ..., Line: N]           │  │
│  │                                                                │  │
│  │  TaefOutputToGeneralTestEventsConverter                        │  │
│  │   (extends CidrFromTagInLineToGeneralTestEventsConverter)      │  │
│  │   └ processLine(outputType, text)                              │  │
│  │     ├ TaefStreamParser.feedLine(line) → events                 │  │
│  │     ├ processEvent → suite/test stack management               │  │
│  │     ├ process(myEventProcessor.testStarted/Finished/...)       │  │
│  │     └ uncaptured lines → processor.onUncapturedOutput          │  │
│  │                                                                │  │
│  │  output/TaefOutputLineClassifier (stateless)                   │  │
│  │   └ classify(line) → TaefOutputLine                            │  │
│  │     ├ StartGroup, EndGroup, Error, Text                        │  │
│  │     ├ TestBlocked, Summary, Ignored                            │  │
│  │     └ prefix matching (no regex except Summary)                │  │
│  │                                                                │  │
│  │  output/TaefStreamParser (stateful)                            │  │
│  │   └ feedLine(line) → List<TaefTestEvent>                       │  │
│  │     ├ tracks currentTest, errorLines, blockedContext            │  │
│  │     ├ accumulates Error: lines, flushes on EndGroup            │  │
│  │     └ buffers TestBlocked context for next test                │  │
│  │                                                                │  │
│  │  output/TaefTestId                                             │  │
│  │   └ parse(fqn) splits on last :: → (suite, test)               │  │
│  │                                                                │  │
│  │  TaefConsoleFilterProvider + TaefSourceLinkFilter               │  │
│  │   └ [File: path, Line: N] → clickable link in all panes        │  │
│  │                                                                │  │
│  │  TaefTestLocator (stub)                                        │  │
│  │   └ getLocation(protocol, path) → List<Location>               │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─── Constants & Utilities (shared) ────────────────────────────┐  │
│  │                                                                │  │
│  │  TaefTestConstants                                             │  │
│  │   ├ PROTOCOL_PREFIX = "taef"                                   │  │
│  │   ├ ALL_TEST_MACROS, TEST_METHOD_MACROS, etc.                  │  │
│  │   └ HEADER_NAME, HEADER_PATTERN                                │  │
│  │                                                                │  │
│  │  TaefCommandLineBuilder (pure, no IDE deps)                    │  │
│  │   └ build(TaefCommandLineParams) → GeneralCommandLine          │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

## Data Flow

The two engines diverge at test detection but converge at run configuration
creation. Everything from `TaefRunConfigurationProducer` onward is shared.

### Classic Engine (cidr.lang)

```
Source file (.cpp)
  ↓ PSI indexing
TaefTestFramework.validateTaefElement
  ↓ macro name + header + substitution + target type
TaefTestFramework.getTestLineMarkInfo → gutter icon (url = taef://Class::Method)
TaefTestFramework.extractTest → CidrTestScopeElement (pattern + configName)
  ↓ user clicks gutter icon
TaefRunConfigurationProducer.doSetupConfigurationFromContext
  ↓  (continues in shared execution path below)
```

### Nova Engine (Radler)

```
Source file (.cpp)
  ↓ text-based analysis (no PSI)
TaefNovaLineMarkerContributor → gutter icon
  ↓ user clicks gutter icon
TaefNovaLanguageSupport.getTestScopeElement → scope element
  ↓
TaefRunConfigurationProducer.doSetupConfigurationFromContext
  ↓  (continues in shared execution path below)
```

### Shared Execution Path (both engines)

```
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
