package com.example.a7metros.datasource.asobal

import com.example.a7metros.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private const val ASOBAL_RESULTS_LIST_URL = "https://www.asobal.es/liga/resultados/"
private const val ASOBAL_RESULTS_BASE_URL = "https://www.asobal.es/liga/resultados"
private const val ASOBAL_CALENDARIO_BASE_URL = "https://www.asobal.es/liga/calendario"
private const val MAX_STALE_DAYS = 30L

// Every Asobal page's footer links to the current jornada's results, e.g. /liga/resultados/8/,
// unlike the default (no-number) results page, which always renders Jornada 1.
private const val CURRENT_JORNADA_LINK_SELECTOR = """a[href~=(?i)liga/resultados/\d+/?$]"""
private val JORNADA_NUMBER_REGEX = Regex("""(\d+)/?$""")
private val MATCH_DATE_REGEX = Regex("""\d{2}/\d{2}/\d{4}""")
private val MATCH_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

class StaleJornadaException(jornada: Int) :
    IllegalStateException("Resolved jornada $jornada's match dates are too far from today to trust")

class AsobalCurrentJornadaResolver(
    private val networkClient: NetworkClient,
    private val today: () -> LocalDate = LocalDate::now,
) {

    suspend fun resolve(): Result<Int> = runCatching {
        val jornada = resolveJornadaNumber(parseDocument(fetchOrThrow(ASOBAL_RESULTS_LIST_URL)))
        resolveJornada(jornada)
    }

    // The nav link can point at a jornada whose results page hasn't been populated yet
    // (e.g. between rounds, or during a scheduled break) - that page has zero match
    // entries, as opposed to one with real-but-too-old entries (handled by
    // requireFreshJornada instead, since that shape signals a broken resolution).
    private suspend fun resolveJornada(jornada: Int): Int {
        val matchDates = extractMatchDates(parseDocument(fetchOrThrow("$ASOBAL_RESULTS_BASE_URL/$jornada/")))
        return if (matchDates.isNotEmpty()) {
            requireFreshJornada(matchDates, jornada)
            jornada
        } else {
            resolveGapJornada(jornada)
        }
    }

    private suspend fun resolveGapJornada(upcomingJornada: Int): Int {
        if (upcomingJornada <= 1) return upcomingJornada
        val previousJornada = upcomingJornada - 1
        val previousLastMatchDate = extractMatchDates(
            parseDocument(fetchOrThrow("$ASOBAL_RESULTS_BASE_URL/$previousJornada/")),
        ).maxOrNull() ?: error("No match dates found for the previous jornada $previousJornada either")
        val upcomingFirstMatchDate = fetchFirstUpcomingMatchDate(upcomingJornada)
        return decideGapJornada(
            previousJornada,
            previousLastMatchDate,
            upcomingJornada,
            upcomingFirstMatchDate,
            today(),
        )
    }

    private suspend fun fetchFirstUpcomingMatchDate(jornada: Int): LocalDate? =
        networkClient.fetch("$ASOBAL_CALENDARIO_BASE_URL/$jornada/").getOrNull()
            ?.let { extractMatchDates(parseDocument(it)).minOrNull() }

    private suspend fun fetchOrThrow(url: String): String = networkClient.fetch(url).getOrThrow()

    private suspend fun parseDocument(html: String): Document =
        withContext(Dispatchers.Default) { Jsoup.parse(html) }

    internal fun resolveJornadaNumber(document: Document): Int {
        val link = document.selectFirst(CURRENT_JORNADA_LINK_SELECTOR)
            ?: error("Could not find the current-jornada nav link")
        val href = link.attr("href")
        val match = JORNADA_NUMBER_REGEX.find(href)
            ?: error("Could not extract a jornada number from href: $href")
        return match.groupValues[1].toInt()
    }

    internal fun extractMatchDates(document: Document): List<LocalDate> =
        document.select("span.head").mapNotNull { element ->
            MATCH_DATE_REGEX.find(element.text())?.value?.let {
                LocalDate.parse(it, MATCH_DATE_FORMAT)
            }
        }

    internal fun requireFreshJornada(matchDates: List<LocalDate>, jornada: Int) {
        val closestGapInDays = matchDates.minOfOrNull { abs(ChronoUnit.DAYS.between(it, today())) }
        if (closestGapInDays == null || closestGapInDays > MAX_STALE_DAYS) {
            throw StaleJornadaException(jornada)
        }
    }

    // Pure: which neighboring jornada to show when `upcomingJornada`'s results aren't
    // populated yet. Ties, and no earlier jornada to fall back to, favor upcoming;
    // an undeterminable upcoming date (e.g. season over, nothing published yet) favors
    // the previous, already-finished one.
    internal fun decideGapJornada(
        previousJornada: Int,
        previousLastMatchDate: LocalDate,
        upcomingJornada: Int,
        upcomingFirstMatchDate: LocalDate?,
        today: LocalDate,
    ): Int {
        if (upcomingFirstMatchDate == null) return previousJornada
        val daysSinceFinished = ChronoUnit.DAYS.between(previousLastMatchDate, today)
        val daysUntilUpcoming = ChronoUnit.DAYS.between(today, upcomingFirstMatchDate)
        return if (daysSinceFinished < daysUntilUpcoming) previousJornada else upcomingJornada
    }
}
