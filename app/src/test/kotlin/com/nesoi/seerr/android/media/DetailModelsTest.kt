package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.seerrJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DetailModelsTest {
    private val movie = """
        {"id":603,"title":"The Matrix","tagline":"Free your mind.","overview":"A hacker.","releaseDate":"1999-03-30",
         "runtime":136,"genres":[{"id":28,"name":"Action"}],"posterPath":"/p.jpg","backdropPath":"/b.jpg","voteAverage":8.2,
         "status":"Released","credits":{"cast":[{"id":6384,"name":"Keanu Reeves","character":"Neo","profilePath":"/k.jpg"}]},
         "collection":{"id":2344,"name":"The Matrix Collection"},"productionCompanies":[{"id":1,"name":"Warner Bros."}],
         "relatedVideos":[{"type":"Teaser","site":"YouTube","url":"https://youtube.com/watch?v=a"},
                          {"type":"Trailer","site":"YouTube","url":"https://www.youtube.com/watch?v=b"}],
         "mediaInfo":{"id":9,"status":5},"onUserWatchlist":true,"somethingNew":1}
    """

    private val tv = """
        {"id":1396,"name":"Breaking Bad","firstAirDate":"2008-01-20","episodeRunTime":[45,47],"numberOfSeasons":5,
         "numberOfEpisodes":62,"networks":[{"id":2,"name":"AMC"}],"status":"Ended","relatedVideos":[]}
    """

    @Test fun `a movie decodes with its extras`() {
        val details = seerrJson.decodeFromString(MovieDetails.serializer(), movie)
        assertEquals("The Matrix", details.title)
        assertEquals(136, details.runtimeMinutes)
        assertEquals("Warner Bros.", details.studio)
        assertEquals("Neo", details.credits?.cast?.single()?.character)
        assertEquals(2344, details.collection?.id)
        assertEquals(true, details.onUserWatchlist)
        assertEquals(MediaStatus.Available, details.mediaInfo?.mediaStatus)
    }

    @Test fun `the trailer is the YouTube trailer, not a teaser`() {
        val details = seerrJson.decodeFromString(MovieDetails.serializer(), movie)
        assertEquals("https://www.youtube.com/watch?v=b", details.trailerUrl())
    }

    @Test fun `a trailer that is not https is not offered`() {
        val details = MovieDetails(1, relatedVideos = listOf(RelatedVideo(url = "http://youtube.com/x", type = "Trailer", site = "YouTube")))
        assertNull(details.trailerUrl())
    }

    @Test fun `a series reads its name, air date, episode length and network`() {
        val details = seerrJson.decodeFromString(TvDetails.serializer(), tv)
        assertEquals("Breaking Bad", details.title)
        assertEquals("2008-01-20", details.date)
        assertEquals(45, details.runtimeMinutes)
        assertEquals("AMC", details.studio)
        assertEquals(null, details.onUserWatchlist)
        assertEquals(TitleType.Series, details.type)
    }

    @Test fun `a title becomes the card a shelf shows`() {
        val item = seerrJson.decodeFromString(MovieDetails.serializer(), movie).asItem()
        assertEquals(MediaItem.Movie(603, "The Matrix", "/p.jpg", "1999-03-30", MediaInfo(9, 5)), item)
    }

    @Test fun `a collection's movies decode without a media type`() {
        val details = seerrJson.decodeFromString(
            CollectionDetails.serializer(),
            """{"id":1,"name":"C","parts":[{"id":5,"title":"One","releaseDate":"2001-01-01"}]}""",
        )
        assertEquals(listOf("One"), details.parts.map { it.title })
    }

    @Test fun `cards open the right page, and people have none yet`() {
        assertEquals("movie/1", MediaItem.Movie(1).detailRoute)
        assertEquals("tv/2", MediaItem.Series(2).detailRoute)
        assertEquals("collection/3", MediaItem.Collection(3).detailRoute)
        assertNull(MediaItem.Person(4).detailRoute)
    }

    @Test fun `runtime reads as hours and minutes`() {
        assertEquals("2h 15m", MediaFormat.runtime(135))
        assertEquals("45m", MediaFormat.runtime(45))
        assertEquals("1h 0m", MediaFormat.runtime(60))
        assertNull(MediaFormat.runtime(0))
        assertNull(MediaFormat.runtime(null))
    }

    @Test fun `a rating has one decimal and no rating is blank`() {
        assertEquals("7.8", MediaFormat.rating(7.84))
        assertNull(MediaFormat.rating(0.0))
        assertNull(MediaFormat.rating(null))
    }

    @Test fun `dates are written for the locale and bad ones are dropped`() {
        assertEquals("Mar 30, 1999", MediaFormat.date("1999-03-30", Locale.US))
        assertNull(MediaFormat.date("soon", Locale.US))
        assertNull(MediaFormat.date("", Locale.US))
        assertEquals("1999", MediaFormat.year("1999-03-30"))
    }
}
