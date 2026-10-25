# Proposal

## Why

All of 7metros currently lives in one Gradle module, `app`. CLAUDE.md calls for
a multi-module, layered architecture where each module has one responsibility,
and where a new data source or feature doesn't touch unrelated modules. Setting
up that structure now, before scores and standings are implemented, saves a
later refactor and lets the first features go straight into the right layer.

## What Changes

- **BREAKING (build only)**: rename the `app` module to `7metros` (folder and
  Gradle path `:7metros`). It stays the top module: it builds the APK and
  depends on every other module. The `applicationId` and `namespace` stay
  `com.example.a7metros`, so the installed app is unchanged.
- Add five empty library modules, each named `7metros-<responsibility>`:
  - `7metros-presentation`: Compose UI, screens, theme and (later) ViewModels.
  - `7metros-domain`: domain models, use cases and repository contracts.
    Has no dependencies on other project modules.
  - `7metros-data`: repository implementations that combine data sources.
  - `7metros-datasource`: scraping data sources, one per provider, behind a
    common abstraction.
  - `7metros-network`: HTTP client and low-level fetching shared by the data
    sources.
- Set up dependencies between modules so they only point inward:
  `presentation → domain`, `data → domain, datasource`, `datasource → network`,
  and `7metros → all` (the app module wires everything together).
- Move the existing UI code (`MainScreen.kt` and `ui/theme/*`) from the app
  module to `7metros-presentation`. `MainActivity`, the manifest, the launcher
  and splash resources, and the existing example tests stay in `7metros`.
- Add the Gradle plugin aliases the new modules need to the version catalog.
- Update CLAUDE.md with the module list, each module's responsibility and the
  dependency rules.

Out of scope (from the request): new code, features or tests inside the
modules, and any CI changes.

## Capabilities

### New Capabilities

None. This is a structural refactor: the app's behavior doesn't change.

### Modified Capabilities

None. The change sets `skip_specs: true` in `.openspec.yaml` because no
spec-level behavior changes.

## Impact

- **Modules touched**: `app` → `7metros` (renamed; keeps app entry point and
  resources), plus the new `7metros-presentation`, `7metros-domain`,
  `7metros-data`, `7metros-datasource` and `7metros-network`.
- **Build files**: `settings.gradle.kts`, the root `build.gradle.kts`,
  `gradle/libs.versions.toml`, and a new `build.gradle.kts` for each module.
- **Source**: `MainScreen.kt` and the theme files move to the presentation
  module (package update only); `MainActivity.kt` imports change to match.
- **Docs**: the CLAUDE.md Architecture section.
- **Tooling**: Android Studio run configurations and any local scripts that
  name `:app` (for example `./gradlew :app:assembleDebug`) must use `:7metros`.
- **Runtime/users**: none. Same app id, same screens.
