package com.example.a7metros.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val HTTP_OK = 200
private const val HTTP_INTERNAL_SERVER_ERROR = 500

class OkHttpNetworkClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpNetworkClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpNetworkClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetch returns the body on a successful response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("hello"))

        val result = client.fetch(server.url("/").toString())

        assertEquals("hello", result.getOrThrow())
    }

    @Test
    fun `fetch returns a failure on a non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_INTERNAL_SERVER_ERROR))

        val result = client.fetch(server.url("/").toString())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HttpException)
    }

    @Test
    fun `fetch returns a failure on a transport error`() = runTest {
        val unreachableUrl = server.url("/").toString()
        server.shutdown()

        val result = client.fetch(unreachableUrl)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
