package com.nesoi.seerr.android.notifications

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

class NotificationSettingsTest {
    private val server = MockWebServer()
    private val fixture = AuthFixture()

    @Before fun start() {
        server.start()
        fixture.api.baseUrl = server.url("/")
    }

    @After fun stop() = server.close()

    private fun user(permissions: Int) = User(id = 7, permissions = permissions)
    private fun kinds(user: User, sync: Boolean = false, enabled: Int = NotificationKind.ALL) =
        NotificationKind.availableTo(user, sync, movie4kEnabled = false, series4kEnabled = false, enabledTypes = enabled)

    @Test fun `a plain requester hears about their own requests`() {
        val plain = user(Permission.REQUEST)
        // Not auto-approved, so approvals and declines matter; they cannot manage, so no pending or failed.
        assertEquals(
            listOf(NotificationKind.MediaApproved, NotificationKind.MediaDeclined, NotificationKind.MediaAvailable),
            kinds(plain),
        )
    }

    @Test fun `auto approved requesters don't get approved or declined`() {
        val auto = user(Permission.REQUEST or Permission.AUTO_APPROVE)
        assertEquals(listOf(NotificationKind.MediaAvailable), kinds(auto))
    }

    @Test fun `request managers get the ones about other people's requests after their own`() {
        val manager = user(Permission.MANAGE_REQUESTS or Permission.REQUEST)
        assertEquals(
            listOf(NotificationKind.MediaAvailable, NotificationKind.MediaPending, NotificationKind.MediaAutoApproved, NotificationKind.MediaFailed),
            kinds(manager),
        )
    }

    @Test fun `issue events depend on the issue permissions`() {
        val reporter = user(Permission.CREATE_ISSUES or Permission.REQUEST or Permission.AUTO_APPROVE)
        assertEquals(
            listOf(NotificationKind.MediaAvailable, NotificationKind.IssueComment, NotificationKind.IssueResolved, NotificationKind.IssueReopened),
            kinds(reporter),
        )
        val manager = user(Permission.MANAGE_ISSUES or Permission.REQUEST or Permission.AUTO_APPROVE)
        assertTrue(NotificationKind.IssueCreated in kinds(manager))
    }

    @Test fun `watchlist auto requests need the permission and sync`() {
        val user = user(Permission.REQUEST or Permission.AUTO_APPROVE or Permission.AUTO_REQUEST)
        assertFalse(NotificationKind.MediaAutoRequested in kinds(user, sync = false))
        assertEquals(NotificationKind.MediaAutoRequested, kinds(user, sync = true).first())
    }

    @Test fun `an admin sees everything an admin can use`() {
        val shown = kinds(user(Permission.ADMIN))
        assertTrue(NotificationKind.MediaPending in shown && NotificationKind.IssueCreated in shown)
        assertFalse(NotificationKind.MediaApproved in shown)
    }

    @Test fun `the admin's allowed events limit what is offered`() {
        val admin = user(Permission.ADMIN)
        val onlyAvailable = NotificationKind.MediaAvailable.bit
        assertEquals(listOf(NotificationKind.MediaAvailable), kinds(admin, enabled = onlyAvailable))
    }

    @Test fun `email defaults to everything and discord to what the admin allows`() {
        val settings = UserNotificationSettings(discordEnabledTypes = 0)
        assertEquals(NotificationKind.ALL, settings.types(NotificationChannel.Email))
        assertEquals(0, settings.types(NotificationChannel.Discord))
        val allowed = UserNotificationSettings(discordEnabledTypes = 12, notificationTypes = mapOf("discord" to 4094))
        assertEquals(12, allowed.types(NotificationChannel.Discord))
    }

    @Test fun `turning an event on and off changes only its bit`() {
        val on = UserNotificationSettings().with(NotificationChannel.Email, NotificationKind.MediaApproved, false)
        assertFalse(NotificationKind.MediaApproved.isOn(on.types(NotificationChannel.Email)))
        assertTrue(NotificationKind.MediaAvailable.isOn(on.types(NotificationChannel.Email)))
        assertTrue(NotificationKind.MediaApproved.isOn(on.with(NotificationChannel.Email, NotificationKind.MediaApproved, true).types(NotificationChannel.Email)))
    }

    @Test fun `channels are offered as the web app decides`() {
        val none = UserNotificationSettings()
        assertEquals(
            listOf(NotificationChannel.Discord, NotificationChannel.Pushbullet, NotificationChannel.Pushover),
            NotificationChannel.entries.filter(none::offers),
        )
        val all = UserNotificationSettings(emailEnabled = true, telegramEnabled = true, telegramBotUsername = "bot")
        assertEquals(NotificationChannel.entries, NotificationChannel.entries.filter(all::offers))
        assertFalse(UserNotificationSettings(telegramEnabled = true).offers(NotificationChannel.Telegram))
    }

    @Test fun `fields are checked like the web app`() {
        val on = mapOf("discord" to 8, "telegram" to 8, "pushbullet" to 8, "pushover" to 8)
        val base = UserNotificationSettings(discordEnabledTypes = 8, notificationTypes = on)
        assertEquals(Problem.DiscordIdMissing, base.problem(NotificationChannel.Discord))
        assertEquals(Problem.DiscordIdFormat, base.copy(discordIds = listOf("123")).problem(NotificationChannel.Discord))
        assertNull(base.copy(discordIds = listOf("123456789012345678")).problem(NotificationChannel.Discord))
        assertEquals(Problem.TelegramChatMissing, base.problem(NotificationChannel.Telegram))
        assertEquals(Problem.TelegramChatFormat, base.copy(telegramChatId = "abc").problem(NotificationChannel.Telegram))
        assertNull(base.copy(telegramChatId = "-100123").problem(NotificationChannel.Telegram))
        assertEquals(Problem.TelegramThreadFormat, base.copy(telegramChatId = "1", telegramMessageThreadId = "x").problem(NotificationChannel.Telegram))
        assertEquals(Problem.PushbulletTokenMissing, base.problem(NotificationChannel.Pushbullet))
        assertEquals(Problem.PushoverAppTokenMissing, base.problem(NotificationChannel.Pushover))
        val key = "a".repeat(30)
        assertEquals(Problem.PushoverUserKeyMissing, base.copy(pushoverApplicationToken = key).problem(NotificationChannel.Pushover))
        assertEquals(Problem.PushoverUserKeyFormat, base.copy(pushoverApplicationToken = key, pushoverUserKey = "short").problem(NotificationChannel.Pushover))
        assertNull(base.copy(pushoverApplicationToken = key, pushoverUserKey = key).problem(NotificationChannel.Pushover))
    }

    @Test fun `with everything off nothing is required`() {
        val off = UserNotificationSettings(discordEnabledTypes = 8, notificationTypes = mapOf("discord" to 0, "pushbullet" to 0))
        assertNull(off.problem(NotificationChannel.Discord))
        assertNull(off.problem(NotificationChannel.Pushbullet))
    }

    @Test fun `normalizing trims and drops empty text`() {
        val messy = UserNotificationSettings(discordIds = listOf(" 1 ", "  "), pushbulletAccessToken = "  ", telegramChatId = " 5 ").normalized()
        assertEquals(listOf("1"), messy.discordIds)
        assertNull(messy.pushbulletAccessToken)
        assertEquals("5", messy.telegramChatId)
    }

    @Test fun `the request body sends every own field, with null for empty ones`() {
        val body = UserNotificationSettings(emailEnabled = true, pushoverUserKey = "k", notificationTypes = mapOf("email" to 8)).toRequestBody()
        assertEquals(JsonNull, body["pgpKey"])
        assertEquals(JsonNull, body["pushbulletAccessToken"])
        assertEquals("k", body["pushoverUserKey"]?.jsonPrimitive?.content)
        assertEquals(0, body["discordIds"]?.jsonArray?.size)
        assertEquals(8, body["notificationTypes"]?.jsonObject?.get("email")?.jsonPrimitive?.content?.toInt())
        // Server-wide flags are never sent.
        assertNull(body["emailEnabled"])
    }

    private val loaded = """{"emailEnabled":true,"discordEnabled":true,"discordEnabledTypes":0,"telegramEnabled":false,"pgpKey":null,"discordIds":null,"notificationTypes":{"email":8,"discord":null}}"""

    private fun dispatch(save: (RecordedRequest) -> MockResponse = { json("{}") }, notifications: () -> MockResponse = { json(loaded) }) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/settings/main") -> json("""{"watchlistSyncMovies":true}""")
                request.method == "POST" -> save(request)
                else -> notifications()
            }
        }
    }

    private fun model(permissions: Int = Permission.REQUEST or Permission.AUTO_APPROVE or Permission.AUTO_REQUEST) =
        NotificationSettingsModel(fixture.api, user(permissions)) { PublicSettings(initialized = true) }

    @Test fun `loading reads the settings, including null event values, and watchlist sync`() = runTest {
        dispatch()
        val model = model()
        model.load()
        val settings = model.settings.value!!
        assertEquals(listOf(NotificationChannel.Email, NotificationChannel.Discord, NotificationChannel.Pushbullet, NotificationChannel.Pushover), model.channels(settings))
        // Sync is on, so the auto-request event is offered.
        assertTrue(NotificationKind.MediaAutoRequested in model.kinds(settings, NotificationChannel.Email))
        assertEquals(NotificationSettingsModel.Summary.Some(1, 2), model.summary(settings, NotificationChannel.Email))
        assertEquals(NotificationSettingsModel.Summary.Off, model.summary(settings, NotificationChannel.Discord))
    }

    @Test fun `a failed load is reported and can be tried again`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(500).build()
        }
        val model = model()
        model.load()
        assertNotNull(model.loadError.value)
        assertNull(model.settings.value)
        dispatch()
        model.load()
        assertNull(model.loadError.value)
        assertNotNull(model.settings.value)
    }

    @Test fun `saving posts everything and reads the settings back`() = runTest {
        var posted: JsonObject? = null
        var served = loaded
        dispatch(
            save = { request ->
                posted = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                served = """{"emailEnabled":true,"discordEnabledTypes":0,"pushbulletAccessToken":"tok","notificationTypes":{"email":8,"pushbullet":8}}"""
                json("{}")
            },
            notifications = { json(served) },
        )
        val model = model()
        model.load()
        val draft = model.settings.value!!.copy(pushbulletAccessToken = " tok ").with(NotificationChannel.Pushbullet, NotificationKind.MediaAvailable, true)

        assertEquals(SaveResult.Saved, model.save(draft, NotificationChannel.Pushbullet))

        assertEquals("tok", posted?.get("pushbulletAccessToken")?.jsonPrimitive?.content)
        assertEquals(8, posted?.get("notificationTypes")?.jsonObject?.get("pushbullet")?.jsonPrimitive?.content?.toInt())
        assertEquals("tok", model.settings.value?.pushbulletAccessToken)
    }

    @Test fun `bad details are refused without asking the server`() = runTest {
        dispatch()
        val model = model()
        model.load()
        val before = server.requestCount
        val draft = model.settings.value!!.with(NotificationChannel.Pushbullet, NotificationKind.MediaAvailable, true)
        assertEquals(SaveResult.Invalid(Problem.PushbulletTokenMissing), model.save(draft, NotificationChannel.Pushbullet))
        assertEquals(before, server.requestCount)
    }

    @Test fun `a refused save reports the server's message and keeps what was loaded`() = runTest {
        dispatch(save = { json("""{"message":"Nope"}""", code = 400) })
        val model = model()
        model.load()
        val loadedSettings = model.settings.value
        val result = model.save(loadedSettings!!.copy(pushbulletAccessToken = "tok"), NotificationChannel.Email)
        assertEquals(SaveResult.Failed("Nope"), result)
        assertEquals(loadedSettings, model.settings.value)
    }
}
