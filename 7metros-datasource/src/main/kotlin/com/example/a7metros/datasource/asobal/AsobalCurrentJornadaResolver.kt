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
        val resultsDocument = parseDocument(fetchOrThrow("$ASOBAL_RESULTS_BASE_URL/$jornada/"))
        requireFreshJornada(resultsDocument, jornada)
        jornada
    }

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

    internal fun requireFreshJornada(document: Document, jornada: Int) {
        val matchDates = document.select("span.head").mapNotNull { element ->
            MATCH_DATE_REGEX.find(element.text())?.value?.let {
                LocalDate.parse(it, MATCH_DATE_FORMAT)
            }
        }
        val closestGapInDays = matchDates.minOfOrNull { abs(ChronoUnit.DAYS.between(it, today())) }
        if (closestGapInDays == null || closestGapInDays > MAX_STALE_DAYS) {
            throw StaleJornadaException(jornada)
        }
    }
}
