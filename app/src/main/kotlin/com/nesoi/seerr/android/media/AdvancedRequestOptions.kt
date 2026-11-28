package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** A Radarr or Sonarr instance Seerr can send requests to, from `GET /service/{radarr|sonarr}`. */
@Serializable
data class ServiceServer(
    val id: Int,
    val name: String = "",
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val activeLanguageProfileId: Int? = null,
    val activeAnimeProfileId: Int? = null,
    val activeAnimeDirectory: String? = null,
    val activeAnimeLanguageProfileId: Int? = null,
    val activeTags: List<Int>? = null,
    val activeAnimeTags: List<Int>? = null,
)

/** `GET /service/{radarr|sonarr}/{id}`: the instance and what it offers. */
@Serializable
data class ServiceDetails(
    val server: ServiceServer,
    val profiles: List<Named> = emptyList(),
    val rootFolders: List<RootFolder> = emptyList(),
    /** Only Sonarr v3 and older have language profiles. */
    val languageProfiles: List<Named>? = null,
    val tags: List<Tag>? = null,
) {
    @Serializable
    data class Named(val id: Int, val name: String = "")

    @Serializable
    data class RootFolder(val id: Int? = null, val path: String? = null, val freeSpace: Long? = null)

    @Serializable
    data class Tag(val id: Int, val label: String = "")
}

/** Where a request goes. A null field leaves the choice to the server. */
data class Destination(
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val languageProfileId: Int? = null,
    val tags: List<Int>? = null,
)

data class AdvancedState(
    /** The instances for this quality, standard or 4K. */
    val servers: List<ServiceServer> = emptyList(),
    val details: ServiceDetails? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val languageProfileId: Int? = null,
    val tags: Set<Int> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
) {
    /** Nothing to choose means the section is left out, as on the web app. */
    val hasChoices: Boolean
        get() {
            val d = details ?: return servers.size > 1
            return servers.size > 1 || d.profiles.size > 1 || d.rootFolders.size > 1 ||
                (d.languageProfiles?.size ?: 0) > 1 || d.tags.orEmpty().isNotEmpty()
        }

    /** The choices so far, or null until an instance's options have loaded. */
    val destination: Destination?
        get() = details?.let { Destination(serverId, profileId, rootFolder, languageProfileId, tags.sorted()) }
}

/**
 * The Destination choices for someone allowed to make them: the Radarr or Sonarr instance, quality
 * profile, root folder, language profile and tags, with the same defaults as the web app.
 */
class AdvancedRequestOptions(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    /** `radarr` for a movie, `sonarr` for a series. */
    private val kind: String,
    private val is4k: Boolean,
    private val isAnime: Boolean,
) {
    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()

    private var detailsJob: Job? = null

    fun load() {
        val now = _state.value
        if (now.loaded || now.loading) return
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val all = api.fetch<List<ServiceServer>>(Routes.serviceServers(kind))
                val servers = all.filter { it.is4k == is4k }
                _state.update { it.copy(servers = servers, loaded = true) }
                initialServer(servers)?.let { loadDetails(it.id) } ?: _state.update { it.copy(loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message.orEmpty()) }
            }
        }
    }

    /** Another instance resets the other choices to that instance's defaults. */
    fun selectServer(id: Int) {
        if (id == _state.value.serverId) return
        detailsJob?.cancel()
        detailsJob = scope.launch { loadDetails(id) }
    }

    fun setProfile(id: Int?) = _state.update { it.copy(profileId = id) }
    fun setRootFolder(path: String?) = _state.update { it.copy(rootFolder = path) }
    fun setLanguageProfile(id: Int?) = _state.update { it.copy(languageProfileId = id) }
    fun setTags(tags: Set<Int>) = _state.update { it.copy(tags = tags) }

    private suspend fun loadDetails(id: Int) {
        _state.update { it.copy(serverId = id, details = null, loading = true, error = null) }
        try {
            val loaded = api.fetch<ServiceDetails>(Routes.serviceServer(kind, id))
            if (_state.value.serverId != id) return
            val chosen = selection(loaded, isAnime)
            _state.update {
                it.copy(
                    details = loaded, loading = false,
                    profileId = chosen.profileId, rootFolder = chosen.rootFolder,
                    languageProfileId = chosen.languageProfileId, tags = chosen.tags.orEmpty().toSet(),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (_state.value.serverId == id) _state.update { it.copy(loading = false, error = error.message.orEmpty()) }
        }
    }

    companion object {
        /** The default instance for this quality, else the first. */
        fun initialServer(servers: List<ServiceServer>): ServiceServer? = servers.firstOrNull { it.isDefault } ?: servers.firstOrNull()

        /**
         * An instance's defaults, its anime ones for anime. Like the web app, a default that no
         * longer exists on the instance is left unset, so the server uses its own setting.
         */
        fun selection(details: ServiceDetails, isAnime: Boolean): Destination {
            val server = details.server
            val profile = (if (isAnime) server.activeAnimeProfileId else null) ?: server.activeProfileId
            val folder = (if (isAnime) server.activeAnimeDirectory else null) ?: server.activeDirectory
            val language = (if (isAnime) server.activeAnimeLanguageProfileId else null) ?: server.activeLanguageProfileId
            val tags = (if (isAnime) server.activeAnimeTags else server.activeTags).orEmpty()
            return Destination(
                serverId = server.id,
                profileId = profile?.takeIf { id -> details.profiles.any { it.id == id } },
                rootFolder = folder?.takeIf { path -> details.rootFolders.any { it.path == path } },
                languageProfileId = language?.takeIf { id -> details.languageProfiles?.any { it.id == id } == true },
                tags = tags,
            )
        }
    }
}
