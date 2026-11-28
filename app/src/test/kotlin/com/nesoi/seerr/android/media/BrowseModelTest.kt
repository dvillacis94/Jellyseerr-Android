package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseModelTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private fun page(number: Int, of: Int, vararg items: String) =
        json("""{"page":$number,"totalPages":$of,"totalResults":99,"results":[${items.joinToString(",")}]}""")

    private fun movie(id: Int) = """{"id":$id,"mediaType":"movie","title":"Movie $id"}"""
    private fun series(id: Int) = """{"id":$id,"mediaType":"tv","name":"Series $id"}"""
    private fun person(id: Int) = """{"id":$id,"mediaType":"person","name":"Person $id"}"""

    private suspend fun PagedLoader.awaitLoaded() =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first { it.hasLoaded } } }

    @Test fun `the grids browse their own type`() = runTest {
        server.enqueue(page(1, 1, movie(1)))
        server.enqueue(page(1, 1, series(2)))

        BrowseModel(this, fixture.api, TitleType.Movie).grid.loadIfNeeded()
        BrowseModel(this, fixture.api, TitleType.Series).grid.loadIfNeeded()

        assertEquals("/api/v1/discover/movies", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/discover/tv", server.takeRequest().url.encodedPath)
    }

    @Test fun `movie search drops everything but movies`() = runTest {
        server.enqueue(page(1, 1, movie(1), series(2), person(3), movie(4)))
        val model = BrowseModel(this, fixture.api, TitleType.Movie)

        model.search.setQuery("x")
        model.search.submit()
        runCurrent()
        val loader = model.search.results.value!!
        loader.awaitLoaded()

        assertEquals(listOf(1, 4), loader.state.value.items.map { it.id })
    }

    @Test fun `a page with none of the type moves on to the next`() = runTest {
        server.enqueue(page(1, 3, movie(1), person(2)))
        server.enqueue(page(2, 3, movie(3)))
        server.enqueue(page(3, 3, series(4), series(5)))
        val model = BrowseModel(this, fixture.api, TitleType.Series)

        model.search.setQuery("x")
        model.search.submit()
        runCurrent()
        val loader = model.search.results.value!!
        loader.awaitLoaded()

        assertEquals(3, server.requestCount)
        assertEquals(listOf(4, 5), loader.state.value.items.map { it.id })
        assertEquals(listOf("1", "2", "3"), List(3) { server.takeRequest().url.queryParameter("page") })
    }

    @Test fun `it stops looking after a few empty pages`() = runTest {
        repeat(10) { server.enqueue(page(it + 1, 10, person(it))) }
        val model = BrowseModel(this, fixture.api, TitleType.Movie)

        model.search.setQuery("x")
        model.search.submit()
        runCurrent()
        val loader = model.search.results.value!!
        loader.awaitLoaded()

        assertEquals(5, server.requestCount)
        assertEquals(emptyList<MediaItem>(), loader.state.value.items)
        assertFalse(loader.state.value.isFailed)
    }
}
