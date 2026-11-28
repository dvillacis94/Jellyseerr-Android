package com.nesoi.seerr.android.users

import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** What the edit screen shows: the user as the server last said, and the edits so far. */
data class UserEditState(
    val user: User,
    val permissions: Int,
    val movieQuota: QuotaOverride = QuotaOverride.of(null, null, null, null),
    val tvQuota: QuotaOverride = QuotaOverride.of(null, null, null, null),
    /** Their settings, loaded only when their limits can be edited. */
    val general: JsonObject? = null,
    val loadError: Throwable? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** One user's permissions and request limits, edited by someone with Manage Users. */
class UserEditModel(
    private val api: ApiClient,
    user: User,
    actor: User,
    private val serverSettings: PublicSettings?,
) {
    val rules = UserManagementRules(actor)
    val options: List<PermissionOption> get() = PermissionOption.all

    private val _state = MutableStateFlow(UserEditState(user, user.permissions))
    val state: StateFlow<UserEditState> = _state.asStateFlow()

    private val user: User get() = _state.value.user

    val canEditPermissions: Boolean get() = rules.canEditPermissions(user)
    val canEditLimits: Boolean get() = rules.canEditLimits(user)

    /** "Plex", "Jellyfin" or "Emby", for the watchlist options. */
    val mediaServerName: String
        get() = when (serverSettings?.mediaServerType) {
            1 -> "Plex"
            2 -> "Jellyfin"
            else -> "Emby"
        }

    fun hasChanges(state: UserEditState = _state.value) = state.permissions != state.user.permissions || limitsChanged(state)

    private fun limitsChanged(state: UserEditState): Boolean {
        val general = state.general ?: return false
        if (!rules.canEditLimits(state.user)) return false
        return state.movieQuota.limitValue != general.int("movieQuotaLimit") || state.movieQuota.daysValue != general.int("movieQuotaDays") ||
            state.tvQuota.limitValue != general.int("tvQuotaLimit") || state.tvQuota.daysValue != general.int("tvQuotaDays")
    }

    fun optionState(option: PermissionOption, parent: PermissionOption?, state: UserEditState = _state.value): PermissionOption.State {
        val result = option.state(
            value = state.permissions,
            parent = parent,
            editingOwner = state.user.id == UserManagementRules.OWNER_ID,
            actorIsOwner = rules.isOwner,
            movie4kEnabled = serverSettings?.movie4kEnabled ?: false,
            series4kEnabled = serverSettings?.series4kEnabled ?: false,
        )
        return if (canEditPermissions) result else result.copy(isEnabled = false)
    }

    fun toggle(option: PermissionOption) = _state.update { it.copy(permissions = it.permissions xor option.permission) }

    fun setMovieQuota(quota: QuotaOverride) = _state.update { it.copy(movieQuota = quota) }
    fun setTvQuota(quota: QuotaOverride) = _state.update { it.copy(tvQuota = quota) }

    /** Loads their settings, when there are limits to edit. */
    suspend fun load() {
        if (!rules.canEditLimits(user) || _state.value.general != null) return
        try {
            apply(api.fetch<JsonObject>(Routes.userGeneralSettings(user.id)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(loadError = error) }
        }
    }

    private fun apply(settings: JsonObject) = _state.update {
        it.copy(
            general = settings,
            loadError = null,
            movieQuota = QuotaOverride.of(settings.int("movieQuotaLimit"), settings.int("movieQuotaDays"), settings.int("globalMovieQuotaLimit"), settings.int("globalMovieQuotaDays")),
            tvQuota = QuotaOverride.of(settings.int("tvQuotaLimit"), settings.int("tvQuotaDays"), settings.int("globalTvQuotaLimit"), settings.int("globalTvQuotaDays")),
        )
    }

    /**
     * Saves permissions, then limits. Returns the user as the server now has them, or null when
     * nothing was saved or it failed; the edits stay so they can be saved again.
     */
    suspend fun save(): User? {
        val current = _state.value
        if (!hasChanges(current) || current.isSaving) return null
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            if (current.permissions != current.user.permissions) {
                api.requestUnit(Routes.saveUserPermissions(current.user.id, current.permissions))
            }
            val general = current.general
            if (general != null && limitsChanged(current)) {
                // The server overwrites every setting with what it is sent, so send the rest back as it was.
                val body = JsonObject(
                    general.filterKeys { !it.startsWith("global") } + mapOf(
                        "movieQuotaLimit" to current.movieQuota.limitValue.json(),
                        "movieQuotaDays" to current.movieQuota.daysValue.json(),
                        "tvQuotaLimit" to current.tvQuota.limitValue.json(),
                        "tvQuotaDays" to current.tvQuota.daysValue.json(),
                    ),
                )
                api.requestUnit(Routes.saveUserGeneralSettings(current.user.id, body))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(isSaving = false, error = error.message ?: "") }
            return null
        }
        refresh()
        _state.update { it.copy(isSaving = false) }
        return user
    }

    /** Reads the user and their settings again, so the screen shows what the server kept. */
    private suspend fun refresh() {
        try {
            val updated = api.fetch<User>(Routes.user(user.id))
            _state.update { it.copy(user = updated, permissions = updated.permissions) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
        if (rules.canEditLimits(user)) {
            try {
                apply(api.fetch<JsonObject>(Routes.userGeneralSettings(user.id)))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        } else {
            _state.update { it.copy(general = null) }
        }
    }

    private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull
    private fun Int?.json() = if (this == null) JsonNull else JsonPrimitive(this)
}
