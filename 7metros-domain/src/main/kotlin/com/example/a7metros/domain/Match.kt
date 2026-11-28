package com.example.a7metros.domain

import java.time.LocalDateTime

data class Match(
    val homeTeam: String,
    val awayTeam: String,
    val score: Score?,
    val status: MatchStatus,
    val minute: Int? = null,
    val kickoff: LocalDateTime? = null,
)

data class Score(val home: Int, val away: Int)

data class JornadaMatches(val jornada: Int, val matches: List<Match>)

enum class MatchStatus {
    NOT_YET_PLAYED,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    EXTRA_TIME,
    PENALTIES,
    IN_PROGRESS,
    FINISHED,
}
