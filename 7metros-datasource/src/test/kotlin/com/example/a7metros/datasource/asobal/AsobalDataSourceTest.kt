package com.example.a7metros.datasource.asobal

import com.example.a7metros.network.NetworkClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

private const val NAV_LINK_URL = "https://www.asobal.es/liga/resultados/"
private const val JORNADA_8_RESULTS_URL = "https://www.asobal.es/liga/resultados/8/"
private const val JORNADA_5_RESULTS_URL = "https://www.asobal.es/liga/resultados/5/"
private const val JORNADA_5_CALENDARIO_URL = "https://www.asobal.es/liga/calendario/5/"
private val TODAY_IN_FIXTURE = LocalDate.parse("2026-11-01")

private val NAV_LINK_HTML = """
    <html><body><a href="/liga/resultados/8/">Jornada 8</a></body></html>
""".trimIndent()

// Minimal markup covering what AsobalResultsParser actually selects (a club-menu entry
// per team, code -> full name, and one finished match referencing those same codes) plus
// the "span.head" match date AsobalCurrentJornadaResolver's freshness check reads, dated
// to TODAY_IN_FIXTURE so resolution doesn't fall into the empty-jornada gap path.
private val RESULTS_HTML = """
    <html><body>
      <div class="clubs text-center">
        <a href="/equipos/GRA/"><img title="6 - Fraikin BM. Granollers"></a>
        <a href="/equipos/BAR/"><img title="2 - Barça"></a>
      </div>
      <div class="info-results">
        <ul>
          <li>
            <span class="head">Jornada 8 - 01/11/2026 <span class="date">20:00</span></span>
            <div class="goals-result">
              <a href="equipos/GRA/">GRA</a>
              <span class="goals"><b>25</b> - <b>36</b></span>
              <a href="equipos/BAR/">BAR</a>
            </div>
            <span class="enlaces">Partido finalizado</span>
          </li>
        </ul>
      </div>
    </body></html>
""".trimIndent()

// Has a match entry (so parsing is attempted) but is missing the team-link markup the
// parser requires, simulating the site's structure changing unexpectedly.
private val MALFORMED_RESULTS_HTML = """
    <html><body>
      <div class="info-results">
        <ul>
          <li>
            <span class="enlaces">Partido finalizado</span>
          </li>
        </ul>
      </div>
    </body></html>
""".trimIndent()

// A jornada that resolved as "upcoming" per the gap-period fallback: the round hasn't
// been populated yet, so resultados has the info-results container but no match entries.
private val EMPTY_RESULTS_HTML = """
    <html><body>
      <div class="info-results">
        <ul></ul>
      </div>
    </body></html>
""".trimIndent()

// calendario shares the same "info-results" markup as resultados, just with a
// not-yet-played entry (placeholder "0 - 0" score, "No ha comenzado" status).
private val CALENDARIO_HTML = """
    <html><body>
      <div class="clubs text-center">
        <a href="/equipos/GRA/"><img title="6 - Fraikin BM. Granollers"></a>
        <a href="/equipos/BAR/"><img title="2 - Barça"></a>
      </div>
      <div class="info-results">
        <ul>
          <li>
            <div class="goals-result">
              <a href="equipos/GRA/">GRA</a>
              <span class="goals"><b>0</b> - <b>0</b></span>
              <a href="equipos/BAR/">BAR</a>
            </div>
            <span class="enlaces">No ha comenzado</span>
          </li>
        </ul>
      </div>
    </body></html>
""".trimIndent()

private val EXPECTED_MATCH = AsobalMatch(
    homeTeam = "Fraikin BM. Granollers",
    awayTeam = "Barça",
    score = AsobalScore(home = 25, away = 36),
    status = AsobalMatchStatus.FINISHED,
)

private val EXPECTED_CALENDARIO_MATCH = AsobalMatch(
    homeTeam = "Fraikin BM. Granollers",
    awayTeam = "Barça",
    score = null,
    status = AsobalMatchStatus.NOT_YET_PLAYED,
)

private class RecordingFakeNetworkClient(
    private val responses: Map<String, Result<ByteArray>>,
) : NetworkClient {
    val fetchedUrls = mutableListOf<String>()

    override suspend fun fetch(url: String): Result<ByteArray> {
        fetchedUrls += url
        return responses[url] ?: error("No fake response configured for $url")
    }
}

class AsobalDataSourceTest {

    private fun dataSource(networkClient: RecordingFakeNetworkClient) = AsobalDataSource(
        networkClient = networkClient,
        currentJornadaResolver = AsobalCurrentJornadaResolver(networkClient, today = { TODAY_IN_FIXTURE }),
    )

    @Test
    fun `fetchResults resolves the current jornada when none is passed`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(
                NAV_LINK_URL to Result.success(NAV_LINK_HTML.toByteArray(Charsets.ISO_8859_1)),
                JORNADA_8_RESULTS_URL to Result.success(RESULTS_HTML.toByteArray(Charsets.ISO_8859_1)),
            ),
        )

        val result = dataSource(networkClient).fetchResults()

        assertTrue(networkClient.fetchedUrls.contains(NAV_LINK_URL))
        assertEquals(listOf(EXPECTED_MATCH), result.getOrThrow())
    }

    @Test
    fun `fetchResults does not resolve the current jornada when one is passed explicitly`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(JORNADA_5_RESULTS_URL to Result.success(RESULTS_HTML.toByteArray(Charsets.ISO_8859_1))),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertFalse(networkClient.fetchedUrls.contains(NAV_LINK_URL))
        assertEquals(listOf(EXPECTED_MATCH), result.getOrThrow())
    }

    @Test
    fun `fetchResults fails when the results page cannot be fetched`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(JORNADA_5_RESULTS_URL to Result.failure(IOException("Connection refused"))),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchResults fails when the results page structure is unrecognized`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(JORNADA_5_RESULTS_URL to Result.success(MALFORMED_RESULTS_HTML.toByteArray(Charsets.ISO_8859_1))),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchResults falls back to calendario when resultados has no matches`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(
                JORNADA_5_RESULTS_URL to Result.success(EMPTY_RESULTS_HTML.toByteArray(Charsets.ISO_8859_1)),
                JORNADA_5_CALENDARIO_URL to Result.success(CALENDARIO_HTML.toByteArray(Charsets.ISO_8859_1)),
            ),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertTrue(networkClient.fetchedUrls.contains(JORNADA_5_CALENDARIO_URL))
        assertEquals(listOf(EXPECTED_CALENDARIO_MATCH), result.getOrThrow())
    }

    @Test
    fun `fetchResults does not fall back to calendario when resultados already has matches`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(JORNADA_5_RESULTS_URL to Result.success(RESULTS_HTML.toByteArray(Charsets.ISO_8859_1))),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertFalse(networkClient.fetchedUrls.contains(JORNADA_5_CALENDARIO_URL))
        assertEquals(listOf(EXPECTED_MATCH), result.getOrThrow())
    }
}
