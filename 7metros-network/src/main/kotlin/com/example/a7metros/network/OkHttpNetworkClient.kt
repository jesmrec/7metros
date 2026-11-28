package com.example.a7metros.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HttpException(val code: Int) : IOException("Unexpected HTTP response code: $code")

class OkHttpNetworkClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : NetworkClient {

    override suspend fun fetch(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpException(response.code)
                response.body?.bytes() ?: ByteArray(0)
            }
        }
    }
}
