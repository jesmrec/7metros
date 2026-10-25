# Tasks

## 1. Wire the plugin into the build

- [x] 1.1 Add a `detekt` version and `detekt` plugin alias (and a `detekt-formatting` library alias, same version) to `gradle/libs.versions.toml`; declare `alias(libs.plugins.detekt) apply false` in the root `build.gradle.kts`; verify `./gradlew help` succeeds with no plugin-resolution errors
- [x] 1.2 Create `config/detekt/detekt.yml` with `build: maxIssues: 0` and no rule overrides yet; in the root `build.gradle.kts`, apply the plugin to every subproject (`subprojects { apply(plugin = libs.plugins.detekt.get().pluginId) }`), configure `buildUponDefaultConfig = true` and `config.setFrom(files("$rootDir/config/detekt/detekt.yml"))` on the `detekt` extension, and make `check` depend on `detekt`; verify `./gradlew :7metros-domain:detekt` runs (NO-SOURCE is fine for the empty modules) and `./gradlew detekt` runs across all six modules without a config-parsing error (findings in `7metros`/`7metros-presentation` are expected at this point)

## 2. Curate the ruleset

- [ ] 2.1 In `config/detekt/detekt.yml`, exclude `style>MagicNumber` for `7metros-presentation/**` only (per design.md Decision 3); verify `./gradlew :7metros-presentation:detekt` no longer reports MagicNumber findings for the existing Dp/alpha literals in `MainScreen.kt`, while `./gradlew :7metros:detekt` still would if a magic number were introduced there
- [ ] 2.2 Add `detekt-formatting` as a `detektPlugins` dependency for every subproject from the same root `subprojects` block, enabling the `formatting` rule set; verify `./gradlew detekt` newly reports at least one `formatting`-rule-set finding (confirms the rule set is active) before the cleanup in task 3.2 removes it

## 3. Enforce zero tolerance and clean up existing source

- [ ] 3.1 Confirm the `Detekt` task's `ignoreFailures` is left at its Gradle default (`false`, not overridden) and that no `detekt-baseline.xml` is configured anywhere; verify `./gradlew :7metros:detekt` exits non-zero while any finding remains in that module
- [ ] 3.2 Fix every finding detekt reports in `7metros` and `7metros-presentation` (the only modules with source today) — formatting, complexity, naming, or otherwise — without adding new rule exclusions or `@Suppress` annotations beyond the one documented `MagicNumber` exclusion from task 2.1; verify `./gradlew detekt` across all six modules exits `0` with zero findings
- [ ] 3.3 Run `./gradlew check`; verify it passes end-to-end (detekt plus the existing unit and instrumented test tasks all succeed), confirming the gate works through the normal build lifecycle and not only via the standalone `detekt` task

## 4. Documentation

- [ ] 4.1 Update CLAUDE.md to state that detekt is the project's static analyzer, that `./gradlew detekt` / `./gradlew check` must pass with zero findings before code is accepted, and where the shared ruleset lives (`config/detekt/detekt.yml`); verify the new text matches the actual Gradle wiring (plugin applied from root, zero-tolerance, no baseline)

## Workflow follow-up

- Open a small PR with this change.
- CI wiring is explicitly out of scope here (per user decision) — raise it as a separate future change when the project sets up its first CI pipeline.
- Archive this change once merged (it has no spec deltas because `skip_specs: true`).
