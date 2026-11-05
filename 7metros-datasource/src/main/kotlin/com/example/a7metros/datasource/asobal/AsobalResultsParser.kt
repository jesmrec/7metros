package com.example.a7metros.datasource.asobal

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val MATCH_SELECTOR = "div.info-results li"
private const val TEAM_LINK_SELECTOR = "div.goals-result > a"
private const val GOAL_SELECTOR = "div.goals-result span.goals b"
private const val STATUS_SELECTOR = "span.enlaces"
private const val FINISHED_LABEL = "Partido finalizado"
private const val NOT_YET_PLAYED_LABEL = "No ha comenzado"

// Both the resultados and calendario pages share this same "info-results" markup,
// just populated with different match states - one parser covers both.
internal fun parseAsobalMatches(document: Document): List<AsobalMatch> =
    document.select(MATCH_SELECTOR).map { parseMatch(it) }

private fun parseMatch(matchElement: Element): AsobalMatch {
    val teamLinks = matchElement.select(TEAM_LINK_SELECTOR)
    val status = parseStatus(matchElement)
    return AsobalMatch(
        homeTeam = teamLinks[0].text().trim(),
        awayTeam = teamLinks[1].text().trim(),
        score = parseScore(matchElement, status),
        status = status,
    )
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
