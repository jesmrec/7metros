# Design

## Context

See `proposal.md` - Why. Today `7metros-network`, `7metros-datasource`, `7metros-domain`, and `7metros-data` are empty module skeletons; `7metros-presentation`/`7metros` only have the default scaffolded screen. This is the first code written in any of the data-flow layers, so this design also sets a few conventions (error modeling, threading, wiring) that later sources/features will follow unless a future change deliberately revisits them.

Research already done (see the `handball data sources` memory) confirms asobal.es is static, server-rendered HTML, and that `/liga/resultados/` with no jornada number renders Jornada 1, not the current round — the current round is only discoverable via a site-wide nav link present on every Asobal page.

## Goals / Non-Goals

**Goals:**
- Establish the layer-to-layer data flow (network → datasource → domain/data → presentation) once, correctly, so later sources/features extend it rather than re-deriving it.
- Keep the `scores` capability's domain model and repository shape source-agnostic, even though only Asobal is implemented.

**Non-Goals:**
- Generalizing the `DataSource` abstraction to fit isquad.es's shape (competition-ID-keyed, not jornada-keyed). It is intentionally Asobal-shaped for now; see Risks.
- Any DI framework. Wiring is manual and deliberately not addressed by this design beyond keeping it in one place.

## Decisions

**HTTP client: OkHttp.**
Mature, minimal, coroutine-friendly (`Call.await()` style extension or a thin suspend wrapper), and testable against a local server (MockWebServer) without hitting the real site in unit tests. Alternative considered: Ktor client — rejected for now since nothing in this project needs Ktor's multiplatform reach; OkHttp is the simpler dependency for a JVM/Android-only app.

**HTML parsing: Jsoup.**
Standard JVM HTML parser, tolerant of malformed markup (expected from a scraped site, not an API), and lets us force a specific charset when parsing instead of trusting the page's own (conflicting) declared encoding. Alternative considered: regex-based extraction — rejected as too brittle against incidental markup changes.

**Error modeling: `Result<T>` from `network` upward.**
`network`'s fetch function returns `Result<String>` (body or failure) rather than throwing; `datasource`, `data`, and `domain` propagate failure the same way rather than introducing a custom sealed error hierarchy. Simpler than modeling specific error types before we know what granularity the UI will ever need, and composes directly with suspend functions. Revisit with a richer error type only when a concrete need appears (e.g. distinguishing "unreachable" from "unexpected format" for the UI).

**Threading: suspend functions over `Dispatchers.IO`.**
Network I/O and HTML parsing both happen off the main thread. The ViewModel launches the fetch in `viewModelScope`; nothing below `presentation` assumes a particular dispatcher is already set, it switches internally.

**Current-jornada resolution: a dedicated `AsobalCurrentJornadaResolver`, composed by `AsobalDataSource`, not inlined into it.**
Resolving "current" takes two sequential requests (fetch any Asobal page → parse the shared nav link → fetch that jornada's results page). Making this its own unit means the future standings datasource can reuse it instead of re-solving the same problem, and it can be unit-tested against the nav-link fixture independently of results-page parsing.

**Current-jornada resolution during gap periods: fall back to whichever neighboring jornada is closer, by day count.**
Confirmed against the live site (during the Nov 2026 international break between jornada 8 and 9): when the nav-link-resolved jornada's results page has **zero match entries** — the round hasn't been populated/opened yet — the original sanity check can't even run (there are no match dates to check), and resolving to an unopened round would just produce "no matches." Instead, `AsobalCurrentJornadaResolver` compares how many days ago the previous jornada's last match was played to how many days until the resolved jornada's first scheduled match (read from `/liga/calendario/<jornada>/`, since `resultados` has no dates to parse in this state) and resolves to whichever is closer. A tie favors the upcoming jornada; so does the resolved jornada being `1` (season start — nothing earlier exists to fall back to). If the upcoming jornada's date can't be determined at all (e.g. the season has ended and no next round is published yet), it resolves to the previous finished jornada instead of failing. Dates are compared as whole calendar days (the existing `LocalDate`-based precision already used by the freshness check), not exact kickoff times. This is a distinct case from the existing staleness check: a results page that *has* entries but all far from today still fails loudly via `StaleJornadaException`, since that shape signals a broken/wrong resolution rather than a scheduled gap.

**`AsobalDataSource` falls back to `/liga/calendario/<jornada>/` when `/liga/resultados/<jornada>/` has no match entries.**
A jornada resolved to be "upcoming" per the rule above still needs real match data to display (team names, scheduled time) rather than an empty result, and `resultados` has nothing for it yet. `calendario` carries the full-season schedule well in advance (jornada 9's not-yet-played fixtures, with placeholder `0-0` scores and "No ha comenzado" status, were already listed there more than a week before jornada 8 even finished), so `AsobalDataSource` tries `resultados` first and falls back to parsing `calendario` for the same jornada number when `resultados` comes back with zero entries, mapping the result to the same not-yet-played datasource model used elsewhere.

**`DataSource` abstraction is Asobal-shaped, not source-agnostic yet.**
`fetchResults(jornada: Int? = null)` matches Asobal's own URL scheme. isquad.es (the next planned source, per the data-sources memory) is keyed by `id_competicion` with a different live-match mechanism, and will not fit this signature unchanged. Generalizing the interface now, before a second implementation exists to validate the generalization against, risks guessing wrong. This is accepted as a known limitation (see Risks), not solved here.

**Wiring: manual, in one composition point.**
No DI framework. `7metros` constructs the network client, datasource, repository, use case, and ViewModel factory in one place (e.g. an `Application`/composition-root class), so introducing a DI framework later is a localized change rather than a hunt through the codebase.

**Domain model shape:** `Match(homeTeam: String, awayTeam: String, score: Score?, status: MatchStatus)`, with `MatchStatus` distinguishing not-yet-played / in-progress / finished and `score` present only for the latter two. Kept intentionally small - no competition/league field yet, since only one competition exists in this change (see proposal's Out of Scope equivalent for logos/other rounds).

## Risks / Trade-offs

- **The nav-link "current jornada" signal is inferred, not documented behavior** → Mitigation: confirmed against raw HTML before implementing the resolver. Confirming it against the live site also surfaced that the resolved jornada's results page can be legitimately **empty** (round not yet opened), not just stale — the sanity check had to be extended to distinguish that from a genuinely wrong/stale link (see Decisions).
- **asobal.es's markup can change at any time without notice** (it's a scraped page, not a versioned API) → Mitigation: the "scraping failures are surfaced, not swallowed" requirement in the spec; parsing failures stay isolated to `datasource` so they can't masquerade as valid data in the layers above.
- **Two sequential network round-trips to resolve "current," on top of the fetch itself, adds latency** against the app's "show scores straight away" goal → Mitigation: accepted for this MVP (single fetch, no caching yet); revisit with caching/parallelization once this slice is proven.
- **The gap-period fallback adds up to two more round-trips** (previous jornada's results, upcoming jornada's calendario) on top of the existing two-request resolution, but only when the resolved jornada's results page is empty → Mitigation: accepted for this MVP, same trade-off as above; it's the uncommon case — most of the time the resolved jornada already has data and no fallback runs.
- **Back-to-back empty jornadas and end-of-season nav-link behavior are unverified against the live site** (can't be observed without waiting for the real calendar to reach those states) → Mitigation: accepted as a known limitation; the fallback assumes at most one empty jornada ahead and degrades to showing the last finished jornada when no next date is discoverable at all.
- **The `DataSource` interface is Asobal-shaped and will likely need to change shape when isquad.es is added** → Mitigation: explicitly accepted now (see Decisions); isolated to `7metros-datasource` and `7metros-data`'s mapping code, so the change is contained rather than reaching into `domain`/`presentation`.
- **Manual wiring will get more tedious as more sources/features are added** → Mitigation: kept in a single composition point now specifically so a future DI framework adoption has one place to change, not many.

## Migration Plan

None required — this is net-new code in otherwise-empty modules; nothing existing is being replaced or migrated.

## Open Questions

- Exact raw-HTML markup/selector for the "current jornada" nav link: needs confirming against the live site's raw HTML (not the markdown-converted fetch used during proposal research) before writing `AsobalCurrentJornadaResolver`. Doesn't change the approach (parse a link's href for a number) or the task breakdown either way.
- Exact technique for forcing correct character decoding despite Asobal's conflicting charset declarations (e.g. which charset to force, and at what layer - raw bytes vs. Jsoup's parse options): a normal implementation detail to settle in code, not a decision that changes the requirement ("team names decode correctly") or the task breakdown.
- Whether `/liga/resultados/<jornada>/` ever natively lists not-yet-played or still-in-progress matches once a jornada has at least one finished match (as opposed to being entirely empty until the round is populated) is unverified against the live site: jornada 8 was observed fully finished and jornada 9 fully empty, with no partially-open example seen. This affects how Task 4.3's not-yet-played/in-progress parsing is implemented, and whether the `calendario` fallback is ever needed for a genuinely in-progress jornada or only for a fully-upcoming one.
