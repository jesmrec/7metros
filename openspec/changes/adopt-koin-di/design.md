# Design

## Context

See proposal.md - Why. Today the only composition point is `7metros/src/main/java/com/example/a7metros/CompositionRoot.kt`, a hand-built object exposing one `viewModelFactory` for the single `OkHttpNetworkClient → AsobalDataSource → AsobalScoresRepository → GetCurrentJornadaMatchesUseCase → ScoresViewModel` chain. There is no `Application` subclass yet; `MainActivity` gets its `ScoresViewModel` via `by viewModels { CompositionRoot.scoresViewModelFactory }`.

Module shape (from CLAUDE.md): `7metros` and `7metros-presentation` use the Android plugins; `7metros-domain`, `7metros-data`, `7metros-datasource`, `7metros-network` use the plain `kotlin.jvm` plugin and must not need Android APIs. Koin's split artifacts match this directly: `koin-core` is pure Kotlin/JVM, `koin-android`/`koin-androidx-compose` are Android-only.

## Goals / Non-Goals

**Goals:**
- Replace the manual composition point with Koin, module-by-module, with zero change to any class's existing constructor or runtime behavior.
- Establish one Koin module per Gradle module, each declaring only the bindings that module owns - so a future module follows the same pattern without rediscovering it.
- Make a missing/misconfigured binding fail at build/test time rather than only at runtime (Koin resolves lazily by default, so a typo normally only surfaces as a crash when that dependency is first requested).

**Non-Goals:**
- Generalizing any binding to multiple concurrent implementations (e.g. a second `DataSource` for isquad.es). Only one implementation exists per interface today; this design documents the convention a second implementation would follow, without introducing speculative qualifiers or multi-binding code now.
- Koin Annotations/KSP - classic DSL only (confirmed with the user).
- Any change to `ScoresViewModel`'s states, the scraping logic, or anything user-visible.
- Scopes beyond Koin's default singleton (`single`)/`factory`/`viewModel` - no custom scopes (e.g. per-screen) until a concrete need appears.

## Decisions

**Versioning: Koin BOM (`io.insert-koin:koin-bom`).**
Each module that needs Koin imports the BOM as a platform dependency and declares `koin-core`/`koin-android`/`koin-androidx-compose`/`koin-test` without an explicit version, mirroring the `androidx.compose.bom` pattern already used in `7metros` and `7metros-presentation`. Keeps every Koin artifact across all six modules on a mutually-tested version set; the exact BOM version is pinned in `gradle/libs.versions.toml` during implementation (check Maven Central for the current stable release at that time rather than hardcoding a version in this document). Alternative considered: pin each artifact's version independently - rejected, same reasoning the project already applied to the Compose BOM (avoids version-skew bugs between Koin artifacts).

**One Koin module per Gradle module, defined beside the code it wires.**
- `7metros-network`: `networkModule` binds `NetworkClient` → `single { OkHttpNetworkClient() }`.
- `7metros-datasource`: `datasourceModule` binds `DataSource` → `single { AsobalDataSource(get(), AsobalCurrentJornadaResolver(get())) }`. `AsobalCurrentJornadaResolver` is constructed inline, not given its own binding - nothing besides `AsobalDataSource` consumes it today, and adding a binding for a class with exactly one consumer would be speculative; give it one if a second consumer appears.
- `7metros-data`: `dataModule` binds `ScoresRepository` (the domain interface) → `single { AsobalScoresRepository(get()) }`.
- `7metros-domain`: `domainModule` provides `GetCurrentJornadaMatchesUseCase` → `factory { GetCurrentJornadaMatchesUseCase(get()) }`. `factory` (new instance per injection) rather than `single`: use cases are stateless wrappers with no reason to share an instance, and `factory` is the convention future use cases should default to.
- `7metros-presentation`: `presentationModule` provides `ScoresViewModel` → `viewModel { ScoresViewModel(get()) }` (koin-androidx-compose's `viewModel` DSL).
Each module is a top-level `val` in that Gradle module's existing package, so it lives next to the classes it binds rather than in a separate "DI" package - keeps each module's Koin wiring inside its own single-responsibility boundary (CLAUDE.md: "don't leak logic across layers").
Alternative considered: one giant module in the app module listing every binding - rejected, defeats the goal of each module owning its own wiring and would force `7metros-network`/`datasource`/`data`/`domain` classes to be `public` across module boundaries beyond what they already expose, or force the app module to depend on implementation classes it has no other reason to see.

**Composition root: a new `Application` subclass in `7metros`.**
`App : Application()` calls `startKoin { androidContext(this); modules(networkModule, datasourceModule, dataModule, domainModule, presentationModule) }` in `onCreate`, registered as `android:name=".App"` in `AndroidManifest.xml`. This is the one place that still knows about every layer - same role `CompositionRoot` played, now a declarative module list instead of hand-built `initializer { }` code. `CompositionRoot.kt` is deleted once this is in place.

**`MainActivity` obtains its ViewModel via Koin's Activity delegate.**
`private val scoresViewModel: ScoresViewModel by viewModel()` (`org.koin.androidx.viewmodel.ext.android.viewModel()`, from `koin-android`) replaces `by viewModels { CompositionRoot.scoresViewModelFactory }`. Keeps the existing shape (one activity-level property, passed into `MainScreen`) rather than restructuring `MainActivity`/`MainScreen` to call `koinViewModel()` inside the Compose tree - a smaller, more localized diff for this change.

**Module-graph verification: a `checkModules` test.**
A new unit test in `7metros` (`src/test/java/...`, alongside the existing `testImplementation(libs.junit)`) uses `koin-test`'s `checkModules { modules(networkModule, datasourceModule, dataModule, domainModule, presentationModule) }` to assert the combined graph resolves. Koin resolves bindings lazily, so a missing/mistyped binding would otherwise only surface as a runtime `NoDefinitionFoundException` the first time that dependency is requested - this test catches it at build time instead, which matters more here than in a single-chain app because future features will add their own module to this same combined list.

## Risks / Trade-offs

- **Koin resolves lazily; a missing binding is normally a runtime crash, not a compile error** (unlike Dagger/Hilt's compile-time graph) → Mitigation: the `checkModules` test above exercises the full graph on every build.
- **Adding `koin-android`/`koin-androidx-compose` to `7metros` and `7metros-presentation` adds to APK size and build classpath** → Mitigation: accepted - Koin's artifacts are small and reflection-free (DSL-based, no classpath scanning), negligible next to Compose's own footprint; no measurable impact expected on the "show scores straight away" startup goal, but this is confirmed during implementation, not assumed.
- **detekt's zero-tolerance `check` runs on every module, including the new Koin DSL code** → Mitigation: no exception is anticipated beyond what `config/detekt/detekt.yml` already documents; any finding is fixed as part of this change, not suppressed.
- **This reverses a previously-documented decision** ("No DI framework" in the archived `fetch-asobal-scores` design) → Mitigation: that design explicitly framed itself as deferring, not rejecting, DI adoption, and named "more sources/features added" as the trigger to revisit it - documented here as the trigger being met, not a reversal made lightly.

## Migration Plan

Implemented bottom-up, one Gradle module at a time, so the build stays green at every step:
1. `7metros-network` → add Koin BOM/`koin-core`, add `networkModule`, run module tests + detekt.
2. `7metros-datasource` → same, add `datasourceModule`.
3. `7metros-domain` → same, add `domainModule`.
4. `7metros-data` → same, add `dataModule`.
5. `7metros-presentation` → add `koin-androidx-compose`, add `presentationModule`.
6. `7metros` → add `koin-android`, the `App` class + manifest entry, switch `MainActivity` to `by viewModel()`, add the `checkModules` test, delete `CompositionRoot.kt`.
7. Update CLAUDE.md's Architecture/Libraries sections to record the per-module Koin convention.

No data migration or rollout step is needed - this is dev-time wiring with no persisted state and no server component. Rollback is reverting the change's commits.
