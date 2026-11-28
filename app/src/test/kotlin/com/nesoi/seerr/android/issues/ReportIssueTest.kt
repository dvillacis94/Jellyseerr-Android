package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.seerrJson
import com.nesoi.seerr.android.json
import com.nesoi.seerr.android.media.MediaInfo
import com.nesoi.seerr.android.media.MovieDetails
import com.nesoi.seerr.android.media.SeasonInfo
import com.nesoi.seerr.android.media.TvDetails
import com.nesoi.seerr.android.media.TvSeason
import com.nesoi.seerr.android.requests.RequestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportIssueTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private val library = MediaInfo(id = 42, status = 5)
    private val movie = MovieDetails(id = 603, title = "The Matrix", mediaInfo = library)
    private val show = TvDetails(
        id = 1399,
        title = "Show",
        seasons = listOf(TvSeason(0, episodeCount = 3), TvSeason(1, episodeCount = 10), TvSeason(2, episodeCount = 8), TvSeason(3, episodeCount = 0)),
        mediaInfo = MediaInfo(id = 7, status = 4, seasons = listOf(SeasonInfo(1, status = 5), SeasonInfo(2, status = 1))),
    )

    private fun model(details: com.nesoi.seerr.android.media.TitleDetails, scope: kotlinx.coroutines.CoroutineScope, reported: () -> Unit = {}) =
        ReportIssueModel(scope, fixture.api, details, onReported = { reported() })

    // What can be reported on

    @Test fun `only titles in the library can be reported on`() {
        assertTrue(MediaInfo(id = 1, status = 5).canReportIssue())
        assertTrue(MediaInfo(id = 1, status = 4).canReportIssue())
        assertTrue(MediaInfo(id = 1, status = 1, status4k = 5).canReportIssue())
        assertFalse(MediaInfo(id = 1, status = 2).canReportIssue())
        assertFalse(MediaInfo(id = null, status = 5).canReportIssue())
        assertFalse(null.canReportIssue())
    }

    @Test fun `seasons come from the library, else from what has aired, and never include specials`() {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        assertEquals(listOf(1), model(show, scope).seasonOptions)
        val untracked = show.copy(mediaInfo = MediaInfo(id = 7, status = 5))
        assertEquals(listOf(1, 2), model(untracked, scope).seasonOptions)
        assertEquals(emptyList<Int>(), model(movie, scope).seasonOptions)
    }

    @Test fun `episodes follow the chosen season, and choosing another season resets the episode`() {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        val model = model(show, scope)
        assertEquals(emptyList<Int>(), model.episodeOptions)
        model.setSeason(2)
        assertEquals((1..8).toList(), model.episodeOptions)
        model.setEpisode(5)
        model.setSeason(2)
        assertEquals(5, model.draft.value.episode)
        model.setSeason(1)
        assertEquals(0, model.draft.value.episode)
        assertEquals((1..10).toList(), model.episodeOptions)
    }

    @Test fun `a report needs a message and the title's library id`() {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        val model = model(movie, scope)
        assertFalse(model.canSubmit)
        model.setMessage("   ")
        assertFalse(model.canSubmit)
        model.setMessage("No picture")
        assertTrue(model.canSubmit)
        val unknown = model(movie.copy(mediaInfo = MediaInfo(id = null, status = 5)), scope)
        unknown.setMessage("x")
        assertFalse(unknown.canSubmit)
    }

    // Sending

    private fun posted() = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test fun `a movie report sends the type, message and library id without a season`() = runTest {
        server.enqueue(json("""{"id":9,"issueType":2,"status":1}"""))
        var reported = 0
        val model = model(movie, this) { reported++ }
        model.setType(IssueType.Audio)
        model.setMessage("  Out of sync  ")
        model.send()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { model.outcome.first { it == ReportOutcome.Sent } } }

        val body = posted()
        assertEquals(2, body["issueType"]!!.jsonPrimitive.int)
        assertEquals("Out of sync", body["message"]!!.jsonPrimitive.content)
        assertEquals(42, body["mediaId"]!!.jsonPrimitive.int)
        assertNull(body["problemSeason"])
        assertNull(body["problemEpisode"])
        assertEquals(1, reported)
    }

    @Test fun `a series report sends the season, and the episode only for a season`() = runTest {
        server.enqueue(json("""{"id":9}"""))
        server.enqueue(json("""{"id":10}"""))
        val whole = model(show, this)
        whole.setMessage("Bad")
        whole.send()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { whole.outcome.first { it == ReportOutcome.Sent } } }
        val allSeasons = posted()
        assertEquals(0, allSeasons["problemSeason"]!!.jsonPrimitive.int)
        assertNull(allSeasons["problemEpisode"])

        val one = model(show, this)
        one.setSeason(1)
        one.setEpisode(4)
        one.setMessage("Bad")
        one.send()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { one.outcome.first { it == ReportOutcome.Sent } } }
        val episode = posted()
        assertEquals(1, episode["problemSeason"]!!.jsonPrimitive.int)
        assertEquals(4, episode["problemEpisode"]!!.jsonPrimitive.int)
    }

    @Test fun `a refused report keeps the draft and reports the message`() = runTest {
        server.enqueue(json("""{"message":"Not allowed"}""", code = 403))
        var reported = 0
        val model = model(movie, this) { reported++ }
        model.setMessage("Broken")
        model.send()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { model.outcome.first { it is ReportOutcome.Failed } } }
        assertEquals(ReportOutcome.Failed("Not allowed"), model.outcome.value)
        assertEquals("Broken", model.draft.value.message)
        assertEquals(0, reported)
        model.dismissError()
        assertEquals(ReportOutcome.Idle, model.outcome.value)
        assertTrue(model.canSubmit)
    }

    // On the title's page

    private fun issue(id: Int, status: Int, by: Int, at: String) =
        Issue(id = id, issueType = 1, status = status, createdBy = RequestUser(by), createdAt = at)

    @Test fun `a title lists its open issues, newest first, that this person can open`() {
        val info = MediaInfo(
            id = 1, status = 5,
            issues = listOf(
                issue(1, 1, 8, "2026-11-01T10:00:00.000Z"),
                issue(2, 2, 7, "2026-11-02T10:00:00.000Z"),
                issue(3, 1, 7, "2026-11-03T10:00:00.000Z"),
                issue(4, 1, 8, "2026-11-04T10:00:00.000Z"),
            ),
        )
        val manager = IssueRules(User(id = 7, permissions = Permission.MANAGE_ISSUES))
        assertEquals(listOf(4, 3, 1), info.openIssues(manager).map { it.id })
        // With only "report issues" someone sees just their own.
        val reporter = IssueRules(User(id = 7, permissions = Permission.CREATE_ISSUES))
        assertEquals(listOf(3), info.openIssues(reporter).map { it.id })
        val nobody = IssueRules(User(id = 7, permissions = Permission.REQUEST))
        assertEquals(emptyList<Issue>(), info.openIssues(nobody))
        assertEquals(emptyList<Issue>(), (null as MediaInfo?).openIssues(manager))
    }

    @Test fun `the issues on a title's media info are read`() {
        val details = seerrJson.decodeFromString<MovieDetails>(
            """{"id":603,"title":"The Matrix","mediaInfo":{"id":42,"status":5,"issues":[{"id":9,"issueType":1,"status":1,"createdBy":{"id":7},"createdAt":"2026-11-27T10:00:00.000Z"}]}}""",
        )
        val issue = details.mediaInfo!!.issues.single()
        assertEquals(9, issue.id)
        assertEquals(IssueType.Video, issue.type)
        assertEquals(7, issue.createdBy?.id)
    }
}
