package com.nesoi.seerr.android.requests

import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Route
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.Chunk
import com.nesoi.seerr.android.media.MediaFormat
import com.nesoi.seerr.android.media.PagedList
import com.nesoi.seerr.android.media.TitleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something a person with the right to can do to a request. */
enum class RequestAction(val path: String?) {
    Approve("approve"), Decline("decline"), Retry("retry"), Delete(null),
}

/** The Requests tab: the list for the chosen filter and sort, and what can be done to a row. */
class RequestsModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    val user: User,
) {
    private val _filter = MutableStateFlow(RequestFilter.All)
    val filter: StateFlow<RequestFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(RequestSort.Added)
    val sort: StateFlow<RequestSort> = _sort.asStateFlow()

    private val _list = MutableStateFlow(newList())

    /** The list for the current filter and sort. It is replaced, not reloaded, when either changes. */
    val list: StateFlow<PagedList<RequestRow>> = _list.asStateFlow()

    /** Requests with an action under way. */
    private val _busy = MutableStateFlow<Set<Int>>(emptySet())
    val busy: StateFlow<Set<Int>> = _busy.asStateFlow()

    /** The last action that failed, until it is dismissed. */
    private val _actionError = MutableStateFlow<Throwable?>(null)
    val actionError: StateFlow<Throwable?> = _actionError.asStateFlow()

    val canManage: Boolean get() = user.canManageRequests

    private fun newList() = PagedList<RequestRow>(key = { it.id }) { offset -> fetchRows(offset) }

    fun setFilter(filter: RequestFilter) {
        if (filter == _filter.value) return
        _filter.value = filter
        _list.value = newList()
    }

    fun setSort(sort: RequestSort) {
        if (sort == _sort.value) return
        _sort.value = sort
        _list.value = newList()
    }

    /** Whether this person can remove the request: managers always, others only their own while it waits. */
    fun canDelete(row: RequestRow): Boolean =
        canManage || row.request.requestedBy?.id == user.id && row.request.requestStatus == RequestStatus.Pending

    fun actionsFor(row: RequestRow): List<RequestAction> = buildList {
        if (canManage) {
            when (row.request.requestStatus) {
                RequestStatus.Pending -> { add(RequestAction.Approve); add(RequestAction.Decline) }
                RequestStatus.Failed -> add(RequestAction.Retry)
                else -> Unit
            }
        }
        if (canDelete(row)) add(RequestAction.Delete)
    }

    /** Runs [action] on the row, then reloads the list so it shows what the server now says. */
    fun perform(action: RequestAction, row: RequestRow, onDone: () -> Unit = {}) {
        if (row.id in _busy.value) return
        _busy.update { it + row.id }
        scope.launch {
            try {
                val route: Route = when (action) {
                    RequestAction.Delete -> Routes.deleteRequest(row.id)
                    else -> Routes.requestAction(row.id, action.path!!)
                }
                api.requestUnit(route)
                _list.value.reload()
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionError.value = error
            } finally {
                _busy.update { it - row.id }
            }
        }
    }

    fun dismissActionError() {
        _actionError.value = null
    }

    private suspend fun fetchRows(offset: Int): Chunk<RequestRow> {
        val page: RequestPage = api.fetch(Routes.requests(offset, PAGE_SIZE, _filter.value.value, _sort.value.value))
        val rows = coroutineScope { page.results.map { async { row(it) } }.awaitAll() }
        return Chunk(rows, received = page.results.size, hasMore = offset + page.results.size < page.pageInfo.results)
    }

    private suspend fun row(request: MediaRequest): RequestRow {
        val summary = try {
            when (request.titleType) {
                TitleType.Movie -> api.fetch<TitleSummary>(Routes.movieDetail(request.media.tmdbId))
                TitleType.Series -> api.fetch<TitleSummary>(Routes.tvDetail(request.media.tmdbId))
                null -> null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        return RequestRow(request, summary?.displayTitle, summary?.posterPath, MediaFormat.year(summary?.date))
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
