# CLion Test Framework Plugin API — Undocumented Contracts

> **Target**: CLion 2025.1 (CL-251.23774.442)
>
> The [official extension point list](https://plugins.jetbrains.com/docs/intellij/clion-extension-point-list.html)
> names the extension points and implementation classes, but documents none of the
> contracts below. Everything here was discovered through decompilation (`javap`)
> and trial-and-error during development of this plugin.

## 1. Test Framework Class Hierarchy

The official docs mention `OCTestFramework` and `CidrTestFrameworkDetector` as
implementation classes. They do not document the inheritance chain or which
intermediate class to extend:

```
OCTestFramework                              (c-plugin.jar)
  └─ CidrTestFrameworkBase<T>                (c-plugin.jar)
       └─ CidrTestWithScopeElementsFramework<T>  (c-plugin.jar)
            └─ CidrTestWithScopeElementsAndGeneratorFramework  (c-plugin.jar)
```

**Which to extend**: `CidrTestWithScopeElementsAndGeneratorFramework` — it provides
`createTestScopeElementForSuiteAndTest()` and integrates with the producer/scope
system. GTest, Boost, Catch2, and Doctest all extend this class.

### 1.1 Constructor — `CidrTestWithScopeElementsAndGeneratorFramework`

```java
protected CidrTestWithScopeElementsAndGeneratorFramework(
    String frameworkId,
    Class<? extends PsiElement>... testHolderClasses
)
```

- `frameworkId`: protocol prefix string (e.g. `"taef"`, `"gtest"`)
- `testHolderClasses`: PSI element types that can contain tests. For macro-based
  frameworks, pass `OCMacroCall::class.java`.

**Not documented anywhere**. Discovered by decompiling `CidrTestFrameworkBase`
which chains to `CidrTestWithScopeElementsFramework`.

### 1.2 Nullable parameter annotations (CLion 2025.1)

Several `OCTestFramework` methods changed nullable annotations in 2025.1.
The official docs do not mention this API break:

| Method | Old signature | 2025.1 signature |
|--------|--------------|-----------------|
| `isAvailable` | `(PsiFile)` | `(PsiFile?)` |
| `isTestMethodOrFunction` | `(OCSymbol, PsiElement, Project)` | `(OCSymbol?, PsiElement?, Project)` |
| `isTestClassOrStruct` | `(OCSymbol, PsiElement, Project)` | `(OCSymbol?, PsiElement?, Project)` |
| `getTestLineMarkInfo` | `(PsiElement)` | `(PsiElement?)` |

### 1.3 `CidrTestFrameworkVersion` is an enum

`createFrameworkVersionDirectly()` must return non-null. The type is an enum
with two values: `NOT_AVAILABLE` and `DEFAULT`. Return `DEFAULT` for a framework
that doesn't version-detect.

### 1.4 `OCSymbol.getContainingOCFile()` requires `Project`

```java
OCFile getContainingOCFile(Project project)
```

Earlier SDK versions had a no-arg overload. In 2025.1, the `Project` parameter
is required. The `OCSymbol` comes from `OCMacroCall.resolveToSymbol()`.

### 1.5 `CidrTestScopeElement.id` is nullable

The `id` property returns `@Nullable String?`. Guard against null when using it
as a map key or in string interpolation.

### 1.6 `findExtension()` lives on `CidrTestFrameworkBase`

```java
public static <T extends OCTestFramework> T findExtension(Class<T> clazz)
```

Use this in your `getInstance()` companion method, not `EP_NAME.findExtension()`.

## 2. Test Framework Detection

Extension point: `cidr.testFrameworkDetector` (namespace: `cidr`)

### 2.1 `CidrTestFrameworkDetector` — jar location

Lives in `cidr-base-plugin.jar`, **not** `c-plugin.jar`.

### 2.2 `getTestHeaderName()` returns a regex

The return value is compiled via `Pattern.compile()` and matched with
`Matcher.find()` (substring search). The `@RegExp` annotation is not visible
in the official docs.

GTest uses `"gtest[.]h"`, Boost uses `"unit_test[.]hpp"`. Use `[.]` for literal
dots. Use `\b` for word boundaries if needed.

### 2.3 `hasTestConfiguration()` and MODULE targets

The default `CidrTestFrameworkDetector.fastFilterTestSource()` fallback **only
runs for EXECUTABLE targets**. TAEF tests (and similar DLL-based frameworks)
must override `hasTestConfiguration()` to explicitly handle
`CMakeConfiguration.TargetType.MODULE_LIBRARY`.

### 2.4 `fastFilterTestSource()` scans first N bytes

```java
static CidrTestFrameworkDetector fastFilterTestSource(
    Collection<File> sources,
    int maxFiles,
    int headerContextLength,
    Condition<VirtualFile> filter
)
```

`headerContextLength` controls how many bytes of each file are scanned.
Use ~4096 for header detection.

## 3. Run Configuration

### 3.1 `CMakeTestRunConfiguration` constructor

```java
public CMakeTestRunConfiguration(
    Project project,
    ConfigurationFactory factory,
    String name,
    Function<CidrTestRunConfiguration, CidrTestRunConfigurationData> dataFactory
)
```

The fourth parameter is a factory function that creates the test data object.
Pass a method reference: `::YourTestRunConfigurationData`.

**This constructor pattern is completely undocumented**. Discovered by
decompiling `CMakeTestRunConfiguration` and cross-referencing with
`CMakeGoogleTestRunConfigurationProducer`.

### 3.2 `CMakeRunConfigurationType` constructor

```java
CMakeRunConfigurationType(
    String id,
    String factoryId,
    String displayName,
    String description,
    NotNullLazyValue<Icon> icon
)
```

The `factoryId` is separate from the type `id`. The factory is auto-created
by the parent class.

### 3.3 `CMakeAppRunConfiguration` useful methods

- `suggestNameForTarget()`: returns the CMake target name, or null
- `getHelper()`: returns `CMakeBuildConfigurationHelper` for the project
- `setTargetAndConfigurationData(BuildTargetAndConfigurationData)`: sets target
- `getBuildAndRunConfigurations(target, null, false)`: resolves build config

## 4. Test Run Configuration Data

### 4.1 `CidrTestRunConfigurationData` abstract methods

```java
abstract void checkData() throws RuntimeConfigurationException;
abstract String formatTestMethod();
abstract String getTestingFrameworkId();
abstract CommandLineState createState(ExecutionEnvironment, Executor, CidrTestScope?);
abstract SMTRunnerConsoleProperties createTestConsoleProperties(Executor, ExecutionTarget);
```

**`createTestConsoleProperties` must return non-null** in 2025.1. Return
`SMTRunnerConsoleProperties(config, frameworkId, executor)` as a minimum.

**`createState` has nullable `CidrTestScope?`** parameter — the `?` is not
obvious from the abstract declaration.

### 4.2 The `myConfiguration` field

`CidrTestRunConfigurationData` stores `myConfiguration` as a protected field.
Cast it to your config type in `createState()`.

## 5. Run Configuration Producer

Extension point: `com.intellij.runConfigurationProducer` (namespace: `com.intellij`)

### 5.1 Class to extend

```
CidrTestWithScopeElementsRunConfigurationProducer<BC, TARGET, CONFIG, TEST_OBJECT>
```

Lives in `cidr-base-plugin.jar`.

For CMake-based frameworks, the type parameters are:
```kotlin
CidrTestWithScopeElementsRunConfigurationProducer<
    CMakeConfiguration,
    CMakeTarget,
    CMakeTestRunConfiguration,   // not CMakeAppRunConfiguration
    CidrTestScopeElement
>
```

### 5.2 Constructor

```java
protected CidrTestWithScopeElementsRunConfigurationProducer(
    CidrTargetRunConfigurationBinder<BC, TARGET, ? super CONFIG> binder,
    CidrTestRunConfigurationLanguageSupport framework
)
```

- `binder`: use `CMakeTargetRunConfigurationBinder.INSTANCE` (in `clion-ide.jar`)
- `framework`: your `TaefTestFramework.getInstance()`

The binder is a **singleton** — do not construct a new one.

### 5.3 Only `getConfigurationFactory()` needs overriding

The base class handles `setupConfigurationFromContext()` and
`isConfigurationFromContext()` automatically by calling your framework's
`extractTest()`, `isTestMethodOrFunction()`, etc.

### 5.4 The config must implement `CidrTestRunConfiguration`

The producer's `getDelegate()` casts the config to `CidrTestRunConfiguration`
to access `getTestData()`. If your config extends `CMakeAppRunConfiguration`
directly (without the test interface), the producer will fail with ClassCastException.

## 6. Jar Locations

Classes are spread across multiple jars with no documentation of which is where:

| Jar | Key classes |
|-----|------------|
| `c-plugin.jar` | `OCTestFramework`, `CidrTestFrameworkBase`, `CidrTestWithScopeElementsFramework`, `CidrTestWithScopeElementsAndGeneratorFramework`, `CidrTestFrameworkVersion`, `OCMacroCall`, `OCMacroSymbol`, `OCFile`, `OCReferenceElement` |
| `cidr-base-plugin.jar` | `CidrTestFrameworkDetector`, `CidrTestRunConfigurationData`, `CidrTestWithScopeElementsRunConfigurationProducer`, `CidrTestRunConfigurationProducer`, `CidrTestScopeElement`, `CidrCommandLineState`, `CidrLauncher` |
| `clion-ide.jar` | `CMakeRunConfigurationType`, `CMakeTestRunConfiguration`, `CMakeAppRunConfiguration`, `CMakeTargetRunConfigurationBinder`, `CMakeBuildConfigurationHelper`, `CMakeLauncher` |

## 7. Gradle Dependency Gotchas

### 7.1 MockK and kotlinx-coroutines conflict

MockK pulls in `kotlinx-coroutines-core` which conflicts with the platform's
bundled version, causing `NoSuchMethodError` on `DebugProbesImpl` or test hangs.

```kotlin
testImplementation("io.mockk:mockk:1.14.9") {
    exclude(group = "org.jetbrains.kotlinx")
}
```

### 7.2 Required bundled plugin dependencies

```kotlin
intellijPlatform {
    clion("2025.1")
    bundledPlugin("com.intellij.clion")
    bundledPlugin("com.intellij.cidr.base")   // CidrTestFrameworkDetector, producers
    bundledPlugin("com.intellij.cidr.lang")   // OCTestFramework, PSI
    bundledPlugin("com.intellij.nativeDebug") // debug support
}
```

`com.intellij.cidr.base` is required for test infrastructure classes.
`com.intellij.cidr.lang` is required for PSI/macro resolution.
Both are undocumented as requirements for test framework plugins.

## 8. Extension Point Registration

### 8.1 Namespace mapping

| Extension point | XML namespace | Config file |
|----------------|--------------|-------------|
| `cidr.lang.testFramework` | `cidr.lang` | Optional config file depending on `com.intellij.modules.cidr.lang` |
| `cidr.testFrameworkDetector` | `cidr` | Main `plugin.xml` |
| `com.intellij.runConfigurationProducer` | `com.intellij` | Main `plugin.xml` |
| `com.intellij.configurationType` | `com.intellij` | Main `plugin.xml` |

### 8.2 Optional dependency pattern

`cidr.lang.testFramework` should be registered in an optional config file
to avoid hard dependency on `com.intellij.modules.cidr.lang`:

```xml
<!-- plugin.xml -->
<depends optional="true" config-file="taef-lang.xml">com.intellij.modules.cidr.lang</depends>

<!-- taef-lang.xml -->
<idea-plugin>
    <extensions defaultExtensionNs="cidr.lang">
        <testFramework implementation="com.github.eviltak.taef.TaefTestFramework"/>
    </extensions>
</idea-plugin>
```

## 9. Scope Elements and the Producer Pipeline

### 9.1 `CidrTestScopeElementImpl` — delegate lifecycle

`CidrTestScopeElementImpl` stores a generator delegate (`myDelegate`) that
provides `getElement()`, `getTestPath()`, `getPattern()`, etc. The delegate
is created by calling the generator `Function` in the constructor.

**Critical**: `AbstractPropertiesGenerator` methods `getElement()`,
`getSuiteName()`, `getTestName()`, and `getId()` all throw
`RuntimeException("Not defined")`. They are stubs — you must override them
or use `DefaultPropertiesGenerator` which accesses `myOwner` fields directly.

```kotlin
// WRONG — getSuiteName() on AbstractPropertiesGenerator throws
object : AbstractPropertiesGenerator() {
    override fun getPattern() = suiteName  // throws "Not defined"
}

// CORRECT — access myOwner fields from DefaultPropertiesGenerator
object : DefaultPropertiesGenerator(impl) {
    override fun getPattern() = "*${myOwner.suiteName.orEmpty()}*"
}
```

### 9.2 `getElement()` — the producer requires it

`CidrTestWithScopeElementsRunConfigurationProducer.getElement(scopeElement)`
calls `CidrTestScopeElement.getElement()` during `doSetupConfigurationFromContext`.
If the delegate's `getElement()` throws, the producer catches it and logs an
error — which `TestLoggerFactory` treats as a test failure.

**`createTestScopeElementForSuiteAndTest` does NOT support `getElement()`**.
The scope element's `getElement()` always delegates to the generator, and
neither `AbstractPropertiesGenerator` nor `DefaultPropertiesGenerator`
implement it.

**Use `createTestScopeElementForVirtualTestPath` instead** — it creates a
`CidrTestScopeElementImpl` subclass that overrides `getElement()` directly
on the impl (not the delegate), using a `Supplier<PsiElement>`:

```kotlin
override fun extractTest(element: PsiElement): CidrTestScopeElement? {
    return CidrTestScopeElementImpl.createTestScopeElementForVirtualTestPath(
        testPath, displayName,
        { element },  // Supplier<PsiElement> — captured for getElement()
        Function { impl -> MyPropertiesGenerator(impl) }
    )
}
```

### 9.3 `getGenerator()` vs `extractTest` generators

- `getGenerator()` returns the framework's default generator. Used by:
  - `createTestScopeElementForSuiteAndTest` (base class)
  - `getTestSerializer` (cache deserialization)
  - `cacheResultForTestHolder` (test list caching)
- `extractTest` can create scope elements with a custom inline generator
  (e.g. via `createTestScopeElementForVirtualTestPath`).

Scope elements from `getGenerator()` will NOT have `getElement()` working.
Scope elements from `extractTest` using `createTestScopeElementForVirtualTestPath`
WILL have it working.

### 9.4 `fastFilterTestSource` — Processor semantics

```java
static ConfigurationTypeBase fastFilterTestSource(
    Collection<File> sources, int maxFiles, int contextLength,
    Processor<? super File> filter
)
```

The `Processor.process(file)` return value:
- `false` → **accept** this file (stop processing, include in scan)
- `true` → **skip** this file (continue to next)

This is the standard IntelliJ `Processor` contract but easily confused.

The method also appends `[\">]` to `getTestHeaderName()` before compiling
the regex, to match the closing bracket/quote in `#include` directives.

## 10. Writing Integration Tests with HeavyPlatformTestCase

### 10.1 CMake workspace setup

`HeavyPlatformTestCase` creates an isolated project. CMake workspace needs
explicit setup:

```kotlin
override fun setUp() {
    super.setUp()
    ensureToolchainConfigured()
    copySampleProjectIntoTestProject()
    CMakeWorkspace.forceUseStandardDirForGenerationInTests = true
    FileSymbolTablesCache.setShouldBuildTablesInTests(
        FileSymbolTablesCache.SymbolsProperties(
            FileSymbolTablesCache.SymbolsProperties.SymbolsKind.ONLY_USED,
            false, false
        )
    )
    FileSymbolTablesCache.forceSymbolsLoadedInTests(true)
    reloadCMakeWorkspace()
}
```

### 10.2 Toolchain configuration

CLion auto-detects the bundled MinGW toolchain ("Test Toolchain"), but CMake
generation may fail if the environment isn't set up. Use
`setEnvironmentFactoryInTests` to provide a `CPPEnvironment`:

```kotlin
CMakeWorkspace.setEnvironmentFactoryInTests { _, _, _, _ ->
    CPPEnvironment(CPPToolchains.getInstance().defaultToolchain!!)
}
```

### 10.3 CMake reload — `loadState` + `load(true)`

`scheduleReload()` alone does NOT trigger CMake generation if the workspace
has no project dir linked. The correct sequence:

```kotlin
val stateElement = CMakeWorkspace.createStateElement(projectRoot)
workspace.loadState(stateElement)

ProgressManager.getInstance().run(
    object : Task.Modal(project, "Loading CMake workspace", false) {
        override fun run(indicator: ProgressIndicator) {
            workspace.load(true)
        }
    }
)
workspace.waitForReloadsToFinish(120_000)
IndexingTestUtil.waitUntilIndexesAreReady(project)
```

**`workspace.load(true)` requires a ProgressIndicator** — wrap in
`ProgressManager.run(Task.Modal(...))`. Without it, throws
`"There is no ProgressIndicator or Job in this thread"`.

**Do NOT use `runBlocking` or `waitForSmartMode()`** — both deadlock
because they block the EDT thread that CMake/indexing dispatches to.

### 10.4 `forceUseStandardDirForGenerationInTests`

Without this, CMake generates build files in
`.intellijPlatform/sandbox/.../system-test/projects/...` which is NOT in the
VFS allowed roots. Setting this puts the build dir under the project temp
directory (which IS in allowed roots).

### 10.5 Symbol table building

By default, `FileSymbolTablesCache` is set to `NO_SYMBOLS` in tests.
This means:
- `getFrameworkVersionUsingImportedMacro()` can't find any macros
- `OCMacroCall.resolveToSymbol()` may return null
- `OCGlobalProjectSymbolsCache.getAllSymbolNames()` returns empty

Enable with `ONLY_USED` mode (builds tables for source files and their
direct includes without processing all system headers):

```kotlin
FileSymbolTablesCache.setShouldBuildTablesInTests(
    FileSymbolTablesCache.SymbolsProperties(
        FileSymbolTablesCache.SymbolsProperties.SymbolsKind.ONLY_USED,
        false, false
    )
)
```

**`ALL_INCLUDING_UNUSED_SYSTEM_HEADERS`** works but takes 30+ seconds
per test because it builds symbol tables for all MinGW system headers.

### 10.6 `collectTestObjects` requires explicit update

`collectTestObjects(file)` reads from the indexed cache, not from
`createTestObjectsDirectly`. The cache is populated by the test list
updater which runs asynchronously. In tests, call
`updateTestsListOrScheduleUpdateIfCannotWait(file)` first:

```kotlin
framework.updateTestsListOrScheduleUpdateIfCannotWait(psiFile)
val testObjects = framework.collectTestObjects(psiFile)
```

### 10.7 Execution targets

`DefaultExecutionTarget.INSTANCE` does not resolve CMake build
configurations. Use `CMakeExecutionTargetProvider` to get a proper target:

```kotlin
val targets = CMakeExecutionTargetProvider().getTargets(project, config)
val executionTarget = targets.firstOrNull() ?: DefaultExecutionTarget.INSTANCE
```

Pass this target to `getBuildAndRunConfigurations()` and
`ExecutionEnvironmentBuilder.target()`.

### 10.8 Copying sample project files

Only copy version-controlled files (not `.idea/`, `cmake-build-*/`):

```kotlin
val filesToCopy = listOf("CMakeLists.txt", "src")
for (name in filesToCopy) {
    File(sampleDir, name).copyRecursively(File(projectRoot, name), overwrite = true)
}
```

### 10.9 `isAvailable` — follow the GTest pattern

Don't override `isAvailable()`. Override `createFrameworkVersionDirectly()`
using `getFrameworkVersionUsingImportedMacro()`:

```kotlin
override fun createFrameworkVersionDirectly(file: PsiFile) =
    getFrameworkVersionUsingImportedMacro(file, "TEST_METHOD")
```

This uses `OCGlobalProjectSymbolsCache` (index-based, not full PSI
resolution) to check if the macro is importable in the file.

### 10.10 `TestLoggerFactory` strictness

`TestLoggerFactory` converts any `Logger.error()` call to a test failure,
even if the error is caught and handled. This affects:
- `RunConfigurationProducer.createConfigurationFromContext()` — catches
  RuntimeExceptions and logs them
- Any CLion framework code that logs errors during normal operation

The fix is to prevent the error from being logged in the first place
(e.g. by overriding `getElement()` on scope elements via
`createTestScopeElementForVirtualTestPath`), not to suppress the logger.

## 11. SMRunner Test Results Pipeline — URL & Name Contracts

The test results tree (SMRunner) requires consistent naming across three
components. URLs must match exactly for the IDE to link gutter icons to
test results.

### 11.1 URL Format

All components must agree on a single URL format:
```
<protocol>://<qualified-test-path>
```

Example: `taef://SampleTestClass::TestMethodPass`

### 11.2 Data flow

```
┌─ PSI Detection (getTestLineMarkInfo) ─────────────────────────┐
│  urlInTestTree = "taef://SampleTestClass::TestMethodPass"     │
│  isSuite = false                                              │
│  → Used by IDE to link gutter icon to test results tree       │
└───────────────────────────────────────────────────────────────┘
         ↕ must match
┌─ Output Converter (processServiceMessages) ───────────────────┐
│  testStarted(name, url):                                      │
│    name = "SampleTestClass::TestMethodPass"                   │
│    url  = "taef://SampleTestClass::TestMethodPass"            │
│  testFinished(name, "", failed):                              │
│    name = "SampleTestClass::TestMethodPass"                   │
│  testErrOut(name, text):                                      │
│    name = "SampleTestClass::TestMethodPass"                   │
│  testStdOut(name, text, nodeId):                              │
│    name = "SampleTestClass::TestMethodPass"                   │
│    nodeId = name (same string)                                │
└───────────────────────────────────────────────────────────────┘
         ↕ url parsed by
┌─ Test Locator (getLocation) ──────────────────────────────────┐
│  Input:  protocol = "taef"                                    │
│          path = "SampleTestClass::TestMethodPass"             │
│  Output: list of PsiElement locations (for "Go to Test")      │
└───────────────────────────────────────────────────────────────┘
```

### 11.3 Command Line State

The `CommandLineState` returned by `createState()` must extend
`CidrTestCommandLineState`, NOT plain `CidrCommandLineState`. This is
because `CidrTestCommandLineState.createConsole()` calls
`CidrConsoleBuilder.createConsole(configType, consoleProperties)` which
wires up the SMRunner test tree. Without it, you get a plain text console
with no test results tree.

GTest reference: `CidrGoogleTestCommandLineState` extends
`CidrTestCommandLineState` and implements:
- `doCreateRerunFailedTestsAction()` — uses `CidrRerunFailedTestsActionEx`
  with a `Function<Pair<AbstractTestProxy, Project>, String>` that extracts
  the test pattern from the proxy's location URL
- `createTestScopeElement(suite, test)` — creates a scope element for
  rerunning specific tests

### 11.4 Console Properties

`CidrAbstractTestConsoleProperties` is the correct base class (not
`SMTRunnerConsoleProperties`). It provides:
- `createTestEventsConverter()` → your custom output parser
- `getTestLocator()` → URL-to-source resolver
- `getAssertionPattern()` → regex for assertion highlighting
