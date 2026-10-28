package io.github.shoalapps.pufferfish.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps URL resolutions visible until the download manager has acknowledged them. */
internal class DownloadState {
    private val lock = Any()
    private val resolving = mutableMapOf<String, Long>()
    private var nextToken = 0L
    private var indexedPending = emptySet<String>()
    private val _done = MutableStateFlow<Set<String>>(emptySet())
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val done: StateFlow<Set<String>> = _done
    val pending: StateFlow<Set<String>> = _pending

    fun reserve(id: String): Long? = synchronized(lock) {
        if (id in _done.value || id in _pending.value) return@synchronized null
        val token = ++nextToken
        resolving[id] = token
        publish()
        token
    }

    fun release(id: String, token: Long) = synchronized(lock) {
        if (resolving[id] == token) resolving.remove(id)
        publish()
    }

    fun reconcile(done: Set<String>, pending: Set<String>) = synchronized(lock) {
        (done + pending).forEach(resolving::remove)
        indexedPending = pending
        _done.value = done
        publish()
    }

    private fun publish() { _pending.value = indexedPending + resolving.keys }
}
