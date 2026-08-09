package com.m3u.smartphone.ui

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.insertHeaderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The search screen merges a Pager with extension results through [combine].
 *
 * combine re-emits the latest value of every source whenever any single one of
 * them changes, so the very same PagingData reaches the collector more than
 * once — and a PagingData taken straight from a Pager may only be collected
 * once. Without a cachedIn in between, Paging throws and the crash takes the
 * whole process down on the first keystroke.
 */
class AppViewModelSearchPagingTest {
    @Test
    fun `a cached stream tolerates the re-emission combine causes`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val promoted = MutableSharedFlow<List<String>>(replay = 1)
            val presenter = SilentPagingDataPresenter()

            val collection = launch {
                combinedSearchResults(
                    local = pagerFlow().cachedIn(scope),
                    promoted = promoted,
                ).collectLatest(presenter::collectFrom)
            }

            // The empty placeholder every search emits before its results land.
            promoted.emit(emptyList())
            settle()
            assertEquals(listOf("local"), presenter.snapshot().items)

            // The results themselves: same PagingData, second collection.
            promoted.emit(listOf("promoted"))
            settle()
            assertEquals(listOf("promoted", "local"), presenter.snapshot().items)

            collection.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `an uncached stream is rejected on that second collection`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val promoted = MutableSharedFlow<List<String>>(replay = 1)
            val presenter = SilentPagingDataPresenter()
            var failure: Throwable? = null

            val collection = launch {
                runCatching {
                    combinedSearchResults(
                        local = pagerFlow(),
                        promoted = promoted,
                    ).collectLatest(presenter::collectFrom)
                }.onFailure { failure = it }
            }

            promoted.emit(emptyList())
            settle()
            promoted.emit(listOf("promoted"))
            settle()

            val message = failure?.message.orEmpty()
            assertTrue(
                "expected Paging to reject the second collection, got: $failure",
                message.contains("collect twice", ignoreCase = true),
            )
            collection.cancel()
        }
    }

    /** Mirrors how AppViewModel folds promoted results onto the paged ones. */
    private fun combinedSearchResults(
        local: Flow<PagingData<String>>,
        promoted: Flow<List<String>>,
    ): Flow<PagingData<String>> = combine(local, promoted) { paging, items ->
        items.asReversed().fold(paging) { acc, item -> acc.insertHeaderItem(item = item) }
    }

    private fun pagerFlow(): Flow<PagingData<String>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
    ) {
        SinglePagePagingSource(listOf("local"))
    }.flow

    /** Paging hops between dispatchers; give those hops room to complete. */
    private suspend fun settle() = repeat(SETTLE_PASSES) { yield() }

    private class SinglePagePagingSource(
        private val items: List<String>,
    ) : PagingSource<Int, String>() {
        override fun getRefreshKey(state: PagingState<Int, String>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> =
            LoadResult.Page(data = items, prevKey = null, nextKey = null)
    }

    /**
     * Presents nothing — the point is that collecting happens at all. The base
     * class keeps the snapshot up to date on its own.
     */
    private class SilentPagingDataPresenter : PagingDataPresenter<String>(
        mainContext = EmptyCoroutineContext,
    ) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<String>) = Unit
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 10_000L
        const val SETTLE_PASSES = 50
    }
}
