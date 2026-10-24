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
