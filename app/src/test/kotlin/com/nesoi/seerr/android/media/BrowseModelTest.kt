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
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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

        BrowseModel(this, fixture.api, TitleType.Movie).grid.value.loadIfNeeded()
        BrowseModel(this, fixture.api, TitleType.Series).grid.value.loadIfNeeded()

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


    @Test fun `applying filters reloads the grid with them`() = runTest {
        server.enqueue(page(1, 1, movie(1)))
        server.enqueue(page(1, 1, movie(2)))
        val model = BrowseModel(this, fixture.api, TitleType.Movie)
        runCurrent()
        model.grid.value.loadIfNeeded()
        server.takeRequest()

        model.apply(DiscoverFilters(genres = setOf(28), minScore = 7))
        model.grid.value.loadIfNeeded()

        val request = server.takeRequest()
        assertEquals("28", request.url.queryParameter("genre"))
        assertEquals("7", request.url.queryParameter("voteAverageGte"))
        assertEquals(listOf(2), model.grid.value.state.value.items.map { it.id })
    }

    @Test fun `saved filters survive a new model and keep the last used ones`() = runTest {
        val storage = MemoryPreferences()
        val first = BrowseModel(this, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        val scifi = DiscoverFilters(genres = setOf(878), yearFrom = 1990, yearTo = 1999)
        first.save("90s sci-fi", scifi)
        runCurrent()
        first.apply(DiscoverFilters(minScore = 8))
        runCurrent()

        val second = BrowseModel(this, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        assertEquals(listOf("90s sci-fi"), second.saved.value.map { it.name })
        assertEquals(scifi, second.saved.value.single().filters)
        assertEquals(DiscoverFilters(minScore = 8), second.filters.value)
    }

    @Test fun `the first load uses the restored filters`() = runTest {
        val storage = MemoryPreferences()
        val first = BrowseModel(this, fixture.api, TitleType.Series, storage, "k")
        runCurrent()
        first.apply(DiscoverFilters(statuses = setOf(0)))
        runCurrent()

        server.enqueue(page(1, 1, series(1)))
        val second = BrowseModel(this, fixture.api, TitleType.Series, storage, "k")
        second.grid.value.loadIfNeeded()

        assertEquals("0", server.takeRequest().url.queryParameter("status"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `rename and delete change the saved list`() = runTest {
        val storage = MemoryPreferences()
        val model = BrowseModel(this, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        model.save("A", DiscoverFilters(minScore = 5))
        model.save("  ", DiscoverFilters(minScore = 6))
        model.save("B", DiscoverFilters(minScore = 7))
        runCurrent()
        assertEquals(listOf("A", "B"), model.saved.value.map { it.name })

        model.rename(model.saved.value[0].id, "Good ones")
        model.delete(model.saved.value[1].id)
        runCurrent()

        assertEquals(listOf("Good ones"), model.saved.value.map { it.name })
        val reopened = BrowseModel(this, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        assertEquals(listOf("Good ones"), reopened.saved.value.map { it.name })
    }

    @Test fun `separate keys keep each tab and account apart`() = runTest {
        val storage = MemoryPreferences()
        val movies = BrowseModel(this, fixture.api, TitleType.Movie, storage, "a.movie")
        runCurrent()
        movies.save("Mine", DiscoverFilters(minScore = 5))
        runCurrent()

        val other = BrowseModel(this, fixture.api, TitleType.Movie, storage, "b.movie")
        runCurrent()
        assertEquals(emptyList<SavedFilter>(), other.saved.value)
    }

    @Test fun `the options load once and a region's services are kept`() = runTest {
        // The three lists are asked for at the same time, so answer by path, not by order.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.url.encodedPath) {
                "/api/v1/genres/movie" -> json("""[{"id":28,"name":"Action"},{"id":12,"name":"Adventure"}]""")
                "/api/v1/languages" -> json("""[{"iso_639_1":"ja","english_name":"Japanese","name":"日本語"}]""")
                "/api/v1/regions" -> json("""[{"iso_3166_1":"US","english_name":"United States"}]""")
                "/api/v1/watchproviders/movies" -> json("""[{"id":8,"name":"Netflix","logoPath":"/n.jpg","displayPriority":1}]""")
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val options = FilterOptions(this, fixture.api, TitleType.Movie)

        options.load()
        withContext(Dispatchers.Default) { withTimeout(5_000) { options.regions.first { it != null } } }
        withContext(Dispatchers.Default) { withTimeout(5_000) { options.genres.first { it != null } } }
        withContext(Dispatchers.Default) { withTimeout(5_000) { options.languages.first { it != null } } }

        assertEquals(listOf("Action", "Adventure"), options.genres.value?.map { it.name })
        assertEquals("ja", options.languages.value?.single()?.code)
        assertEquals("US", options.regions.value?.single()?.code)
        val paths = List(3) { server.takeRequest().url.encodedPath }.sorted()
        assertEquals(listOf("/api/v1/genres/movie", "/api/v1/languages", "/api/v1/regions"), paths)

        options.loadProviders("US")
        options.loadProviders("US")
        withContext(Dispatchers.Default) { withTimeout(5_000) { options.providers.first { "US" in it } } }
        val request = server.takeRequest()
        assertEquals("/api/v1/watchproviders/movies", request.url.encodedPath)
        assertEquals("US", request.url.queryParameter("watchRegion"))
        assertEquals(4, server.requestCount)
    }
}
