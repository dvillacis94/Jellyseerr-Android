package com.nesoi.seerr.android.users

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
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
import java.util.Collections

class UsersTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private val bodies = Collections.synchronizedMap(mutableMapOf<String, String>())

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private val manager = User(id = 5, permissions = Permission.MANAGE_USERS)
    private val owner = User(id = 1, permissions = Permission.ADMIN)

    private fun row(id: Int, permissions: Int = Permission.REQUEST) = """{"id":$id,"displayName":"User $id","permissions":$permissions,"requestCount":$id}"""

    private fun page(ids: List<Int>, total: Int) =
        """{"pageInfo":{"pages":1,"page":1,"results":$total},"results":[${ids.joinToString(",") { row(it) }}]}"""

    private fun serve(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.url.encodedPath}"
                seen += key
                request.body?.utf8()?.takeIf { it.isNotEmpty() }?.let { bodies[key] = it }
                return handler(request)
            }
        }
    }

    // The list

    @Test fun `the list pages by offset and counts everyone`() = runBlocking {
        serve { request ->
            val skip = request.url.queryParameter("skip")
            when (skip) {
                "0" -> json(page((1..25).toList(), 30))
                else -> json(page((26..30).toList(), 30))
            }
        }
        val model = UsersModel(fixture.api, manager)
        val list = model.list.value
        list.reload()
        assertEquals(25, list.state.value.items.size)
        assertEquals(30, model.total.value)
        assertTrue(list.state.value.hasMore)
        list.loadMoreIfNeeded(24)
        assertEquals(30, list.state.value.items.size)
        assertFalse(list.state.value.hasMore)
        val first = server.takeRequest()
        assertEquals("25", first.url.queryParameter("take"))
        assertEquals("displayname", first.url.queryParameter("sort"))
        assertNull(first.url.queryParameter("q"))
    }

    @Test fun `searching sends the text and starts a new list`() = runBlocking {
        serve { request -> json(if (request.url.queryParameter("q") == "ann") page(listOf(4), 1) else page(listOf(1, 2, 3), 3)) }
        val model = UsersModel(fixture.api, manager)
        model.list.value.reload()
        val before = model.list.value
        model.search("  ann ")
        assertEquals("ann", model.query.value)
        assertFalse(before === model.list.value)
        model.list.value.reload()
        assertEquals(listOf(4), model.list.value.state.value.items.map { it.id })
        assertEquals(1, model.total.value)
        // The same search again keeps the list that is showing.
        val shown = model.list.value
        model.search("ann")
        assertTrue(shown === model.list.value)
    }

    @Test fun `a user who changed shows the new values in the list`() = runBlocking {
        serve { json(page(listOf(7, 8), 2)) }
        val model = UsersModel(fixture.api, manager)
        model.list.value.reload()
        model.didUpdate(User(id = 8, displayName = "Renamed", permissions = 99))
        assertEquals(99, model.list.value.state.value.items.first { it.id == 8 }.permissions)
        assertEquals(7, model.list.value.state.value.items.first().id)
    }

    // Deleting

    @Test fun `deleting a user takes the row away`() = runBlocking {
        serve { request -> if (request.method == "DELETE") json("{}") else json(page(listOf(7, 8), 2)) }
        val model = UsersModel(fixture.api, manager)
        model.list.value.reload()
        assertNull(model.delete(model.list.value.state.value.items.first()))
        assertEquals(listOf(8), model.list.value.state.value.items.map { it.id })
        assertEquals(1, model.total.value)
        assertTrue("DELETE /api/v1/user/7" in seen)
    }

    @Test fun `a refused delete keeps the row and says why`() = runBlocking {
        serve { request -> if (request.method == "DELETE") json("""{"message":"Not allowed"}""", 403) else json(page(listOf(7), 1)) }
        val model = UsersModel(fixture.api, manager)
        model.list.value.reload()
        assertNotNull(model.delete(model.list.value.state.value.items.first()))
        assertEquals(1, model.list.value.state.value.items.size)
        assertEquals(1, model.total.value)
    }

    @Test fun `the owner and yourself are never sent to the server to delete`() = runBlocking {
        serve { json("{}") }
        val model = UsersModel(fixture.api, manager)
        assertEquals("", model.delete(owner))
        assertEquals("", model.delete(manager))
        assertTrue(seen.none { it.startsWith("DELETE") })
    }

    // Editing

    private val settings = """{"username":"seven","email":"seven@example.com","locale":"en","discoverRegion":"US","watchlistSyncMovies":true,
        |"movieQuotaLimit":null,"movieQuotaDays":null,"tvQuotaLimit":4,"tvQuotaDays":2,
        |"globalMovieQuotaLimit":10,"globalMovieQuotaDays":14,"globalTvQuotaLimit":null,"globalTvQuotaDays":null}""".trimMargin()

    private fun edit(target: User = User(id = 7, displayName = "Seven", permissions = Permission.REQUEST), actor: User = manager, public: PublicSettings? = null) =
        UserEditModel(fixture.api, target, actor, public)

    private fun serveUser(permissionsAfter: Int = Permission.REQUEST, general: String = settings, fail: String? = null) {
        serve { request ->
            val key = "${request.method} ${request.url.encodedPath}"
            when {
                key == fail -> json("""{"message":"No"}""", 500)
                key == "GET /api/v1/user/7/settings/main" -> json(general)
                key == "GET /api/v1/user/7" -> json(row(7, permissionsAfter))
                else -> json("{}")
            }
        }
    }

    @Test fun `limits load for someone whose limits can be edited`() = runBlocking {
        serveUser()
        val model = edit()
        model.load()
        val state = model.state.value
        assertNotNull(state.general)
        // No override: the editor starts from the server default.
        assertFalse(state.movieQuota.isOverridden)
        assertEquals(10, state.movieQuota.limit)
        assertEquals(14, state.movieQuota.days)
        assertTrue(state.tvQuota.isOverridden)
        assertEquals(4, state.tvQuota.limit)
        assertFalse(model.hasChanges())
    }

    @Test fun `limits are not loaded for someone with manage users`() = runBlocking {
        serveUser()
        val model = edit(target = User(id = 9, permissions = Permission.MANAGE_USERS))
        model.load()
        assertNull(model.state.value.general)
        assertTrue(seen.isEmpty())
    }

    @Test fun `toggling flips one bit and marks a change`() {
        val model = edit()
        val option = PermissionOption.all.first { it.permission == Permission.MANAGE_ISSUES }
        model.toggle(option)
        assertEquals(Permission.REQUEST or Permission.MANAGE_ISSUES, model.state.value.permissions)
        assertTrue(model.hasChanges())
        model.toggle(option)
        assertFalse(model.hasChanges())
    }

    @Test fun `nothing can be changed on the owner or yourself`() {
        assertFalse(edit(target = owner).canEditPermissions)
        assertFalse(edit(target = manager).canEditPermissions)
        val shown = edit(target = owner).optionState(PermissionOption.all.first { it.permission == Permission.REQUEST }, null)
        assertEquals(PermissionOption.State(true, false), shown)
    }

    @Test fun `saving permissions sends them and reads the user back`() = runBlocking {
        serveUser(permissionsAfter = Permission.REQUEST or Permission.VIEW_ISSUES)
        val model = edit()
        model.load()
        model.toggle(PermissionOption.all.first { it.permission == Permission.MANAGE_ISSUES }.children.first { it.permission == Permission.VIEW_ISSUES })
        val saved = model.save()
        assertEquals(Permission.REQUEST or Permission.VIEW_ISSUES, saved?.permissions)
        assertEquals(Permission.REQUEST or Permission.VIEW_ISSUES, Json.parseToJsonElement(bodies.getValue("POST /api/v1/user/7/settings/permissions")).jsonObject["permissions"]?.jsonPrimitive?.intOrNull)
        // Limits did not change, so they were not sent.
        assertFalse("POST /api/v1/user/7/settings/main" in seen)
        assertFalse(model.hasChanges())
        assertFalse(model.state.value.isSaving)
    }

    @Test fun `saving limits sends every setting back, with the new numbers`() = runBlocking {
        serveUser()
        val model = edit()
        model.load()
        model.setMovieQuota(QuotaOverride(isOverridden = true, limit = 3, days = 5))
        model.setTvQuota(QuotaOverride(isOverridden = false, limit = 4, days = 2))
        assertTrue(model.hasChanges())
        model.save()
        val body: JsonObject = Json.parseToJsonElement(bodies.getValue("POST /api/v1/user/7/settings/main")).jsonObject
        assertEquals(3, body["movieQuotaLimit"]?.jsonPrimitive?.intOrNull)
        assertEquals(5, body["movieQuotaDays"]?.jsonPrimitive?.intOrNull)
        // Clearing an override means sending null: the server overwrites whatever is missing.
        assertEquals(JsonNull, body["tvQuotaLimit"])
        assertEquals(JsonNull, body["tvQuotaDays"])
        assertEquals("seven@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals("US", body["discoverRegion"]?.jsonPrimitive?.content)
        assertTrue(body.keys.none { it.startsWith("global") })
        assertFalse("POST /api/v1/user/7/settings/permissions" in seen)
    }

    @Test fun `a failed save keeps the edits and says so`() = runBlocking {
        serveUser(fail = "POST /api/v1/user/7/settings/permissions")
        val model = edit()
        model.toggle(PermissionOption.all.first { it.permission == Permission.MANAGE_ISSUES })
        assertNull(model.save())
        assertNotNull(model.state.value.error)
        assertTrue(model.hasChanges())
        assertFalse(model.state.value.isSaving)
    }

    @Test fun `saving without changes sends nothing`() = runBlocking {
        serveUser()
        val model = edit()
        model.load()
        seen.clear()
        assertNull(model.save())
        assertTrue(seen.isEmpty())
    }

    @Test fun `the media server's name is for the watchlist options`() {
        assertEquals("Plex", edit(public = PublicSettings(initialized = true, mediaServerType = 1)).mediaServerName)
        assertEquals("Jellyfin", edit(public = PublicSettings(initialized = true, mediaServerType = 2)).mediaServerName)
        assertEquals("Emby", edit(public = PublicSettings(initialized = true, mediaServerType = 3)).mediaServerName)
    }
}
