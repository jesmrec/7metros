# Design

## Context

See proposal.md (Why) for the motivation. Current state:

- `settings.gradle.kts` includes only `:app`; `rootProject.name = "7metros"`.
- `app` uses AGP 9.4.1 with its built-in Kotlin support (no `kotlin-android`
  plugin is applied) plus the `kotlin.compose` plugin, Java 11 source and target,
  `namespace`/`applicationId` = `com.example.a7metros`, minSdk 28, compileSdk 37.
- The version catalog declares only the `android-application` and
  `kotlin-compose` plugins.
- The source code is small: `MainActivity.kt`, `ui/MainScreen.kt` and
  `ui/theme/{Color,Theme,Type}.kt`. `MainScreen` and the theme use hard-coded
  strings and no `R` references, so they can move without carrying resources.
- The manifest, launcher icons, splash drawable, `strings.xml` (`app_name`) and
  `themes.xml` (the `Theme._7metros*` styles used by the manifest) belong to the
  app entry point.

## Goals / Non-Goals

**Goals:**

- Every module compiles, and the app builds and runs exactly as before.
- Module dependencies only point inward, with domain at the center and no
  other project dependencies.
- Adding a module later follows an obvious, documented pattern.

**Non-Goals:**

- Convention plugins or `build-logic` for shared Gradle config. With six
  short build files this isn't worth it yet; revisit as modules grow.
- Dependency injection setup, new interfaces, placeholder classes or tests in
  the new modules.
- Splitting modules by feature (scores, standings).

## Decisions

### 1. Module layout and dependency graph

```
7metros ──► presentation, domain, data, datasource, network

presentation ──► domain
data         ──► domain, datasource
datasource   ──► network
domain       ──► (nothing)
network      ──► (nothing)
```

| Module | Plugin | Depends on | Responsibility |
|---|---|---|---|
| `7metros` | android application | all modules | Entry point (`MainActivity`), manifest, app resources, wiring everything together |
| `7metros-presentation` | android library + compose | `domain` | Compose screens, theme, ViewModels |
| `7metros-domain` | kotlin jvm | — | Models, use cases, repository interfaces |
| `7metros-data` | kotlin jvm | `domain`, `datasource` | Repository implementations; picks or merges sources |
| `7metros-datasource` | kotlin jvm | `network` | One scraper per provider behind a common data-source abstraction |
| `7metros-network` | kotlin jvm | — | HTTP client and raw fetching |

Each project dependency is declared as `implementation(project(":…"))`.
`7metros` depends on every module directly, so later DI wiring can see the
implementations without leaking them to presentation.

- *Why data → domain and not the other way:* domain owns the repository
  interfaces and data implements them (dependency inversion), so use cases
  can be unit-tested without data or network.
- *Alternative considered:* merging datasource and network into data (four
  modules). Rejected in favor of the user's choice of separate modules, which
  keeps "add a new scraping source" confined to `7metros-datasource`.

### 2. Plain Kotlin/JVM for the non-UI layers

`domain`, `data`, `datasource` and `network` use the `org.jetbrains.kotlin.jvm`
plugin instead of the Android library plugin.

- *Why:* these layers don't need the Android framework. Plain JVM modules
  build faster, run unit tests on the JVM directly (as CLAUDE.md's testing
  strategy requires), and can't accidentally use Android APIs.
- *Alternative:* Android library everywhere. More uniform, but slower and it
  makes it easy to leak `Context` into lower layers. A module can switch
  later if it really needs Android (for example, on-device caching).
- JVM modules target Java 11 to match the app (`java { sourceCompatibility /
  targetCompatibility = 11 }` and `kotlin { compilerOptions.jvmTarget = JVM_11 }`).

### 3. Naming

- Gradle paths and folders: `:7metros` and `:7metros-<layer>`, flat at the repo root.
- Android namespace / Kotlin package root: `com.example.a7metros.<layer>`. A
  Kotlin package can't start with a digit, so the existing `a7metros` prefix
  stays. The app module keeps `com.example.a7metros`.

### 4. Moving the existing UI

`ui/MainScreen.kt` and `ui/theme/*` move to `7metros-presentation` under
`com.example.a7metros.presentation` (`…presentation.MainScreen`,
`…presentation.theme.*`). The only edit is the package lines, plus the
imports in `MainActivity.kt`. Compose UI dependencies (material3, icons-core,
ui, ui-graphics, tooling-preview, tooling) move with the code into the
presentation module. `7metros` keeps `activity-compose`, `core-splashscreen`,
`core-ktx`, `lifecycle-runtime-ktx` and the Compose BOM it needs for
`setContent`, along with its test dependencies.

- *Alternative:* keep the old `com.example.a7metros.ui` package to skip editing
  package lines. Rejected because a module's package should match its namespace.

### 5. Version catalog

Add `android-library` (`com.android.library`, `agp` version) and `kotlin-jvm`
(`org.jetbrains.kotlin.jvm`, `kotlin` version) plugin aliases, and declare them
`apply false` in the root `build.gradle.kts`. No new library versions.

## Risks / Trade-offs

- [The subproject `:7metros` has the same name as the root project] → Gradle
  allows this because the default coordinates differ (group `7metros` vs root),
  but some IDE views can be confusing. Mitigation: verify `./gradlew projects`
  and a full build. If it causes resolution problems, fall back to renaming
  only the folder.
- [AGP 9 built-in Kotlin alongside the `kotlin.jvm` plugin] → Both resolve to
  the same Kotlin Gradle Plugin on the build classpath, so version mismatches
  are possible. Mitigation: use the single `kotlin` catalog version for both,
  and check the build output for KGP version warnings.
- [Empty JVM modules] → They produce empty jars, which is harmless. Without a
  `src` folder some tasks report NO-SOURCE, which is expected.
- [The IDE run configuration still points at `app`] → After the rename,
  re-sync Gradle in Android Studio and select the `7metros` configuration.
  `.idea/` is gitignored, so nothing needs committing.

## Migration Plan

This is a single PR on `technical/modularize_app`. Rollback means reverting the
PR. No user data or app id changes, so a device can upgrade over the old
install.
