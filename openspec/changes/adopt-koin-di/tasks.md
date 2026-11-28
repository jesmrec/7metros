# Tasks

## 1. Dependency setup

- [ ] 1.1 Add `io.insert-koin:koin-bom` (current stable version) to `gradle/libs.versions.toml` as a version-catalog entry, plus `koin-core`, `koin-android`, `koin-androidx-compose`, and `koin-test` library aliases (versions resolved via the BOM, no explicit version on each alias). Verify: `./gradlew :7metros-network:dependencies` resolves `koin-core` with no version conflict.

## 2. `7metros-network`

- [ ] 2.1 Add the Koin BOM (platform) + `koin-core` to `7metros-network/build.gradle.kts`, add `networkModule` (binds `NetworkClient` → `single { OkHttpNetworkClient() }`) next to `NetworkClient`/`OkHttpNetworkClient`. Verify: `./gradlew :7metros-network:test :7metros-network:detekt` pass with zero findings.

## 3. `7metros-datasource`

- [ ] 3.1 Add the Koin BOM + `koin-core` to `7metros-datasource/build.gradle.kts`, add `datasourceModule` (binds `DataSource` → `single { AsobalDataSource(get(), AsobalCurrentJornadaResolver(get())) }`) next to `DataSource`/`AsobalDataSource`. Verify: `./gradlew :7metros-datasource:test :7metros-datasource:detekt` pass with zero findings.

## 4. `7metros-domain`

- [ ] 4.1 Add the Koin BOM + `koin-core` to `7metros-domain/build.gradle.kts`, add `domainModule` (provides `GetCurrentJornadaMatchesUseCase` → `factory { GetCurrentJornadaMatchesUseCase(get()) }`) next to `GetCurrentJornadaMatchesUseCase`/`ScoresRepository`. Verify: `./gradlew :7metros-domain:test :7metros-domain:detekt` pass with zero findings.

## 5. `7metros-data`

- [ ] 5.1 Add the Koin BOM + `koin-core` to `7metros-data/build.gradle.kts`, add `dataModule` (binds `ScoresRepository` → `single { AsobalScoresRepository(get()) }`) next to `AsobalScoresRepository`. Verify: `./gradlew :7metros-data:test :7metros-data:detekt` pass with zero findings.

## 6. `7metros-presentation`

- [ ] 6.1 Add the Koin BOM + `koin-androidx-compose` to `7metros-presentation/build.gradle.kts`, add `presentationModule` (provides `ScoresViewModel` → `viewModel { ScoresViewModel(get()) }`) next to `ScoresViewModel`. Verify: `./gradlew :7metros-presentation:detekt` passes with zero findings and `./gradlew :7metros-presentation:connectedAndroidTest` (or `:testDebugUnitTest` if no device test exists yet) still passes unchanged.

## 7. `7metros` app module

- [ ] 7.1 Add the Koin BOM + `koin-android` + `koin-test` (test-only) to `7metros/build.gradle.kts`. Verify: `./gradlew :7metros:dependencies` resolves cleanly.
- [ ] 7.2 Add an `App : Application()` class calling `startKoin { androidContext(this); modules(networkModule, datasourceModule, dataModule, domainModule, presentationModule) }`, and register it as `android:name=".App"` in `AndroidManifest.xml`. Verify: app builds and launches without a Koin startup exception.
- [ ] 7.3 Add a unit test (`src/test/java/...`) using `koin-test`'s `checkModules { modules(networkModule, datasourceModule, dataModule, domainModule, presentationModule) }` to assert the combined graph resolves with no missing binding. Verify: `./gradlew :7metros:testDebugUnitTest` passes, and fails if a binding is deliberately removed (sanity-check the test actually catches a break, then restore).
- [ ] 7.4 Replace `MainActivity`'s `by viewModels { CompositionRoot.scoresViewModelFactory }` with `by viewModel()` (`org.koin.androidx.viewmodel.ext.android.viewModel()`), then delete `CompositionRoot.kt`. Verify: `./gradlew :7metros:assembleDebug` succeeds with no remaining reference to `CompositionRoot`.
- [ ] 7.5 Run the existing instrumented UI test(s) (`7metros/src/androidTest`, `7metros-presentation`'s instrumented tests if any) against the Koin-wired app on an emulator/device. Verify: tests pass unchanged, and manually confirm the app still shows current-jornada scores immediately on launch with the same loading/data/empty/failure behavior as before.
- [ ] 7.6 Run `./gradlew check` from the repo root. Verify: unit tests, instrumented tests, and detekt all pass with zero findings across every module.

## 8. Documentation

- [ ] 8.1 Update CLAUDE.md's Architecture and Libraries sections to record: Koin (classic DSL) as the project's DI framework, the one-Koin-module-per-Gradle-module convention, and that `7metros`'s `App` class is the single composition point combining every layer's module. Verify: CLAUDE.md reflects the structure now in the code, matching its own "update this file in the same PR" rule.
