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
private val TODAY_IN_FIXTURE = LocalDate.parse("2026-11-01")

private val FRESH_RESULTS_HTML = """
    <html><body>
      <span class="head">Jornada 8 - 01/11/2026 <span class="date">20:00</span></span>
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
    fun `requireFreshJornada accepts a jornada whose match dates are near today`() {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(emptyMap()),
            today = { TODAY_IN_FIXTURE },
        )

        resolver.requireFreshJornada(Jsoup.parse(FRESH_RESULTS_HTML), EXPECTED_CURRENT_JORNADA)
    }

    @Test(expected = StaleJornadaException::class)
    fun `requireFreshJornada rejects a jornada whose match dates are far from today`() {
        val resolver = AsobalCurrentJornadaResolver(
            networkClient = FakeNetworkClient(emptyMap()),
            today = { TODAY_IN_FIXTURE },
        )

        // The nav-link fixture is the default (no-jornada) results page, which always
        // renders Jornada 1's matches (September 2026) - stale relative to November.
        resolver.requireFreshJornada(loadNavLinkFixture(), EXPECTED_CURRENT_JORNADA)
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
}
