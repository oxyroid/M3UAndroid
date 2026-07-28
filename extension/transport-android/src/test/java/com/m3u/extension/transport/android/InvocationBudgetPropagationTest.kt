package com.m3u.extension.transport.android

import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionInvocationBudget
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.runtime.HostInvocationDeadlineExceededException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class InvocationBudgetPropagationTest {
    @Test
    fun `transport dispatch subtracts elapsed time from the shared budget`() {
        val request = envelope(
            ExtensionInvocationBudget(
                remainingTimeMillis = 5_000,
                maxBrokerRequests = 8,
                maxBrokerRequestBytes = 4_096,
                maxBrokerResponseBytes = 8_192,
            )
        )

        val dispatched = request.afterTransportElapsed(1_250)

        assertEquals(3_750L, dispatched.invocationBudget?.remainingTimeMillis)
        assertEquals(8, dispatched.invocationBudget?.maxBrokerRequests)
        assertEquals(4_096L, dispatched.invocationBudget?.maxBrokerRequestBytes)
        assertEquals(8_192L, dispatched.invocationBudget?.maxBrokerResponseBytes)
    }

    @Test
    fun `transport refuses dispatch after the shared deadline`() {
        val request = envelope(
            ExtensionInvocationBudget(
                remainingTimeMillis = 100,
                maxBrokerRequests = 1,
                maxBrokerRequestBytes = 1,
                maxBrokerResponseBytes = 1,
            )
        )

        assertFailsWith<HostInvocationDeadlineExceededException> {
            request.afterTransportElapsed(100)
        }
        assertFailsWith<HostInvocationDeadlineExceededException> {
            request.afterTransportElapsed(Long.MAX_VALUE)
        }
    }

    @Test
    fun `legacy request without a budget remains unchanged`() {
        val request = envelope(invocationBudget = null)

        assertSame(request, request.afterTransportElapsed(500))
    }

    private fun envelope(
        invocationBudget: ExtensionInvocationBudget?,
    ) = SerializedExtensionEnvelope(
        apiVersion = ExtensionApiVersion(1, 0),
        invocationId = InvocationId("invocation-budget-test"),
        extensionId = ExtensionId("com.example.transport"),
        hook = ExtensionHookIds.PlaybackSourceResolve,
        schemaVersion = 1,
        payload = JsonObject(emptyMap()),
        invocationBudget = invocationBudget,
    )
}
