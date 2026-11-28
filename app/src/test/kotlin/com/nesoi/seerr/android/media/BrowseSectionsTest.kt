package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseSectionsTest {
    private val genres = listOf(Genre(28, "Action"), Genre(35, "Comedy"))
    private fun title(list: BrowseList) = when (val t = list.title) {
        is SectionTitle.Res -> "res:${t.id}"
        is SectionTitle.Name -> t.text
    }

    // The sections themselves

    @Test fun `without filters the sections are saved filters, then popular, upcoming, top rated, then a section per genre`() {
        val saved = listOf(SavedFilter("1", "Anime", DiscoverFilters(genres = setOf(16))))
        val lists = browseLists(TitleType.Movie, genres, DiscoverFilters(), saved)
        assertEquals(
            listOf("Anime", "res:${R.string.list_popular_movies}", "res:${R.string.list_upcoming_movies}", "res:${R.string.browse_top_rated_movies}", "Action", "Comedy"),
            lists.map(::title),
        )
        assertEquals(BrowseList.Kind.Upcoming, lists[2].kind)
        assertEquals(setOf(28), lists[4].filters.genres)
    }

    @Test fun `with filters the sections are top results and a filtered top rated, with the genres narrowing them`() {
        val filters = DiscoverFilters(yearFrom = 1990, minScore = 7)
        val lists = browseLists(TitleType.Series, genres, filters, saved = listOf(SavedFilter("1", "Anime", DiscoverFilters(genres = setOf(16)))))
        assertEquals(
            listOf("res:${R.string.browse_top_results}", "res:${R.string.browse_top_rated_series}", "Action", "Comedy"),
            lists.map(::title),
        )
        // Top results is in the order the filters chose, and top rated by score with enough votes.
        assertEquals(filters, lists[0].filters)
        assertEquals("vote_average.desc", lists[1].filters.sort)
        assertEquals(TOP_RATED_MINIMUM_VOTES, lists[1].filters.voteCountMin)
        assertEquals(1990, lists[1].filters.yearFrom)
        assertEquals(setOf(28), lists[2].filters.genres)
        assertEquals(1990, lists[2].filters.yearFrom)
    }

    @Test fun `picking genres drops the genre sections`() {
        val lists = browseLists(TitleType.Movie, genres, DiscoverFilters(genres = setOf(28)), emptyList())
        assertEquals(listOf("res:${R.string.browse_top_results}", "res:${R.string.browse_top_rated_movies}"), lists.map(::title))
    }

    @Test fun `the same section twice is shown once`() {
        val same = DiscoverFilters(genres = setOf(16))
        val saved = listOf(SavedFilter("1", "Anime", same), SavedFilter("2", "Anime", same))
        assertEquals(1, browseLists(TitleType.Movie, emptyList(), DiscoverFilters(), saved).count { title(it) == "Anime" })
    }

    @Test fun `each section asks the right list`() {
        val upcoming = BrowseList(SectionTitle.Name("x"), BrowseList.Kind.Upcoming, DiscoverFilters())
        assertEquals("/discover/movies/upcoming", upcoming.route(TitleType.Movie, 2).path)
        assertEquals("/discover/tv/upcoming", upcoming.route(TitleType.Series, 1).path)
        val top = BrowseList(SectionTitle.Name("x"), BrowseList.Kind.Discover, DiscoverFilters(sort = "vote_average.desc", voteCountMin = 300))
        val route = top.route(TitleType.Movie, 3)
        assertEquals("/discover/movies", route.path)
        assertTrue("sortBy" to "vote_average.desc" in route.query)
        assertTrue("voteCountGte" to "300" in route.query)
        assertTrue("page" to "3" in route.query)
        assertEquals("/discover/tv", top.route(TitleType.Series, 1).path)
    }

    @Test fun `a section's key is the same for the same list and differs between lists`() {
        val a = BrowseList(SectionTitle.Name("A"), BrowseList.Kind.Discover, DiscoverFilters(genres = setOf(1)))
        assertEquals(a.key, a.copy().key)
        assertFalse(a.key == a.copy(filters = DiscoverFilters(genres = setOf(2))).key)
        assertTrue(Regex("^-?[0-9a-f]+$").matches(a.key))
    }

    @Test fun `top rated votes aren't counted as a filter the user chose`() {
        assertEquals(0, DiscoverFilters(voteCountMin = 300).activeCount())
        assertEquals(listOf("voteCountGte" to "300"), DiscoverFilters(voteCountMin = 300).queryItems(TitleType.Movie))
    }

    // The model

    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    @Test fun `the sections appear once what the tab remembered is read, and follow the filters`() = runTest {
        val storage = MemoryPreferences()
        val first = BrowseModel(backgroundScope, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        first.save("Mine", DiscoverFilters(minScore = 8))
        runCurrent()
        first.reset()
        runCurrent()
        assertEquals(listOf("Mine", "res:${R.string.list_popular_movies}", "res:${R.string.list_upcoming_movies}", "res:${R.string.browse_top_rated_movies}"), first.shelves.value.map { title(it.list) })

        first.apply(DiscoverFilters(minScore = 6))
        runCurrent()
        assertEquals(listOf("res:${R.string.browse_top_results}", "res:${R.string.browse_top_rated_movies}"), first.shelves.value.map { title(it.list) })

        // A tab opened later starts from what was remembered.
        val second = BrowseModel(backgroundScope, fixture.api, TitleType.Movie, storage, "k")
        runCurrent()
        assertEquals(listOf("res:${R.string.browse_top_results}", "res:${R.string.browse_top_rated_movies}"), second.shelves.value.map { title(it.list) })
        assertEquals(0, server.requestCount)
    }

    @Test fun `genre sections arrive with the genres and a section that is the same keeps what it loaded`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v1/genres/movie" -> json("""[{"id":28,"name":"Action"}]""")
                else -> json("""{"page":1,"totalPages":1,"totalResults":1,"results":[{"id":1,"mediaType":"movie","title":"M"}]}""")
            }
        }
        val model = BrowseModel(backgroundScope, fixture.api, TitleType.Movie)
        runCurrent()
        val popular = model.shelves.value.first()
        popular.loader.loadIfNeeded()
        assertEquals(1, popular.loader.state.value.items.size)

        model.options.loadGenres()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.withTimeout(5_000) { model.options.genres.first { it != null } } }
        runCurrent()

        assertEquals("Action", title(model.shelves.value.last().list))
        assertSame(popular, model.shelves.value.first())
        // "See all" finds a section by its key.
        val action = model.shelves.value.last()
        assertSame(action, model.section(action.list.key))
        assertNull(model.section("nope"))
    }

    @Test fun `every section empty is reported only once all have loaded`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json("""{"page":1,"totalPages":1,"totalResults":0,"results":[]}""")
        }
        val model = BrowseModel(backgroundScope, fixture.api, TitleType.Movie)
        runCurrent()
        model.apply(DiscoverFilters(minScore = 9))
        runCurrent()
        assertFalse(model.sectionsEmpty.first())
        model.shelves.value.forEach { it.loader.loadIfNeeded() }
        assertTrue(model.sectionsEmpty.first())
        assertNotNull(model.shelves.value.firstOrNull())
    }
}
