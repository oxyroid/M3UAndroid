package com.m3u.extension.sdk.android

import android.content.Context
import com.m3u.extension.api.security.BrokerErrorCode
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.transport.android.ExtensionResultDispatcher
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import java.io.Closeable
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP client for one broker-enabled Hook call.
 *
 * [execute] is restricted to the exact origins and opaque credential handles in that call's
 * short-lived scope. Provider account Hooks receive only their selected account origin and
 * credential. Other supported Hooks receive only user-approved manifest or setting origins.
 * The host resolves opaque secrets without serializing plaintext back to extension code.
 *
 * [authenticate] is reserved for provider validation Hooks.
 */
class ExtensionHostNetworkBroker private constructor(
    private val invokeOperation: suspend (BrokerOperation) -> BrokerOperationResult,
    private val closeOperation: () -> Unit,
) : Closeable {
    private constructor(invoker: BoundBrokerInvoker) : this(
        invokeOperation = invoker::invoke,
        closeOperation = invoker::close,
    )

    internal constructor(
        context: Context,
        bridge: IExtensionHostBridge,
        json: Json,
        brokerProtocolVersion: Int,
    ) : this(
        BoundBrokerInvoker(
            context = context,
            bridge = bridge,
            json = json,
            brokerProtocolVersion = brokerProtocolVersion,
        )
    )

    suspend fun execute(request: BrokeredHttpRequest): BrokeredHttpResponse =
        when (val result = invokeOperation(BrokerOperation.Http(request))) {
            is BrokerOperationResult.Http -> result.response
            is BrokerOperationResult.Authentication -> throw invalidHostResponse()
        }

    suspend fun authenticate(request: BrokerAuthenticationRequest): BrokerAuthenticationResponse =
        when (val result = invokeOperation(BrokerOperation.Authenticate(request))) {
            is BrokerOperationResult.Authentication -> result.response
            is BrokerOperationResult.Http -> throw invalidHostResponse()
        }

    override fun close() {
        closeOperation()
    }

    companion object {
        internal fun forTesting(
            invokeOperation: suspend (BrokerOperation) -> BrokerOperationResult,
        ): ExtensionHostNetworkBroker = ExtensionHostNetworkBroker(invokeOperation) {}

        internal fun forHttpTesting(
            executeRequest: suspend (BrokeredHttpRequest) -> BrokeredHttpResponse,
        ): ExtensionHostNetworkBroker = ExtensionHostNetworkBroker(
            invokeOperation = { operation ->
                when (operation) {
                    is BrokerOperation.Http -> BrokerOperationResult.Http(
                        executeRequest(operation.request)
                    )
                    is BrokerOperation.Authenticate -> throw invalidHostResponse()
                }
            },
            closeOperation = {},
        )
    }
}

private class BoundBrokerInvoker(
    private val context: Context,
    private val bridge: IExtensionHostBridge,
    private val json: Json,
    private val brokerProtocolVersion: Int,
) : Closeable {
    private val resultDispatcher = ExtensionResultDispatcher(MAX_PENDING_BROKER_RESULTS)
    private val bridgeCallLock = Any()

    suspend fun invoke(operation: BrokerOperation): BrokerOperationResult =
        withContext(Dispatchers.IO) {
            try {
                val input = ParcelFileCodec.write(
                    context,
                    json.encodeToString(
                        BrokerInvocation(
                            brokerProtocolVersion = brokerProtocolVersion,
                            operation = operation,
                        )
                    ),
                    maximumBytes = MAX_REQUEST_ENVELOPE_BYTES,
                )
                val output = input.use { request ->
                    resultDispatcher.await(
                        onCancellation = { requestId ->
                            synchronized(bridgeCallLock) {
                                runCatching { bridge.cancelHttp(requestId) }
                            }
                        },
                    ) { requestId, callback ->
                        synchronized(bridgeCallLock) {
                            if (resultDispatcher.isPending(requestId)) {
                                bridge.executeHttp(requestId, request, callback)
                            }
                        }
                    }
                }
                json.decodeFromString<BrokerInvocationResult>(
                    ParcelFileCodec.readInterruptibly(output, MAX_RESPONSE_ENVELOPE_BYTES),
                ).operationResultOrThrow()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: BrokerException) {
                throw failure
            } catch (_: Exception) {
                throw invalidHostResponse()
            }
        }

    override fun close() {
        resultDispatcher.close()
    }

    private companion object {
        const val MAX_PENDING_BROKER_RESULTS = 4
        const val MAX_REQUEST_ENVELOPE_BYTES = 2 * 1024 * 1024
        const val MAX_RESPONSE_ENVELOPE_BYTES = 5 * 1024 * 1024
    }
}

class BrokerException(
    val code: BrokerErrorCode,
    val recoverable: Boolean,
    message: String,
) : Exception(message)

internal fun BrokerInvocationResult.operationResultOrThrow(): BrokerOperationResult = when (this) {
    is BrokerInvocationResult.Success -> result
    is BrokerInvocationResult.Failure -> {
        if (error.code == BrokerErrorCodes.Cancelled) {
            throw CancellationException(error.message)
        }
        throw BrokerException(
            code = error.code,
            recoverable = error.recoverable,
            message = error.message,
        )
    }
}

private fun invalidHostResponse() = BrokerException(
    code = BrokerErrorCodes.Internal,
    recoverable = true,
    message = "The host broker returned an invalid response",
)
