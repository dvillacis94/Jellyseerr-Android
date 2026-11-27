package com.nesoi.seerr.android.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PagedLoaderTest {
    private fun movie(id: Int) = MediaItem.Movie(id = id, title = "Movie $id")
    private fun page(number: Int, of: Int, vararg ids: Int) = MediaPage(number, of, 100, ids.map(::movie))

    @Test fun `loads the first page once`() = runTest {
        val requested = mutableListOf<Int>()
        val loader = PagedLoader { requested += it; page(it, 3, 1, 2) }
        loader.loadIfNeeded()
        loader.loadIfNeeded()
        assertEquals(listOf(1), requested)
        assertEquals(listOf(1, 2), loader.state.value.items.map { it.id })
        assertTrue(loader.state.value.hasLoaded)
    }

    @Test fun `scrolling near the end loads the next page`() = runTest {
        val requested = mutableListOf<Int>()
        val loader = PagedLoader { requested += it; page(it, 3, it * 10 + 1, it * 10 + 2, it * 10 + 3, it * 10 + 4, it * 10 + 5, it * 10 + 6, it * 10 + 7, it * 10 + 8) }
        loader.loadIfNeeded()
        loader.loadMoreIfNeeded(0)
        assertEquals(listOf(1), requested)
        loader.loadMoreIfNeeded(7)
        assertEquals(listOf(1, 2), requested)
        assertEquals(16, loader.state.value.items.size)
    }

    @Test fun `stops after the last page`() = runTest {
        val requested = mutableListOf<Int>()
        val loader = PagedLoader { requested += it; page(it, 2, it) }
        loader.loadIfNeeded()
        loader.loadMoreIfNeeded(0)
        loader.loadMoreIfNeeded(1)
        assertEquals(listOf(1, 2), requested)
        assertFalse(loader.state.value.hasMore)
    }

    @Test fun `repeated titles are dropped`() = runTest {
        val loader = PagedLoader { if (it == 1) page(1, 2, 1, 2) else page(2, 2, 2, 3) }
        loader.loadIfNeeded()
        loader.loadMoreIfNeeded(1)
        assertEquals(listOf(1, 2, 3), loader.state.value.items.map { it.id })
    }

    @Test fun `a failure is kept and a retry continues`() = runTest {
        var fail = true
        val loader = PagedLoader { if (fail) throw IOException("down") else page(it, 1, 1) }
        loader.loadIfNeeded()
        assertTrue(loader.state.value.isFailed)
        fail = false
        loader.retry()
        assertNull(loader.state.value.error)
        assertEquals(listOf(1), loader.state.value.items.map { it.id })
    }

    @Test fun `a failed next page keeps the rows and can be retried`() = runTest {
        var fail = false
        val loader = PagedLoader { if (fail && it == 2) throw IOException("down") else page(it, 2, it) }
        loader.loadIfNeeded()
        fail = true
        loader.loadMoreIfNeeded(0)
        assertNotNull(loader.state.value.error)
        assertFalse(loader.state.value.isFailed)
        assertEquals(listOf(1), loader.state.value.items.map { it.id })
        fail = false
        loader.retry()
        assertEquals(listOf(1, 2), loader.state.value.items.map { it.id })
    }

    @Test fun `a cancelled load isn't an error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val loader = PagedLoader { gate.await(); page(it, 1, 1) }
        val job = async { runCatching { loader.loadIfNeeded() } }
        testScheduler.runCurrent()
        assertTrue(loader.state.value.isLoading)
        job.cancel()
        runCatching { job.await() }
        assertNull(loader.state.value.error)
        assertFalse(loader.state.value.isLoading)
        assertFalse(loader.state.value.hasLoaded)
        // Loading again works.
        gate.complete(Unit)
        loader.loadIfNeeded()
        assertEquals(1, loader.state.value.items.size)
    }

    @Test fun `a reload keeps the rows until the new ones arrive`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var round = 0
        val loader = PagedLoader {
            if (round > 0) gate.await()
            page(it, 1, round * 10 + 1)
        }
        loader.loadIfNeeded()
        round = 1
        val reload = async { loader.reload() }
        testScheduler.runCurrent()
        assertEquals(listOf(1), loader.state.value.items.map { it.id })
        assertTrue(loader.state.value.isLoading)
        gate.complete(Unit)
        reload.await()
        assertEquals(listOf(11), loader.state.value.items.map { it.id })
    }

    @Test fun `a failed reload keeps the old rows`() = runTest {
        var fail = false
        val loader = PagedLoader { if (fail) throw IOException("down") else page(it, 1, 1) }
        loader.loadIfNeeded()
        fail = true
        loader.reload()
        assertEquals(listOf(1), loader.state.value.items.map { it.id })
        assertNotNull(loader.state.value.error)
        assertFalse(loader.state.value.isFailed)
    }

    @Test fun `unsupported results are left out`() = runTest {
        val loader = PagedLoader { MediaPage(1, 1, 2, listOf(movie(1), UnsupportedItem(2))) }
        loader.loadIfNeeded()
        assertEquals(listOf(1), loader.state.value.items.map { it.id })
    }

}
