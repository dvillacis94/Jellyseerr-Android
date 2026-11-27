package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchModelTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    /** The request goes out on a real network thread, so this waits in real time. */
    private suspend fun SearchModel.awaitLoaded() {
        val loader = results.value ?: error("no search started")
        withContext(Dispatchers.Default) { withTimeout(5_000) { loader.state.first { it.hasLoaded } } }
    }

    private val results = """{"page":1,"totalPages":1,"totalResults":1,"results":[{"id":1,"mediaType":"movie","title":"Dune"}]}"""

    @Test fun `typing waits, then searches once for the latest text`() = runTest {
        val model = SearchModel(this, fixture.api, debounceMillis = 350)
        server.enqueue(json(results))

        model.setQuery("d")
        advanceTimeBy(200)
        model.setQuery("du")
        model.setQuery("dune")
        advanceTimeBy(349)
        assertEquals(0, server.requestCount)
        advanceTimeBy(2)
        runCurrent()
        model.awaitLoaded()

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/v1/search", request.url.encodedPath)
        assertEquals("dune", request.url.queryParameter("query"))
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals(listOf("Dune"), model.results.value?.state?.value?.items?.map { it.title })
    }

    @Test fun `clearing the text clears the results and cancels a pending search`() = runTest {
        val model = SearchModel(this, fixture.api)
        server.enqueue(json(results))
        model.setQuery("dune")
        advanceUntilIdle()
        model.awaitLoaded()
        assertNotNull(model.results.value)

        model.setQuery("  ")
        assertNull(model.results.value)

        model.setQuery("x")
        model.setQuery("")
        advanceUntilIdle()
        assertEquals(1, server.requestCount)
    }

    @Test fun `submitting searches at once`() = runTest {
        val model = SearchModel(this, fixture.api, debounceMillis = 10_000)
        server.enqueue(json(results))
        model.setQuery("dune")
        model.submit()
        runCurrent()
        model.awaitLoaded()
        assertEquals(1, server.requestCount)
    }

}
