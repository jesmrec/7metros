package com.example.a7metros.datasource.asobal

data class AsobalMatch(
    val homeTeam: String,
    val awayTeam: String,
    val score: AsobalScore?,
    val status: AsobalMatchStatus,
    val minute: Int? = null,
)

data class AsobalScore(val home: Int, val away: Int)

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
