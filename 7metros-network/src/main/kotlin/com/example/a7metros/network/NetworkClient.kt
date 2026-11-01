package com.example.a7metros.network

// Fetches a raw response body; callers interpret the content (HTML, JSON...).
interface NetworkClient {
    suspend fun fetch(url: String): Result<String>
}
