# Request

Fetch and display current-round handball scores from Liga Asobal (Spain's top men's league) via web scraping.

## Why

The app's core purpose is showing live handball scores. Before anything else can be built (standings, other competitions, nicer UI), we need a first working path from "scrape a source" to "model flows through the layers to the screen." Asobal.es is the first source: it's the top Spanish league, and it's already confirmed to be static server-rendered HTML (see memory on handball data sources), so no headless browser is needed.

## What Changes

End-to-end vertical slice: fetch Asobal's current-jornada results page, parse it into a domain model, and print the results on screen, with unit tests across every non-presentation layer.

- **`7metros-network`**: add an HTTP client (e.g. OkHttp) exposing a minimal "fetch raw body for this URL" capability. No scraping/parsing logic here — this layer only knows about HTTP, not about Asobal or HTML.
- **`7metros-datasource`**: add a common data-source abstraction (so future sources like isquad.es can plug in the same way) and an `AsobalDataSource` implementation that:
  - Fetches `/liga/resultados/<jornada>/`, parameterized by a jornada identifier (e.g. `fetchResults(jornada: Int? = null)`, where `null`/absent resolves to the current jornada). This change only ever calls it for the current round, but the signature already supports fetching any specific round, so picking next/previous/arbitrary rounds later is purely a presentation-layer concern, not a rework of this layer.
  - Parses the HTML (e.g. via Jsoup) into a datasource-level model: both teams' names, score (nullable/absent for matches not yet played), and match status. Team logos are not scraped in this change (see Out of Scope).
  - Explicitly handles Asobal's duplicate/conflicting charset meta tags (`iso-8859-1` vs `utf-8`) so accented characters don't come out mangled.
  - Resolving "current jornada" when none is specified: confirmed (by fetching the live site) that `/liga/resultados/` with no jornada defaults to Jornada 1, not the current one — but every Asobal page carries a site-wide nav link (e.g. a "Resultados" link pointing at `/liga/resultados/<n>/`) that tracks the current round, and this link is identical on the results page *and* the standings page. So resolving "current jornada" is two requests, not one: fetch any Asobal page, parse that shared nav link's href for `<n>`, then fetch `/liga/resultados/<n>/` for matches. Because that nav link isn't specific to results, the resolver should live as its own reusable piece in `7metros-datasource` (e.g. `AsobalCurrentJornadaResolver`) that `AsobalDataSource` composes — not logic private to the scores path — since the future standings datasource will need the exact same resolution and shouldn't duplicate it.
  - Residual implementation detail (not blocking scope): the nav-link finding above came from a markdown-converted fetch, not raw HTML, so the exact selector/markup still needs verifying against raw HTML during implementation.
  - All fetching/parsing runs off the main thread (suspend functions over a background dispatcher) — this is scraping + HTML parsing, not something to run on the UI thread.
- **`7metros-domain`**: add the `Match`-level model (teams, score, status) and a repository interface + use case that carry the same optional jornada parameter down to the datasource. Keep the model source-agnostic — nothing Asobal-specific leaks above `datasource`.
- **`7metros-data`**: add a repository implementation that calls the datasource(s) and maps datasource models to domain models. Only one source exists today (Asobal), but the repository shape should not need to change when a second source is added later.
- **`7metros-presentation`** / **`7metros`**: wire the use case through a minimal ViewModel and print the fetched matches as plain text on screen (team names, score, status) when the app opens, always requesting the current jornada (no round-picker UI). No layout/styling work, no loading/error states beyond what falls out naturally. Wiring stays manual (plain constructors/factories) — no DI framework is introduced in this change.
- `7metros`: add the `INTERNET` permission to `AndroidManifest.xml` — without it the scrape fails at runtime on-device despite working in unit tests.
- Unit tests across `network`, `datasource`, `data`, and `domain`:
  - `network`: successful fetch, non-2xx HTTP response, and a transport failure (e.g. `IOException`) — covers how a failure is surfaced (exception vs. a `Result`-like return) before layers above decide what to do with it.
  - `datasource`: parsing against saved HTML fixtures covering all three match states (finished with a score, in-progress/live, not-yet-played), the encoding fix, and `AsobalCurrentJornadaResolver` extracting the jornada number from the shared nav-link fixture on its own (separately from the results-page parsing).
  - `data`: datasource→domain mapping (including the null-score case) and that an explicit jornada parameter is passed through unchanged.
  - `domain`: use case forwards the optional jornada parameter correctly and surfaces datasource/repository failures rather than swallowing them.

## Impact

- New dependencies in `gradle/libs.versions.toml` + module `build.gradle.kts` files for the chosen HTTP client and HTML parser (`7metros-network`, `7metros-datasource`).
- New source sets/classes across `7metros-network`, `7metros-datasource`, `7metros-domain`, `7metros-data`, and minimal changes in `7metros-presentation`/`7metros` for wiring and printing.
- Test resources: saved Asobal results-page HTML fixtures covering finished/live/not-yet-played match states, plus a fixture for the shared nav-link page used by `AsobalCurrentJornadaResolver`.
- `CLAUDE.md`: once the HTTP client and HTML parser are chosen, document them as the project's settled network/scraping libraries.

## Out of Scope

- Presentation polish: no layouts/formatting, just printing fetched data to confirm it was scraped correctly. No instrumented/UI tests.
- Standings — scores only, this change.
- Live polling (Asobal's 20s auto-refresh of in-progress matches) — this change does a single fetch; polling is a follow-up change.
- Actually fetching/showing other jornadas and other competitions/sources (e.g. isquad.es) — follow-up changes once this vertical slice works. The lower layers are parameterized to make that easy, but no round-picker UI or multi-source logic is built in this change.
- Team logos — the domain model doesn't carry a logo field yet; scraping and surfacing crests is a follow-up change.


