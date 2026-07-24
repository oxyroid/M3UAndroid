package com.m3u.smartphone.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelSearchFlowTest {
    @Test
    fun `result from another query is ignored even before the clear signal arrives`() {
        val stale = QuerySearchResults("old", listOf("stale-result"))

        assertEquals(emptyList<String>(), stale.itemsFor("new"))
        assertEquals(listOf("stale-result"), stale.itemsFor("old"))
    }

    @Test
    fun `new query clears promoted results before its search completes`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val queries = MutableSharedFlow<String>(replay = 1)
            val newQueryResult = CompletableDeferred<List<String>>()
            val emissions = Channel<QuerySearchResults<String>>(Channel.UNLIMITED)
            val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                queries.mapLatestSearchResults { query ->
                    when (query) {
                        "old" -> listOf("old-result")
                        "new" -> newQueryResult.await()
                        else -> error("Unexpected query: $query")
                    }
                }.collect(emissions::send)
            }

            queries.emit("old")
            assertEquals(
                QuerySearchResults<String>("old", emptyList()),
                emissions.receive(),
            )
            assertEquals(
                QuerySearchResults("old", listOf("old-result")),
                emissions.receive(),
            )

            queries.emit("new")
            assertEquals(
                QuerySearchResults<String>("new", emptyList()),
                emissions.receive(),
            )

            newQueryResult.complete(listOf("new-result"))
            assertEquals(
                QuerySearchResults("new", listOf("new-result")),
                emissions.receive(),
            )
            collection.cancel()
        }
    }

    @Test
    fun `cancelled older search cannot emit after a newer query`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val queries = MutableSharedFlow<String>(replay = 1)
            val oldQueryResult = CompletableDeferred<List<String>>()
            val newQueryResult = CompletableDeferred<List<String>>()
            val emissions = Channel<QuerySearchResults<String>>(Channel.UNLIMITED)
            val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                queries.mapLatestSearchResults { query ->
                    when (query) {
                        "old" -> oldQueryResult.await()
                        "new" -> newQueryResult.await()
                        else -> error("Unexpected query: $query")
                    }
                }.collect(emissions::send)
            }

            queries.emit("old")
            assertEquals(
                QuerySearchResults<String>("old", emptyList()),
                emissions.receive(),
            )

            queries.emit("new")
            assertEquals(
                QuerySearchResults<String>("new", emptyList()),
                emissions.receive(),
            )

            oldQueryResult.complete(listOf("stale-result"))
            newQueryResult.complete(listOf("current-result"))
            assertEquals(
                QuerySearchResults("new", listOf("current-result")),
                emissions.receive(),
            )
            collection.cancel()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
