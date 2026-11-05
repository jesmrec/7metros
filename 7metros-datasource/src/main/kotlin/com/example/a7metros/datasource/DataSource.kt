package com.example.a7metros.datasource

// Asobal-shaped for now (see design.md's "DataSource abstraction is Asobal-shaped" decision):
// a future source may need a different shape rather than conforming to this one unchanged.
interface DataSource {
    suspend fun fetchResults(jornada: Int? = null): Result<ByteArray>
}
