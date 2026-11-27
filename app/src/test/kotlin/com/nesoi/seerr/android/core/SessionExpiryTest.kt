package com.nesoi.seerr.android.core

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.json
import com.nesoi.seerr.android.core.net.ApiException
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.MediaPage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SessionExpiryTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()
    private var expired = 0

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
        fixture.auth.onSessionExpired = { expired++ }
    }

    @After fun stop() = server.close()

    @Test fun `a 401 on a call that needs a session reports it and drops the session`() = runTest {
        fixture.store.write("session", "saved")
        server.enqueue(json("""{"message":"nope"}""", code = 401))

        try {
            fixture.api.fetch<MediaPage>(Routes.discoverTrending(1))
            fail("expected a 401")
        } catch (_: ApiException.Unauthorized) {
        }

        assertEquals(1, expired)
        assertTrue(fixture.store.values["session"] == null)
    }

    @Test fun `a wrong password isn't a lost session`() = runTest {
        server.enqueue(json("""{"message":"INVALID_CREDENTIALS"}""", code = 401))
        try {
            fixture.api.fetch<User>(Routes.loginLocal("a@b.c", "x"))
            fail("expected a 401")
        } catch (_: ApiException.Unauthorized) {
        }
        assertEquals(0, expired)
    }

    @Test fun `checking for a session at launch isn't a lost session`() = runTest {
        server.enqueue(json("{}", code = 401))
        try {
            fixture.api.fetch<User>(Routes.authMe)
            fail("expected a 401")
        } catch (_: ApiException.Unauthorized) {
        }
        assertEquals(0, expired)
    }

    @Test fun `a route's query is sent`() = runTest {
        server.enqueue(json("""{"page":2,"totalPages":2,"results":[]}"""))
        fixture.api.fetch<MediaPage>(Routes.search("the matrix & more", 2))
        val request = server.takeRequest()
        assertEquals("/api/v1/search", request.url.encodedPath)
        assertEquals("the matrix & more", request.url.queryParameter("query"))
        assertEquals("2", request.url.queryParameter("page"))
    }
}
