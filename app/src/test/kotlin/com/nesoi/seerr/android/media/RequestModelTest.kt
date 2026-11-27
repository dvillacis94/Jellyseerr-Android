package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestModelTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private fun user(vararg permissions: Int) = User(id = 7, permissions = permissions.fold(0) { a, b -> a or b })
    private val everything = PublicSettings(initialized = true, movie4kEnabled = true, series4kEnabled = true)

    private val movie = MovieDetails(id = 603, title = "The Matrix")

    private fun series(info: MediaInfo? = null, seasons: Int = 3) = TvDetails(
        id = 1399,
        title = "Show",
        seasons = (0..seasons).map { TvSeason(it, "S$it", 8) },
        mediaInfo = info,
    )

    private fun model(details: TitleDetails, user: User = user(Permission.REQUEST), settings: PublicSettings? = everything) =
        RequestModel(kotlinx.coroutines.CoroutineScope(Dispatchers.Default), fixture.api, user, details, settings)

    @Test fun `permissions follow Seerr's rules`() {
        assertTrue(user(Permission.REQUEST).canRequest(TitleType.Movie, false))
        assertFalse(user(Permission.REQUEST).canRequest(TitleType.Movie, true))
        assertTrue(user(Permission.REQUEST_TV).canRequest(TitleType.Series, false))
        assertFalse(user(Permission.REQUEST_TV).canRequest(TitleType.Movie, false))
        assertTrue(user(Permission.REQUEST_4K_MOVIE).canRequest(TitleType.Movie, true))
        assertFalse(user(Permission.REQUEST_4K_MOVIE).canRequest(TitleType.Series, true))
        assertTrue(user(Permission.ADMIN).canRequest(TitleType.Series, true))
        assertFalse(user().canRequest(TitleType.Movie, false))
    }

    @Test fun `a movie is offered until it is requested or in the library`() {
        val u = user(Permission.REQUEST)
        assertTrue(movie.canBeRequestedBy(u, everything))
        assertTrue(movie.copy(mediaInfo = MediaInfo(status = 1)).canBeRequestedBy(u, everything))
        assertTrue(movie.copy(mediaInfo = MediaInfo(status = 7)).canBeRequestedBy(u, everything))
        assertFalse(movie.copy(mediaInfo = MediaInfo(status = 2)).canBeRequestedBy(u, everything))
        assertFalse(movie.copy(mediaInfo = MediaInfo(status = 5)).canBeRequestedBy(u, everything))
        assertFalse(movie.copy(mediaInfo = MediaInfo(status = 6)).canBeRequestedBy(u, everything))
        assertFalse(movie.canBeRequestedBy(user(), everything))
    }

    @Test fun `4K is offered only when the server and the user allow it`() {
        val available = movie.copy(mediaInfo = MediaInfo(status = 5))
        assertFalse(available.canBeRequestedBy(user(Permission.REQUEST), everything))
        assertTrue(available.canBeRequestedBy(user(Permission.REQUEST, Permission.REQUEST_4K), everything))
        assertFalse(available.canBeRequestedBy(user(Permission.REQUEST, Permission.REQUEST_4K), PublicSettings(initialized = true)))
        assertFalse(available.canBeRequestedBy(user(Permission.REQUEST, Permission.REQUEST_4K), null))
    }

    @Test fun `the sheet starts on 4K only when standard is no longer open`() {
        val available = movie.copy(mediaInfo = MediaInfo(status = 5))
        val u = user(Permission.REQUEST, Permission.REQUEST_4K)
        assertTrue(model(available, u).draft.value.is4k)
        assertFalse(model(available, u).offersBoth)
        assertFalse(model(movie, u).draft.value.is4k)
        assertTrue(model(movie, u).offersBoth)
    }

    @Test fun `seasons already requested or in the library can't be picked, specials stay hidden`() {
        val info = MediaInfo(
            seasons = listOf(SeasonInfo(1, status = 5), SeasonInfo(2, status = 3)),
            requests = listOf(RequestInfo(1, status = 1, seasons = listOf(RequestedSeason(3)))),
        )
        val choices = series(info).seasonChoices(false, everything).associate { it.season.seasonNumber to it.availability }
        assertEquals(setOf(1, 2, 3), choices.keys)
        assertEquals(SeasonAvailability.Available, choices[1])
        assertEquals(SeasonAvailability.Requested, choices[2])
        assertEquals(SeasonAvailability.Requested, choices[3])
        assertFalse(series(info).requestable(false, everything))
    }

    @Test fun `a declined request frees its seasons, and 4K is judged apart`() {
        val info = MediaInfo(
            requests = listOf(
                RequestInfo(1, status = 3, seasons = listOf(RequestedSeason(1))),
                RequestInfo(2, status = 1, is4k = true, seasons = listOf(RequestedSeason(2))),
            ),
        )
        val standard = series(info).seasonChoices(false, everything).associate { it.season.seasonNumber to it.availability }
        assertEquals(SeasonAvailability.Requestable, standard[1])
        assertEquals(SeasonAvailability.Requestable, standard[2])
        val fourK = series(info).seasonChoices(true, everything).associate { it.season.seasonNumber to it.availability }
        assertEquals(SeasonAvailability.Requested, fourK[2])
    }

    @Test fun `specials appear when the server allows them`() {
        val settings = everything.copy(enableSpecialEpisodes = true)
        assertEquals(listOf(0, 1, 2, 3), series().seasonChoices(false, settings).map { it.season.seasonNumber })
    }

    @Test fun `picking seasons, and everything when partial requests are off`() {
        val m = model(series())
        assertTrue(m.draft.value.seasons.isEmpty())
        assertEquals(RequestModel.Blocker.NoSeasons, m.blocker(m.draft.value))
        m.toggle(2)
        m.toggle(3)
        assertEquals(setOf(2, 3), m.draft.value.seasons)
        assertNull(m.blocker(m.draft.value))
        m.toggle(2)
        assertEquals(setOf(3), m.draft.value.seasons)
        m.selectAll()
        assertEquals(setOf(1, 2, 3), m.draft.value.seasons)
        m.clearSeasons()
        assertTrue(m.draft.value.seasons.isEmpty())

        val whole = model(series(), settings = everything.copy(partialRequestsEnabled = false))
        assertEquals(setOf(1, 2, 3), whole.draft.value.seasons)
        whole.toggle(1)
        whole.clearSeasons()
        assertEquals(setOf(1, 2, 3), whole.draft.value.seasons)
    }

    @Test fun `a used up quota blocks the request, and too many seasons are caught`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json(
                """{"movie":{"days":7,"limit":2,"used":2,"remaining":0,"restricted":true},"tv":{"days":7,"limit":3,"used":1,"remaining":2,"restricted":false}}""",
            )
        }
        val tv = model(series())
        tv.loadQuota()
        withContext(Dispatchers.Default) { withTimeout(5_000) { tv.draft.first { it.quota != null } } }
        tv.selectAll()
        assertEquals(RequestModel.Blocker.TooManySeasons(2), tv.blocker(tv.draft.value))
        tv.clearSeasons()
        tv.toggle(1)
        assertNull(tv.blocker(tv.draft.value))

        val film = model(movie)
        film.loadQuota()
        withContext(Dispatchers.Default) { withTimeout(5_000) { film.draft.first { it.quota != null } } }
        assertEquals(RequestModel.Blocker.QuotaUsedUp, film.blocker(film.draft.value))
    }

    @Test fun `no limit means no blocker`() {
        val quota = Quota(movie = QuotaStatus(limit = 0, restricted = false))
        val m = model(movie)
        assertFalse(quota.movie.hasLimit)
        assertNull(m.blocker(RequestDraft(quota = quota)))
    }

    @Test fun `sending a movie posts its id and resolution`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json("""{"id":9,"status":1}""")
        }
        val m = model(movie)
        m.send()
        val outcome = withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Sent || it is RequestOutcome.Failed } } }

        assertEquals(RequestOutcome.Sent(approved = false), outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("/request"))
        val body = request.body?.utf8().orEmpty()
        assertTrue(body, """"mediaType":"movie"""" in body && """"mediaId":603""" in body && """"is4k":false""" in body)
        assertFalse(body, "seasons" in body)
    }

    @Test fun `sending a series posts the chosen seasons, and an auto approved one says so`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json("""{"id":9,"status":2}""")
        }
        val m = model(series())
        m.toggle(3)
        m.toggle(1)
        m.send()
        val outcome = withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Sent || it is RequestOutcome.Failed } } }

        assertEquals(RequestOutcome.Sent(approved = true), outcome)
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body, """"mediaType":"tv"""" in body && """"seasons":[1,3]""" in body)
    }

    @Test fun `a refusal from the server is shown and can be dismissed`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse.Builder().code(403).body("""{"message":"Movie Quota exceeded."}""").addHeader("Content-Type", "application/json").build()
        }
        val m = model(movie)
        m.send()
        val outcome = withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Failed } } } as RequestOutcome.Failed

        assertEquals("Movie Quota exceeded.", outcome.error.message)
        m.dismissError()
        assertEquals(RequestOutcome.Idle, m.outcome.value)
    }
}
