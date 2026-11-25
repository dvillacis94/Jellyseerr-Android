package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.AuthFixture
import com.nesoi.seerr.android.USER_JSON
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LoginModelTest {
    private val seerr = MockWebServer()
    private val plexServer = MockWebServer()
    private lateinit var fixture: AuthFixture
    private var signedIn: User? = null

    @Before fun start() {
        seerr.start()
        plexServer.start()
        fixture = AuthFixture(seerr.url("/"))
        fixture.api.baseUrl = seerr.url("/")
    }

    @After fun stop() {
        seerr.close()
        plexServer.close()
    }

    private fun kotlinx.coroutines.test.TestScope.model(settings: PublicSettings) = LoginModel(
        scope = backgroundScope,
        auth = fixture.auth,
        plex = PlexAuthService(PlexClientInfo("c", "Seerr Android", "1", "Android", "Pixel"), OkHttpClient(), plexServer.url("/")),
        settings = settings,
        serverUrl = seerr.url("/"),
        onSignedIn = { signedIn = it },
    )

    private val plexSettings = PublicSettings(true, mediaServerType = 1, localLogin = true)
    private val jellyfinSettings = PublicSettings(true, mediaServerType = 2, localLogin = true)

    private fun sessionResponse() = MockResponse.Builder().code(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("Set-Cookie", "connect.sid=abc; Path=/; Max-Age=3600")
        .body(USER_JSON).build()

    @Test fun `the media server form is first when the server has one`() = runTest {
        assertEquals(PasswordMode.MediaServer, model(jellyfinSettings).state.value.passwordMode)
        assertEquals(PasswordMode.Local, model(plexSettings).state.value.passwordMode)
    }

    @Test fun `the password button waits for both fields`() = runTest {
        val model = model(jellyfinSettings)
        assertFalse(model.state.value.canSubmitPassword())
        model.setUsername("me")
        assertFalse(model.state.value.canSubmitPassword())
        model.setPassword("pw")
        assertTrue(model.state.value.canSubmitPassword())
        model.setUsername("   ")
        assertFalse(model.state.value.canSubmitPassword())
    }

    @Test fun `signing in with a password hands over the user and clears the password`() = runTest {
        seerr.enqueue(sessionResponse())
        val model = model(plexSettings)
        model.setEmail(" me@example.com ")
        model.setPassword("secret")

        model.signInWithPassword()
        model.state.first { !it.isSigningIn && it.password.isEmpty() }

        assertEquals(3, signedIn?.id)
        assertEquals("""{"email":"me@example.com","password":"secret"}""", seerr.takeRequest().body!!.utf8())
    }

    @Test fun `a wrong password shows a failure and keeps the form`() = runTest {
        seerr.enqueue(json("""{"message":"INVALID_CREDENTIALS"}""", 401))
        val model = model(plexSettings)
        model.setEmail("me@example.com")
        model.setPassword("nope")

        model.signInWithPassword()
        val state = model.state.first { it.failure != null }

        assertEquals(SignInFailure.InvalidCredentials, state.failure)
        assertFalse(state.isSigningIn)
        assertEquals("nope", state.password)
        assertNull(signedIn)
    }

    @Test fun `plex sign in opens the approval page and signs in with the token`() = runTest {
        plexServer.enqueue(json("""{"id":7,"code":"ABCD","expiresAt":"${Instant.now().plusSeconds(600)}"}"""))
        plexServer.enqueue(json("""{"id":7,"code":"ABCD","authToken":"tok"}"""))
        seerr.enqueue(sessionResponse())
        val model = model(plexSettings)

        model.signInWithPlex()
        assertTrue(model.state.value.isWaitingForPlex)
        assertTrue(model.browserUrls.first().startsWith("https://app.plex.tv/auth/#!?"))
        model.state.first { signedIn != null && !it.isSigningIn }

        assertEquals("""{"authToken":"tok"}""", seerr.takeRequest().body!!.utf8())
        assertFalse(model.state.value.isWaitingForPlex)
    }

    @Test fun `cancelling plex stops waiting without an error`() = runTest {
        plexServer.enqueue(json("""{"id":7,"code":"ABCD","expiresAt":"${Instant.now().plusSeconds(600)}"}"""))
        repeat(50) { plexServer.enqueue(json("""{"id":7,"code":"ABCD"}""")) }
        val model = model(plexSettings)

        model.signInWithPlex()
        model.browserUrls.first()
        model.cancelPlex()

        val state = model.state.value
        assertFalse(state.isSigningIn)
        assertFalse(state.isWaitingForPlex)
        assertNull(state.failure)
        assertNull(signedIn)
    }

    @Test fun `plex being unreachable is reported`() = runTest {
        plexServer.close()
        val model = model(plexSettings)

        model.signInWithPlex()
        val state = model.state.first { it.failure != null }

        assertEquals(SignInFailure.Unreachable, state.failure)
        assertFalse(state.isWaitingForPlex)
    }
}
