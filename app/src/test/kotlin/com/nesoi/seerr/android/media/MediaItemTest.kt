package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.seerrJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {
    private fun page(vararg results: String) =
        seerrJson.decodeFromString<MediaPage>("""{"page":1,"totalPages":3,"totalResults":60,"results":[${results.joinToString(",")}]}""")

    @Test fun `each result decodes to its type`() {
        val items = page(
            """{"id":1,"mediaType":"movie","title":"A Movie","posterPath":"/a.jpg","releaseDate":"2019-05-01"}""",
            """{"id":2,"mediaType":"tv","name":"A Show","firstAirDate":"2021-09-10"}""",
            """{"id":3,"mediaType":"person","name":"Someone","profilePath":"/p.jpg"}""",
            """{"id":4,"mediaType":"collection","title":"A Collection"}""",
        ).results

        assertEquals(listOf("A Movie", "A Show", "Someone", "A Collection"), items.map { it.title })
        assertTrue(items[0] is MediaItem.Movie && items[1] is MediaItem.Series && items[2] is MediaItem.Person && items[3] is MediaItem.Collection)
        assertEquals("/p.jpg", items[2].posterPath)
        assertEquals(listOf("2019", "2021", null, null), items.map { it.year })
    }

    @Test fun `a result without a type is a movie`() {
        assertTrue(page("""{"id":9,"title":"Plain"}""").results.single() is MediaItem.Movie)
    }

    @Test fun `an unknown type doesn't fail the page`() {
        val items = page("""{"id":1,"mediaType":"hologram"}""", """{"id":2,"mediaType":"movie","title":"Kept"}""").results
        assertEquals(2, items.size)
        assertEquals("Kept", items.last().title)
    }

    @Test fun `the same id as a movie and a series gives different keys`() {
        val items = page("""{"id":7,"mediaType":"movie"}""", """{"id":7,"mediaType":"tv"}""").results
        assertTrue(items[0].key != items[1].key)
    }

    @Test fun `library status comes from mediaInfo`() {
        val movie = page("""{"id":1,"mediaType":"movie","mediaInfo":{"id":5,"status":5}}""").results.single()
        assertEquals(MediaStatus.Available, movie.mediaInfo?.mediaStatus)
        val missing = page("""{"id":2,"mediaType":"movie"}""").results.single()
        assertNull(missing.mediaInfo)
        assertEquals(MediaStatus.Unknown, MediaStatus.from(99))
        assertTrue(!MediaStatus.Unknown.isNoteworthy && MediaStatus.Pending.isNoteworthy)
    }

    @Test fun `paging knows when there is more`() {
        assertTrue(page().hasMore)
        assertTrue(!seerrJson.decodeFromString<MediaPage>("""{"page":3,"totalPages":3,"results":[]}""").hasMore)
    }
}
