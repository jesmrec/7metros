package com.example.a7metros.datasource.asobal

import java.time.LocalDateTime

data class AsobalMatch(
    val homeTeam: String,
    val awayTeam: String,
    val score: AsobalScore?,
    val status: AsobalMatchStatus,
    val minute: Int? = null,
    val kickoff: LocalDateTime? = null,
)

data class AsobalScore(val home: Int, val away: Int)

data class AsobalJornadaMatches(val jornada: Int, val matches: List<AsobalMatch>)

enum class AsobalMatchStatus {
    NOT_YET_PLAYED,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    EXTRA_TIME,
    PENALTIES,
    IN_PROGRESS,
    FINISHED,
}
