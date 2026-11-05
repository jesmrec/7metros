package com.example.a7metros.datasource.asobal

import com.example.a7metros.datasource.DataSource
import com.example.a7metros.network.NetworkClient

private const val ASOBAL_RESULTS_BASE_URL = "https://www.asobal.es/liga/resultados"

class AsobalDataSource(
    private val networkClient: NetworkClient,
    private val currentJornadaResolver: AsobalCurrentJornadaResolver,
) : DataSource {

    override suspend fun fetchResults(jornada: Int?): Result<ByteArray> {
        val resolvedJornada = jornada
            ?: currentJornadaResolver.resolve().getOrElse { return Result.failure(it) }
        return networkClient.fetch("$ASOBAL_RESULTS_BASE_URL/$resolvedJornada/")
    }
}
