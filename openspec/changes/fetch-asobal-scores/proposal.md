# Proposal

## Why

7metros's core purpose is showing live handball scores, but today there is no scraping, no model, and no data flowing through the layers at all — only empty module skeletons and a default `MainActivity`. Before standings, other competitions, or any UI polish can be built, we need a first working path from "scrape a source" to "model flows through the layers to the screen." Liga Asobal (asobal.es) is the first source: it's Spain's top men's league, and it's already confirmed (see the handball-data-sources research) to be static, server-rendered HTML, so no headless browser is needed.

## What Changes

End-to-end vertical slice: fetch Asobal's current-jornada results page, parse it into a domain model, and print the results on screen, with unit tests across every non-presentation layer.

- **`7metros-network`**: add an HTTP client (e.g. OkHttp) exposing a minimal "fetch raw body for this URL" capability. No scraping/parsing logic here — this layer only knows about HTTP, not about Asobal or HTML.
- **`7metros-datasource`**: add a common data-source abstraction (so a second source can later implement it too) and an `AsobalDataSource` that:
  - Fetches `/liga/resultados/<jornada>/`, parameterized by an optional jornada number (`null`/absent resolves to the current jornada). This change only ever calls it for the current round, but the signature already supports fetching any specific round.
  - Parses the HTML (e.g. via Jsoup) into a datasource-level model: both teams' names, score (nullable/absent for matches not yet played), and match status. Team logos are not scraped in this change.
  - Explicitly handles Asobal's duplicate/conflicting charset meta tags (`iso-8859-1` vs `utf-8`) so accented characters aren't mangled.
  - Resolves "current jornada" via a dedicated, reusable `AsobalCurrentJornadaResolver`: the default results page (no jornada in the URL) renders Jornada 1, not the current round, but every Asobal page carries a site-wide nav link that tracks the current round. Resolving "current" is therefore two requests (fetch any Asobal page, parse that shared nav link for the jornada number, then fetch that jornada's results), and the resolver is its own composable piece rather than logic private to the scores path, since a future standings datasource will need the same resolution.
  - Runs entirely off the main thread (suspend functions over a background dispatcher).
- **`7metros-domain`**: add the `Match` model (teams, score, status), a repository interface, and a use case, all carrying the same optional jornada parameter down to the datasource. The model stays source-agnostic — nothing Asobal-specific leaks above `datasource`.
- **`7metros-data`**: add a repository implementation that calls the datasource(s) and maps datasource models to domain models. Only one source exists today, but the repository shape is not expected to change when a second source is added later.
- **`7metros-presentation`** / **`7metros`**: wire the use case through a minimal ViewModel and print the fetched matches as plain text on screen (team names, score, status) when the app opens, always requesting the current jornada. No layout/styling work, no loading/error states beyond what falls out naturally. Wiring stays manual (plain constructors/factories) — no DI framework is introduced in this change.
- `7metros`: add the `INTERNET` permission to `AndroidManifest.xml`.
- Unit tests across `network`, `datasource`, `data`, and `domain` layers, including the HTTP failure paths, all three match states, the encoding fix, the jornada resolver, and parameter/error propagation through the repository and use case.

## Capabilities

### New Capabilities
- `scores`: the app's ability to fetch and expose current handball match scores (teams, score, status) sourced from web scraping, independent of which provider backs it. This change implements its first and only requirement set: current-jornada Liga Asobal results.

### Modified Capabilities
(none — `scores` does not exist yet)

## Impact

- New dependencies in `gradle/libs.versions.toml` + `build.gradle.kts` for `7metros-network` (HTTP client) and `7metros-datasource` (HTML parser).
- New source sets/classes across `7metros-network`, `7metros-datasource`, `7metros-domain`, `7metros-data`, and minimal changes in `7metros-presentation`/`7metros` for wiring and printing.
- `7metros/src/main/AndroidManifest.xml`: add `INTERNET` permission.
- Test resources: saved Asobal results-page HTML fixtures (finished/live/not-yet-played match states) and a fixture for the shared nav-link page used by `AsobalCurrentJornadaResolver`.
- `CLAUDE.md`: once the HTTP client and HTML parser are chosen, document them as the project's settled network/scraping libraries.
