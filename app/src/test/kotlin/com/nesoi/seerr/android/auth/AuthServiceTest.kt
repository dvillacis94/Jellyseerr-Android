package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.INITIALIZED_SETTINGS
import com.nesoi.seerr.android.USER_JSON
import com.nesoi.seerr.android.core.ServerConnectionError
import com.nesoi.seerr.android.core.ServerConnectionException
import com.nesoi.seerr.android.json
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthServiceTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()
    private val auth = fixture.auth

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun address() = server.url("/").toString()

    @Test fun `discovering a server returns its status and settings`() = runTest {
        server.enqueue(json("""{"version":"2.1.0"}"""))
        server.enqueue(json(INITIALIZED_SETTINGS))

        val info = auth.discoverServer(address())

        assertEquals("2.1.0", info.status.version)
        assertEquals("Seerr", info.settings.applicationTitle)
        assertEquals("/api/v1/status", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/settings/public", server.takeRequest().url.encodedPath)
    }

    @Test fun `discovery doesn't change the configured server`() = runTest {
        server.enqueue(json("""{"version":"2.1.0"}"""))
        server.enqueue(json(INITIALIZED_SETTINGS))
        auth.discoverServer(address())
        assertNull(fixture.preferences.url)
        assertNull(auth.serverUrl)
    }

    @Test fun `a server that isn't set up is reported`() = runTest {
        server.enqueue(json("""{"version":"2.1.0"}"""))
        server.enqueue(json("""{"initialized":false}"""))
        assertEquals(ServerConnectionError.NotInitialized, failure { auth.discoverServer(address()) })
    }

    @Test fun `a page that isn't Seerr is reported`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("<html>nope</html>").build())
        assertEquals(ServerConnectionError.NotSeerr, failure { auth.discoverServer(address()) })
    }

    @Test fun `html with a 200 isn't Seerr either`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("<html>hi</html>").build())
        assertEquals(ServerConnectionError.NotSeerr, failure { auth.discoverServer(address()) })
    }

    @Test fun `a server that can't be reached is reported`() = runTest {
        val unused = server.url("/").toString()
        server.close()
        assertEquals(ServerConnectionError.Unreachable, failure { auth.discoverServer(unused) })
    }

    @Test fun `empty and malformed addresses are reported`() = runTest {
        assertEquals(ServerConnectionError.EmptyAddress, failure { auth.discoverServer("") })
        assertEquals(ServerConnectionError.InvalidAddress, failure { auth.discoverServer("ftp://x") })
    }

    @Test fun `using a server saves it`() = runTest {
        val url = server.url("/")
        auth.useServer(url)
        assertEquals(url, fixture.preferences.url)
        assertEquals(url, auth.serverUrl)
    }

    @Test fun `local sign in sends the credentials and keeps the session`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())

        val user = auth.signIn(SignInMethod.Local("me@example.com", "secret"))

        assertEquals("Me", user.displayNameOrEmail)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/local", request.url.encodedPath)
        assertEquals("""{"email":"me@example.com","password":"secret"}""", request.body!!.utf8())
        assertNotNull(fixture.store.read("session"))
    }

    @Test fun `jellyfin and plex sign in use their own routes`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        server.enqueue(sessionResponse())

        auth.signIn(SignInMethod.Jellyfin("me", "pw"))
        auth.signIn(SignInMethod.Plex("token"))

        assertEquals("/api/v1/auth/jellyfin", server.takeRequest().url.encodedPath)
        val plex = server.takeRequest()
        assertEquals("/api/v1/auth/plex", plex.url.encodedPath)
        assertEquals("""{"authToken":"token"}""", plex.body!!.utf8())
    }

    @Test fun `the csrf token is echoed back on writes`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .addHeader("Set-Cookie", "XSRF-TOKEN=t0k; Path=/").body(USER_JSON).build(),
        )
        server.enqueue(json("{}"))
        auth.signIn(SignInMethod.Local("a", "b"))
        server.takeRequest()
        auth.signOut()
        assertEquals("t0k", server.takeRequest().headers["X-XSRF-TOKEN"])
    }

    @Test fun `sign in failures are explained`() = runTest {
        auth.useServer(server.url("/"))

        suspend fun failureFor(code: Int, body: String, method: SignInMethod): SignInFailure {
            server.enqueue(json(body, code))
            return assertThrowsSuspend { auth.signIn(method) }.failure
        }

        val local = SignInMethod.Local("a", "b")
        val plex = SignInMethod.Plex("t")
        assertEquals(SignInFailure.InvalidCredentials, failureFor(401, """{"message":"INVALID_CREDENTIALS"}""", local))
        assertEquals(SignInFailure.InvalidCredentials, failureFor(403, """{"message":"Access denied."}""", local))
        assertEquals(SignInFailure.AccessDenied, failureFor(403, """{"message":"Access denied."}""", plex))
        assertEquals(SignInFailure.InvalidCredentials, failureFor(403, """{"message":"INVALID_CREDENTIALS"}""", plex))
        assertEquals(SignInFailure.MethodDisabled, failureFor(500, """{"message":"Jellyfin login is disabled"}""", SignInMethod.Jellyfin("a", "b")))
        assertEquals(SignInFailure.InvalidCredentials, failureFor(500, """{"message":"INVALID_CREDENTIALS"}""", local))
        assertEquals(SignInFailure.Message("Boom"), failureFor(500, """{"message":"Boom"}""", local))
        assertEquals(SignInFailure.Message("Bad"), failureFor(400, """{"error":"Bad"}""", local))
    }

    @Test fun `signing in with the server down is an unreachable failure`() = runTest {
        auth.useServer(server.url("/"))
        server.close()
        assertEquals(SignInFailure.Unreachable, assertThrowsSuspend { auth.signIn(SignInMethod.Local("a", "b")) }.failure)
    }

    @Test fun `restoring with no saved server finds no user`() = runTest {
        assertNull(auth.restoreSession())
        assertEquals(0, server.requestCount)
    }

    @Test fun `restoring a session puts the saved cookie back`() = runTest {
        // Sign in, then start over with a fresh jar sharing only the backup store.
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        auth.signIn(SignInMethod.Local("a", "b"))
        server.takeRequest()

        val relaunched = AuthFixture(server.url("/"))
        relaunched.store.values.putAll(fixture.store.values)
        server.enqueue(json(USER_JSON))

        val user = relaunched.auth.restoreSession()

        assertEquals(3, user?.id)
        assertTrue(server.takeRequest().headers["Cookie"].orEmpty().contains("connect.sid=abc"))
    }

    @Test fun `an expired session is cleared`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        auth.signIn(SignInMethod.Local("a", "b"))
        server.enqueue(json("""{"message":"Unauthorized"}""", 401))

        assertNull(auth.restoreSession())
        assertNull(fixture.store.read("session"))
    }

    @Test fun `restoring while the server is down is an error not a sign out`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        auth.signIn(SignInMethod.Local("a", "b"))
        server.close()

        try {
            auth.restoreSession()
            error("Expected an IOException")
        } catch (_: java.io.IOException) {
        }
        assertNotNull(fixture.store.read("session"))
    }

    @Test fun `signing out clears the session even if the server doesn't answer`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        auth.signIn(SignInMethod.Local("a", "b"))
        server.close()

        auth.signOut()
        assertNull(fixture.store.read("session"))
    }

    @Test fun `forgetting the server clears the address`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(json("{}"))
        auth.forgetServer()
        assertNull(fixture.preferences.url)
        assertNull(auth.serverUrl)
    }

    @Test fun `switching to another server drops the old session`() = runTest {
        auth.useServer(server.url("/"))
        server.enqueue(sessionResponse())
        auth.signIn(SignInMethod.Local("a", "b"))

        auth.useServer(okhttp3.HttpUrl.Builder().scheme("http").host("other.example.com").build())
        assertNull(fixture.store.read("session"))
    }

    private fun sessionResponse() = MockResponse.Builder().code(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("Set-Cookie", "connect.sid=abc; Path=/; Max-Age=3600; HttpOnly")
        .body(USER_JSON).build()

    private suspend fun failure(block: suspend () -> Unit): ServerConnectionError =
        try {
            block()
            error("Expected a ServerConnectionException")
        } catch (error: ServerConnectionException) {
            error.error
        }

    private suspend fun assertThrowsSuspend(block: suspend () -> Unit): SignInException =
        try {
            block()
            error("Expected a SignInException")
        } catch (error: SignInException) {
            error
        }
}
