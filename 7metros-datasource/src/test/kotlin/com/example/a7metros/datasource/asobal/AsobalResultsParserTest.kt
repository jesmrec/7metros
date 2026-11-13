package com.example.a7metros.datasource.asobal

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val LIVE_MATCH_HTML = """
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
              <span class="goals"><b>12</b> - <b>14</b></span>
              <a href="equipos/BAR/">BAR</a>
            </div>
            <span class="enlaces">45'</span>
          </li>
        </ul>
      </div>
    </body></html>
""".trimIndent()

private const val FINISHED_FIXTURE_PATH = "fixtures/asobal/resultados-finished.html"
private const val NOT_YET_PLAYED_FIXTURE_PATH = "fixtures/asobal/calendario-not-started.html"
private const val EXPECTED_MATCHES_PER_JORNADA = 8

class AsobalResultsParserTest {

    private fun loadFixture(path: String) =
        Jsoup.parse(
            checkNotNull(javaClass.classLoader.getResourceAsStream(path)),
            "iso-8859-1",
            "https://www.asobal.es/",
        )

    @Test
    fun `parseAsobalMatches extracts a finished match with its final score`() {
        val matches = parseAsobalMatches(loadFixture(FINISHED_FIXTURE_PATH))

        assertEquals(EXPECTED_MATCHES_PER_JORNADA, matches.size)
        val firstMatch = matches[0]
        assertEquals("Fraikin BM. Granollers", firstMatch.homeTeam)
        assertEquals("Barça", firstMatch.awayTeam)
        assertEquals(AsobalScore(home = 25, away = 36), firstMatch.score)
        assertEquals(AsobalMatchStatus.FINISHED, firstMatch.status)
    }

    @Test
    fun `parseAsobalMatches extracts a not-yet-played match with no score`() {
        val matches = parseAsobalMatches(loadFixture(NOT_YET_PLAYED_FIXTURE_PATH))

        assertEquals(EXPECTED_MATCHES_PER_JORNADA, matches.size)
        val firstMatch = matches[0]
        assertEquals("Barça", firstMatch.homeTeam)
        assertEquals("Cajasol Sevilla Bm. Proin", firstMatch.awayTeam)
        assertNull(firstMatch.score)
        assertEquals(AsobalMatchStatus.NOT_YET_PLAYED, firstMatch.status)
    }

    @Test
    fun `parseAsobalMatches treats an unrecognized status label as in progress`() {
        val matches = parseAsobalMatches(Jsoup.parse(LIVE_MATCH_HTML, "https://www.asobal.es/"))

        val match = matches.single()
        assertEquals(AsobalScore(home = 12, away = 14), match.score)
        assertEquals(AsobalMatchStatus.IN_PROGRESS, match.status)
    }

    @Test
    fun `parseAsobalMatches decodes an accented team name correctly`() {
        val matches = parseAsobalMatches(loadFixture(FINISHED_FIXTURE_PATH))

        val matchWithAccentedTeam = matches.single { it.awayTeam == "Dicorpebal Logroño La Rioja" }

        assertEquals("BM Caserío Ciudad Real", matchWithAccentedTeam.homeTeam)
    }
}
