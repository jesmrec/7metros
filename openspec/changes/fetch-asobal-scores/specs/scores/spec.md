# Spec Delta

## Purpose

The scores capability fetches current handball match information (teams, score, match status) from web-scraped sources and exposes it for display, starting with Liga Asobal's current jornada.

## ADDED Requirements

### Requirement: Current-jornada Liga Asobal match data is available
The system SHALL provide match data for the current jornada of Liga Asobal (asobal.es), covering finished, in-progress, and not-yet-played matches. Every match SHALL include both team names and a status; a score SHALL be present for finished and in-progress matches and absent for not-yet-played matches.

#### Scenario: Finished match has a final score
- **WHEN** the current jornada includes a match that has already been played
- **THEN** the returned match data includes both team names, a final score, and a finished status

#### Scenario: In-progress match has a current score
- **WHEN** the current jornada includes a match that is currently being played
- **THEN** the returned match data includes both team names, the current (partial) score, and an in-progress status

#### Scenario: Not-yet-played match has no score
- **WHEN** the current jornada includes a match that has not started yet
- **THEN** the returned match data includes both team names, no score, and a not-yet-played status

### Requirement: Scraped team names decode correctly
Liga Asobal's pages declare conflicting character encodings. The system SHALL decode scraped text so that accented and special characters in team names match the source text exactly, regardless of that inconsistency.

#### Scenario: Team name with an accented character
- **WHEN** a match involves a team whose name contains an accented character (e.g. "á", "ñ")
- **THEN** the returned team name renders that character correctly, with no mojibake

### Requirement: Liga Asobal scraping failures are surfaced, not swallowed
If asobal.es cannot be reached, or its results page no longer matches the structure the scraper expects, the system SHALL report a failure for that request rather than returning an empty, partial, or incorrect result as if it had succeeded.

#### Scenario: Source unreachable
- **WHEN** asobal.es cannot be reached while fetching the current jornada
- **THEN** the request for current scores results in a failure outcome, not a successful empty result

#### Scenario: Source page structure changes unexpectedly
- **WHEN** asobal.es's results page HTML no longer matches the structure the scraper expects
- **THEN** the request for current scores results in a failure outcome rather than silently incorrect data

### Requirement: Current scores display when the app opens
The system SHALL show the current jornada's matches on screen when the app is opened, without requiring any user action to trigger the fetch.

#### Scenario: Matches are available
- **WHEN** the app opens and current-jornada matches were fetched successfully
- **THEN** each match's team names, score (if any), and status are shown on screen

#### Scenario: No matches are available
- **WHEN** the app opens and the current jornada has no matches to show
- **THEN** the screen reflects that no matches are available, rather than showing stale or placeholder data

#### Scenario: Fetching fails
- **WHEN** the app opens and fetching current-jornada matches fails
- **THEN** the screen does not display match data as if the fetch had succeeded
