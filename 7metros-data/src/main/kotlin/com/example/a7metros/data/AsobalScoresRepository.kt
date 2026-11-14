package com.example.a7metros.data

import com.example.a7metros.datasource.DataSource
import com.example.a7metros.datasource.asobal.AsobalJornadaMatches
import com.example.a7metros.datasource.asobal.AsobalMatch
import com.example.a7metros.datasource.asobal.AsobalMatchStatus
import com.example.a7metros.domain.JornadaMatches
import com.example.a7metros.domain.Match
import com.example.a7metros.domain.MatchStatus
import com.example.a7metros.domain.Score
import com.example.a7metros.domain.ScoresRepository

class AsobalScoresRepository(
    private val dataSource: DataSource,
) : ScoresRepository {

    override suspend fun getCurrentJornadaMatches(jornada: Int?): Result<JornadaMatches> =
        dataSource.fetchResults(jornada).map { it.toDomain() }
}

private fun AsobalJornadaMatches.toDomain(): JornadaMatches =
    JornadaMatches(jornada = jornada, matches = matches.map { it.toDomain() })

private fun AsobalMatch.toDomain(): Match = Match(
    homeTeam = homeTeam,
    awayTeam = awayTeam,
    score = score?.let { Score(home = it.home, away = it.away) },
    status = status.toDomain(),
    minute = minute,
    kickoff = kickoff,
)

private fun AsobalMatchStatus.toDomain(): MatchStatus = when (this) {
    AsobalMatchStatus.NOT_YET_PLAYED -> MatchStatus.NOT_YET_PLAYED
    AsobalMatchStatus.FIRST_HALF -> MatchStatus.FIRST_HALF
    AsobalMatchStatus.HALF_TIME -> MatchStatus.HALF_TIME
    AsobalMatchStatus.SECOND_HALF -> MatchStatus.SECOND_HALF
    AsobalMatchStatus.EXTRA_TIME -> MatchStatus.EXTRA_TIME
    AsobalMatchStatus.PENALTIES -> MatchStatus.PENALTIES
    AsobalMatchStatus.IN_PROGRESS -> MatchStatus.IN_PROGRESS
    AsobalMatchStatus.FINISHED -> MatchStatus.FINISHED
}
