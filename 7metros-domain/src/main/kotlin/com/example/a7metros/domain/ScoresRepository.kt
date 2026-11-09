package com.example.a7metros.domain

interface ScoresRepository {
    suspend fun getCurrentJornadaMatches(jornada: Int? = null): Result<List<Match>>
}
