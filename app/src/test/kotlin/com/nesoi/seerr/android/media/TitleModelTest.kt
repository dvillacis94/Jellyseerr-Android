package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TitleModelTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private val list = """{"page":1,"totalPages":1,"totalResults":1,"results":[{"id":7,"mediaType":"movie","title":"Related"}]}"""

    private fun serve(details: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/recommendations") || request.url.encodedPath.endsWith("/similar") -> json(list)
                else -> details
            }
        }
    }

    private suspend fun TitleModel.awaitDone() = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            state.first { it !is DetailState.Loading }
            recommendations.state.first { it.hasLoaded }
            similar.state.first { it.hasLoaded }
        }
    }

    @Test fun `a movie page loads its details and both related lists`() = runBlocking {
        serve(json("""{"id":603,"title":"The Matrix"}"""))
        val model = TitleModel(this, fixture.api, TitleType.Movie, 603)

        model.load()
        model.awaitDone()

        val loaded = model.state.value as DetailState.Loaded
        assertEquals("The Matrix", loaded.value.title)
        assertEquals(listOf("Related"), model.recommendations.state.value.items.map { it.title })
        assertEquals(listOf("Related"), model.similar.state.value.items.map { it.title })
        val paths = List(server.requestCount) { server.takeRequest().url.encodedPath }.toSet()
        assertEquals(
            setOf("/api/v1/movie/603", "/api/v1/movie/603/recommendations", "/api/v1/movie/603/similar"),
            paths,
        )
    }

    @Test fun `a series page asks for tv`() = runBlocking {
        serve(json("""{"id":1396,"name":"Breaking Bad"}"""))
        val model = TitleModel(this, fixture.api, TitleType.Series, 1396)

        model.load()
        model.awaitDone()

        assertEquals("Breaking Bad", (model.state.value as DetailState.Loaded).value.title)
        val paths = List(server.requestCount) { server.takeRequest().url.encodedPath }.toSet()
        assertTrue("/api/v1/tv/1396/similar" in paths)
    }

    @Test fun `a failed page says so, and trying again can work`() = runBlocking {
        serve(json("""{"message":"nope"}""", 500))
        val model = TitleModel(this, fixture.api, TitleType.Movie, 1)
        model.load()
        model.awaitDone()
        assertTrue(model.state.value is DetailState.Failed)

        serve(json("""{"id":1,"title":"Back"}"""))
        model.retry()
        withContext(Dispatchers.Default) { withTimeout(5_000) { model.state.first { it is DetailState.Loaded } } }
        assertEquals("Back", (model.state.value as DetailState.Loaded).value.title)
    }

    @Test fun `a refresh that fails keeps the page that is showing`() = runBlocking {
        serve(json("""{"id":1,"title":"Kept"}"""))
        val model = TitleModel(this, fixture.api, TitleType.Movie, 1)
        model.load()
        model.awaitDone()

        serve(json("""{"message":"down"}""", 500))
        model.refresh()

        assertEquals("Kept", (model.state.value as DetailState.Loaded).value.title)
    }

    @Test fun `a collection loads its movies`() = runBlocking {
        server.enqueue(json("""{"id":5,"name":"Saga","parts":[{"id":1,"title":"One"},{"id":2,"title":"Two"}]}"""))
        val model = CollectionModel(this, fixture.api, 5)
        model.load()
        withContext(Dispatchers.Default) { withTimeout(5_000) { model.state.first { it !is DetailState.Loading } } }
        assertEquals(listOf("One", "Two"), (model.state.value as DetailState.Loaded).value.parts.map { it.title })
        assertEquals("/api/v1/collection/5", server.takeRequest().url.encodedPath)
    }
}
