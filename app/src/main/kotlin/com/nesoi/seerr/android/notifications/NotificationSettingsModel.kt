package com.nesoi.seerr.android.notifications

import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** How saving a channel's settings went. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data class Invalid(val problem: Problem) : SaveResult
    data class Failed(val message: String?) : SaveResult
}

/** The signed-in user's notification channels and which events each one sends. */
class NotificationSettingsModel(
    private val api: ApiClient,
    val user: User,
    /** What the server allows everyone, such as 4K. Read when needed, as it may arrive late. */
    private val serverSettings: () -> PublicSettings?,
) {
    private val _settings = MutableStateFlow<UserNotificationSettings?>(null)
    val settings: StateFlow<UserNotificationSettings?> = _settings.asStateFlow()

    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()

    /** "Request automatically submitted" only applies when watchlist sync is on. */
    private var syncsWatchlist = false

    val isIssueManager: Boolean get() = user.has(com.nesoi.seerr.android.core.Permission.MANAGE_ISSUES)

    /** The channels the server lets this user set up. */
    fun channels(settings: UserNotificationSettings): List<NotificationChannel> = NotificationChannel.entries.filter(settings::offers)

    fun kinds(settings: UserNotificationSettings, channel: NotificationChannel): List<NotificationKind> =
        NotificationKind.availableTo(
            user = user,
            syncsWatchlist = syncsWatchlist,
            movie4kEnabled = serverSettings()?.movie4kEnabled ?: false,
            series4kEnabled = serverSettings()?.series4kEnabled ?: false,
            enabledTypes = settings.enabledTypes(channel),
        )

    /** How many of a channel's events are on: nothing, all, or a count out of those the user can pick. */
    fun summary(settings: UserNotificationSettings, channel: NotificationChannel): Summary {
        val kinds = kinds(settings, channel)
        val on = kinds.count { it.isOn(settings.types(channel)) }
        return when {
            kinds.isEmpty() || on == 0 -> Summary.Off
            on == kinds.size -> Summary.All
            else -> Summary.Some(on, kinds.size)
        }
    }

    sealed interface Summary {
        data object Off : Summary
        data object All : Summary
        data class Some(val on: Int, val of: Int) : Summary
    }

    suspend fun load() {
        _loadError.value = null
        try {
            coroutineScope {
                val general = async { runCatching { api.fetch<JsonObject>(Routes.userGeneralSettings(user.id)) }.getOrNull() }
                val loaded = api.fetch<UserNotificationSettings>(Routes.userNotificationSettings(user.id))
                val main = general.await()
                syncsWatchlist = main.flag("watchlistSyncMovies") || main.flag("watchlistSyncTv")
                _settings.value = loaded
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _loadError.value = error
        }
    }

    /** Saves one channel's edits. */
    suspend fun save(draft: UserNotificationSettings, channel: NotificationChannel): SaveResult {
        val settings = draft.normalized()
        settings.problem(channel)?.let { return SaveResult.Invalid(it) }
        // Masked to the events the admin allows, like the web app.
        val body = settings.withTypes(channel, settings.types(channel)).toRequestBody()
        try {
            api.requestUnit(Routes.saveNotificationSettings(user.id, body))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return SaveResult.Failed(error.message)
        }
        // What the server answers leaves out its own settings, so read it back.
        _settings.value = try {
            api.fetch<UserNotificationSettings>(Routes.userNotificationSettings(user.id))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            settings.copy(
                emailEnabled = _settings.value?.emailEnabled,
                discordEnabled = _settings.value?.discordEnabled,
                discordEnabledTypes = _settings.value?.discordEnabledTypes,
                telegramEnabled = _settings.value?.telegramEnabled,
                telegramBotUsername = _settings.value?.telegramBotUsername,
            )
        }
        return SaveResult.Saved
    }

    private fun JsonObject?.flag(name: String) = (this?.get(name) as? JsonPrimitive)?.booleanOrNull == true
}
