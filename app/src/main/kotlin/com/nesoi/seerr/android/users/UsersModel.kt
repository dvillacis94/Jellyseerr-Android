package com.nesoi.seerr.android.users

import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.Chunk
import com.nesoi.seerr.android.media.PagedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
private data class UserListPage(val pageInfo: PageInfo = PageInfo(), val results: List<User> = emptyList()) {
    @Serializable
    data class PageInfo(val pages: Int = 0, val page: Int = 1, val results: Int = 0)
}

/** Profile › Users: everyone on the server, searchable, for people with Manage Users. */
class UsersModel(private val api: ApiClient, actor: User) {
    val rules = UserManagementRules(actor)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _total = MutableStateFlow(0)

    /** How many users match the search, not just how many are loaded. */
    val total: StateFlow<Int> = _total.asStateFlow()

    private val _list = MutableStateFlow(newList())

    /** The list for the current search. It is replaced, not reloaded, when the search changes. */
    val list: StateFlow<PagedList<User>> = _list.asStateFlow()

    /** Searches by name or email. The screen waits for typing to pause before calling this. */
    fun search(text: String) {
        val trimmed = text.trim()
        if (trimmed == _query.value) return
        _query.value = trimmed
        _list.value = newList()
    }

    /** After someone's permissions or limits change, so the row shows what the server kept. */
    fun didUpdate(user: User) = _list.value.replace(user)

    /** Deletes a user and takes the row away. Returns what went wrong, or null when it worked. */
    suspend fun delete(user: User): String? {
        if (!rules.canDelete(user)) return ""
        try {
            api.requestUnit(Routes.deleteUser(user.id))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return error.message ?: ""
        }
        _list.value.remove(user)
        _total.value = (_total.value - 1).coerceAtLeast(0)
        return null
    }

    private fun newList(): PagedList<User> {
        val search = _query.value
        return PagedList(key = { it.id }) { offset ->
            val page: UserListPage = api.fetch(Routes.users(offset, PAGE_SIZE, search))
            // A reply for a search the screen has moved on from must not change the count.
            if (search == _query.value) _total.value = page.pageInfo.results
            Chunk(page.results, received = page.results.size, hasMore = page.results.isNotEmpty() && offset + page.results.size < page.pageInfo.results)
        }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
