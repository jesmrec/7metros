package com.example.a7metros.datasource.asobal

import com.example.a7metros.network.NetworkClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val NAV_LINK_URL = "https://www.asobal.es/liga/resultados/"
private const val JORNADA_8_RESULTS_URL = "https://www.asobal.es/liga/resultados/8/"
private const val JORNADA_5_RESULTS_URL = "https://www.asobal.es/liga/resultados/5/"
private val TODAY_IN_FIXTURE = LocalDate.parse("2026-11-01")

private val NAV_LINK_HTML = """
    <html><body><a href="/liga/resultados/8/">Jornada 8</a></body></html>
""".trimIndent()

private val RESULTS_HTML = """
    <html><body>
      <span class="head">Jornada 8 - 01/11/2026 <span class="date">20:00</span></span>
    </body></html>
""".trimIndent()

private class RecordingFakeNetworkClient(
    private val responses: Map<String, Result<String>>,
) : NetworkClient {
    val fetchedUrls = mutableListOf<String>()

    override suspend fun fetch(url: String): Result<String> {
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
                NAV_LINK_URL to Result.success(NAV_LINK_HTML),
                JORNADA_8_RESULTS_URL to Result.success(RESULTS_HTML),
            ),
        )

        val result = dataSource(networkClient).fetchResults()

        assertTrue(networkClient.fetchedUrls.contains(NAV_LINK_URL))
        assertEquals(RESULTS_HTML, result.getOrThrow())
    }

    @Test
    fun `fetchResults does not resolve the current jornada when one is passed explicitly`() = runTest {
        val networkClient = RecordingFakeNetworkClient(
            mapOf(JORNADA_5_RESULTS_URL to Result.success(RESULTS_HTML)),
        )

        val result = dataSource(networkClient).fetchResults(jornada = 5)

        assertFalse(networkClient.fetchedUrls.contains(NAV_LINK_URL))
        assertEquals(RESULTS_HTML, result.getOrThrow())
    }
}
