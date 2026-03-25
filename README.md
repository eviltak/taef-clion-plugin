# taef-clion-plugin
A CLion plugin that provides first-class support for [TAEF (Test Authoring and Execution Framework)](https://learn.microsoft.com/en-us/windows-hardware/drivers/taef/) tests.

Gutter icons, run configurations, and a full test-tree UI — works on both the
classic (cidr.lang) and Nova (Radler) C++ engines.

## Project Structure

```
taef-clion-plugin/              root = plugin shim + integration tests
├── build.gradle.kts            composedModule(:shared,:classic,:nova) + radler
├── settings.gradle.kts         include("shared","classic","nova")
├── src/main/resources/META-INF/
│   ├── plugin.xml              main descriptor (optional depends on cidr.lang + radler)
│   ├── pluginIcon.svg
│   └── (taef-lang.xml in classic/, taef-nova.xml in nova/)
├── src/test/                   extension point tests (needs full plugin loaded)
├── shared/                     engine-agnostic code (run config, output parser, launcher)
├── classic/                    classic C++ engine (TaefTestFramework, PSI-based detection)
├── nova/                       Nova engine (TaefNovaLanguageSupport + line marker)
└── testData/                   sample TAEF project + mock TE.exe for manual testing
```

### Module Overview

| Module      | Purpose                          | Engine Dependency | Key Classes                                                   |
|-------------|----------------------------------|-------------------|---------------------------------------------------------------|
| **shared**  | Engine-agnostic core             | None              | `TaefLanguageSupport`, `TaefRunConfigurationProducer`, output parser |
| **classic** | Classic C++ (cidr.lang) support  | cidr.lang         | `TaefTestFramework`, `TaefClassicLanguageSupport`             |
| **nova**    | Nova/Radler engine support       | radler            | `TaefNovaLanguageSupport`, `TaefNovaLineMarkerContributor`   |
| **root**    | Plugin assembly + integration tests | radler         | `plugin.xml`, `TaefExtensionPointTest`                        |

Both engine modules register an `applicationService` implementing `TaefLanguageSupport`.
At runtime, `TaefLanguageSupport.getInstance()` returns whichever engine is active,
and the run configuration producer, launcher, and output converter all work unchanged.

## Architecture

See [docs/architecture.md](docs/architecture.md) for the full component diagram and
data flow.

**Engine abstraction** — `TaefLanguageSupport` (in `shared`) is the service interface
that decouples run configuration creation from engine-specific test detection:

```
shared:  TaefLanguageSupport (interface)
           ├── isTestTarget(file)
           ├── getTestScopeElement(element)
           └── getInstance() → application service

classic: TaefClassicLanguageSupport implements TaefLanguageSupport
           └── delegates to TaefTestFramework (PSI-based, cidr.lang)

nova:    TaefNovaLanguageSupport implements TaefLanguageSupport
           └── text-based detection (no PSI / cidr.lang dependency)
```

The classic engine also registers `TaefTestFramework` as a `cidr.lang.testFramework`
extension for PSI gutter icons. The Nova engine uses `TaefNovaLineMarkerContributor`
(a `runLineMarkerContributor`) instead.

## Building

Requires JDK 21. Uses the [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

```bash
# Run tests per module
./gradlew :shared:test          # engine-agnostic tests
./gradlew :classic:test         # classic engine tests
./gradlew :nova:test            # Nova engine tests
./gradlew test                  # all tests (shared + classic + nova + root integration)

# IDE and distribution
./gradlew runIde                # launch a Nova dev instance of CLion
./gradlew buildPlugin           # distributable ZIP in build/distributions/
./gradlew verifyPlugin          # IntelliJ Plugin Verifier compatibility check
```

## Development Setup

1. Clone the repository.
2. Open in IntelliJ IDEA (or CLion) with the Gradle import wizard.
3. **(Optional)** Point to a local CLion installation to avoid downloading one:

   Add to `~/.gradle/gradle.properties`:
   ```properties
   clion.localIde.path=C:/path/to/CLion
   ```
4. Run `./gradlew test` to verify everything builds.

### Module IDE dependencies

| Module    | Bundled Plugins Used                      |
|-----------|------------------------------------------|
| root      | clion, cmake, nativeDebug, **radler**    |
| shared    | clion, cmake, nativeDebug                |
| classic   | clion, cmake, nativeDebug, **cidr.lang** |
| nova      | clion, cmake, nativeDebug, **radler**    |

## Further Documentation

- [Architecture](docs/architecture.md) — component diagram, data flow, design decisions
- [TAEF Feature Support](docs/taef-feature-support.md) — what's implemented, what's not
- [CLion Test Plugin API](docs/clion-test-plugin-api.md) — undocumented contracts discovered during development
