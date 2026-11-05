package com.example.a7metros.network

// Fetches a raw response body; callers interpret the content (HTML, JSON...),
// including choosing its charset - this layer doesn't decode text, only bytes.
interface NetworkClient {
    suspend fun fetch(url: String): Result<ByteArray>
}
