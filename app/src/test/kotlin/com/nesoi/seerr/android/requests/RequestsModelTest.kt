package com.nesoi.seerr.android.requests

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.seerrJson
import com.nesoi.seerr.android.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestsModelTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()
    private val paths = mutableListOf<String>()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private fun user(vararg permissions: Int) = User(id = 7, permissions = permissions.fold(0) { a, b -> a or b })

    private fun model(user: User) = RequestsModel(CoroutineScope(Dispatchers.Default), fixture.api, user)

    private fun request(id: Int, type: String = "movie", status: Int = 1, mediaStatus: Int = 2, by: Int = 7, is4k: Boolean = false) =
        """{"id":$id,"status":$status,"type":"$type","is4k":$is4k,"createdAt":"2026-11-27T10:00:00.000Z",
           "media":{"tmdbId":${id * 100},"mediaType":"$type","status":$mediaStatus,"status4k":1},
           "requestedBy":{"id":$by,"displayName":"User $by"},"seasons":[{"seasonNumber":2}]}"""

    private fun page(vararg requests: String, total: Int = requests.size) =
        """{"pageInfo":{"pages":1,"page":1,"results":$total},"results":[${requests.joinToString(",")}]}"""

    private fun serve(list: String, onOther: (RecordedRequest) -> MockResponse = { json("{}") }) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.method + " " + request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                val path = request.url.encodedPath
                return when {
                    request.method == "GET" && path.endsWith("/api/v1/request") -> json(list)
                    path.contains("/api/v1/movie/") -> json("""{"id":1,"title":"A Movie","posterPath":"/p.jpg","releaseDate":"2019-04-26"}""")
                    path.contains("/api/v1/tv/") -> json("""{"id":1,"name":"A Show","firstAirDate":"2011-04-17"}""")
                    else -> onOther(request)
                }
            }
        }
    }

    private suspend fun RequestsModel.rows(): List<RequestRow> = withContext(Dispatchers.Default) {
        val list = list.value
        list.loadIfNeeded()
        withTimeout(5_000) { list.state.first { it.hasLoaded } }.items
    }

    @Test fun `a request is shown with the title it is for`() = runBlocking {
        serve(page(request(1), request(2, type = "tv")))
        val rows = model(user(Permission.REQUEST)).rows()

        assertEquals(listOf("A Movie", "A Show"), rows.map { it.title })
        assertEquals(listOf("2019", "2011"), rows.map { it.year })
        assertEquals("/p.jpg", rows[0].posterPath)
        assertEquals("User 7", rows[0].request.requestedBy?.name)
    }

    @Test fun `a title that can't be fetched leaves the request in the list`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.url.encodedPath.endsWith("/api/v1/request")) json(page(request(1)))
                else MockResponse.Builder().code(404).body("{}").build()
        }
        val rows = model(user(Permission.REQUEST)).rows()
        assertEquals(1, rows.size)
        assertNull(rows[0].title)
    }

    @Test fun `the filter and sort go to the server, and changing one starts a new list`() = runBlocking {
        serve(page(request(1)))
        val m = model(user(Permission.REQUEST))
        m.rows()
        val first = m.list.value
        m.setFilter(RequestFilter.Pending)
        m.setSort(RequestSort.Modified)
        assertTrue(first !== m.list.value)
        m.rows()

        val query = paths.last { it.startsWith("GET /api/v1/request?") }
        assertTrue(query, "filter=pending" in query && "sort=modified" in query && "take=20" in query && "skip=0" in query)
    }

    @Test fun `later pages continue from how many rows came back`() = runBlocking {
        serve(page(request(1), request(2), total = 3))
        val m = model(user(Permission.REQUEST))
        val list = m.rows().let { m.list.value }
        assertTrue(list.state.value.hasMore)
        withContext(Dispatchers.Default) { list.loadMoreIfNeeded(1) }

        assertTrue(paths.any { it.startsWith("GET /api/v1/request?") && "skip=2" in it })
    }

    @Test fun `what each person can do with a request`() = runBlocking {
        serve(page(request(1, status = 1), request(2, status = 4), request(3, status = 2), request(4, status = 1, by = 99)))
        val manager = model(user(Permission.MANAGE_REQUESTS))
        val rows = manager.rows()
        assertEquals(listOf(RequestAction.Approve, RequestAction.Decline, RequestAction.Delete), manager.actionsFor(rows[0]))
        assertEquals(listOf(RequestAction.Retry, RequestAction.Delete), manager.actionsFor(rows[1]))
        assertEquals(listOf(RequestAction.Delete), manager.actionsFor(rows[2]))

        val member = model(user(Permission.REQUEST))
        assertEquals(listOf(RequestAction.Delete), member.actionsFor(rows[0]))
        assertEquals(emptyList<RequestAction>(), member.actionsFor(rows[1]))
        assertEquals(emptyList<RequestAction>(), member.actionsFor(rows[2]))
        assertEquals(emptyList<RequestAction>(), member.actionsFor(rows[3]))
    }

    @Test fun `an approved request is processing until it is in the library`() {
        fun display(status: Int, media: Int) =
            seerrJson.decodeFromString<MediaRequest>(request(1, status = status, mediaStatus = media)).display
        assertEquals(RequestDisplay.Pending, display(1, 2))
        assertEquals(RequestDisplay.Processing, display(2, 3))
        assertEquals(RequestDisplay.Available, display(2, 5))
        assertEquals(RequestDisplay.PartiallyAvailable, display(2, 4))
        assertEquals(RequestDisplay.Declined, display(3, 1))
        assertEquals(RequestDisplay.Failed, display(4, 1))
        assertEquals(RequestDisplay.Completed, display(5, 5))
        assertEquals(RequestDisplay.Deleted, display(2, 7))
    }

    @Test fun `4K requests are judged by the 4K status`() {
        val r = seerrJson.decodeFromString<MediaRequest>(request(1, status = 2, mediaStatus = 5, is4k = true))
        assertEquals(RequestDisplay.Processing, r.display)
    }

    @Test fun `approving posts to the request and reloads the list`() = runBlocking {
        serve(page(request(1))) { json("{}") }
        val m = model(user(Permission.MANAGE_REQUESTS))
        val row = m.rows().single()
        val done = CompletableDeferred<Unit>()
        m.perform(RequestAction.Approve, row) { done.complete(Unit) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { done.await() } }

        assertTrue(paths.toString(), "POST /api/v1/request/1/approve" in paths)
        assertTrue(m.busy.value.isEmpty())
        assertEquals(2, paths.count { it.startsWith("GET /api/v1/request?") })
    }

    @Test fun `declining, retrying and deleting use their own routes`() = runBlocking {
        serve(page(request(1))) { MockResponse.Builder().code(204).build() }
        val m = model(user(Permission.MANAGE_REQUESTS))
        val row = m.rows().single()
        for (action in listOf(RequestAction.Decline, RequestAction.Retry, RequestAction.Delete)) {
            val done = CompletableDeferred<Unit>()
            m.perform(action, row) { done.complete(Unit) }
            withContext(Dispatchers.Default) { withTimeout(5_000) { done.await() } }
        }
        assertTrue("POST /api/v1/request/1/decline" in paths)
        assertTrue("POST /api/v1/request/1/retry" in paths)
        assertTrue("DELETE /api/v1/request/1" in paths)
    }

    @Test fun `a refused action is reported and the list is left alone`() = runBlocking {
        serve(page(request(1))) { MockResponse.Builder().code(409).body("""{"message":"Only pending requests can be approved or declined."}""").build() }
        val m = model(user(Permission.MANAGE_REQUESTS))
        val row = m.rows().single()
        m.perform(RequestAction.Approve, row)
        val error = withContext(Dispatchers.Default) { withTimeout(5_000) { m.actionError.first { it != null } } }

        assertEquals("Only pending requests can be approved or declined.", error?.message)
        assertEquals(1, paths.count { it.startsWith("GET /api/v1/request?") })
        m.dismissActionError()
        assertNull(m.actionError.value)
        assertFalse(row.id in m.busy.value)
    }

    @Test fun `deleting without permission does not sign the user out`() = runBlocking {
        var signedOut = false
        fixture.api.onUnauthorized = { signedOut = true }
        serve(page(request(1))) { MockResponse.Builder().code(401).body("""{"message":"You do not have permission to delete this request."}""").build() }
        val m = model(user(Permission.REQUEST))
        val row = m.rows().single()
        m.perform(RequestAction.Delete, row)
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.actionError.first { it != null } } }
        assertFalse(signedOut)
    }
}
