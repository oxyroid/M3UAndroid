package com.m3u.extension.sdk.android

import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerInvocationError
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.ProviderAuthenticationReceipt
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.BrokeredHttpResponse
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ExtensionHostNetworkBrokerTest {
    @Test
    fun `success result returns the broker response`() {
        val result = BrokerInvocationResult.Success(
            BrokerOperationResult.Http(
                BrokeredHttpResponse(
                    statusCode = 204,
                    headers = emptyMap(),
                    body = "",
                )
            )
        )

        val response = result.operationResultOrThrow() as BrokerOperationResult.Http
        assertEquals(204, response.response.statusCode)
    }

    @Test
    fun `failure result throws a typed broker exception`() {
        val error = BrokerInvocationError(
            code = BrokerErrorCodes.NetworkFailed,
            recoverable = true,
            message = "The broker network request failed",
        )

        val failure = assertFailsWith<BrokerException> {
            BrokerInvocationResult.Failure(error).operationResultOrThrow()
        }

        assertEquals(BrokerErrorCodes.NetworkFailed, failure.code)
        assertTrue(failure.recoverable)
        assertEquals(error.message, failure.message)
    }

    @Test
    fun `cancelled result preserves coroutine cancellation semantics`() {
        val error = BrokerInvocationError(
            code = BrokerErrorCodes.Cancelled,
            recoverable = true,
            message = "The broker request was cancelled",
        )

        assertFailsWith<CancellationException> {
            BrokerInvocationResult.Failure(error).operationResultOrThrow()
        }
    }

    @Test
    fun `authenticate returns only the typed authentication response`() = runBlocking {
        val expected = BrokerAuthenticationResponse(
            statusCode = 200,
            receipt = ProviderAuthenticationReceipt("receipt-1"),
        )
        val broker = ExtensionHostNetworkBroker.forTesting {
            BrokerOperationResult.Authentication(expected)
        }

        val response = broker.authenticate(
            BrokerAuthenticationRequest(
                exchange = BrokerHttpExchange("POST", "https://media.example.test/login"),
                primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
            )
        )

        assertEquals(expected, response)
    }

    @Test
    fun `operation result mismatch becomes a typed internal failure`() = runBlocking {
        val broker = ExtensionHostNetworkBroker.forTesting {
            BrokerOperationResult.Authentication(
                BrokerAuthenticationResponse(
                    statusCode = 401,
                    receipt = null,
                )
            )
        }

        val failure = assertFailsWith<BrokerException> {
            broker.execute(
                BrokeredHttpRequest(
                    method = "GET",
                    url = "https://media.example.test/items",
                )
            )
        }

        assertEquals(BrokerErrorCodes.Internal, failure.code)
        assertTrue(failure.recoverable)
    }
}
