# Proposal

## Why

Wiring today is manual: `7metros/src/main/java/com/example/a7metros/CompositionRoot.kt` hand-builds the one `OkHttpNetworkClient` → `AsobalDataSource` → `AsobalScoresRepository` → `GetCurrentJornadaMatchesUseCase` → `ScoresViewModel` chain, by design (see the archived `fetch-asobal-scores` change's design.md: "No DI framework. ... so introducing a DI framework later is a localized change rather than a hunt through the codebase"). That design explicitly deferred the DI-framework decision rather than ruling it out, and flagged manual wiring getting "more tedious as more sources/features are added" as a known trade-off.

The project is about to grow past that single chain: standings (MVP, not yet built) and additional scraping sources (e.g. isquad.es) per CLAUDE.md's "multiple sources per type of information" goal mean multiple implementations of the same interface (e.g. several `DataSource`s, several repositories) needing to be selected/combined at composition time. Doing that by hand in one object gets harder to read and easier to get wrong (e.g. forgetting to update the one shared composition point when a dependency changes). Adopting a DI framework now, while the dependency graph is still small (one chain, five modules), is the moment flagged by the original design as the point to revisit this - introducing it later, once several features each have their own chain, would be a much larger change.

Koin is the framework of choice: it's a lightweight, idiomatic-Kotlin DSL-based DI framework with first-class support for both plain Kotlin/JVM modules (no Android dependency needed) and Android/Compose (ViewModel injection), matching this project's mix of `kotlin.jvm` and Android-plugin modules without forcing Android APIs onto the lower layers.

## What Changes

- Add Koin (`io.insert-koin`, via the Koin BOM for coherent cross-artifact versions) as a dependency: `koin-core` to every `kotlin.jvm` module that provides bindings (`7metros-network`, `7metros-datasource`, `7metros-data`, `7metros-domain`), `koin-android` + `koin-androidx-compose` to `7metros-presentation` and `7metros` for ViewModel/Compose integration.
- Each module gets its own Koin module (classic Kotlin DSL - `module { }` blocks, no annotation processing/KSP) declaring the bindings it owns: a network module, a datasource module, a data module, a domain module, a presentation module.
- `7metros` module: add an `Application` subclass that calls `startKoin { androidContext(this); modules(...) }`, combining every layer's module; register it in `AndroidManifest.xml`.
- Replace the hand-built `scoresViewModelFactory` in `CompositionRoot.kt` with a Koin-provided `ScoresViewModel` (`koinViewModel()` in Compose, or `by viewModel()` on the `ComponentActivity`); delete `CompositionRoot.kt`.
- `ScoresViewModel`'s constructor dependency (`GetCurrentJornadaMatchesUseCase`) and every class in the existing chain keep their current constructors (plain constructor injection) - Koin resolves them, nothing about the classes themselves needs to change.
- No observable app behavior changes: same `ScoresViewModel` states, same scraping logic, same screens. This is wiring-mechanism-only.
- Document the Koin convention in `CLAUDE.md`'s Architecture/Libraries section so future modules follow it without rediscovering the pattern.

## Capabilities

### New Capabilities
(none - no new system behavior)

### Modified Capabilities
(none - `scores`'s externally observable behavior is unchanged; this is an internal wiring mechanism swap, not a requirements change. `skip_specs: true` is set in this change's `.openspec.yaml` accordingly.)

## Impact

- **Affected modules**: all six (`7metros`, `7metros-presentation`, `7metros-domain`, `7metros-data`, `7metros-datasource`, `7metros-network`) - each gains a Koin module declaring its own bindings, per CLAUDE.md's "document new modules and their responsibility" convention.
- **New dependency**: Koin (`io.insert-koin:koin-bom`, `koin-core`, `koin-android`, `koin-androidx-compose`), added to `gradle/libs.versions.toml`.
- **Removed**: `7metros/src/main/java/com/example/a7metros/CompositionRoot.kt` and its manual `viewModelFactory`.
- **New file**: an `Application` class in `7metros`, referenced from `AndroidManifest.xml`.
- **Tests**: existing unit tests (`OkHttpNetworkClientTest`, `AsobalDataSourceTest`, `AsobalResultsParserTest`, `AsobalCurrentJornadaResolverTest`, `AsobalScoresRepositoryTest`, `GetCurrentJornadaMatchesUseCaseTest`) construct their subjects directly and are unaffected. A new lightweight test verifies the combined Koin module graph resolves (`koin-test`'s `checkModules`/`verify`), catching a missing binding at build time instead of at runtime.
- **Static analysis**: detekt's zero-tolerance `check` must still pass on every module after the Koin DSL code is added; no baseline/exception is anticipated, but this will be confirmed during implementation.
- **CLAUDE.md**: Architecture/Libraries sections updated to record Koin as the project's DI convention for future features.
