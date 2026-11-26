package com.nesoi.seerr.android.core

import com.nesoi.seerr.android.FakeSecureStore
import com.nesoi.seerr.android.core.net.SessionCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {
    private val server = "https://seerr.example.com/".toHttpUrl()
    // Cookie.parse reads the real clock for Max-Age, so the fake one starts there.
    private var now = System.currentTimeMillis()
    private val store = FakeSecureStore()
    private val jar = SessionCookieJar(store) { now }

    private fun session(url: okhttp3.HttpUrl = server, maxAge: Int = 3600) =
        Cookie.parse(url, "connect.sid=abc; Path=/; Max-Age=$maxAge; HttpOnly; Secure")!!

    @Test fun `a saved cookie is sent back to the same server`() {
        jar.saveFromResponse(server, listOf(session()))
        assertEquals(listOf("connect.sid"), jar.loadForRequest(server.resolve("/api/v1/auth/me")!!).map { it.name })
        assertTrue(jar.loadForRequest("https://other.example.com/".toHttpUrl()).isEmpty())
    }

    @Test fun `an expired cookie is dropped`() {
        jar.saveFromResponse(server, listOf(session(maxAge = 10)))
        now += 11_000
        assertTrue(jar.loadForRequest(server).isEmpty())
    }

    @Test fun `the session cookie survives a new jar after persist`() {
        jar.saveFromResponse(server, listOf(session()))
        jar.persist(server)

        val restarted = SessionCookieJar(store) { now }
        assertTrue(restarted.loadForRequest(server).isEmpty())
        restarted.restoreIfNeeded(server)
        assertEquals("abc", restarted.loadForRequest(server).single().value)
    }

    @Test fun `a backup for another server isn't restored`() {
        jar.saveFromResponse(server, listOf(session()))
        jar.persist(server)

        val restarted = SessionCookieJar(store) { now }
        restarted.restoreIfNeeded("https://different.example.com/".toHttpUrl())
        assertTrue(restarted.loadForRequest("https://different.example.com/".toHttpUrl()).isEmpty())
    }

    @Test fun `an expired backup isn't restored`() {
        jar.saveFromResponse(server, listOf(session(maxAge = 60)))
        jar.persist(server)
        now += 120_000

        val restarted = SessionCookieJar(store) { now }
        restarted.restoreIfNeeded(server)
        assertTrue(restarted.loadForRequest(server).isEmpty())
    }

    @Test fun `clear forgets the cookie and the backup`() {
        jar.saveFromResponse(server, listOf(session()))
        jar.persist(server)
        assertNotNull(store.read("session"))

        jar.clear()
        assertTrue(jar.loadForRequest(server).isEmpty())
        assertNull(store.read("session"))
    }

    @Test fun `a cookie with the same name replaces the old one`() {
        jar.saveFromResponse(server, listOf(session()))
        jar.saveFromResponse(server, listOf(Cookie.parse(server, "connect.sid=new; Path=/; Max-Age=3600")!!))
        assertEquals(listOf("new"), jar.loadForRequest(server).map { it.value })
    }
}
