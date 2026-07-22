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
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A capability-scoped network client. It never exposes provider credentials to the plugin. */
class ExtensionHostNetworkBroker private constructor(
    private val invokeOperation: suspend (BrokerOperation) -> BrokerOperationResult,
) {
    internal constructor(
        context: Context,
        bridge: IExtensionHostBridge,
        json: Json,
        brokerProtocolVersion: Int,
    ) : this(
        invokeOperation = { operation ->
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
                    )
                    val output = input.use(bridge::executeHttp)
                    json.decodeFromString<BrokerInvocationResult>(
                        ParcelFileCodec.read(output, MAX_RESPONSE_ENVELOPE_BYTES),
                    ).operationResultOrThrow()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: BrokerException) {
                    throw failure
                } catch (_: Exception) {
                    throw invalidHostResponse()
                }
            }
        }
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

    companion object {
        private const val MAX_RESPONSE_ENVELOPE_BYTES = 5 * 1024 * 1024

        internal fun forTesting(
            invokeOperation: suspend (BrokerOperation) -> BrokerOperationResult,
        ): ExtensionHostNetworkBroker = ExtensionHostNetworkBroker(invokeOperation)

        internal fun forHttpTesting(
            executeRequest: suspend (BrokeredHttpRequest) -> BrokeredHttpResponse,
        ): ExtensionHostNetworkBroker = ExtensionHostNetworkBroker { operation ->
            when (operation) {
                is BrokerOperation.Http -> BrokerOperationResult.Http(
                    executeRequest(operation.request)
                )
                is BrokerOperation.Authenticate -> throw invalidHostResponse()
            }
        }
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
