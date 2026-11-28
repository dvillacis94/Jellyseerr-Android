package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.Chunk
import com.nesoi.seerr.android.media.MediaFormat
import com.nesoi.seerr.android.media.PagedList
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.requests.RequestSort
import com.nesoi.seerr.android.requests.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Profile › Issues: a filterable, paged list. People with only "report issues" see their own. */
class IssuesModel(private val api: ApiClient, user: User) {
    val rules = IssueRules(user)

    private val _filter = MutableStateFlow(IssueFilter.Open)
    val filter: StateFlow<IssueFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(RequestSort.Added)
    val sort: StateFlow<RequestSort> = _sort.asStateFlow()

    private val _list = MutableStateFlow(newList())

    /** The list for the current filter and sort. It is replaced, not reloaded, when either changes. */
    val list: StateFlow<PagedList<IssueRow>> = _list.asStateFlow()

    /** Whether to say who reported each issue: only useful when it isn't always you. */
    val showsReporter: Boolean get() = rules.canViewOthers

    fun setFilter(filter: IssueFilter) {
        if (filter == _filter.value) return
        _filter.value = filter
        _list.value = newList()
    }

    fun setSort(sort: RequestSort) {
        if (sort == _sort.value) return
        _sort.value = sort
        _list.value = newList()
    }

    /** Counts the changes made to issues, so a title's page knows to fetch its own issues again. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** After an issue is reported or changed, so the list and any title page show what the server now says. */
    suspend fun reload() {
        _revision.value++
        _list.value.reload()
    }

    private fun newList() = PagedList<IssueRow>(key = { it.id }) { offset -> fetchRows(offset) }

    private suspend fun fetchRows(offset: Int): Chunk<IssueRow> {
        val page: IssuePage = api.fetch(Routes.issues(offset, PAGE_SIZE, _filter.value.value, _sort.value.value))
        val rows = coroutineScope { page.results.map { async { row(api, it) } }.awaitAll() }
        return Chunk(rows, received = page.results.size, hasMore = offset + page.results.size < page.pageInfo.results)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

/** The title an issue is about, fetched since the list carries only its ids. A failure leaves it unnamed. */
internal suspend fun row(api: ApiClient, issue: Issue): IssueRow {
    val summary = summary(api, issue)
    return IssueRow(issue, summary?.displayTitle, summary?.posterPath, MediaFormat.year(summary?.date))
}

internal suspend fun summary(api: ApiClient, issue: Issue): TitleSummary? {
    val tmdbId = issue.media?.tmdbId?.takeIf { it > 0 } ?: return null
    return try {
        when (issue.titleType) {
            TitleType.Movie -> api.fetch<TitleSummary>(Routes.movieDetail(tmdbId))
            TitleType.Series -> api.fetch<TitleSummary>(Routes.tvDetail(tmdbId))
            null -> null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
