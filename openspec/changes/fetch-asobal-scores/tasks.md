# Tasks

## 1. Setup

- [x] 1.1 Add the chosen HTTP client (OkHttp) to `gradle/libs.versions.toml` and `7metros-network/build.gradle.kts`; verify `./gradlew :7metros-network:build` succeeds with the dependency resolved.
- [x] 1.2 Add the chosen HTML parser (Jsoup) to `gradle/libs.versions.toml` and `7metros-datasource/build.gradle.kts`; verify `./gradlew :7metros-datasource:build` succeeds with the dependency resolved.
- [x] 1.3 Add the `INTERNET` permission to `7metros/src/main/AndroidManifest.xml`; verify by inspecting the merged manifest (`./gradlew :7metros:processDebugManifest` then check `build/intermediates/merged_manifest/debug/AndroidManifest.xml` contains the permission).

## 2. Network layer

- [x] 2.1 Implement a minimal HTTP client abstraction in `7metros-network` exposing a suspend "fetch raw body for a URL" function returning `Result<String>`, running off the main thread; verify with a unit test against a local test server (e.g. MockWebServer) for a successful 200 response.
- [x] 2.2 Handle non-2xx responses and transport failures as a `Result` failure rather than throwing uncaught; verify with unit tests covering a non-2xx response and a simulated `IOException`/connection failure.

## 3. Datasource layer: current-jornada resolution

- [x] 3.1 Save a raw-HTML fixture of an Asobal page containing the site-wide "current jornada" nav link; verify the exact markup against the live site's raw HTML (not a markdown conversion) before using it, per design.md's Open Questions.
- [x] 3.2 Implement `AsobalCurrentJornadaResolver` (or equivalent) that parses that nav link's href and extracts the jornada number; verify with a unit test parsing the fixture from 3.1 and asserting the correct jornada number.
- [x] 3.3 Add a sanity check to the resolver so a resolved jornada whose matches fall far from the current date is treated as a resolution failure rather than trusted blindly (per design.md's Risks); verify with a unit test using a fixture where the resolved jornada's dates are stale.
- [x] 3.4 Extend `AsobalCurrentJornadaResolver` so that when the resolved jornada's results page has zero match entries (round not yet populated, per design.md's "Current-jornada resolution during gap periods" decision), it resolves to whichever of the previous (finished) jornada or the resolved (upcoming) jornada is closer by day count — using the previous jornada's last match date and the upcoming jornada's first scheduled match date from `/liga/calendario/<jornada>/`; a tie or the resolved jornada being `1` favors the upcoming jornada; an undeterminable upcoming date falls back to the previous finished jornada. Verify with unit tests covering: closer-to-finished, closer-to-upcoming, a tie, jornada `1`, and the undeterminable-next-date fallback.

## 4. Datasource layer: results scraping

- [x] 4.1 Define the common `DataSource` abstraction in `7metros-datasource` (so a second implementation can later conform to it) and an `AsobalDataSource` that fetches `/liga/resultados/<jornada>/`, where `jornada: Int? = null` resolves via the Task 3 resolver when absent; verify with a unit test asserting the resolver is invoked only when no jornada is passed.
- [ ] 4.2 Save raw-HTML fixtures of an Asobal results page covering all three match states (finished with a score, in-progress/live, not-yet-played); verify each fixture's content matches the live site's raw HTML for a page in that state. If a given state can't be found already-populated on `resultados` against the live site (see design.md's Open Questions), source it from the Task 3.4/4.6 `calendario` fallback path instead and note which path it came from.
  - Done so far: `resultados-finished.html` (finished, verified against live `resultados/8/`) and `calendario-not-started.html` (not-yet-played, verified against live `calendario/9/`, since `resultados/9/` is still empty).
  - Still missing: in-progress. Jornadas usually play over a weekend (occasionally a weekday fixture happens too, e.g. rescheduled matches), and no jornada is playing right now, so no live match exists to capture (confirmed live, and via the Wayback Machine, which has no archived snapshots of this path at all). Deferred to Workflow follow-up below - capture it during jornada 9's playing window (13-15 Nov 2026).
- [ ] 4.3 Implement HTML parsing of the results page into a datasource-level model (both team names, nullable score, match status); verify with unit tests against the Task 4.2 fixtures, one assertion per match state.
  - Done so far: `AsobalMatch`/`AsobalScore`/`AsobalMatchStatus` model and `parseAsobalMatches` parser added, with passing assertions for finished and not-yet-played (both fixtures from 4.2). `AsobalMatchStatus.IN_PROGRESS` exists in the model but has no fixture-backed assertion yet.
  - Still missing: the in-progress assertion, blocked on the same fixture as 4.2 - tracked in the same Workflow follow-up entry.
- [x] 4.4 Force correct character decoding during parsing despite Asobal's conflicting charset declarations; verify with a unit test asserting an accented team name (e.g. containing "á" or "ñ") decodes correctly from the fixture.
- [ ] 4.5 Surface fetch/parse failures (unreachable source, unexpected HTML structure) as a `Result` failure from `AsobalDataSource` rather than a successful empty/partial result; verify with unit tests simulating a network failure and a malformed-HTML fixture.
- [ ] 4.6 Add the `calendario` fallback to `AsobalDataSource` (per design.md's "`AsobalDataSource` falls back to `/liga/calendario/<jornada>/`" decision): when `resultados`'s parsed match list for a jornada is empty, fetch and parse `/liga/calendario/<jornada>/` for the same jornada number instead, mapping its scheduled matches to the same datasource-level model as not-yet-played; verify with unit tests covering the fallback triggering on an empty `resultados` fixture and not triggering when `resultados` already has entries.

## 5. Domain layer

- [ ] 5.1 Add the `Match` model (`homeTeam`, `awayTeam`, `score: Score?`, `status: MatchStatus`) and `MatchStatus` enum (not-yet-played / in-progress / finished) to `7metros-domain`, with no Asobal-specific fields; verify by compiling `7metros-domain` standalone with no dependency on `7metros-datasource`.
- [ ] 5.2 Add a repository interface (e.g. `ScoresRepository.getCurrentJornadaMatches(jornada: Int? = null): Result<List<Match>>`) to `7metros-domain`; verify it compiles with no implementation yet.
- [ ] 5.3 Add a use case that calls the repository and forwards the optional jornada parameter and any failure unchanged; verify with a unit test using a fake repository asserting both the parameter pass-through and failure propagation.

## 6. Data layer

- [ ] 6.1 Implement the repository, calling `AsobalDataSource` and mapping its datasource-level model to the domain `Match` model (including the null-score case for not-yet-played matches); verify with a unit test asserting the mapping for all three match states.
- [ ] 6.2 Verify an explicit jornada parameter passed into the repository reaches the datasource unchanged, and a datasource failure reaches the caller as a `Result` failure; cover both with unit tests using a fake/mock datasource.

## 7. Presentation and app wiring

- [ ] 7.1 Add a minimal ViewModel in `7metros-presentation` that calls the use case for the current jornada on initialization via `viewModelScope`, exposing the result (matches, empty, or failure) as UI state; verify by compiling `7metros-presentation` with only a dependency on `7metros-domain` (no datasource/network/data leakage).
- [ ] 7.2 Update `MainScreen`/`MainActivity` to print each match's team names, score, and status as plain text for the data state, and a distinct plain-text indicator for the empty and failure states (per `specs/scores/spec.md`'s "Current scores display when the app opens" scenarios); verify by running the app against the real network and confirming the current jornada's matches appear on screen.
- [ ] 7.3 Add the manual composition root in `7metros` that constructs the network client, `AsobalDataSource` (with its resolver), repository, use case, and ViewModel factory in one place, with no DI framework; verify the app builds and runs end-to-end (Task 7.2's manual check covers this).

## 8. Documentation

- [ ] 8.1 Update `CLAUDE.md` to document OkHttp and Jsoup as the project's settled network/HTML-parsing libraries, per proposal.md's Impact section; verify the relevant section reflects the libraries actually added in Tasks 1.1-1.2.

## Workflow follow-up

- Capture a live-verified "in-progress" `resultados` fixture during jornada 9's playing window (13-15 Nov 2026) and complete Task 4.2's third state; extend Task 4.3's parsing and tests for that state once it's in hand. Until then, 4.3-4.5 proceed with the finished and not-yet-played states only.
- Confirm CI (unit tests; instrumented tests are not applicable since this change has none) passes before archiving, per the project's `operations.archive` rule.
- Archive the change once implementation and CI are verified.
