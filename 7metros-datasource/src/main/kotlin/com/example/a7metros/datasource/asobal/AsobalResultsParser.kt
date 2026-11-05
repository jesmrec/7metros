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
    val status = parseStatus(matchElement)
    return AsobalMatch(
        homeTeam = teamName(teamLinks[0], clubNames),
        awayTeam = teamName(teamLinks[1], clubNames),
        score = parseScore(matchElement, status),
        status = status,
    )
}

private fun teamName(teamLink: Element, clubNames: Map<String, String>): String {
    val code = clubCode(teamLink) ?: error("Could not extract a team code from href: ${teamLink.attr("href")}")
    return clubNames[code] ?: error("No club name found for team code $code")
}

private fun parseStatus(matchElement: Element): AsobalMatchStatus {
    val statusText = matchElement.selectFirst(STATUS_SELECTOR)?.text().orEmpty()
    return when {
        statusText.contains(FINISHED_LABEL) -> AsobalMatchStatus.FINISHED
        statusText.contains(NOT_YET_PLAYED_LABEL) -> AsobalMatchStatus.NOT_YET_PLAYED
        else -> error("Unrecognized match status text: $statusText")
    }
}

// Not-yet-played matches carry a "0 - 0" placeholder score in the markup, not a real one.
private fun parseScore(matchElement: Element, status: AsobalMatchStatus): AsobalScore? {
    if (status == AsobalMatchStatus.NOT_YET_PLAYED) return null
    val goals = matchElement.select(GOAL_SELECTOR).map { it.text().trim().toInt() }
    return AsobalScore(home = goals[0], away = goals[1])
}
