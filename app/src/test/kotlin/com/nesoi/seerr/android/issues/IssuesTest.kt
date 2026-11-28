package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.json
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.requests.RequestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class IssuesTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private fun user(permissions: Int, id: Int = 7) = User(id = id, permissions = permissions)
    private fun comment(id: Int, by: Int, at: String = "2026-11-27T10:00:00.000Z", message: String = "m$id") =
        IssueComment(id, message, RequestUser(by, displayName = "User $by"), at, at)
    private fun issue(by: Int = 7, comments: List<IssueComment> = listOf(comment(1, by))) =
        Issue(id = 5, issueType = 2, status = 1, createdBy = RequestUser(by), comments = comments, media = IssueMedia(id = 9, tmdbId = 603, mediaType = "movie"))

    // Rules

    @Test fun `a reporter can see and change only their own issue`() {
        val rules = IssueRules(user(Permission.CREATE_ISSUES))
        assertTrue(rules.canViewIssues)
        assertFalse(rules.canViewOthers)
        assertTrue(rules.canModify(issue(by = 7)))
        assertFalse(rules.canModify(issue(by = 8)))
    }

    @Test fun `viewing alone changes nothing`() {
        val rules = IssueRules(user(Permission.VIEW_ISSUES))
        assertTrue(rules.canViewOthers)
        assertFalse(rules.canReport)
        assertFalse(rules.canModify(issue(by = 7)))
    }

    @Test fun `managers and admins can change and delete any issue`() {
        for (permissions in listOf(Permission.MANAGE_ISSUES, Permission.ADMIN)) {
            val rules = IssueRules(user(permissions))
            val other = issue(by = 8, comments = listOf(comment(1, 8), comment(2, 9)))
            assertTrue(rules.canModify(other))
            assertTrue(rules.canDelete(other))
        }
    }

    @Test fun `a reporter can delete their issue only until someone replies`() {
        val rules = IssueRules(user(Permission.CREATE_ISSUES))
        assertTrue(rules.canDelete(issue(by = 7)))
        assertFalse(rules.canDelete(issue(by = 7, comments = listOf(comment(1, 7), comment(2, 8)))))
    }

    @Test fun `only the author edits a comment, and the description can't be deleted on its own`() {
        val manager = IssueRules(user(Permission.MANAGE_ISSUES))
        val mine = comment(2, 7)
        val theirs = comment(3, 8)
        val issue = issue(by = 8, comments = listOf(comment(1, 8), mine, theirs))
        assertTrue(manager.canEdit(mine))
        assertFalse(manager.canEdit(theirs))
        assertTrue(manager.canDelete(theirs, issue))
        assertFalse(manager.canDelete(issue.description!!, issue))
        val reporter = IssueRules(user(Permission.CREATE_ISSUES))
        assertTrue(reporter.canDelete(mine, issue))
        assertFalse(reporter.canDelete(theirs, issue))
    }

    @Test fun `someone with no issue permissions sees nothing`() {
        val rules = IssueRules(user(Permission.REQUEST))
        assertFalse(rules.canViewIssues)
        assertFalse(rules.canReport)
    }

    // Models

    @Test fun `the first comment is the description and the rest are replies in order`() {
        val issue = issue(comments = listOf(comment(3, 8, "2026-11-27T12:00:00.000Z"), comment(1, 7, "2026-11-27T10:00:00.000Z"), comment(2, 8, "2026-11-27T11:00:00.000Z")))
        assertEquals(1, issue.description?.id)
        assertEquals(listOf(2, 3), issue.replies.map { it.id })
    }

    @Test fun `the affected season and episode`() {
        assertNull(issue().affected)
        assertNull(issue().copy(problemSeason = 0, problemEpisode = 4).affected)
        assertEquals(2 to null, issue().copy(problemSeason = 2, problemEpisode = 0).affected)
        assertEquals(2 to 5, issue().copy(problemSeason = 2, problemEpisode = 5).affected)
    }

    @Test fun `types, statuses and the title type read from the codes`() {
        assertEquals(IssueType.Audio, issue().type)
        assertEquals(IssueType.Other, IssueType.from(99))
        assertEquals(IssueStatus.Open, issue().issueStatus)
        assertEquals(IssueStatus.Unknown, IssueStatus.from(null))
        assertEquals(TitleType.Movie, issue().titleType)
        assertEquals(TitleType.Series, issue().copy(media = IssueMedia(mediaType = "tv")).titleType)
    }

    @Test fun `a comment is edited when saved well after it was written`() {
        assertFalse(comment(1, 7).isEdited)
        assertTrue(comment(1, 7).copy(updatedAt = "2026-11-27T10:05:00.000Z").isEdited)
        assertFalse(IssueComment(1, "x").isEdited)
    }

    @Test fun `dates show in the locale and zone`() {
        // Newer JDKs put a narrow no-break space before AM.
        val shown = formatDateTime("2026-11-27T10:00:00.000Z", Locale.US, ZoneId.of("America/Bogota"))?.replace('\u202f', ' ')
        assertEquals("Nov 27, 2026, 5:00 AM", shown)
        assertNull(formatDateTime("not a date"))
        assertNull(formatDateTime(null))
    }

    // The list

    private fun page(vararg ids: Int, total: Int = ids.size) = json(
        """{"pageInfo":{"pages":1,"page":1,"results":$total},"results":[${ids.joinToString(",") { """{"id":$it,"issueType":1,"status":1,"createdBy":{"id":7},"media":{"id":1,"tmdbId":${600 + it},"mediaType":"movie"},"comments":[]}""" }}]}""",
    )

    private fun serve(issuesBody: (RecordedRequest) -> MockResponse = { page(1) }, other: (RecordedRequest) -> MockResponse = { json("{}") }) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/api/v1/issue" -> issuesBody(request)
                request.url.encodedPath.startsWith("/api/v1/movie/") -> json("""{"title":"Title ${request.url.encodedPath.substringAfterLast('/')}","posterPath":"/p.jpg","releaseDate":"2001-03-31"}""")
                else -> other(request)
            }
        }
    }

    @Test fun `the list asks for the chosen filter and sort by offset, and names the titles`() = runTest {
        serve(issuesBody = { page(1, 2, total = 2) })
        val model = IssuesModel(fixture.api, user(Permission.MANAGE_ISSUES))
        model.setFilter(IssueFilter.Resolved)
        model.setSort(com.nesoi.seerr.android.requests.RequestSort.Modified)
        val list = model.list.value
        list.loadIfNeeded()

        val request = server.takeRequest()
        assertEquals("resolved", request.url.queryParameter("filter"))
        assertEquals("modified", request.url.queryParameter("sort"))
        assertEquals("0", request.url.queryParameter("skip"))
        assertEquals("20", request.url.queryParameter("take"))
        val rows = list.state.value.items
        assertEquals(listOf("Title 601", "Title 602"), rows.map { it.title })
        assertEquals("2001", rows.first().year)
        assertFalse(list.state.value.hasMore)
    }

    @Test fun `a title that can't be fetched leaves the issue in the list`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.url.encodedPath == "/api/v1/issue") page(1) else MockResponse.Builder().code(500).build()
        }
        val model = IssuesModel(fixture.api, user(Permission.MANAGE_ISSUES))
        model.list.value.loadIfNeeded()
        val row = model.list.value.state.value.items.single()
        assertNull(row.title)
        assertEquals(1, row.id)
    }

    @Test fun `only people who see others' issues are told who reported`() {
        assertTrue(IssuesModel(fixture.api, user(Permission.VIEW_ISSUES)).showsReporter)
        assertFalse(IssuesModel(fixture.api, user(Permission.CREATE_ISSUES)).showsReporter)
    }

    // The issue's page

    private val issueJson = """{"id":5,"issueType":1,"status":1,"createdBy":{"id":7},"media":{"id":1,"tmdbId":603,"mediaType":"movie"},"comments":[{"id":1,"message":"Broken","user":{"id":7},"createdAt":"2026-11-27T10:00:00.000Z","updatedAt":"2026-11-27T10:00:00.000Z"}]}"""

    private fun detail(permissions: Int, changed: suspend () -> Unit = {}, scope: kotlinx.coroutines.CoroutineScope) =
        IssueDetailModel(scope, fixture.api, 5, IssueRules(user(permissions)), onChanged = changed)

    @Test fun `loading shows the issue and its title`() = runTest {
        serve(other = { request -> if (request.url.encodedPath == "/api/v1/issue/5") json(issueJson) else json("{}") })
        val model = detail(Permission.CREATE_ISSUES, scope = this)
        model.load()
        assertEquals(5, model.issue.value?.id)
        assertEquals("Title 603", model.title.value?.name)
        assertEquals("movie/603", model.titleRoute)
    }

    @Test fun `a failed load is reported`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(500).build()
        }
        val model = detail(Permission.CREATE_ISSUES, scope = this)
        model.load()
        assertNotNull(model.loadError.value)
        assertNull(model.issue.value)
    }

    private suspend fun IssueDetailModel.awaitIdle() = withContext(Dispatchers.Default) { withTimeout(5_000) { working.first { !it } } }

    @Test fun `sending a comment posts it, clears the draft, reloads the issue and tells the list`() = runTest {
        val posts = mutableListOf<String>()
        serve(other = { request ->
            when {
                request.method == "POST" -> { posts += request.body!!.utf8(); json("{}") }
                request.url.encodedPath == "/api/v1/issue/5" -> json(issueJson)
                else -> json("{}")
            }
        })
        var changes = 0
        val model = detail(Permission.CREATE_ISSUES, changed = { changes++ }, scope = this)
        model.load()
        model.setDraft("  Still broken  ")
        model.sendComment()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (changes == 0) kotlinx.coroutines.delay(10) } }

        assertEquals(listOf("""{"message":"Still broken"}"""), posts)
        assertEquals("", model.draft.value)
        assertEquals(1, changes)
    }

    @Test fun `someone who can't modify the issue can't comment or change it`() = runTest {
        val writes = mutableListOf<String>()
        serve(other = { request ->
            if (request.method != "GET") writes += "${request.method} ${request.url.encodedPath}"
            if (request.url.encodedPath == "/api/v1/issue/5") json(issueJson) else json("{}")
        })
        val model = detail(Permission.VIEW_ISSUES, scope = this)
        model.load()
        model.setDraft("hi")
        model.sendComment()
        model.setStatus(IssueStatus.Resolved)
        model.delete {}
        advanceUntilIdle()
        assertFalse(model.canComment)
        assertFalse(model.canDelete)
        assertEquals(emptyList<String>(), writes)
    }

    @Test fun `resolving posts to the status route`() = runTest {
        val seen = mutableListOf<String>()
        serve(other = { request ->
            seen += "${request.method} ${request.url.encodedPath}"
            if (request.url.encodedPath == "/api/v1/issue/5") json(issueJson) else json("{}")
        })
        val model = detail(Permission.MANAGE_ISSUES, scope = this)
        model.load()
        model.setStatus(IssueStatus.Resolved)
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { while ("POST /api/v1/issue/5/resolved" !in seen) kotlinx.coroutines.delay(10) } }
        assertTrue("POST /api/v1/issue/5/resolved" in seen)
    }

    @Test fun `a refused change reports the message and keeps the issue`() = runTest {
        serve(other = { request ->
            when {
                request.method == "POST" -> json("""{"message":"Nope"}""", code = 403)
                request.url.encodedPath == "/api/v1/issue/5" -> json(issueJson)
                else -> json("{}")
            }
        })
        val model = detail(Permission.MANAGE_ISSUES, scope = this)
        model.load()
        model.setStatus(IssueStatus.Resolved)
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { model.actionError.first { it != null } } }
        assertEquals("Nope", model.actionError.value)
        assertEquals(IssueStatus.Open, model.issue.value?.issueStatus)
        model.dismissError()
        assertNull(model.actionError.value)
    }

    @Test fun `deleting the issue calls back and tells the list`() = runTest {
        val seen = mutableListOf<String>()
        serve(other = { request ->
            seen += "${request.method} ${request.url.encodedPath}"
            if (request.url.encodedPath == "/api/v1/issue/5" && request.method == "GET") json(issueJson) else json("{}")
        })
        var deleted = false
        var changes = 0
        val model = detail(Permission.MANAGE_ISSUES, changed = { changes++ }, scope = this)
        model.load()
        model.delete { deleted = true }
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!deleted) kotlinx.coroutines.delay(10) } }
        assertTrue("DELETE /api/v1/issue/5" in seen)
        assertEquals(1, changes)
    }

    @Test fun `editing and deleting a comment use the comment routes`() = runTest {
        val seen = mutableListOf<String>()
        val twoComments = issueJson.replace("""}]}""", """},{"id":2,"message":"Mine","user":{"id":7},"createdAt":"2026-11-27T11:00:00.000Z","updatedAt":"2026-11-27T11:00:00.000Z"}]}""")
        serve(other = { request ->
            seen += "${request.method} ${request.url.encodedPath}"
            if (request.url.encodedPath == "/api/v1/issue/5") json(twoComments) else json("{}")
        })
        val model = detail(Permission.CREATE_ISSUES, scope = this)
        model.load()
        val mine = model.issue.value!!.replies.single()
        assertTrue(model.canEdit(mine))
        model.updateComment(mine, "Edited")
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { while ("PUT /api/v1/issueComment/2" !in seen) kotlinx.coroutines.delay(10) } }
        model.awaitIdle()
        model.deleteComment(mine)
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { while ("DELETE /api/v1/issueComment/2" !in seen) kotlinx.coroutines.delay(10) } }
        // The description can't be deleted.
        assertFalse(model.canDelete(model.issue.value!!.description!!))
    }
}
