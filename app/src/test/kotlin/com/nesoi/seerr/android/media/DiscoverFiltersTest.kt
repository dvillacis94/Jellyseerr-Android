package com.nesoi.seerr.android.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverFiltersTest {
    @Test fun `no filters send nothing`() {
        assertTrue(DiscoverFilters().queryItems(TitleType.Movie).isEmpty())
        assertTrue(DiscoverFilters().isEmpty)
    }

    @Test fun `movie filters use the server's formats`() {
        val filters = DiscoverFilters(
            sort = "vote_average.desc",
            genres = setOf(28, 12),
            yearFrom = 1990,
            yearTo = 1999,
            language = "ja",
            minScore = 7,
            runtimeMin = 60,
            runtimeMax = 120,
            providers = setOf(8, 337),
            region = "US",
            statuses = setOf(3),
        )
        assertEquals(
            listOf(
                "sortBy" to "vote_average.desc",
                "genre" to "12,28",
                "primaryReleaseDateGte" to "1990-01-01",
                "primaryReleaseDateLte" to "1999-12-31",
                "language" to "ja",
                "withRuntimeGte" to "60",
                "withRuntimeLte" to "120",
                "voteAverageGte" to "7",
                "watchProviders" to "8|337",
                "watchRegion" to "US",
            ),
            filters.queryItems(TitleType.Movie),
        )
    }

    @Test fun `series use air dates and statuses`() {
        val filters = DiscoverFilters(yearFrom = 2020, statuses = setOf(4, 0))
        assertEquals(
            listOf("firstAirDateGte" to "2020-01-01", "status" to "0|4"),
            filters.queryItems(TitleType.Series),
        )
    }

    @Test fun `a region alone sends nothing, and services need one`() {
        assertTrue(DiscoverFilters(region = "US").queryItems(TitleType.Movie).isEmpty())
        assertEquals(listOf("watchProviders" to "8"), DiscoverFilters(providers = setOf(8)).queryItems(TitleType.Movie))
    }

    @Test fun `the badge counts each filter once`() {
        assertEquals(0, DiscoverFilters(region = "US").activeCount())
        assertEquals(1, DiscoverFilters(yearFrom = 2000, yearTo = 2010).activeCount())
        assertEquals(1, DiscoverFilters(runtimeMin = 30, runtimeMax = 90).activeCount())
        assertEquals(3, DiscoverFilters(genres = setOf(1, 2), minScore = 5, sort = "revenue.desc").activeCount())
    }

    @Test fun `sorting by revenue and title is for movies only`() {
        assertTrue(TitleType.Movie.sortOptions().any { it.value == "revenue.desc" })
        assertTrue(TitleType.Series.sortOptions().none { it.value.startsWith("revenue") || it.value.startsWith("original_title") })
        assertTrue(TitleType.Series.sortOptions().any { it.value == "first_air_date.desc" })
    }
}
