package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.json
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class PlexAuthServiceTest {
    private val server = MockWebServer()
    private val info = PlexClientInfo("client-1", "Seerr Android", "1.2", "Android", "Pixel 9")
    private lateinit var plex: PlexAuthService

    @Before fun start() {
        server.start()
        plex = PlexAuthService(info, OkHttpClient(), server.url("/"))
    }

    @After fun stop() = server.close()

    @Test fun `creating a pin asks for a strong one and says who is asking`() = runTest {
        server.enqueue(json("""{"id":7,"code":"ABCD","authToken":null,"expiresAt":"2030-01-01T00:00:00Z"}"""))

        val pin = plex.createPin()

        assertEquals(7, pin.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/pins?strong=true", request.url.encodedPath + "?" + request.url.encodedQuery)
        assertEquals("client-1", request.headers["X-Plex-Client-Identifier"])
        assertEquals("Seerr Android", request.headers["X-Plex-Product"])
        assertEquals("Android", request.headers["X-Plex-Platform"])
        assertEquals("Pixel 9", request.headers["X-Plex-Device"])
    }

    @Test fun `the approval page carries the pin in the fragment`() {
        val url = plex.authUrl(PlexPin(7, "ABCD"))
        assertTrue(url.startsWith("https://app.plex.tv/auth/#!?"))
        assertTrue(url.contains("clientID=client-1"))
        assertTrue(url.contains("code=ABCD"))
        assertTrue(url.contains("context%5Bdevice%5D%5Bproduct%5D=Seerr%20Android"))
        assertTrue(url.contains("context%5Bdevice%5D%5Bdevice%5D=Pixel%209"))
        assertTrue(!url.contains("forwardUrl"))
    }

    @Test fun `polling returns the token once the pin is approved`() = runTest {
        val pin = PlexPin(7, "ABCD", expiresAt = Instant.now().plusSeconds(600).toString())
        server.enqueue(json("""{"id":7,"code":"ABCD","authToken":null}"""))
        server.enqueue(json("""{"id":7,"code":"ABCD","authToken":""}"""))
        server.enqueue(json("""{"id":7,"code":"ABCD","authToken":"tok"}"""))

        assertEquals("tok", plex.pollForToken(pin, interval = 1.seconds))
        assertEquals(3, server.requestCount)
    }

    @Test fun `a failed poll doesn't end the wait`() = runTest {
        val pin = PlexPin(7, "ABCD", expiresAt = Instant.now().plusSeconds(600).toString())
        server.enqueue(json("{}", 500))
        server.enqueue(json("""{"id":7,"code":"ABCD","authToken":"tok"}"""))

        assertEquals("tok", plex.pollForToken(pin, interval = 1.seconds))
    }

    @Test fun `an expired pin times out`() = runTest {
        val pin = PlexPin(7, "ABCD", expiresAt = Instant.now().minusSeconds(5).toString())
        try {
            plex.pollForToken(pin)
            error("Expected a timeout")
        } catch (_: PlexTimedOutException) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `a pin that is never approved times out`() = runTest {
        val pin = PlexPin(7, "ABCD", expiresAt = Instant.now().plusSeconds(3).toString())
        repeat(10) { server.enqueue(json("""{"id":7,"code":"ABCD"}""")) }
        try {
            plex.pollForToken(pin, interval = 1.seconds)
            error("Expected a timeout")
        } catch (_: PlexTimedOutException) {
        }
    }
}
