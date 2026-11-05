package com.example.a7metros.datasource.asobal

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `parseAsobalMatches decodes an accented team name correctly`() {
        val matches = parseAsobalMatches(loadFixture(FINISHED_FIXTURE_PATH))

        val matchWithAccentedTeam = matches.single { it.awayTeam == "Dicorpebal Logroño La Rioja" }

        assertEquals("BM Caserío Ciudad Real", matchWithAccentedTeam.homeTeam)
    }
}
