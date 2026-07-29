package com.m3u.testing.hostile

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import com.m3u.extension.api.ExtensionId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

private const val SERVICE_ACTION = "com.m3u.extension.action.BIND_EXTENSION"
private const val SERVICE_DESCRIPTOR =
    "com.m3u.extension.transport.android.ipc.IExtensionService"
private const val RESULT_CALLBACK_DESCRIPTOR =
    "com.m3u.extension.transport.android.ipc.IExtensionResultCallback"
private const val HOST_BRIDGE_DESCRIPTOR =
    "com.m3u.extension.transport.android.ipc.IExtensionHostBridge"

private const val TRANSACTION_HANDSHAKE = IBinder.FIRST_CALL_TRANSACTION + 0
private const val TRANSACTION_OPEN_MANIFEST = IBinder.FIRST_CALL_TRANSACTION + 1
private const val TRANSACTION_INVOKE = IBinder.FIRST_CALL_TRANSACTION + 2
private const val TRANSACTION_CANCEL = IBinder.FIRST_CALL_TRANSACTION + 3
private const val TRANSACTION_HEALTH = IBinder.FIRST_CALL_TRANSACTION + 4
private const val TRANSACTION_RESULT_SUCCESS = IBinder.FIRST_CALL_TRANSACTION + 0
private const val TRANSACTION_RESULT_FAILURE = IBinder.FIRST_CALL_TRANSACTION + 1
private const val TRANSACTION_EXECUTE_HTTP = IBinder.FIRST_CALL_TRANSACTION + 0

private const val TRANSPORT_VERSION = 4

private fun booleanString(value: Boolean): String =
    if (value) "true" else "false"

private const val BROKER_PROTOCOL_VERSION = 4
private const val API_MAJOR = 1
private const val API_MINOR = 0
private const val BACKGROUND_TASK_HOOK = "background.task.run"
private const val BACKGROUND_TASK_SCHEMA_VERSION = 2
private const val BACKGROUND_TASK_CAPABILITY = "background.task"
private const val NETWORK_CAPABILITY = "network"

private const val EXTENSION_ID_VALUE = "com.m3u.testing.hostile"
private const val BASELINE_TASK_ID = "baseline"
private const val IGNORE_CANCEL_TASK_ID = "ignore-cancel"
private const val MALFORMED_TASK_ID = "malformed"
private const val OVERSIZE_VALID_TASK_ID = "oversize-valid"
private const val RETAIN_BRIDGE_TASK_ID = "retain-bridge"
private const val USE_RETAINED_BRIDGE_TASK_ID = "use-retained-bridge"
private const val PROCESS_DEATH_TASK_ID = "process-death"

private const val OUTPUT_SCENARIO = "scenario"
private const val OUTPUT_EXTENSION_PID = "extensionPid"
private const val OUTPUT_EXTENSION_UID = "extensionUid"
private const val OUTPUT_BROKER_SCOPE_PRESENT = "brokerScopePresent"
private const val OUTPUT_LATE_RESPONSE = "lateResponse"
private const val OUTPUT_BRIDGE_RETAINED = "bridgeRetained"
private const val OUTPUT_ACTIVE_BRIDGE_CALLBACK = "activeBridgeCallback"
private const val OUTPUT_ACTIVE_BRIDGE_CALLBACK_CODE = "activeBridgeCallbackCode"
private const val OUTPUT_RETAINED_BRIDGE_PRESENT = "retainedBridgePresent"
private const val OUTPUT_RETAINED_BRIDGE_CALLBACK = "retainedBridgeCallback"
private const val OUTPUT_RETAINED_BRIDGE_CALLBACK_CODE = "retainedBridgeCallbackCode"
private const val OUTPUT_OVERSIZED_VALUE = "oversizedValue"
private const val OUTPUT_UNKNOWN_TASK_ID = "unknownTaskId"

private const val IGNORE_CANCEL_DELAY_MILLIS = 2_000L
private const val BRIDGE_PROBE_DELAY_MILLIS = 750L
private const val HOST_RESPONSE_LIMIT_BYTES = 4 * 1024 * 1024
private const val OVERSIZED_VALUE_BYTES = HOST_RESPONSE_LIMIT_BYTES
private const val MAX_HANDSHAKE_BYTES = 16 * 1024
private const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
private const val MAX_REQUEST_ID_LENGTH = 64
private const val ACTIVE_BRIDGE_REQUEST_ID = "active-bridge-probe"
private const val RETAINED_BRIDGE_REQUEST_ID = "retained-bridge-probe"

private fun readTypedDescriptorStatic(parcel: Parcel?): ParcelFileDescriptor? {
    if (parcel == null || parcel.readInt() == 0) return null
    return ParcelFileDescriptor.CREATOR.createFromParcel(parcel)
}

private fun closeQuietlyStatic(descriptor: ParcelFileDescriptor?) {
    if (descriptor == null) return
    try {
        descriptor.close()
    } catch (failure: Exception) {
        // Best-effort ownership cleanup.
    }
}

private fun sameString(left: String?, right: String?): Boolean {
    if (left == null || right == null || left.length != right.length) return false
    var index = 0
    while (index < left.length) {
        if (left[index] != right[index]) return false
        index += 1
    }
    return true
}

/**
 * A self-contained Binder fixture for host-containment tests.
 *
 * The service process cannot see classes from the instrumented application. Keep its executable
 * path limited to Android/JDK APIs and reproduce the small AIDL/JSON wire surface directly. The
 * typed protocol constants below are only loaded by the instrumentation process.
 */
class HostileExtensionService : Service() {
    private val executor = ScheduledThreadPoolExecutor(2)
    private val retainedBridge = AtomicReference<IBinder?>(null)
    private val negotiatedBrokerProtocolVersion = AtomicInteger(0)

    private val binder = object : Binder() {
        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            if (
                code >= IBinder.FIRST_CALL_TRANSACTION &&
                code <= IBinder.LAST_CALL_TRANSACTION
            ) {
                data.enforceInterface(SERVICE_DESCRIPTOR)
            }
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(SERVICE_DESCRIPTOR)
                return true
            }
            return when (code) {
                TRANSACTION_HANDSHAKE -> {
                    handleHandshake(
                        data.readString(),
                        readTypedDescriptor(data),
                        data.readStrongBinder(),
                    )
                    true
                }

                TRANSACTION_OPEN_MANIFEST -> {
                    handleOpenManifest(data.readString(), data.readStrongBinder())
                    true
                }

                TRANSACTION_INVOKE -> {
                    handleInvoke(
                        requestId = data.readString(),
                        invocationId = data.readString(),
                        hookId = data.readString(),
                        schemaVersion = data.readInt(),
                        request = readTypedDescriptor(data),
                        hostBridge = data.readStrongBinder(),
                        callback = data.readStrongBinder(),
                    )
                    true
                }

                TRANSACTION_CANCEL -> {
                    data.readString()
                    // Deliberately ignored. Containment cannot rely on a cooperative extension.
                    true
                }

                TRANSACTION_HEALTH -> {
                    handleHealth(data.readString(), data.readStrongBinder())
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val action: String? = if (intent == null) null else intent.action
        return if (SERVICE_ACTION.equals(action)) binder else null
    }

    override fun onDestroy() {
        retainedBridge.set(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun handleHandshake(
        requestId: String?,
        request: ParcelFileDescriptor?,
        callback: IBinder?,
    ) {
        if (!isValidRequestId(requestId) || request == null || callback == null) {
            closeQuietly(request)
            return
        }
        executor.execute(object : Runnable {
            override fun run() {
                try {
                    val payload = readPayload(request, MAX_HANDSHAKE_BYTES)
                    val handshake = if (payload == null) null else JSONObject(payload)
                    if (handshake == null) throw IOException("Missing handshake")

                    val transportVersion = handshake.optInt("transportVersion", -1)
                    val hostApi = handshake.optJSONObject("hostApiVersion")
                    val hostApiMajor =
                        if (hostApi == null) -1 else hostApi.optInt("major", -1)
                    val supportedBrokerVersions =
                        handshake.optJSONArray("supportedBrokerProtocolVersions")
                    val supportsCurrentBroker =
                        containsInt(supportedBrokerVersions, BROKER_PROTOCOL_VERSION)

                    var errorCode: String? = null
                    var errorMessage: String? = null
                    if (transportVersion != TRANSPORT_VERSION) {
                        errorCode = "transport.incompatible"
                        errorMessage = "Hostile fixture transport version is incompatible"
                    } else if (hostApiMajor != API_MAJOR) {
                        errorCode = "api.incompatible"
                        errorMessage = "Hostile fixture API version is incompatible"
                    } else if (!supportsCurrentBroker) {
                        errorCode = "broker.incompatible"
                        errorMessage = "Hostile fixture broker protocol is incompatible"
                    }

                    val response = createHandshakeResponse(errorCode, errorMessage)
                    if (errorCode == null) {
                        negotiatedBrokerProtocolVersion.set(BROKER_PROTOCOL_VERSION)
                    }
                    sendSuccess(callback, requestId, response.toString())
                } catch (failure: Exception) {
                    sendFailure(
                        callback,
                        requestId,
                        "fixture.handshake_failed",
                        "Hostile fixture handshake failed",
                    )
                } finally {
                    closeQuietly(request)
                }
            }
        })
    }

    private fun handleOpenManifest(requestId: String?, callback: IBinder?) {
        if (!isValidRequestId(requestId) || callback == null) return
        executor.execute(object : Runnable {
            override fun run() {
                try {
                    sendSuccess(callback, requestId, createManifest().toString())
                } catch (failure: Exception) {
                    sendFailure(
                        callback,
                        requestId,
                        "fixture.manifest_failed",
                        "Hostile fixture manifest failed",
                    )
                }
            }
        })
    }

    private fun handleInvoke(
        requestId: String?,
        invocationId: String?,
        hookId: String?,
        schemaVersion: Int,
        request: ParcelFileDescriptor?,
        hostBridge: IBinder?,
        callback: IBinder?,
    ) {
        if (
            !isValidRequestId(requestId) ||
            isNullOrEmpty(invocationId) ||
            isNullOrEmpty(hookId) ||
            request == null ||
            hostBridge == null ||
            callback == null
        ) {
            closeQuietly(request)
            if (isValidRequestId(requestId) && callback != null) {
                sendFailure(
                    callback,
                    requestId,
                    "fixture.request_invalid",
                    "Hostile fixture invocation is invalid",
                )
            }
            return
        }
        executor.execute(object : Runnable {
            override fun run() {
                try {
                    if (negotiatedBrokerProtocolVersion.get() != BROKER_PROTOCOL_VERSION) {
                        throw IllegalStateException("Handshake must complete before invocation")
                    }
                    val payload = readPayload(request, MAX_REQUEST_BYTES)
                    val envelope = if (payload == null) null else JSONObject(payload)
                    if (envelope == null) throw IOException("Missing invocation envelope")
                    val envelopeInvocationId = stringValue(envelope, "invocationId")
                    val envelopeExtensionId = stringValue(envelope, "extensionId")
                    val envelopeHook = stringValue(envelope, "hook")
                    val envelopeSchemaVersion = envelope.optInt("schemaVersion", -1)
                    val requestPayload = envelope.optJSONObject("payload")
                    val taskId = stringValue(requestPayload, "taskId")

                    if (
                        !sameString(invocationId, envelopeInvocationId) ||
                        !EXTENSION_ID_VALUE.equals(envelopeExtensionId) ||
                        !sameString(hookId, envelopeHook) ||
                        !BACKGROUND_TASK_HOOK.equals(envelopeHook) ||
                        schemaVersion != envelopeSchemaVersion ||
                        schemaVersion != BACKGROUND_TASK_SCHEMA_VERSION ||
                        taskId == null
                    ) {
                        throw IllegalArgumentException("Invocation envelope mismatch")
                    }
                    invokeScenario(
                        requestId,
                        callback,
                        hostBridge,
                        envelope,
                        taskId,
                    )
                } catch (failure: Exception) {
                    sendFailure(
                        callback,
                        requestId,
                        "fixture.request_failed",
                        "Hostile fixture invocation failed",
                    )
                } finally {
                    closeQuietly(request)
                }
            }
        })
    }

    private fun handleHealth(requestId: String?, callback: IBinder?) {
        if (!isValidRequestId(requestId) || callback == null) return
        executor.execute(object : Runnable {
            override fun run() {
                sendSuccess(callback, requestId, "healthy")
            }
        })
    }

    private fun invokeScenario(
        requestId: String?,
        callback: IBinder?,
        hostBridge: IBinder?,
        envelope: JSONObject?,
        taskId: String?,
    ) {
        if (
            requestId == null ||
            callback == null ||
            hostBridge == null ||
            envelope == null ||
            taskId == null
        ) {
            return
        }
        if (BASELINE_TASK_ID.equals(taskId)) {
            sendTypedSuccess(requestId, callback, envelope, taskId, null)
            return
        }
        if (IGNORE_CANCEL_TASK_ID.equals(taskId)) {
            val extra = JSONObject()
            extra.put(OUTPUT_LATE_RESPONSE, "true")
            executor.schedule(
                DelayedSuccess(
                    requestId,
                    callback,
                    envelope,
                    taskId,
                    extra,
                ),
                IGNORE_CANCEL_DELAY_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            return
        }
        if (MALFORMED_TASK_ID.equals(taskId)) {
            sendSuccess(callback, requestId, "{")
            return
        }
        if (OVERSIZE_VALID_TASK_ID.equals(taskId)) {
            val extra = JSONObject()
            extra.put(OUTPUT_OVERSIZED_VALUE, repeatedCharacter('x', OVERSIZED_VALUE_BYTES))
            val oversized = encodedSuccess(envelope, taskId, extra)
            if (oversized == null || oversized.length <= HOST_RESPONSE_LIMIT_BYTES) {
                sendFailure(
                    callback,
                    requestId,
                    "fixture.oversize_failed",
                    "Hostile fixture did not create an oversized result",
                )
            } else {
                sendSuccess(callback, requestId, oversized)
            }
            return
        }
        if (RETAIN_BRIDGE_TASK_ID.equals(taskId)) {
            retainAndProbeBridge(
                requestId,
                callback,
                hostBridge,
                envelope,
                taskId,
            )
            return
        }
        if (USE_RETAINED_BRIDGE_TASK_ID.equals(taskId)) {
            probeRetainedBridge(requestId, callback, envelope, taskId)
            return
        }
        if (PROCESS_DEATH_TASK_ID.equals(taskId)) {
            Process.killProcess(Process.myPid())
            return
        }

        val extra = JSONObject()
        extra.put(OUTPUT_UNKNOWN_TASK_ID, taskId)
        sendTypedSuccess(requestId, callback, envelope, taskId, extra)
    }

    private fun retainAndProbeBridge(
        requestId: String?,
        callback: IBinder?,
        hostBridge: IBinder?,
        envelope: JSONObject?,
        taskId: String?,
    ) {
        if (
            requestId == null ||
            callback == null ||
            hostBridge == null ||
            envelope == null ||
            taskId == null
        ) {
            return
        }
        retainedBridge.set(hostBridge)
        val callbackDelivered = AtomicBoolean(false)
        val callbackCode = AtomicReference<String>("")
        executeHostBridge(
            hostBridge,
            ACTIVE_BRIDGE_REQUEST_ID,
            createBrokerInvocation().toString(),
            ProbeResultCallback(callbackDelivered, callbackCode),
        )
        executor.schedule(
            ActiveBridgeProbeCompletion(
                requestId,
                callback,
                envelope,
                taskId,
                callbackDelivered,
                callbackCode,
            ),
            BRIDGE_PROBE_DELAY_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun probeRetainedBridge(
        requestId: String?,
        callback: IBinder?,
        envelope: JSONObject?,
        taskId: String?,
    ) {
        if (requestId == null || callback == null || envelope == null || taskId == null) return
        val bridge = retainedBridge.get()
        val callbackDelivered = AtomicBoolean(false)
        val callbackCode = AtomicReference<String>("")
        if (bridge == null) {
            sendRetainedBridgeProbeResult(
                requestId,
                callback,
                envelope,
                taskId,
                false,
                callbackDelivered,
                callbackCode,
            )
            return
        }

        val probeCallback = ProbeResultCallback(callbackDelivered, callbackCode)
        executeHostBridge(
            bridge,
            RETAINED_BRIDGE_REQUEST_ID,
            createBrokerInvocation().toString(),
            probeCallback,
        )
        executor.schedule(
            RetainedBridgeProbeCompletion(
                requestId,
                callback,
                envelope,
                taskId,
                callbackDelivered,
                callbackCode,
            ),
            BRIDGE_PROBE_DELAY_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun sendRetainedBridgeProbeResult(
        requestId: String?,
        callback: IBinder?,
        envelope: JSONObject?,
        taskId: String?,
        retainedBridgePresent: Boolean,
        callbackDelivered: AtomicBoolean?,
        callbackCode: AtomicReference<String>?,
    ) {
        if (
            requestId == null ||
            callback == null ||
            envelope == null ||
            taskId == null ||
            callbackDelivered == null ||
            callbackCode == null
        ) {
            return
        }
        val extra = JSONObject()
        extra.put(
            OUTPUT_RETAINED_BRIDGE_PRESENT,
            booleanString(retainedBridgePresent),
        )
        extra.put(
            OUTPUT_RETAINED_BRIDGE_CALLBACK,
            booleanString(callbackDelivered.get()),
        )
        extra.put(OUTPUT_RETAINED_BRIDGE_CALLBACK_CODE, callbackCode.get())
        sendTypedSuccess(requestId, callback, envelope, taskId, extra)
    }

    private fun sendTypedSuccess(
        requestId: String?,
        callback: IBinder?,
        envelope: JSONObject?,
        taskId: String?,
        extraOutput: JSONObject?,
    ) {
        val payload = encodedSuccess(envelope, taskId, extraOutput)
        if (payload != null) sendSuccess(callback, requestId, payload)
    }

    private fun encodedSuccess(
        envelope: JSONObject?,
        taskId: String?,
        extraOutput: JSONObject?,
    ): String? {
        if (envelope == null || taskId == null) return null
        val invocationId = stringValue(envelope, "invocationId") ?: return null
        val extensionId = stringValue(envelope, "extensionId") ?: return null
        val hook = stringValue(envelope, "hook") ?: return null
        val schemaVersion = envelope.optInt("schemaVersion", -1)
        if (schemaVersion <= 0) return null

        val output = JSONObject()
        output.put(OUTPUT_SCENARIO, taskId)
        output.put(OUTPUT_EXTENSION_PID, Integer.toString(Process.myPid()))
        output.put(OUTPUT_EXTENSION_UID, Integer.toString(Process.myUid()))
        output.put(
            OUTPUT_BROKER_SCOPE_PRESENT,
            booleanString(
                envelope.has("brokerScope") && !envelope.isNull("brokerScope")
            ),
        )
        if (extraOutput != null) {
            val keys = extraOutput.keys()
            while (keys.hasNext()) {
                val key: String? = keys.next()
                if (key != null) output.put(key, extraOutput.opt(key))
            }
        }

        val resultPayload = JSONObject()
        resultPayload.put("output", output)
        val result = JSONObject()
        result.put("invocationId", invocationId)
        result.put("extensionId", extensionId)
        result.put("hook", hook)
        result.put("schemaVersion", schemaVersion)
        result.put("payload", resultPayload)
        return result.toString()
    }

    private fun createHandshakeResponse(
        errorCode: String?,
        errorMessage: String?,
    ): JSONObject {
        val response = JSONObject()
        response.put("transportVersion", TRANSPORT_VERSION)
        response.put("extensionApiRange", createApiRange())
        if (errorCode == null) {
            response.put("brokerProtocolVersion", BROKER_PROTOCOL_VERSION)
        } else {
            val error = JSONObject()
            error.put("code", errorCode)
            error.put("message", errorMessage)
            response.put("error", error)
        }
        return response
    }

    private fun createManifest(): JSONObject {
        val version = JSONObject()
        version.put("major", 1)
        version.put("minor", 0)
        version.put("patch", 0)

        val requiredCapabilities = JSONArray()
        requiredCapabilities.put(BACKGROUND_TASK_CAPABILITY)
        requiredCapabilities.put(NETWORK_CAPABILITY)
        val hook = JSONObject()
        hook.put("hook", BACKGROUND_TASK_HOOK)
        hook.put("schemaVersion", BACKGROUND_TASK_SCHEMA_VERSION)
        hook.put("requiredCapabilities", requiredCapabilities)
        val hooks = JSONArray()
        hooks.put(hook)

        val backgroundCapability = JSONObject()
        backgroundCapability.put("capability", BACKGROUND_TASK_CAPABILITY)
        backgroundCapability.put(
            "reason",
            "Exercise hostile cross-process invocation handling",
        )
        val networkCapability = JSONObject()
        networkCapability.put("capability", NETWORK_CAPABILITY)
        networkCapability.put(
            "reason",
            "Exercise revocation of one invocation's host bridge",
        )
        val capabilities = JSONArray()
        capabilities.put(backgroundCapability)
        capabilities.put(networkCapability)

        val metadata = JSONObject()
        metadata.put("developer", "M3U hostile conformance fixture")
        val manifest = JSONObject()
        manifest.put("id", EXTENSION_ID_VALUE)
        manifest.put("displayName", "Hostile transport fixture")
        manifest.put("extensionVersion", version)
        manifest.put("apiRange", createApiRange())
        manifest.put("hooks", hooks)
        manifest.put("capabilities", capabilities)
        manifest.put("metadata", metadata)
        return manifest
    }

    private fun createApiRange(): JSONObject {
        val minimum = JSONObject()
        minimum.put("major", API_MAJOR)
        minimum.put("minor", API_MINOR)
        val maximum = JSONObject()
        maximum.put("major", API_MAJOR)
        maximum.put("minor", API_MINOR)
        val range = JSONObject()
        range.put("minimum", minimum)
        range.put("maximum", maximum)
        return range
    }

    private fun createBrokerInvocation(): JSONObject {
        val url = JSONObject()
        url.put("type", "literal")
        url.put("value", "https://hostile.invalid/retained-bridge-probe")
        val request = JSONObject()
        request.put("method", "GET")
        request.put("url", url)
        val operation = JSONObject()
        operation.put("type", "http")
        operation.put("request", request)
        val invocation = JSONObject()
        invocation.put("brokerProtocolVersion", BROKER_PROTOCOL_VERSION)
        invocation.put("operation", operation)
        return invocation
    }

    private fun executeHostBridge(
        bridge: IBinder?,
        requestId: String?,
        payload: String?,
        callback: IBinder?,
    ) {
        if (bridge == null || requestId == null || payload == null || callback == null) return
        val descriptor = writePayload(payload)
        if (descriptor == null) return
        val data: Parcel? = Parcel.obtain()
        if (data == null) {
            closeQuietly(descriptor)
            return
        }
        try {
            data.writeInterfaceToken(HOST_BRIDGE_DESCRIPTOR)
            data.writeString(requestId)
            writeTypedDescriptor(data, descriptor)
            data.writeStrongBinder(callback)
            bridge.transact(
                TRANSACTION_EXECUTE_HTTP,
                data,
                null,
                IBinder.FLAG_ONEWAY,
            )
        } catch (failure: Exception) {
            // A revoked or dead host bridge is an expected hostile-fixture outcome.
        } finally {
            data.recycle()
            closeQuietly(descriptor)
        }
    }

    private fun sendSuccess(
        callback: IBinder?,
        requestId: String?,
        payload: String?,
    ) {
        if (callback == null || requestId == null || payload == null) return
        val response = writePayload(payload)
        if (response == null) return
        val data: Parcel? = Parcel.obtain()
        if (data == null) {
            closeQuietly(response)
            return
        }
        try {
            data.writeInterfaceToken(RESULT_CALLBACK_DESCRIPTOR)
            data.writeString(requestId)
            writeTypedDescriptor(data, response)
            callback.transact(
                TRANSACTION_RESULT_SUCCESS,
                data,
                null,
                IBinder.FLAG_ONEWAY,
            )
        } catch (failure: Exception) {
            // The host may already have timed out, cancelled, or disconnected.
        } finally {
            data.recycle()
            closeQuietly(response)
        }
    }

    private fun sendFailure(
        callback: IBinder?,
        requestId: String?,
        code: String?,
        message: String?,
    ) {
        if (callback == null || requestId == null || code == null || message == null) return
        val data: Parcel? = Parcel.obtain()
        if (data == null) return
        try {
            data.writeInterfaceToken(RESULT_CALLBACK_DESCRIPTOR)
            data.writeString(requestId)
            data.writeString(code)
            data.writeString(message)
            callback.transact(
                TRANSACTION_RESULT_FAILURE,
                data,
                null,
                IBinder.FLAG_ONEWAY,
            )
        } catch (failure: Exception) {
            // The host may already have timed out, cancelled, or disconnected.
        } finally {
            data.recycle()
        }
    }

    private fun readPayload(
        descriptor: ParcelFileDescriptor?,
        maximumBytes: Int,
    ): String? {
        if (descriptor == null || maximumBytes <= 0) return null
        val statSize = descriptor.statSize
        if (statSize < 0L || statSize > maximumBytes.toLong()) {
            throw IOException("Fixture payload size is invalid")
        }
        val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > maximumBytes) {
                    throw IOException("Fixture payload exceeds its transport limit")
                }
                output.write(buffer, 0, count)
            }
            val bytes: ByteArray? = output.toByteArray()
            val charset: Charset? = StandardCharsets.UTF_8
            if (bytes == null || charset == null) return null
            return String(bytes, charset)
        } finally {
            try {
                input.close()
            } catch (failure: Exception) {
                // Best-effort ownership cleanup.
            }
        }
    }

    private fun writePayload(payload: String?): ParcelFileDescriptor? {
        if (payload == null) return null
        val directory: File? = cacheDir
        if (directory == null) return null
        val file: File? = File.createTempFile("hostile-extension-", ".json", directory)
        if (file == null) return null
        try {
            val writer = OutputStreamWriter(FileOutputStream(file), "UTF-8")
            try {
                writer.write(payload)
                writer.flush()
            } finally {
                writer.close()
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            file.delete()
        }
    }

    private fun readTypedDescriptor(parcel: Parcel?): ParcelFileDescriptor? {
        if (parcel == null || parcel.readInt() == 0) return null
        return ParcelFileDescriptor.CREATOR.createFromParcel(parcel)
    }

    private fun writeTypedDescriptor(
        parcel: Parcel?,
        descriptor: ParcelFileDescriptor?,
    ) {
        if (parcel == null) return
        if (descriptor == null) {
            parcel.writeInt(0)
        } else {
            parcel.writeInt(1)
            descriptor.writeToParcel(parcel, 0)
        }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        if (descriptor == null) return
        try {
            descriptor.close()
        } catch (failure: Exception) {
            // Best-effort ownership cleanup.
        }
    }

    private fun stringValue(json: JSONObject?, key: String?): String? {
        if (json == null || key == null) return null
        val value = json.opt(key)
        return if (value is String) value else null
    }

    private fun containsInt(values: JSONArray?, expected: Int): Boolean {
        if (values == null) return false
        var index = 0
        while (index < values.length()) {
            if (values.optInt(index, -1) == expected) return true
            index += 1
        }
        return false
    }

    private fun isValidRequestId(value: String?): Boolean {
        if (value == null || value.length == 0 || value.length > MAX_REQUEST_ID_LENGTH) {
            return false
        }
        var index = 0
        while (index < value.length) {
            if (!Character.isWhitespace(value[index])) return true
            index += 1
        }
        return false
    }

    private fun isNullOrEmpty(value: String?): Boolean =
        value == null || value.length == 0

    private fun repeatedCharacter(character: Char, count: Int): String {
        val characters = CharArray(count)
        Arrays.fill(characters, character)
        return String(characters)
    }

    private inner class DelayedSuccess(
        private val requestId: String?,
        private val callback: IBinder?,
        private val envelope: JSONObject?,
        private val taskId: String?,
        private val extraOutput: JSONObject?,
    ) : Runnable {
        override fun run() {
            sendTypedSuccess(requestId, callback, envelope, taskId, extraOutput)
        }
    }

    private inner class ActiveBridgeProbeCompletion(
        private val requestId: String?,
        private val callback: IBinder?,
        private val envelope: JSONObject?,
        private val taskId: String?,
        private val callbackDelivered: AtomicBoolean?,
        private val callbackCode: AtomicReference<String>?,
    ) : Runnable {
        override fun run() {
            val extra = JSONObject()
            extra.put(OUTPUT_BRIDGE_RETAINED, "true")
            extra.put(
                OUTPUT_ACTIVE_BRIDGE_CALLBACK,
                booleanString(callbackDelivered?.get() == true),
            )
            extra.put(
                OUTPUT_ACTIVE_BRIDGE_CALLBACK_CODE,
                callbackCode?.get() ?: "",
            )
            sendTypedSuccess(requestId, callback, envelope, taskId, extra)
        }
    }

    private inner class RetainedBridgeProbeCompletion(
        private val requestId: String?,
        private val callback: IBinder?,
        private val envelope: JSONObject?,
        private val taskId: String?,
        private val callbackDelivered: AtomicBoolean?,
        private val callbackCode: AtomicReference<String>?,
    ) : Runnable {
        override fun run() {
            sendRetainedBridgeProbeResult(
                requestId,
                callback,
                envelope,
                taskId,
                true,
                callbackDelivered,
                callbackCode,
            )
        }
    }

    private class ProbeResultCallback(
        private val delivered: AtomicBoolean?,
        private val resultCode: AtomicReference<String>?,
    ) : Binder() {
        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            if (
                code >= IBinder.FIRST_CALL_TRANSACTION &&
                code <= IBinder.LAST_CALL_TRANSACTION
            ) {
                data.enforceInterface(RESULT_CALLBACK_DESCRIPTOR)
            }
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(RESULT_CALLBACK_DESCRIPTOR)
                return true
            }
            if (code == TRANSACTION_RESULT_SUCCESS) {
                data.readString()
                val response = readTypedDescriptorStatic(data)
                closeQuietlyStatic(response)
                if (resultCode != null) resultCode.set("success")
                if (delivered != null) delivered.set(true)
                return true
            }
            if (code == TRANSACTION_RESULT_FAILURE) {
                data.readString()
                val callbackCode = data.readString()
                data.readString()
                if (resultCode != null) {
                    resultCode.set(if (callbackCode == null) "" else callbackCode)
                }
                if (delivered != null) delivered.set(true)
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }
}

object HostileExtensionFixtureProtocol {
    const val EXTENSION_ID_VALUE = "com.m3u.testing.hostile"
    val EXTENSION_ID: ExtensionId
        get() = ExtensionId(EXTENSION_ID_VALUE)
    const val SERVICE_CLASS_NAME =
        "com.m3u.testing.hostile.HostileExtensionService"

    const val BASELINE_TASK_ID = "baseline"
    const val IGNORE_CANCEL_TASK_ID = "ignore-cancel"
    const val MALFORMED_TASK_ID = "malformed"
    const val OVERSIZE_VALID_TASK_ID = "oversize-valid"
    const val RETAIN_BRIDGE_TASK_ID = "retain-bridge"
    const val USE_RETAINED_BRIDGE_TASK_ID = "use-retained-bridge"
    const val PROCESS_DEATH_TASK_ID = "process-death"

    const val OUTPUT_SCENARIO = "scenario"
    const val OUTPUT_EXTENSION_PID = "extensionPid"
    const val OUTPUT_EXTENSION_UID = "extensionUid"
    const val OUTPUT_BROKER_SCOPE_PRESENT = "brokerScopePresent"
    const val OUTPUT_LATE_RESPONSE = "lateResponse"
    const val OUTPUT_BRIDGE_RETAINED = "bridgeRetained"
    const val OUTPUT_ACTIVE_BRIDGE_CALLBACK = "activeBridgeCallback"
    const val OUTPUT_ACTIVE_BRIDGE_CALLBACK_CODE = "activeBridgeCallbackCode"
    const val OUTPUT_RETAINED_BRIDGE_PRESENT = "retainedBridgePresent"
    const val OUTPUT_RETAINED_BRIDGE_CALLBACK = "retainedBridgeCallback"
    const val OUTPUT_RETAINED_BRIDGE_CALLBACK_CODE = "retainedBridgeCallbackCode"
    const val OUTPUT_OVERSIZED_VALUE = "oversizedValue"
    const val OUTPUT_UNKNOWN_TASK_ID = "unknownTaskId"

    const val IGNORE_CANCEL_DELAY_MILLIS = 2_000L
    const val HOST_RESPONSE_LIMIT_BYTES = 4 * 1024 * 1024
    const val OVERSIZED_VALUE_BYTES = HOST_RESPONSE_LIMIT_BYTES
}
