package com.example.a7metros.datasource.asobal

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val MATCH_SELECTOR = "div.info-results li"
private const val TEAM_LINK_SELECTOR = "div.goals-result > a"
private const val GOAL_SELECTOR = "div.goals-result span.goals b"
private const val STATUS_SELECTOR = "span.enlaces"
private const val CLUB_LINK_SELECTOR = "div.clubs a"
private const val FINISHED_LABEL = "Partido finalizado"
private const val NOT_YET_PLAYED_LABEL = "No ha comenzado"
private const val FIRST_HALF_LABEL = "Primera parte"
private const val HALF_TIME_LABEL = "Descanso"
private const val SECOND_HALF_LABEL = "Segunda parte"

// Extra time / penalty shootout labels are a best-effort guess (Spanish handball
// terminology); unconfirmed against a live match, unlike the labels above which were all
// observed on the site. If the real text differs, it simply falls through to the
// IN_PROGRESS fallback below rather than mis-mapping to the wrong phase.
private const val EXTRA_TIME_LABEL = "Prórroga"
private const val PENALTIES_LABEL = "Penaltis"
private val MINUTE_REGEX = Regex("""(\d+)'""")
private val CLUB_CODE_REGEX = Regex("""equipos/([A-Za-z0-9]+)/?""")
private val CLUB_TITLE_NUMBER_PREFIX_REGEX = Regex("""^\d+\s*-\s*""")

// Both the resultados and calendario pages share this same "info-results" markup,
// just populated with different match states - one parser covers both.
internal fun parseAsobalMatches(document: Document): List<AsobalMatch> {
    val clubNames = parseClubNames(document)
    return document.select(MATCH_SELECTOR).map { parseMatch(it, clubNames) }
}

// Match entries only show a team's short code (e.g. "GRA"); the page separately
// carries a club menu mapping that same code to the full, accented team name
// (e.g. "Fraikin BM. Granollers") - resolve through it rather than showing codes.
private fun parseClubNames(document: Document): Map<String, String> =
    document.select(CLUB_LINK_SELECTOR).mapNotNull { link ->
        val code = clubCode(link) ?: return@mapNotNull null
        val fullName = link.selectFirst("img")?.attr("title")
            ?.replaceFirst(CLUB_TITLE_NUMBER_PREFIX_REGEX, "")
            ?.trim()
            .orEmpty()
        if (fullName.isEmpty()) null else code to fullName
    }.toMap()

private fun clubCode(link: Element): String? = CLUB_CODE_REGEX.find(link.attr("href"))?.groupValues?.get(1)

private fun parseMatch(matchElement: Element, clubNames: Map<String, String>): AsobalMatch {
    val teamLinks = matchElement.select(TEAM_LINK_SELECTOR)
    val statusText = matchElement.selectFirst(STATUS_SELECTOR)?.text()
        ?: error("Could not find a status element for this match")
    val status = parseStatus(statusText)
    return AsobalMatch(
        homeTeam = teamName(teamLinks[0], clubNames),
        awayTeam = teamName(teamLinks[1], clubNames),
        score = parseScore(matchElement, status),
        status = status,
        minute = parseMinute(statusText),
    )
}

private fun teamName(teamLink: Element, clubNames: Map<String, String>): String {
    val code = clubCode(teamLink) ?: error("Could not extract a team code from href: ${teamLink.attr("href")}")
    return clubNames[code] ?: error("No club name found for team code $code")
}

// Any other, non-empty status text means the match is live but in a phase we don't
// recognize - rather than labelling it "unknown" (worse UX than a plain live indicator),
// it falls back to the generic IN_PROGRESS state.
private fun parseStatus(statusText: String): AsobalMatchStatus = when {
    statusText.contains(FINISHED_LABEL) -> AsobalMatchStatus.FINISHED
    statusText.contains(NOT_YET_PLAYED_LABEL) -> AsobalMatchStatus.NOT_YET_PLAYED
    statusText.contains(FIRST_HALF_LABEL) -> AsobalMatchStatus.FIRST_HALF
    statusText.contains(SECOND_HALF_LABEL) -> AsobalMatchStatus.SECOND_HALF
    statusText.contains(HALF_TIME_LABEL) -> AsobalMatchStatus.HALF_TIME
    statusText.contains(EXTRA_TIME_LABEL) -> AsobalMatchStatus.EXTRA_TIME
    statusText.contains(PENALTIES_LABEL) -> AsobalMatchStatus.PENALTIES
    else -> AsobalMatchStatus.IN_PROGRESS
}

// The minute is embedded in the same status text as the phase label, e.g. "Primera
// parte (29')" - null when absent, as for "Descanso" (half-time) which carries no clock.
private fun parseMinute(statusText: String): Int? = MINUTE_REGEX.find(statusText)?.groupValues?.get(1)?.toIntOrNull()

// Not-yet-played matches carry a "0 - 0" placeholder score in the markup, not a real one.
private fun parseScore(matchElement: Element, status: AsobalMatchStatus): AsobalScore? {
    if (status == AsobalMatchStatus.NOT_YET_PLAYED) return null
    val goals = matchElement.select(GOAL_SELECTOR).map { it.text().trim().toInt() }
    return AsobalScore(home = goals[0], away = goals[1])
}
