# Tasks

## 1. Rename the app module to `7metros`

- [x] 1.1 Move the `app/` folder to `7metros/` with `git mv` (keeps history) and change `include(":app")` to `include(":7metros")` in `settings.gradle.kts`; verify `./gradlew projects` lists `:7metros` and not `:app`
- [x] 1.2 Confirm `7metros/build.gradle.kts` still has `namespace` and `applicationId` = `com.example.a7metros`; verify `./gradlew :7metros:assembleDebug` succeeds

## 2. Version catalog and root build

- [x] 2.1 Add the `android-library` (`com.android.library`, `agp`) and `kotlin-jvm` (`org.jetbrains.kotlin.jvm`, `kotlin`) plugin aliases to `gradle/libs.versions.toml`, and declare both `apply false` in the root `build.gradle.kts`; verify Gradle sync and `./gradlew help` succeed with no KGP version-mismatch warnings

## 3. Create the library modules (structure only, no code)

- [ ] 3.1 Create `7metros-domain/build.gradle.kts` (kotlin-jvm, Java/JVM target 11, no project dependencies) and include it in settings; verify `./gradlew :7metros-domain:build` succeeds
- [ ] 3.2 Create `7metros-network/build.gradle.kts` (kotlin-jvm, Java/JVM target 11, no project dependencies) and include it; verify `./gradlew :7metros-network:build` succeeds
- [ ] 3.3 Create `7metros-datasource/build.gradle.kts` (kotlin-jvm, Java/JVM target 11, `implementation(project(":7metros-network"))`) and include it; verify `./gradlew :7metros-datasource:build` succeeds
- [ ] 3.4 Create `7metros-data/build.gradle.kts` (kotlin-jvm, Java/JVM target 11, `implementation` on `:7metros-domain` and `:7metros-datasource`) and include it; verify `./gradlew :7metros-data:build` succeeds
- [ ] 3.5 Create `7metros-presentation/build.gradle.kts` (android-library + kotlin-compose, `namespace = "com.example.a7metros.presentation"`, compileSdk 37, minSdk 28, Java 11, `compose = true`, `implementation(project(":7metros-domain"))`) and include it; verify `./gradlew :7metros-presentation:assembleDebug` succeeds

## 4. Wire the app module and move the existing UI

- [ ] 4.1 Add `implementation(project(...))` to `7metros/build.gradle.kts` for all five library modules; verify `./gradlew :7metros:dependencies --configuration debugRuntimeClasspath` shows all five
- [ ] 4.2 Use `git mv` to move `ui/MainScreen.kt` to `7metros-presentation/src/main/java/com/example/a7metros/presentation/MainScreen.kt` and `ui/theme/*` to `…/presentation/theme/`, change only their `package` lines, and move the Compose UI dependencies (material3, material-icons-core, ui, ui-graphics, ui-tooling-preview, debug ui-tooling) to the presentation module; verify `./gradlew :7metros-presentation:assembleDebug` succeeds
- [ ] 4.3 Update the `MainActivity.kt` imports to `com.example.a7metros.presentation.MainScreen` and `…presentation.theme._7metrosTheme`, and drop any app-module dependencies that no longer have a user; verify `./gradlew assembleDebug` succeeds and `7metros/src/main/java` contains only `MainActivity.kt`

## 5. Verify unchanged behavior (non-presentation layers: unit tests)

- [ ] 5.1 Run `./gradlew test`; verify it passes, including the existing `ExampleUnitTest` in `7metros` (the new JVM modules have no sources yet and report NO-SOURCE)

## 6. Verify unchanged behavior (presentation layer: instrumented UI tests)

- [ ] 6.1 Run `./gradlew connectedAndroidTest` on an emulator or device; verify the existing `ExampleInstrumentedTest` passes (the package name is still `com.example.a7metros`)
- [ ] 6.2 Install the debug APK over the previous build and check by hand that the splash, top bar, drawer and bottom bar (Resultados / Clasificación) look and behave the same as before

## 7. Documentation

- [ ] 7.1 Update the CLAUDE.md "Architecture" section with the six modules, each one's single responsibility, the allowed dependency directions, the naming convention (`7metros-<layer>`, package `com.example.a7metros.<layer>`) and the JVM-vs-Android plugin rule; verify every module in `settings.gradle.kts` appears in CLAUDE.md and vice versa

## Workflow follow-up

- Open a small PR from `technical/modularize_app` to `main`.
- Archive the change once it's merged (it has no spec deltas because `skip_specs: true`).
