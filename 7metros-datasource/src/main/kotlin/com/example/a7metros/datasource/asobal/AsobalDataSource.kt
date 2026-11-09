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
        val matches = fetchAndParse("$ASOBAL_RESULTS_BASE_URL/$resolvedJornada/")
        // An upcoming jornada resolved via the gap-period fallback has nothing under
        // resultados yet; calendario carries its scheduled matches well in advance,
        // sharing the same "info-results" markup so parseAsobalMatches covers it too.
        if (matches.isNotEmpty()) matches else fetchAndParse("$ASOBAL_CALENDARIO_BASE_URL/$resolvedJornada/")
    }

    private suspend fun fetchAndParse(url: String): List<AsobalMatch> {
        val bytes = networkClient.fetch(url).getOrThrow()
        val document = Jsoup.parse(ByteArrayInputStream(bytes), ASOBAL_CHARSET, "")
        return parseAsobalMatches(document)
    }
}
