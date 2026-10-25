# Design

## Context

See proposal.md (Why) for motivation. Current state:

- Six Gradle modules: `7metros` (android application), `7metros-presentation`
  (android library + compose), `7metros-domain`, `7metros-data`,
  `7metros-datasource`, `7metros-network` (all plain `kotlin.jvm`).
- Kotlin `2.2.10`, AGP `9.4.1`, Gradle `9.6.0` (see `gradle/libs.versions.toml`
  and `gradle/wrapper/gradle-wrapper.properties`).
- No static analysis tool is configured today, and no CI pipeline exists in
  the repo (`.github/workflows` or similar is absent) — CLAUDE.md's CI
  section describes a goal, not a running pipeline.
- Only a handful of source files exist (`MainActivity.kt`, `MainScreen.kt`,
  the three theme files), so a first detekt run is cheap to clean up by hand.

## Goals / Non-Goals

**Goals:**

- A single, centrally-defined ruleset that all six modules use identically.
- Any detekt finding (there is no severity distinction detekt enforces by
  default — every finding is a "code smell" that is either accepted or not)
  fails `./gradlew check` / `./gradlew build` locally. Zero exceptions.
- The existing source passes cleanly after this change lands.
- Low ceremony: adding detekt to a future seventh module should be automatic
  (via the root build script), not a copy-pasted block per module.

**Non-Goals:**

- CI wiring. No CI pipeline exists yet; this change only has to work via
  `./gradlew detekt` / `./gradlew check` locally. (Explicit user decision.)
- A `build-logic`/convention-plugin module for sharing the detekt setup.
  Matches the same non-goal already recorded in the modularize-app design —
  six modules is still cheap to configure from the root `build.gradle.kts`.
- A baseline file (`detekt-baseline.xml`). Zero-tolerance means fixing
  existing issues now, not grandfathering them in.
- Linting of test sources (`src/test`, `src/androidTest`). Detekt's default
  source-set wiring only analyzes `main`; extending it to tests is a
  separate, later decision if test code quality becomes a problem.
- Third-party Compose-specific detekt rules (e.g. the community
  `compose-rules` project). The built-in rule sets are enough for now;
  revisit if Compose-specific smells (e.g. unstable parameters) become a
  recurring problem.
- Migrating to detekt 2.x. See Decision 1.

## Decisions

### 1. Use detekt 1.23.8, not a 2.0.0-alpha

The Gradle Plugin Portal's newest `dev.detekt` build is `2.0.0-alpha.4`
(package/group renamed from `io.gitlab.arturbosch.detekt` to `dev.detekt`).
`1.23.8` is the latest stable 1.x release.

- *Why 1.23.8:* a zero-tolerance gate needs a stable tool. An alpha with an
  in-flight package rename is the wrong foundation — a patch bump could
  change rule IDs or defaults under us. detekt documents its own Kotlin/AGP
  dependencies as `compileOnly` ("you can pick your own versions" per
  detekt.dev's compatibility page), so 1.23.8 (built against Kotlin 2.0.21)
  is expected to run fine against this project's Kotlin 2.2.10 / AGP 9.4.1 /
  Gradle 9.6.0 — detekt's own analysis engine doesn't need to match the
  project's exact compiler version. This must be confirmed empirically by
  running `./gradlew detekt` once the plugin is wired up (see tasks.md); if
  it doesn't resolve cleanly, that's a blocker to raise, not something to
  silently work around.
- *Alternative considered:* adopt `dev.detekt` 2.0.0-alpha.4 now, since it
  targets Gradle 9.x/AGP 9.x/Kotlin 2.4 — closer to this project's toolchain
  on paper. Rejected: it's pre-release, its own docs list Kotlin 2.4 as the
  floor (this project is on 2.2.10), and adopting an alpha for a tool whose
  entire job is to block the build on any issue is the wrong risk trade-off
  for a greenfield project that can revisit this cheaply later.

### 2. Apply the plugin from the root build script to every subproject

`gradle/libs.versions.toml` gets a `detekt` version and a `detekt` plugin
alias, declared `apply false` at the root like the other plugins. The root
`build.gradle.kts` then applies it to every subproject and configures it
identically:

```kotlin
subprojects {
    apply(plugin = libs.plugins.detekt.get().pluginId)

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }

    tasks.named("check") {
        dependsOn(tasks.named("detekt"))
    }
}
```

- *Why:* one place to add/upgrade detekt instead of six. `subprojects` is
  plain Gradle, not a custom convention plugin, consistent with the
  Non-Goals above. Version-catalog accessors (`libs`) are visible inside the
  root script's `subprojects {}` block, so no extra wiring is needed to read
  the plugin ID from the catalog.
- *Alternative considered:* declare `alias(libs.plugins.detekt)` in each of
  the six `build.gradle.kts` files. Rejected as repetitive busywork with no
  benefit over the root-level `subprojects` block, and a new module would
  silently lack detekt unless someone remembered to copy the block.
- `buildUponDefaultConfig = true` means `config/detekt/detekt.yml` only has
  to list the rules we want to change from detekt's shipped defaults, not
  restate the entire rule catalog.

### 3. One shared `config/detekt/detekt.yml`, curated per rule set

Enable (keep at their default `active: true`) the rule sets that matter for
a young Kotlin/Android codebase doing scraping and Compose UI: `complexity`,
`style`, `naming`, `exceptions`, `potential-bugs`, `coroutines`. Tune
specific rules on top of `buildUponDefaultConfig`:

- **`style>MagicNumber`**: excluded (`excludes: ['**/7metros-presentation/**']`)
  for the presentation module only. Compose layout code is full of literal
  spacing/alpha values (`16.dp`, `0.7f`) that aren't bugs; forcing every one
  into a named constant would be busywork, not a quality improvement. The
  rule stays active (default) for `domain`, `data`, `datasource`, `network`
  and the `7metros` app-entry module, where an unexplained literal (a
  timeout, a retry count, a scraped-field index) is exactly the kind of
  thing worth naming.
  - *Alternative considered:* a per-call `ignoreNumbers` allowlist instead
    of excluding the module. Rejected — Compose spacing values are
    open-ended (every new screen introduces new dp/sp literals), so an
    allowlist would need constant upkeep for no real signal.
- **`formatting` rule set**: enabled. It catches indentation, trailing
  whitespace, and import-order drift that's otherwise invisible until a
  diff review. The codebase is tiny today, so bringing it in now costs one
  cleanup pass instead of a larger one later.
  - *Alternative considered:* leave `formatting` off and rely on
    IDE/`ktlint` formatting conventions informally. Rejected — "no
    warnings accepted" should include style drift, not just logic smells,
    and detekt already bundles this rule set.
- No rule set is disabled outright; defaults are kept for naming,
  exceptions, and potential-bugs since nothing about this project
  contradicts them.

### 4. Enforcement mechanism: Gradle's `ignoreFailures`, not just config

The Detekt Gradle task's `ignoreFailures` property already defaults to
`false` — any finding fails that task, independent of count. This change
relies on that default (keeps it explicit in the config for clarity) rather
than `build.maxIssues` in the YAML, which is a threshold-based escape hatch
meant for callers who want to tolerate *some* issues. Combined with no
baseline file (Decision/Non-Goal above) and `check` depending on `detekt`,
there is no path to a passing build with an outstanding finding.

- *Why not `maxIssues: N > 0`:* that would silently permit N issues forever,
  which contradicts the explicit requirement ("no warnings and errors MUST
  be returned ... to accept the code").

## Risks / Trade-offs

- [detekt 1.23.8's `compileOnly` Kotlin/AGP dependencies might not actually
  resolve cleanly against Kotlin 2.2.10 / AGP 9.4.1 at run time, despite
  detekt's own compatibility claims] → Mitigation: the first task in
  tasks.md is to apply the plugin and run `./gradlew detekt` before writing
  any rule customization; a resolution or ABI failure here is a blocker to
  report, not route around.
- [Excluding `MagicNumber` for the whole presentation module could also
  hide a genuine magic number in business-ish UI logic later] → Mitigation:
  every other rule set stays active for that module; revisit the exclusion
  if it's ever used to justify something that isn't a layout literal.
- [Enabling the `formatting` rule set alongside zero-tolerance means any
  contributor's un-formatted draft fails the build immediately, which can
  be surprising] → Mitigation: this is the intended effect (catch drift
  immediately); contributors run `./gradlew detekt` locally before pushing.
- [No CI pipeline enforces this yet, so the gate only works if a
  contributor remembers to run Gradle locally] → Accepted for this change
  per explicit user direction; tracked as future work, not solved here.

## Migration Plan

Single PR on top of the current state. No runtime/user impact — build-time
tooling only. Rollback means reverting the PR (remove the plugin, catalog
entries, and `config/detekt/detekt.yml`).
