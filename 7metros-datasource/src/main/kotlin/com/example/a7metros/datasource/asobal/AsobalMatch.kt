package com.example.a7metros.datasource.asobal

data class AsobalMatch(
    val homeTeam: String,
    val awayTeam: String,
    val score: AsobalScore?,
    val status: AsobalMatchStatus,
)

data class AsobalScore(val home: Int, val away: Int)

enum class AsobalMatchStatus {
    NOT_YET_PLAYED,
    IN_PROGRESS,
    FINISHED,
}
