package io.github.shoalapps.pufferfish.player

import org.junit.Assert.*
import org.junit.Test

class DownloadStateTest {
    @Test fun refreshKeepsUnacknowledgedReservationsAndPreventsDuplicates() {
        val state = DownloadState()
        val token = state.reserve("song")!!
        state.reconcile(emptySet(), setOf("other"))
        assertEquals(setOf("song", "other"), state.pending.value)
        assertNull(state.reserve("song"))
        state.reconcile(emptySet(), setOf("song"))
        state.release("song", token)
        assertEquals(setOf("song"), state.pending.value)
        state.reconcile(setOf("song"), emptySet())
        assertEquals(setOf("song"), state.done.value)
        assertNull(state.reserve("song"))
    }

    @Test fun concurrentRequestsReserveTheSameSongOnlyOnce() {
        val state = DownloadState()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val ready = java.util.concurrent.CountDownLatch(1)
            val requests = (1..32).map { executor.submit<Long?> { ready.await(); state.reserve("song") } }
            ready.countDown()
            assertEquals(1, requests.count { it.get() != null })
            assertEquals(setOf("song"), state.pending.value)
        } finally { executor.shutdownNow() }
    }

    @Test fun staleTimeoutCannotReleaseANewerReservation() {
        val state = DownloadState()
        val old = state.reserve("song")!!
        state.release("song", old)
        val current = state.reserve("song")!!
        state.release("song", old)
        assertEquals(setOf("song"), state.pending.value)
        state.release("song", current)
        assertTrue(state.pending.value.isEmpty())
    }
}
