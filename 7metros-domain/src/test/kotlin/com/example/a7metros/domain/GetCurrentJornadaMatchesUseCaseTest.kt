package com.example.a7metros.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

private const val JORNADA = 5

private class RecordingFakeScoresRepository(
    private val result: Result<JornadaMatches>,
) : ScoresRepository {
    var requestedJornada: Int? = null
        private set

    override suspend fun getCurrentJornadaMatches(jornada: Int?): Result<JornadaMatches> {
        requestedJornada = jornada
        return result
    }
}

class GetCurrentJornadaMatchesUseCaseTest {

    @Test
    fun `invoke forwards the jornada parameter to the repository`() = runTest {
        val repository = RecordingFakeScoresRepository(Result.success(JornadaMatches(JORNADA, emptyList())))
        val useCase = GetCurrentJornadaMatchesUseCase(repository)

        useCase(jornada = JORNADA)

        assertEquals(JORNADA, repository.requestedJornada)
    }

    @Test
    fun `invoke forwards a repository failure unchanged`() = runTest {
        val failure = IllegalStateException("boom")
        val repository = RecordingFakeScoresRepository(Result.failure(failure))
        val useCase = GetCurrentJornadaMatchesUseCase(repository)

        val result = useCase()

        assertSame(failure, result.exceptionOrNull())
    }
}
