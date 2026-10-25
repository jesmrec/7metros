# Proposal

## Why

The project has no static code analysis yet. As the module count grows
(six modules already from the recent modularization) and more
contributors/agents touch the codebase, style and correctness issues
(unused code, complexity, naming, formatting drift) will accumulate
silently. CLAUDE.md already names lint/static analysis as a maturing CI
concern; adding detekt now, while the codebase is still small, is cheap
and prevents a larger cleanup later.

## What Changes

- Add the detekt Gradle plugin to the version catalog and apply it to
  every module: `7metros`, `7metros-presentation`, `7metros-domain`,
  `7metros-data`, `7metros-datasource`, `7metros-network`.
- Add a single shared `config/detekt/detekt.yml` ruleset at the repo
  root (not detekt's bare default config) curated for this Kotlin/Android
  project, enabling the rule sets relevant here (complexity, style,
  naming, exceptions, potential-bugs, coroutines) and tuning thresholds
  that don't fit a young, small codebase.
- Configure detekt so that **any** finding — warning or error — fails
  the build: `ignoreFailures = false` and `maxIssues = 0` project-wide.
  There is no "accept with warnings" path.
- Wire detekt into the normal build lifecycle locally (e.g. `check`
  depends on `detekt`), so running `./gradlew build` or `./gradlew check`
  surfaces violations without a separate command, while `./gradlew
  detekt` keeps working standalone.
- Fix whatever the initial detekt run flags in existing source
  (`MainActivity.kt`, `MainScreen.kt`, the theme files) so the repo
  starts clean under the new ruleset.

Out of scope (per user direction): wiring detekt into a CI pipeline.
This repo has no CI pipeline yet (CLAUDE.md's CI section is aspirational,
not implemented) — that setup is a separate, later task. This change
only has to work locally via Gradle.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is pure build tooling: it doesn't change any app-observable
behavior, so `skip_specs: true` is set in `.openspec.yaml`.

## Impact

- **Build files**: `gradle/libs.versions.toml` (new detekt plugin alias
  + version), root `build.gradle.kts` (apply detekt, shared config,
  `check`/`detekt` wiring), and each module's `build.gradle.kts` if any
  per-module detekt configuration is needed (expected to be minimal if
  the plugin is applied centrally from the root).
- **`gradle/gradle-daemon-jvm.properties`**: repinned from
  `toolchainVersion=25` to `17`. detekt 1.23.8 cannot run at all under a
  JDK 25 Gradle daemon (see design.md Decision 1/Risks); this is a
  project-wide, committed change — every contributor's and CI's Gradle
  daemon now provisions/runs on JDK 17 for this repo, not just this
  machine. Module bytecode output is unaffected (still Java 11 source/
  target compatibility).
- **New file**: `config/detekt/detekt.yml` — the curated ruleset.
- **Source**: any existing file that currently violates the new ruleset
  (expected: minor, in the `7metros` and `7metros-presentation` modules).
- **Docs**: CLAUDE.md should note that static analysis (detekt) is in
  place and zero-tolerance, so future contributors/agents know `./gradlew
  check` must be clean.
- **Developer workflow**: `./gradlew detekt` or `./gradlew check` must
  pass with zero issues before a change is considered done; this becomes
  a local gate immediately, independent of any future CI setup.
