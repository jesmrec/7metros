# 7metros

Android app to show live handball scores, focused on Spanish competitions.
The goal is to show the current scores straight away, right after opening the app.

## Project status

Greenfield project. Functionality is minimal for now and will grow incrementally.
Current priorities below reflect the MVP scope, not the final feature set.

## Core idea

Scores are obtained via web scraping, pulling from the best free and open
sources available. The app is designed to support multiple data sources per
type of information (scores, standings, etc.), not tied to a single provider.

## Features

- **Scores** (main feature, MVP)
- **Standings** (main feature, MVP)
- More features may be added later (schedules, team/player info, stats...),
  but these two are the current focus.

## Architecture

- Multi-module project, following a layered architecture. Each module has a
  single responsibility (e.g. presentation + ViewModels, use cases, data
  sources, repositories, network...).
- The agent decides the exact module breakdown and layer structure as the
  project evolves — this is not fixed up front.
- Designed to be extensible: adding a new data source or feature should not
  require touching unrelated modules.

### Modules

| Module | Plugin | Depends on | Responsibility |
|---|---|---|---|
| `7metros` | android application | all modules | Entry point (`MainActivity`), manifest, app resources, wiring everything together |
| `7metros-presentation` | android library + compose | `domain` | Compose screens, theme, ViewModels |
| `7metros-domain` | kotlin jvm | — | Models, use cases, repository interfaces |
| `7metros-data` | kotlin jvm | `domain`, `datasource` | Repository implementations; picks or merges sources |
| `7metros-datasource` | kotlin jvm | `network` | One scraper per provider behind a common data-source abstraction |
| `7metros-network` | kotlin jvm | — | HTTP client and raw fetching |

- Dependencies only point inward, with `domain` at the center and no
  dependencies of its own:
  `7metros → presentation, domain, data, datasource, network`;
  `presentation → domain`; `data → domain, datasource`; `datasource → network`.
- Naming convention: Gradle path and folder `7metros-<layer>`, flat at the
  repo root; Android namespace / Kotlin package root `com.example.a7metros.<layer>`.
  The top module keeps the bare `7metros` name and `com.example.a7metros` package.
- Plugin rule: `7metros` and `7metros-presentation` use the Android plugins
  (`com.android.application` / `com.android.library`) because they need the
  Android framework or Compose UI. `7metros-domain`, `7metros-data`,
  `7metros-datasource` and `7metros-network` use the plain
  `org.jetbrains.kotlin.jvm` plugin instead — they don't need Android APIs,
  build faster, and run their unit tests directly on the JVM. Switch a module
  to the Android library plugin only if it genuinely needs Android (e.g.
  on-device caching).

## Testing strategy

- **Presentation layer**: instrumented UI tests, verifying the UI reacts
  correctly and renders the right information for different data sets coming
  from the layers below.
- **Rest of the layers** (use cases, repositories, data sources, network...):
  unit tests.
- All tests run in CI on every change.

## CI

- Tests (unit + instrumented) run automatically in CI.
- CI should also cover build correctness and, ideally, lint/static analysis
  as the project matures.

## Conventions

- Keep each module focused on its single responsibility — don't leak logic
  across layers.
- Prefer small, incremental PRs given the greenfield/evolving nature of the
  project.
- Document new modules and their responsibility briefly as they're created,
  so the structure stays legible as it grows.
- Whenever a module boundary, naming convention, or architectural decision
  is settled (even informally), update this file in the same PR — CLAUDE.md
  must always reflect the actual current structure, not the initial plan.
