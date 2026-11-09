package com.example.a7metros.domain

class GetCurrentJornadaMatchesUseCase(private val repository: ScoresRepository) {

    suspend operator fun invoke(jornada: Int? = null): Result<List<Match>> =
        repository.getCurrentJornadaMatches(jornada)
}
