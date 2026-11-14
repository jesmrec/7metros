package com.example.a7metros.data

import com.example.a7metros.datasource.DataSource
import com.example.a7metros.datasource.asobal.AsobalMatch
import com.example.a7metros.datasource.asobal.AsobalMatchStatus
import com.example.a7metros.datasource.asobal.AsobalScore
import com.example.a7metros.domain.Match
import com.example.a7metros.domain.MatchStatus
import com.example.a7metros.domain.Score
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime

private const val JORNADA = 5
private val KICKOFF = LocalDateTime.parse("2026-11-15T12:00")

private class RecordingFakeDataSource(
    private val result: Result<List<AsobalMatch>>,
) : DataSource {
    var requestedJornada: Int? = null
        private set

    override suspend fun fetchResults(jornada: Int?): Result<List<AsobalMatch>> {
        requestedJornada = jornada
        return result
    }
}

class AsobalScoresRepositoryTest {

    @Test
    fun `getCurrentJornadaMatches maps match states, minute and kickoff to the domain model`() = runTest {
        val asobalMatches = listOf(
            AsobalMatch(
                homeTeam = "Fraikin BM. Granollers",
                awayTeam = "Barça",
                score = AsobalScore(home = 25, away = 36),
                status = AsobalMatchStatus.FINISHED,
            ),
            AsobalMatch(
                homeTeam = "BM Caserío Ciudad Real",
                awayTeam = "Bathco BM. Torrelavega",
                score = AsobalScore(home = 10, away = 8),
                status = AsobalMatchStatus.SECOND_HALF,
                minute = 52,
            ),
            AsobalMatch(
                homeTeam = "Dicorpebal Logroño La Rioja",
                awayTeam = "IRUDEK Bidasoa Irun",
                score = null,
                status = AsobalMatchStatus.NOT_YET_PLAYED,
                kickoff = KICKOFF,
            ),
        )
        val repository = AsobalScoresRepository(RecordingFakeDataSource(Result.success(asobalMatches)))

        val result = repository.getCurrentJornadaMatches()

        assertEquals(
            listOf(
                Match(
                    homeTeam = "Fraikin BM. Granollers",
                    awayTeam = "Barça",
                    score = Score(home = 25, away = 36),
                    status = MatchStatus.FINISHED,
                ),
                Match(
                    homeTeam = "BM Caserío Ciudad Real",
                    awayTeam = "Bathco BM. Torrelavega",
                    score = Score(home = 10, away = 8),
                    status = MatchStatus.SECOND_HALF,
                    minute = 52,
                ),
                Match(
                    homeTeam = "Dicorpebal Logroño La Rioja",
                    awayTeam = "IRUDEK Bidasoa Irun",
                    score = null,
                    status = MatchStatus.NOT_YET_PLAYED,
                    kickoff = KICKOFF,
                ),
            ),
            result.getOrThrow(),
        )
    }

    @Test
    fun `getCurrentJornadaMatches forwards an explicit jornada to the datasource unchanged`() = runTest {
        val dataSource = RecordingFakeDataSource(Result.success(emptyList()))
        val repository = AsobalScoresRepository(dataSource)

        repository.getCurrentJornadaMatches(jornada = JORNADA)

        assertEquals(JORNADA, dataSource.requestedJornada)
    }

    @Test
    fun `getCurrentJornadaMatches surfaces a datasource failure as a Result failure`() = runTest {
        val failure = IllegalStateException("boom")
        val repository = AsobalScoresRepository(RecordingFakeDataSource(Result.failure(failure)))

        val result = repository.getCurrentJornadaMatches()

        assertSame(failure, result.exceptionOrNull())
    }
}
