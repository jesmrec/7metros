package com.example.a7metros.domain

data class Match(
    val homeTeam: String,
    val awayTeam: String,
    val score: Score?,
    val status: MatchStatus,
)

data class Score(val home: Int, val away: Int)

enum class MatchStatus {
    NOT_YET_PLAYED,
    IN_PROGRESS,
    FINISHED,
}
