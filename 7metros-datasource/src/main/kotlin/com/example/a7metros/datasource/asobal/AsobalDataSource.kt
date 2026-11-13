package com.example.a7metros.datasource.asobal

import com.example.a7metros.datasource.DataSource
import com.example.a7metros.network.NetworkClient
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

private const val ASOBAL_RESULTS_BASE_URL = "https://www.asobal.es/liga/resultados"
private const val ASOBAL_CALENDARIO_BASE_URL = "https://www.asobal.es/liga/calendario"

// Same charset forced by AsobalCurrentJornadaResolver - see its comment for why.
private const val ASOBAL_CHARSET = "ISO-8859-1"

class AsobalDataSource(
    private val networkClient: NetworkClient,
    private val currentJornadaResolver: AsobalCurrentJornadaResolver,
) : DataSource {

    // Wraps the whole fetch+parse pipeline so a malformed-page parse failure (e.g. a
    // selector no longer matching, see AsobalResultsParser's error() calls) surfaces as
    // a Result failure exactly like a network failure, rather than throwing uncaught or
    // slipping through as an empty/partial list.
    override suspend fun fetchResults(jornada: Int?): Result<List<AsobalMatch>> = runCatching {
        val resolvedJornada = jornada ?: currentJornadaResolver.resolve().getOrThrow()
        val resultadosMatches = fetchAndParse("$ASOBAL_RESULTS_BASE_URL/$resolvedJornada/")
        // calendario carries every match of the jornada (scheduled well in advance,
        // sharing the same "info-results" markup so parseAsobalMatches covers it too),
        // while resultados only renders entries for matches it has started tracking
        // (in progress or finished) - matches still to be played are simply absent from
        // it while the round is live, not listed there with a "not yet played" status.
        // Use calendario's full list as the base and layer resultados's entries on top,
        // since those reflect the live/finished state more promptly.
        val calendarioMatches = fetchAndParse("$ASOBAL_CALENDARIO_BASE_URL/$resolvedJornada/")
        mergeWithLiveMatches(calendarioMatches, resultadosMatches)
    }

    private fun mergeWithLiveMatches(
        calendarioMatches: List<AsobalMatch>,
        resultadosMatches: List<AsobalMatch>,
    ): List<AsobalMatch> {
        if (calendarioMatches.isEmpty()) return resultadosMatches
        val resultadosByTeams = resultadosMatches.associateBy { it.homeTeam to it.awayTeam }
        return calendarioMatches.map { resultadosByTeams[it.homeTeam to it.awayTeam] ?: it }
    }

    private suspend fun fetchAndParse(url: String): List<AsobalMatch> {
        val bytes = networkClient.fetch(url).getOrThrow()
        val document = Jsoup.parse(ByteArrayInputStream(bytes), ASOBAL_CHARSET, "")
        return parseAsobalMatches(document)
    }
}
