package com.example.a7metros.datasource.asobal

import com.example.a7metros.network.NetworkClient
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

private const val NAV_LINK_FIXTURE_PATH = "fixtures/asobal/nav-link-current-jornada-8.html"
private const val EXPECTED_CURRENT_JORNADA = 8
private const val FINISHED_JORNADA = 8
private const val UPCOMING_JORNADA = 9
private val TODAY_IN_FIXTURE = LocalDate.parse("2026-11-01")

private val FRESH_RESULTS_HTML = """
    <html><body>
      <span class="head">Jornada 8 - 01/11/2026 <span class="date">20:00</span></span>
    </body></html>
""".trimIndent()

private val EMPTY_RESULTS_HTML = """
    <html><body>
      <div class="info-results"><ul></ul></div>
    </body></html>
""".trimIndent()

private val NAV_LINK_JORNADA_1_HTML = """
    <html><body><a href="/liga/resultados/1/">Jornada 1</a></body></html>
""".trimIndent()

private val NAV_LINK_JORNADA_9_HTML = """
    <html><body><a href="/liga/resultados/9/">Jornada 9</a></body></html>
""".trimIndent()

private fun resultsHtmlWithDate(jornada: Int, date: String) = """
    <html><body>
      <span class="head">Jornada $jornada - $date <span class="date">20:00</span></span>
    </body></html>
""".trimIndent()

private fun calendarioHtmlWithDate(date: String) = """
    <html><body>
      <span class="head">$date - 20:00<span class="date"> ASOBAL TV</span></span>
    </body></html>
""".trimIndent()

private class FakeNetworkClient(
    private val responses: Map<String, Result<String>>,
) : NetworkClient {
    override suspend fun fetch(url: String): Result<String> =
        responses[url] ?: error("No fake response configured for $url")
}

class AsobalCurrentJornadaResolverTest {

    private fun loadNavLinkFixture() =
        Jsoup.parse(
            checkNotNull(javaClass.classLoader.getResourceAsStream(NAV_LINK_FIXTURE_PATH)),
            "iso-8859-1",
            "https://www.asobal.es/liga/resultados/",
        )

    @Test
    fun `resolveJornadaNumber extracts the current jornada from the shared nav link`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val jornada = resolver.resolveJornadaNumber(loadNavLinkFixture())

        assertEquals(EXPECTED_CURRENT_JORNADA, jornada)
    }

    @Test
    fun `extractMatchDates parses every match date from the shared head spans`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val dates = resolver.extractMatchDates(Jsoup.parse(FRESH_RESULTS_HTML))

        assertEquals(listOf(LocalDate.parse("2026-11-01")), dates)
    }

    @Test
    fun `extractMatchDates returns an empty list when the results page has no match entries`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val dates = resolver.extractMatchDates(Jsoup.parse(EMPTY_RESULTS_HTML))

        assertTrue(dates.isEmpty())
    }

    @Test
    fun `requireFreshJornada accepts a jornada whose match dates are near today`() {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(emptyMap()),
            today = { TODAY_IN_FIXTURE },
        )

        resolver.requireFreshJornada(listOf(TODAY_IN_FIXTURE), EXPECTED_CURRENT_JORNADA)
    }

    @Test(expected = StaleJornadaException::class)
    fun `requireFreshJornada rejects a jornada whose match dates are far from today`() {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(emptyMap()),
            today = { TODAY_IN_FIXTURE },
        )

        // September matches, 49 days before the November "today" - stale either way.
        resolver.requireFreshJornada(listOf(LocalDate.parse("2026-09-13")), EXPECTED_CURRENT_JORNADA)
    }

    @Test
    fun `decideGapJornada favors the finished jornada when it ended more recently than the upcoming one starts`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val resolved = resolver.decideGapJornada(
            previousJornada = FINISHED_JORNADA,
            previousLastMatchDate = LocalDate.parse("2026-11-03"),
            upcomingJornada = UPCOMING_JORNADA,
            upcomingFirstMatchDate = LocalDate.parse("2026-11-15"),
            today = LocalDate.parse("2026-11-05"),
        )

        assertEquals(FINISHED_JORNADA, resolved)
    }

    @Test
    fun `decideGapJornada favors the upcoming jornada when it starts sooner than the finished one ended`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val resolved = resolver.decideGapJornada(
            previousJornada = FINISHED_JORNADA,
            previousLastMatchDate = LocalDate.parse("2026-11-02"),
            upcomingJornada = UPCOMING_JORNADA,
            upcomingFirstMatchDate = LocalDate.parse("2026-11-07"),
            today = LocalDate.parse("2026-11-05"),
        )

        assertEquals(UPCOMING_JORNADA, resolved)
    }

    @Test
    fun `decideGapJornada favors the upcoming jornada on a tie`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val resolved = resolver.decideGapJornada(
            previousJornada = FINISHED_JORNADA,
            previousLastMatchDate = LocalDate.parse("2026-10-31"),
            upcomingJornada = UPCOMING_JORNADA,
            upcomingFirstMatchDate = LocalDate.parse("2026-11-10"),
            today = LocalDate.parse("2026-11-05"),
        )

        assertEquals(UPCOMING_JORNADA, resolved)
    }

    @Test
    fun `decideGapJornada favors the finished jornada when the upcoming one's date is undeterminable`() {
        val resolver = AsobalCurrentJornadaResolver(networkClient = FakeNetworkClient(emptyMap()))

        val resolved = resolver.decideGapJornada(
            previousJornada = FINISHED_JORNADA,
            previousLastMatchDate = LocalDate.parse("2026-11-03"),
            upcomingJornada = UPCOMING_JORNADA,
            upcomingFirstMatchDate = null,
            today = LocalDate.parse("2026-11-05"),
        )

        assertEquals(FINISHED_JORNADA, resolved)
    }

    @Test
    fun `resolve fetches the nav link page then the resolved jornada's results page`() = runTest {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(
                mapOf(
                    "https://www.asobal.es/liga/resultados/" to Result.success(
                        loadNavLinkFixture().outerHtml(),
                    ),
                    "https://www.asobal.es/liga/resultados/8/" to Result.success(FRESH_RESULTS_HTML),
                ),
            ),
            today = { TODAY_IN_FIXTURE },
        )

        val result = resolver.resolve()

        assertEquals(EXPECTED_CURRENT_JORNADA, result.getOrThrow())
    }

    @Test
    fun `resolve surfaces a failure when the nav link page cannot be fetched`() = runTest {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(
                mapOf(
                    "https://www.asobal.es/liga/resultados/" to Result.failure(IOException("unreachable")),
                ),
            ),
        )

        val result = resolver.resolve()

        assertTrue(result.isFailure)
    }

    @Test
    fun `resolve falls back to the finished jornada when the upcoming one has no results yet`() = runTest {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(
                mapOf(
                    "https://www.asobal.es/liga/resultados/" to Result.success(NAV_LINK_JORNADA_9_HTML),
                    "https://www.asobal.es/liga/resultados/9/" to Result.success(EMPTY_RESULTS_HTML),
                    "https://www.asobal.es/liga/resultados/8/" to Result.success(
                        resultsHtmlWithDate(FINISHED_JORNADA, "03/11/2026"),
                    ),
                    "https://www.asobal.es/liga/calendario/9/" to Result.success(
                        calendarioHtmlWithDate("15/11/2026"),
                    ),
                ),
            ),
            today = { LocalDate.parse("2026-11-05") },
        )

        val result = resolver.resolve()

        assertEquals(FINISHED_JORNADA, result.getOrThrow())
    }

    @Test
    fun `resolve returns jornada 1 directly when its results are empty and there is no earlier jornada`() = runTest {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(
                mapOf(
                    "https://www.asobal.es/liga/resultados/" to Result.success(NAV_LINK_JORNADA_1_HTML),
                    "https://www.asobal.es/liga/resultados/1/" to Result.success(EMPTY_RESULTS_HTML),
                ),
            ),
        )

        val result = resolver.resolve()

        assertEquals(1, result.getOrThrow())
    }
}
