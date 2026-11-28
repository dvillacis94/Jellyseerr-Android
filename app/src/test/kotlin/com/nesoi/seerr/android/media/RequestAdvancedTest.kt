package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.seerrJson
import com.nesoi.seerr.android.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class RequestAdvancedTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private val settings = PublicSettings(initialized = true, movie4kEnabled = true, series4kEnabled = true)
    private val movie = MovieDetails(id = 603, title = "The Matrix")
    private fun user(vararg permissions: Int, id: Int = 7) = User(id = id, permissions = permissions.fold(0) { a, b -> a or b })
    private fun scope() = CoroutineScope(Dispatchers.Default)
    private fun model(details: TitleDetails, user: User) = RequestModel(scope(), fixture.api, user, details, settings)

    private val radarr = """[{"id":1,"name":"Radarr","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/movies","activeTags":[3]},{"id":2,"name":"Radarr 4K","is4k":true,"isDefault":true}]"""
    private val radarrDetails = """{"server":{"id":1,"name":"Radarr","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/movies","activeTags":[3]},"profiles":[{"id":4,"name":"HD"},{"id":5,"name":"Ultra"}],"rootFolders":[{"id":1,"path":"/movies","freeSpace":1000},{"id":2,"path":"/more","freeSpace":2000}],"tags":[{"id":3,"label":"kids"},{"id":9,"label":"anime"}]}"""

    private fun serve(vararg answers: Pair<String, String>, others: (RecordedRequest) -> MockResponse = { json("{}") }) {
        val map = answers.toMap()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = map[request.url.encodedPath.removePrefix("/api/v1")]?.let { json(it) } ?: others(request)
        }
    }

    // Defaults

    @Test fun `the default instance for the quality is chosen, else the first`() {
        val servers = seerrJson.decodeFromString<List<ServiceServer>>(radarr)
        assertEquals(1, AdvancedRequestOptions.initialServer(servers.filter { !it.is4k })?.id)
        assertEquals(2, AdvancedRequestOptions.initialServer(servers.filter { it.is4k })?.id)
        assertEquals(5, AdvancedRequestOptions.initialServer(listOf(ServiceServer(5, "A"), ServiceServer(6, "B")))?.id?.let { it })
        assertNull(AdvancedRequestOptions.initialServer(emptyList()))
    }

    @Test fun `an instance's defaults are preselected, and ones it no longer has are left unset`() {
        val details = seerrJson.decodeFromString<ServiceDetails>(radarrDetails)
        val chosen = AdvancedRequestOptions.selection(details, isAnime = false)
        assertEquals(Destination(1, 4, "/movies", null, listOf(3)), chosen)

        val gone = details.copy(server = details.server.copy(activeProfileId = 99, activeDirectory = "/old"))
        val left = AdvancedRequestOptions.selection(gone, isAnime = false)
        assertNull(left.profileId)
        assertNull(left.rootFolder)
    }

    @Test fun `anime uses the instance's anime defaults when it has them`() {
        val details = seerrJson.decodeFromString<ServiceDetails>(radarrDetails)
            .copy(server = ServiceServer(1, "Sonarr", isDefault = true, activeProfileId = 4, activeAnimeProfileId = 5, activeDirectory = "/movies", activeAnimeDirectory = "/more", activeAnimeTags = listOf(9)))
        val anime = AdvancedRequestOptions.selection(details, isAnime = true)
        assertEquals(Destination(1, 5, "/more", null, listOf(9)), anime)
        assertEquals(4, AdvancedRequestOptions.selection(details, isAnime = false).profileId)
        // Without anime defaults it falls back to the normal ones.
        val plain = details.copy(server = details.server.copy(activeAnimeProfileId = null, activeAnimeDirectory = null))
        assertEquals(4, AdvancedRequestOptions.selection(plain, isAnime = true).profileId)
    }

    @Test fun `the anime keyword marks a series as anime`() {
        assertTrue(TvDetails(id = 1, keywords = listOf(Keyword(210024, "anime"))).isAnime)
        assertFalse(TvDetails(id = 1, keywords = listOf(Keyword(1, "x"))).isAnime)
    }

    @Test fun `the destination section is left out when there is nothing to choose`() {
        val single = ServiceDetails(ServiceServer(1, "R"), profiles = listOf(ServiceDetails.Named(1, "A")), rootFolders = listOf(ServiceDetails.RootFolder(1, "/a")))
        assertFalse(AdvancedState(servers = listOf(ServiceServer(1)), details = single).hasChoices)
        assertTrue(AdvancedState(servers = listOf(ServiceServer(1), ServiceServer(2))).hasChoices)
        assertTrue(AdvancedState(details = single.copy(profiles = single.profiles + ServiceDetails.Named(2, "B"))).hasChoices)
        assertTrue(AdvancedState(details = single.copy(tags = listOf(ServiceDetails.Tag(1, "t")))).hasChoices)
    }

    // Loading and choosing

    @Test fun `loading picks the default instance and its defaults, and choosing changes the destination`() = runBlocking {
        serve("/service/radarr" to radarr, "/service/radarr/1" to radarrDetails)
        val options = AdvancedRequestOptions(scope(), fixture.api, "radarr", is4k = false, isAnime = false)
        assertNull(options.state.value.destination)
        options.load()
        val state = withContext(Dispatchers.Default) { withTimeout(5_000) { options.state.first { it.details != null } } }

        assertEquals(listOf(1), state.servers.map { it.id })
        assertEquals(Destination(1, 4, "/movies", null, listOf(3)), state.destination)
        assertTrue(state.hasChoices)
        options.setProfile(5)
        options.setRootFolder("/more")
        options.setTags(setOf(3, 9))
        assertEquals(Destination(1, 5, "/more", null, listOf(3, 9)), options.state.value.destination)
    }

    @Test fun `a failed load leaves the defaults to the server`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(500).build()
        }
        val options = AdvancedRequestOptions(scope(), fixture.api, "radarr", false, false)
        options.load()
        val state = withContext(Dispatchers.Default) { withTimeout(5_000) { options.state.first { it.error != null } } }
        assertNull(state.destination)
        assertFalse(state.loading)
    }

    // Who may do what

    @Test fun `the destination is for advanced requesters and managers only`() {
        assertNull(model(movie, user(Permission.REQUEST)).advanced.value)
        assertNotNull(model(movie, user(Permission.REQUEST, Permission.REQUEST_ADVANCED)).advanced.value)
        assertNotNull(model(movie, user(Permission.REQUEST, Permission.MANAGE_REQUESTS)).advanced.value)
        assertNotNull(model(movie, user(Permission.ADMIN)).advanced.value)
    }

    @Test fun `requesting as someone needs both manage permissions`() {
        assertFalse(model(movie, user(Permission.MANAGE_USERS)).canRequestOnBehalf)
        assertFalse(model(movie, user(Permission.MANAGE_REQUESTS)).canRequestOnBehalf)
        assertTrue(model(movie, user(Permission.MANAGE_USERS, Permission.MANAGE_REQUESTS)).canRequestOnBehalf)
        assertTrue(model(movie, user(Permission.ADMIN)).canRequestOnBehalf)
    }

    private val people = """{"pageInfo":{"results":3},"results":[{"id":7,"displayName":"Me","permissions":2},{"id":8,"displayName":"Ana","permissions":${Permission.REQUEST}},{"id":9,"displayName":"Bo","permissions":${Permission.REQUEST_4K}}]}"""

    @Test fun `the people to request for are those allowed this kind and resolution`() = runBlocking {
        serve("/user" to people)
        val m = model(movie, user(Permission.ADMIN))
        m.loadUsers()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.users.first { it.isNotEmpty() } } }
        // Standard: the admin and Ana. Bo can only ask in 4K.
        assertEquals(listOf(7, 8), m.eligibleUsers(m.draft.value).map { it.id })
        assertTrue(m.canChooseUser(m.draft.value))
        assertEquals(listOf(7, 9), m.eligibleUsers(m.draft.value.copy(is4k = true)).map { it.id })
        val request = server.takeRequest()
        assertEquals("1000", request.url.queryParameter("take"))
        assertEquals("displayname", request.url.queryParameter("sort"))
    }

    @Test fun `someone without the permissions never loads the list`() = runBlocking {
        serve("/user" to people)
        val m = model(movie, user(Permission.REQUEST))
        m.loadUsers()
        assertEquals(0, server.requestCount)
        assertFalse(m.canChooseUser(m.draft.value))
    }

    @Test fun `choosing someone loads their quota, and a quota reply for someone else is ignored`() = runBlocking {
        serve(
            "/user" to people,
            "/user/8/quota" to """{"movie":{"days":7,"limit":2,"used":2,"remaining":0,"restricted":true},"tv":{}}""",
            "/user/7/quota" to """{"movie":{"limit":0},"tv":{}}""",
        )
        val m = model(movie, user(Permission.ADMIN))
        m.loadUsers()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.users.first { it.isNotEmpty() } } }
        m.setRequestUser(8)
        val draft = withContext(Dispatchers.Default) { withTimeout(5_000) { m.draft.first { it.quota != null } } }

        assertEquals(8, draft.requestUserId)
        assertTrue(m.isForSomeoneElse(draft))
        assertEquals("Ana", m.requestUserName(draft))
        assertEquals(RequestModel.Blocker.QuotaUsedUp, m.blocker(draft))
        // Back to yourself: that quota is not theirs.
        m.setRequestUser(7)
        assertNull(m.draft.value.quota)
        assertNull(m.requestUserName(m.draft.value))
    }

    @Test fun `a manager can let the request skip a limited quota`() = runBlocking {
        serve("/user" to people, "/user/8/quota" to """{"movie":{"days":7,"limit":2,"used":2,"remaining":0,"restricted":true},"tv":{}}""")
        val m = model(movie, user(Permission.ADMIN))
        m.loadUsers()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.users.first { it.isNotEmpty() } } }
        m.setRequestUser(8)
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.draft.first { it.quota != null } } }
        assertTrue(m.canBypassQuota(m.draft.value))
        m.setIgnoreQuota(true)
        assertNull(m.blocker(m.draft.value))
        // Choosing someone else starts with the bypass off again.
        m.setRequestUser(9)
        assertFalse(m.draft.value.ignoreQuota)
    }

    @Test fun `without manage requests there is no bypass`() {
        val m = model(movie, user(Permission.REQUEST))
        val limited = RequestDraft(quota = Quota(movie = QuotaStatus(limit = 2, remaining = 0, restricted = true)))
        assertFalse(m.canBypassQuota(limited))
    }

    // Sending

    private fun posted(): JsonObject = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test fun `a request sends the destination that was chosen`() = runBlocking {
        serve("/service/radarr" to radarr, "/service/radarr/1" to radarrDetails, "/request" to """{"id":9,"status":2}""")
        val m = model(movie, user(Permission.REQUEST, Permission.REQUEST_ADVANCED))
        val options = m.advanced.value!!
        options.load()
        withContext(Dispatchers.Default) { withTimeout(5_000) { options.state.first { it.details != null } } }
        server.takeRequest(); server.takeRequest()
        options.setProfile(5)
        options.setRootFolder("/more")
        options.setTags(setOf(9))
        m.send()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Sent } } }

        val body = posted()
        assertEquals(1, body["serverId"]!!.jsonPrimitive.int)
        assertEquals(5, body["profileId"]!!.jsonPrimitive.int)
        assertEquals("/more", body["rootFolder"]!!.jsonPrimitive.content)
        assertEquals(listOf(9), body["tags"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertNull(body["userId"])
        assertNull(body["ignoreQuota"])
    }

    @Test fun `without a loaded destination or an advanced permission nothing extra is sent`() = runBlocking {
        serve("/request" to """{"id":9,"status":1}""")
        val m = model(movie, user(Permission.REQUEST))
        m.send()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Sent } } }
        val body = posted()
        assertEquals(setOf("mediaType", "mediaId", "is4k"), body.keys)
    }

    @Test fun `a request for someone else sends their id, and the bypass when it is on`() = runBlocking {
        serve(
            "/user" to people,
            "/user/8/quota" to """{"movie":{"days":7,"limit":2,"used":2,"remaining":0,"restricted":true},"tv":{}}""",
            "/request" to """{"id":9,"status":2}""",
        )
        val m = model(movie, user(Permission.ADMIN))
        m.loadUsers()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.users.first { it.isNotEmpty() } } }
        server.takeRequest()
        m.setRequestUser(8)
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.draft.first { it.quota != null } } }
        server.takeRequest()
        m.setIgnoreQuota(true)
        m.send()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.outcome.first { it is RequestOutcome.Sent } } }

        val body = posted()
        assertEquals(8, body["userId"]!!.jsonPrimitive.int)
        assertTrue(body["ignoreQuota"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun `switching to 4K gives the 4K instances and returns to you when the person can't ask in 4K`() = runBlocking {
        serve("/user" to people, "/service/radarr" to radarr, "/service/radarr/2" to radarrDetails.replace("\"id\":1,\"name\":\"Radarr\"", "\"id\":2,\"name\":\"Radarr 4K\""))
        val m = model(movie, user(Permission.ADMIN))
        m.loadUsers()
        withContext(Dispatchers.Default) { withTimeout(5_000) { m.users.first { it.isNotEmpty() } } }
        val standard = m.advanced.value
        m.setRequestUser(8)
        m.set4k(true)
        // Ana can't request in 4K, so it goes back to the signed-in user.
        assertEquals(7, m.draft.value.requestUserId)
        assertFalse(standard === m.advanced.value)
        val options = m.advanced.value!!
        options.load()
        val state = withContext(Dispatchers.Default) { withTimeout(5_000) { options.state.first { it.details != null } } }
        assertEquals(listOf(2), state.servers.map { it.id })
    }
}
