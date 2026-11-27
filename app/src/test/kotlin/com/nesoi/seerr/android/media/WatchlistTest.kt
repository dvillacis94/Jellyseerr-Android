package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.net.ApiException
import com.nesoi.seerr.android.json
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchlistTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()
    private lateinit var store: WatchlistStore

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
        store = WatchlistStore(fixture.api)
    }

    @After fun stop() = server.close()

    private fun empty(code: Int) = MockResponse.Builder().code(code).build()

    @Test fun `adding posts the title and remembers it`() = runBlocking {
        server.enqueue(json("{}", 201))
        store.set(true, TitleType.Movie, 603, "The Matrix")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/watchlist", request.url.encodedPath)
        assertEquals("""{"tmdbId":603,"mediaType":"movie","title":"The Matrix"}""", request.body?.utf8())
        assertTrue(WatchlistStore.contains(store.overrides.value, TitleType.Movie, 603, loaded = false))
        assertEquals(1, store.revision.value)
    }

    @Test fun `removing deletes by id with the media type`() = runBlocking {
        server.enqueue(empty(204))
        store.set(false, TitleType.Series, 1396, "Breaking Bad")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/watchlist/1396", request.url.encodedPath)
        assertEquals("tv", request.url.queryParameter("mediaType"))
        assertFalse(WatchlistStore.contains(store.overrides.value, TitleType.Series, 1396, loaded = true))
    }

    @Test fun `already added or already removed counts as done`() = runBlocking {
        server.enqueue(json("""{"message":"dup"}""", 409))
        store.set(true, TitleType.Movie, 1, null)
        server.enqueue(empty(404))
        store.set(false, TitleType.Movie, 2, null)
        assertEquals(2, store.revision.value)
    }

    @Test fun `a real failure is thrown and changes nothing`() = runBlocking {
        server.enqueue(json("""{"message":"boom"}""", 500))
        assertThrows(ApiException::class.java) { runBlocking { store.set(true, TitleType.Movie, 1, null) } }
        assertEquals(0, store.revision.value)
        assertTrue(store.updating.value.isEmpty())
    }

    @Test fun `what the page said stands until the user changes it`() {
        assertTrue(WatchlistStore.contains(emptyMap(), TitleType.Movie, 1, loaded = true))
        assertFalse(WatchlistStore.contains(emptyMap(), TitleType.Movie, 1, loaded = null))
        // A movie and a series can share a TMDB id.
        val overrides = mapOf(WatchlistStore.key(TitleType.Movie, 1) to true)
        assertFalse(WatchlistStore.contains(overrides, TitleType.Series, 1, loaded = false))
    }

    @Test fun `a page of the watchlist gets artwork from each title and skips ones that are gone`() = runBlocking {
        val entries = """{"page":1,"totalPages":1,"totalResults":3,"results":[
            {"id":1,"tmdbId":603,"mediaType":"movie","title":"The Matrix"},
            {"id":2,"tmdbId":1396,"mediaType":"tv","title":"Breaking Bad"},
            {"id":3,"tmdbId":999,"mediaType":"movie","title":"Gone"}]}"""
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v1/discover/watchlist" -> json(entries)
                "/api/v1/movie/603" -> json("""{"id":603,"title":"The Matrix","posterPath":"/m.jpg","releaseDate":"1999-03-30"}""")
                "/api/v1/tv/1396" -> json("""{"id":1396,"name":"Breaking Bad","posterPath":"/b.jpg","firstAirDate":"2008-01-20"}""")
                else -> empty(404)
            }
        }

        val page = fixture.api.watchlistPage(1)

        assertEquals(listOf("The Matrix", "Breaking Bad"), page.results.map { it.title })
        assertEquals(listOf("/m.jpg", "/b.jpg"), page.results.map { it.posterPath })
        assertTrue(page.results[1] is MediaItem.Series)
    }
}
