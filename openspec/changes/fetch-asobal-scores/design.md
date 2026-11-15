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

**`NetworkClient` returns raw bytes (`Result<ByteArray>`), not a decoded `String`.**
Originally `fetch()` decoded the body to `String` itself (via OkHttp, which picks the charset from the HTTP `Content-Type` header). That's fine as long as every response reliably sends a correct charset header - but if any ever doesn't, OkHttp defaults to UTF-8, and a wrong guess on asobal.es's ISO-8859-1 bytes is **not recoverable** afterward (an extended-ASCII byte like `ñ`'s `0xF1` isn't valid UTF-8, so a wrong decode permanently replaces it with `�`, not something a later layer can undo). Since `7metros-network`'s own stated job is "raw fetching," not interpreting content, charset is a decision pushed to the Asobal-specific datasource layer instead: `AsobalCurrentJornadaResolver` parses the raw bytes with Jsoup, forcing `ISO-8859-1` explicitly (confirmed from the live site, independent of both the HTTP header and the page's own conflicting `<meta charset>` tags) rather than trusting either to guess right.

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

**`AsobalDataSource` always fetches and merges `/liga/calendario/<jornada>/` with `/liga/resultados/<jornada>/`, using `calendario` as the base.**
`calendario` carries the full-season schedule well in advance and reliably lists every match of a jornada (jornada 9's not-yet-played fixtures, with placeholder `0-0` scores and "No ha comenzado" status, were already listed there more than a week before jornada 8 even finished), while `resultados` only ever renders entries for matches it has started tracking (in progress or finished) — matches still to be played are simply absent from it while the round is live, not listed there with a not-yet-played status. Originally `AsobalDataSource` tried `resultados` first and only fell back to `calendario` when `resultados` came back fully empty (the gap-period case described above); that undercounted a jornada where `resultados` had *partial* data (some matches finished or live, others not yet started) by omitting the not-yet-played ones entirely instead of just the empty-round case. `AsobalDataSource` now always fetches both pages and merges them: `calendario`'s full match list is the base, and each entry is replaced by `resultados`'s version of the same fixture (matched by home/away team name pair) when `resultados` has one, since `resultados` reflects live/finished state more promptly. If `calendario` itself comes back empty, `resultados`'s list is used unchanged.

**In-progress matches are split into finer sub-statuses instead of a single generic `IN_PROGRESS` value.**
Asobal's live status label (`Primera parte (29')`, `Descanso`, `Segunda parte (56')`, `Prórroga (65')`, `Penaltis`) already distinguishes first half, half-time, second half, extra time, and penalty shootout, and carries the elapsed match minute for every phase except half-time and the shootout; collapsing all of them into one generic status would throw that information away even though the UI can show it directly. `AsobalResultsParser` maps each recognized label to its own `AsobalMatchStatus` value (plus the minute, when the label carries one) and falls back to the original generic `IN_PROGRESS` status only for an unrecognized live label, so an unexpected site wording degrades gracefully instead of failing or misclassifying the match.

**Pull-to-refresh re-runs the same current-jornada fetch; it is not a cheaper partial update.**
`ScoresViewModel.refresh()` calls the identical `GetCurrentJornadaMatchesUseCase` the initial load uses. A separate `isRefreshing` flag (rather than flipping `uiState` back to `Loading`) keeps the previously loaded matches on screen while the new fetch is in flight; a failed refresh still replaces them with the failure state, the same as an initial failed fetch — there is no "keep stale data visible on error" behavior. No incremental/delta fetching exists; the simplest option is correct for an MVP without caching.

**`DataSource` abstraction is Asobal-shaped, not source-agnostic yet.**
`fetchResults(jornada: Int? = null)` matches Asobal's own URL scheme. isquad.es (the next planned source, per the data-sources memory) is keyed by `id_competicion` with a different live-match mechanism, and will not fit this signature unchanged. Generalizing the interface now, before a second implementation exists to validate the generalization against, risks guessing wrong. This is accepted as a known limitation (see Risks), not solved here.

**Wiring: manual, in one composition point.**
No DI framework. `7metros` constructs the network client, datasource, repository, use case, and ViewModel factory in one place (e.g. an `Application`/composition-root class), so introducing a DI framework later is a localized change rather than a hunt through the codebase.

**Domain model shape:** `Match(homeTeam: String, awayTeam: String, score: Score?, status: MatchStatus, minute: Int? = null, kickoff: LocalDateTime? = null)`, wrapped together with the resolved jornada number as `JornadaMatches(jornada: Int, matches: List<Match>)`. `MatchStatus` distinguishes `NOT_YET_PLAYED`, five in-progress sub-states (`FIRST_HALF`, `HALF_TIME`, `SECOND_HALF`, `EXTRA_TIME`, `PENALTIES`), a generic `IN_PROGRESS` fallback for a live match whose status label isn't recognized, and `FINISHED`. `score` is present for every in-progress sub-state (finer or generic) and for `FINISHED`, absent for `NOT_YET_PLAYED`. `minute` is only ever populated for an in-progress match whose label carries one (not `HALF_TIME`/`PENALTIES`). `kickoff` is only ever populated for `NOT_YET_PLAYED`. Kept intentionally small - no competition/league field yet, since only one competition exists in this change (see proposal's Out of Scope equivalent for logos/other rounds).

**Team names are resolved to the full club name, not the short code shown in the match list.** Each match entry only shows a 3-4 letter code (e.g. "GRA"), plain ASCII, never accented - but every Asobal page separately carries a club menu mapping that same code to the full name (e.g. "Fraikin BM. Granollers", with the real accented characters the spec's decoding requirement is actually about). `AsobalResultsParser` builds that code-to-name lookup from the same page and resolves each match's home/away code through it, rather than exposing the raw code as `AsobalMatch.homeTeam`/`awayTeam`.

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
- ~~Exact technique for forcing correct character decoding despite Asobal's conflicting charset declarations~~ - settled: `NetworkClient` returns raw bytes rather than a decoded `String` (see Decisions), and `AsobalCurrentJornadaResolver` forces `ISO-8859-1` explicitly when parsing them with Jsoup.
- Whether `/liga/resultados/<jornada>/` ever natively lists not-yet-played matches once a jornada has at least one finished or in-progress match (as opposed to omitting them entirely until the round is populated) is unverified against the live site: jornada 8 was observed fully finished and jornada 9 fully empty, with no partially-open example seen. Jornadas usually play over a weekend, though not always (weekday fixtures happen occasionally too), so this can only be observed live while some jornada is actually playing. This no longer blocks anything structurally - `AsobalDataSource` now unconditionally merges `calendario` (full schedule) with `resultados` (live/finished state) rather than fallback-switching between them, so a `resultados` page that's empty, partial, or complete is handled the same way - but a live, fixture-backed verification of the in-progress match states themselves (as opposed to the hand-built HTML snippets currently used) is still outstanding (see tasks.md's Workflow follow-up).
