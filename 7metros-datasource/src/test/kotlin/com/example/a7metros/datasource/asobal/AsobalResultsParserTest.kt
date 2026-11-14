package com.example.a7metros.datasource.asobal

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun liveMatchHtml(statusText: String) = """
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
            <span class="enlaces">$statusText</span>
          </li>
        </ul>
      </div>
    </body></html>
""".trimIndent()

private const val UNRECOGNIZED_STATUS_MINUTE = 45
private const val FIRST_HALF_MINUTE = 29
private const val SECOND_HALF_MINUTE = 56
private const val EXTRA_TIME_MINUTE = 65

private val LIVE_MATCH_HTML = liveMatchHtml("$UNRECOGNIZED_STATUS_MINUTE'")

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
        assertEquals(UNRECOGNIZED_STATUS_MINUTE, match.minute)
    }

    @Test
    fun `parseAsobalMatches recognizes a first-half match with its minute`() {
        val html = liveMatchHtml("Primera parte ($FIRST_HALF_MINUTE')")
        val match = parseAsobalMatches(Jsoup.parse(html, "https://www.asobal.es/")).single()

        assertEquals(AsobalMatchStatus.FIRST_HALF, match.status)
        assertEquals(FIRST_HALF_MINUTE, match.minute)
    }

    @Test
    fun `parseAsobalMatches recognizes a half-time match with no minute`() {
        val html = liveMatchHtml("Descanso")
        val match = parseAsobalMatches(Jsoup.parse(html, "https://www.asobal.es/")).single()

        assertEquals(AsobalMatchStatus.HALF_TIME, match.status)
        assertNull(match.minute)
    }

    @Test
    fun `parseAsobalMatches recognizes a second-half match with its minute`() {
        val html = liveMatchHtml("Segunda parte ($SECOND_HALF_MINUTE')")
        val match = parseAsobalMatches(Jsoup.parse(html, "https://www.asobal.es/")).single()

        assertEquals(AsobalMatchStatus.SECOND_HALF, match.status)
        assertEquals(SECOND_HALF_MINUTE, match.minute)
    }

    @Test
    fun `parseAsobalMatches recognizes an extra-time match with its minute`() {
        val html = liveMatchHtml("Prórroga ($EXTRA_TIME_MINUTE')")
        val match = parseAsobalMatches(Jsoup.parse(html, "https://www.asobal.es/")).single()

        assertEquals(AsobalMatchStatus.EXTRA_TIME, match.status)
        assertEquals(EXTRA_TIME_MINUTE, match.minute)
    }

    @Test
    fun `parseAsobalMatches recognizes a penalty shootout match with no minute`() {
        val html = liveMatchHtml("Penaltis")
        val match = parseAsobalMatches(Jsoup.parse(html, "https://www.asobal.es/")).single()

        assertEquals(AsobalMatchStatus.PENALTIES, match.status)
        assertNull(match.minute)
    }

    @Test
    fun `parseAsobalMatches decodes an accented team name correctly`() {
        val matches = parseAsobalMatches(loadFixture(FINISHED_FIXTURE_PATH))

        val matchWithAccentedTeam = matches.single { it.awayTeam == "Dicorpebal Logroño La Rioja" }

        assertEquals("BM Caserío Ciudad Real", matchWithAccentedTeam.homeTeam)
    }
}
