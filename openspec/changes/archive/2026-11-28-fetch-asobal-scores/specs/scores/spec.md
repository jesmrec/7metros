# Spec Delta

## Purpose

The scores capability fetches current handball match information (teams, score, match status) from web-scraped sources and exposes it for display, starting with Liga Asobal's current jornada.

## ADDED Requirements

### Requirement: Current-jornada Liga Asobal match data is available
The system SHALL provide match data for the current jornada of Liga Asobal (asobal.es), covering finished, not-yet-played, and in-progress matches, with in-progress matches reported at a finer granularity when the source distinguishes it (first half, half-time, second half, extra time, penalty shootout) and a generic in-progress status otherwise. Every match SHALL include both team names and a status; a score SHALL be present for every in-progress or finished match and absent for not-yet-played matches.

#### Scenario: Finished match has a final score
- **WHEN** the current jornada includes a match that has already been played
- **THEN** the returned match data includes both team names, a final score, and a finished status

#### Scenario: In-progress match in a recognized phase has a current score, phase, and minute
- **WHEN** the current jornada includes a match currently being played whose source status matches a recognized phase (first half, second half, or extra time)
- **THEN** the returned match data includes both team names, the current (partial) score, that phase's specific status, and the elapsed match minute

#### Scenario: In-progress match at half-time or in a penalty shootout has a current score and phase, with no minute
- **WHEN** the current jornada includes a match that is at half-time or in a penalty shootout
- **THEN** the returned match data includes both team names, the current score, that phase's specific status, and no match minute

#### Scenario: In-progress match with an unrecognized phase still reports a generic in-progress status
- **WHEN** the current jornada includes a match currently being played whose source status text does not match any recognized phase
- **THEN** the returned match data includes both team names, the current (partial) score, and a generic in-progress status, rather than being dropped or misreported as another state

#### Scenario: Not-yet-played match has no score but has a scheduled kickoff
- **WHEN** the current jornada includes a match that has not started yet
- **THEN** the returned match data includes both team names, no score, a not-yet-played status, and the match's scheduled kickoff date and time

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
- **THEN** the jornada number is shown alongside each match's team names, score (if any), status, scheduled kickoff (for not-yet-played matches), and elapsed minute (for in-progress matches that report one)

#### Scenario: No matches are available
- **WHEN** the app opens and the current jornada has no matches to show
- **THEN** the screen reflects that no matches are available, rather than showing stale or placeholder data

#### Scenario: Fetching fails
- **WHEN** the app opens and fetching current-jornada matches fails
- **THEN** the screen does not display match data as if the fetch had succeeded

### Requirement: Any jornada's matches can be viewed, not only the current one
The system SHALL let the user navigate to the jornada immediately before or after whichever one is currently displayed, re-fetching that specific jornada's matches rather than only ever showing the current one.

#### Scenario: Navigating to the previous jornada
- **WHEN** the user requests the previous jornada while viewing jornada N
- **THEN** the screen shows jornada N-1's matches

#### Scenario: Navigating to the next jornada
- **WHEN** the user requests the next jornada while viewing jornada N
- **THEN** the screen shows jornada N+1's matches

#### Scenario: A match rescheduled outside its jornada's normal window remains reachable
- **WHEN** a jornada contains a match that was postponed to after, or played before, the rest of that jornada, and the app's default view has since moved on to a later current jornada
- **THEN** the user can navigate back to that match's own jornada and see its result once it has been played, rather than it being permanently unreachable

### Requirement: Scores can be refreshed on demand
The system SHALL let the user manually trigger a re-fetch of whichever jornada's matches are currently displayed, after the initial load.

#### Scenario: Manual refresh updates the displayed matches
- **WHEN** the user triggers a manual refresh and the re-fetch succeeds
- **THEN** the screen updates to show the newly fetched jornada number and matches

#### Scenario: Previously displayed matches remain visible while a manual refresh is in flight
- **WHEN** the user triggers a manual refresh
- **THEN** the previously displayed matches remain on screen, with a refreshing indicator, until the re-fetch completes - rather than being replaced by the initial loading state

#### Scenario: A failed manual refresh replaces the displayed matches with a failure indicator
- **WHEN** the user triggers a manual refresh and the re-fetch fails
- **THEN** the screen shows the failure indicator in place of the previously displayed matches, the same as an initial failed fetch
