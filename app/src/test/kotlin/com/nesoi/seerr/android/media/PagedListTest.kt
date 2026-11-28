package com.nesoi.seerr.android.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedListTest {
    private val all = (1..25).toList()

    private fun list(log: MutableList<Int> = mutableListOf(), failOnce: Boolean = false): PagedList<Int> {
        var failed = !failOnce
        return PagedList(key = { it }) { offset ->
            log += offset
            if (!failed) { failed = true; error("boom") }
            val chunk = all.drop(offset).take(10)
            Chunk(chunk, received = chunk.size, hasMore = offset + chunk.size < all.size)
        }
    }

    @Test fun `loads by the number of rows received`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val list = list(offsets)
        list.loadIfNeeded()
        list.loadMoreIfNeeded(index = 9)
        list.loadMoreIfNeeded(index = 19)

        assertEquals(all, list.state.value.items)
        assertEquals(listOf(0, 10, 20), offsets)
        assertFalse(list.state.value.hasMore)
        list.loadMoreIfNeeded(index = 24)
        assertEquals(3, offsets.size)
    }

    @Test fun `waits until the end is near`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val list = list(offsets)
        list.loadIfNeeded()
        list.loadMoreIfNeeded(index = 0)
        assertEquals(listOf(0), offsets)
    }

    @Test fun `rows that are repeated are kept once, and the offset still counts them`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val list = PagedList<Int>(key = { it }) { offset ->
            offsets += offset
            val chunk = if (offset == 0) listOf(1, 2, 3) else listOf(3, 4)
            Chunk(chunk, received = chunk.size, hasMore = offset == 0)
        }
        list.loadIfNeeded()
        list.loadMoreIfNeeded(2)
        assertEquals(listOf(1, 2, 3, 4), list.state.value.items)
        assertEquals(listOf(0, 3), offsets)
    }

    @Test fun `a failed first load can be retried`() = runBlocking {
        val list = list(failOnce = true)
        list.loadIfNeeded()
        assertTrue(list.state.value.isFailed)
        list.retry()
        assertEquals(10, list.state.value.items.size)
        assertFalse(list.state.value.isFailed)
    }

    @Test fun `a reload starts over and keeps the rows until it lands`() = runBlocking {
        val list = list()
        list.loadIfNeeded()
        list.loadMoreIfNeeded(9)
        list.reload()
        assertEquals((1..10).toList(), list.state.value.items)
        assertTrue(list.state.value.hasMore)
    }
}
