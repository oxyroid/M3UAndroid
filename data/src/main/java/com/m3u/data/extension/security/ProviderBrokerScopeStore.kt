package com.m3u.data.extension.security

import android.os.SystemClock
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.Hook
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ProviderAuthenticationReceipt
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
internal class ProviderBrokerScopeStore private constructor(
    private val credentialVault: CredentialVault,
    private val principalRegistry: ActiveExtensionPrincipalRegistry,
    configuration: ProviderBrokerScopeConfiguration,
) {
    private val clock = configuration.clock
    private val idFactory = configuration.idFactory
    private val defaultTtlMillis = configuration.defaultTtlMillis
    private val maximumTtlMillis = configuration.maximumTtlMillis
    private val maximumScopes = configuration.maximumScopes
    private val maximumCredentialsPerScope = configuration.maximumCredentialsPerScope
    private val lock = Any()
    private val scopes = linkedMapOf<BrokerScopeHandle, ScopeRecord>()

    @Inject
    constructor(
        credentialVault: CredentialVault,
        principalRegistry: ActiveExtensionPrincipalRegistry,
    ) : this(
        credentialVault = credentialVault,
        principalRegistry = principalRegistry,
        configuration = ProviderBrokerScopeConfiguration(
            clock = SystemClock::elapsedRealtime,
            idFactory = { UUID.randomUUID().toString() },
            defaultTtlMillis = DEFAULT_TTL_MILLIS,
            maximumTtlMillis = DEFAULT_TTL_MILLIS,
            maximumScopes = DEFAULT_MAXIMUM_SCOPES,
            maximumCredentialsPerScope = DEFAULT_MAXIMUM_CREDENTIALS_PER_SCOPE,
        ),
    )

    internal constructor(
        credentialVault: CredentialVault,
        principalRegistry: ActiveExtensionPrincipalRegistry,
        clock: () -> Long,
        idFactory: () -> String,
        defaultTtlMillis: Long,
        maximumTtlMillis: Long = defaultTtlMillis,
        maximumScopes: Int = DEFAULT_MAXIMUM_SCOPES,
        maximumCredentialsPerScope: Int = DEFAULT_MAXIMUM_CREDENTIALS_PER_SCOPE,
    ) : this(
        credentialVault = credentialVault,
        principalRegistry = principalRegistry,
        configuration = ProviderBrokerScopeConfiguration(
            clock = clock,
            idFactory = idFactory,
            defaultTtlMillis = defaultTtlMillis,
            maximumTtlMillis = maximumTtlMillis,
            maximumScopes = maximumScopes,
            maximumCredentialsPerScope = maximumCredentialsPerScope,
        ),
    )

    init {
        require(defaultTtlMillis > 0) { "Default broker scope TTL must be positive" }
        require(maximumTtlMillis >= defaultTtlMillis) {
            "Maximum broker scope TTL must include the default"
        }
        require(maximumScopes > 0) { "Broker scope capacity must be positive" }
        require(maximumCredentialsPerScope > 0) {
            "Broker credential capacity must be positive"
        }
    }

    fun mintAuthenticationScope(
        principal: ExtensionPrincipal,
        approvedBaseUrl: String,
        transientCredentials: Map<String, CredentialHandle>,
        ttlMillis: Long = defaultTtlMillis,
    ): BrokerScopeHandle = synchronized(lock) {
        require(transientCredentials.size <= maximumCredentialsPerScope) {
            "Authentication scope contains too many credentials"
        }
        require(transientCredentials.values.toSet().size == transientCredentials.size) {
            "Authentication credential handles must be unique"
        }
        requireActive(principal)
        val now = clock()
        purgeExpiredLocked(now)
        requireCapacityLocked()
        val approvedOrigin = approvedBaseUrl.toCanonicalHttpOrigin()
        val expiresAt = expiresAt(now, ttlMillis)
        val scopeHandle = nextScopeHandleLocked()
        val credentials = credentialVault.consumeAll(transientCredentials.values.toSet())
            ?: error("Authentication credential is unavailable")
        scopes[scopeHandle] = ScopeRecord(
            principal = principal,
            kind = ProviderBrokerScopeKind.AUTHENTICATION,
            allowedHook = ExtensionHookIds.SubscriptionProviderValidate,
            approvedOrigin = approvedOrigin,
            accountId = null,
            credentials = credentials,
            capturedHandles = emptySet(),
            opaqueContexts = emptyMap(),
            authenticationReceipts = emptyMap(),
            expiresAtEpochMillis = expiresAt,
        )
        scopeHandle
    }

    fun mintAccountScope(
        principal: ExtensionPrincipal,
        allowedHook: Hook,
        account: ProviderAccount,
        credential: ProviderCredentialEntity,
        ttlMillis: Long = defaultTtlMillis,
    ): BrokerScopeHandle = synchronized(lock) {
        requireActive(principal)
        require(principal.owns(account)) {
            "Extension principal does not own the provider account"
        }
        require(credential.accountId == account.id) {
            "Provider credential does not belong to the provider account"
        }
        val now = clock()
        purgeExpiredLocked(now)
        requireCapacityLocked()
        val approvedOrigin = account.baseUrl.toCanonicalHttpOrigin()
        val expiresAt = expiresAt(now, ttlMillis)
        val encryptedMaterial = credentialVault.decrypt(credential)
            ?: error("Provider credential is unavailable")
        val material = ProviderCredentialMaterial.decode(encryptedMaterial)
        val handle = CredentialHandle(credential.credentialHandle)
        val scopeHandle = nextScopeHandleLocked()
        scopes[scopeHandle] = ScopeRecord(
            principal = principal,
            kind = ProviderBrokerScopeKind.ACCOUNT,
            allowedHook = allowedHook,
            approvedOrigin = approvedOrigin,
            accountId = account.id,
            credentials = mapOf(handle to material.primaryCredential),
            capturedHandles = emptySet(),
            opaqueContexts = material.opaqueContexts,
            authenticationReceipts = emptyMap(),
            expiresAtEpochMillis = expiresAt,
        )
        scopeHandle
    }

    fun authorize(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
    ): ProviderBrokerScopeAccess = synchronized(lock) {
        val record = requireScopeLocked(scope, principal, hook)
        record.toAccess(scope)
    }

    fun resolveCredential(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        handle: CredentialHandle,
    ): String = synchronized(lock) {
        val record = requireScopeLocked(scope, principal, hook)
        record.credentials[handle]
            ?: throw SecurityException("Credential handle is outside the broker scope")
    }

    fun resolveContext(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        reference: ContextReference,
    ): String = synchronized(lock) {
        val record = requireScopeLocked(scope, principal, hook)
        record.opaqueContexts[reference.key]
            ?: throw SecurityException("Opaque context is outside the broker scope")
    }

    fun recordAuthentication(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        primaryCredential: String,
        opaqueContexts: Map<String, String>,
    ): ProviderAuthenticationReceipt = synchronized(lock) {
        require(primaryCredential.isNotBlank()) { "Captured credential must not be blank" }
        require(opaqueContexts.size <= MAXIMUM_CONTEXTS_PER_SCOPE) {
            "Authentication captured too many opaque contexts"
        }
        require(opaqueContexts.values.all { value ->
            value.isNotEmpty() && value.encodeToByteArray().size <= MAXIMUM_CONTEXT_VALUE_BYTES
        }) { "Captured opaque context is empty or exceeds the host limit" }
        val record = requireScopeLocked(scope, principal, hook)
        check(record.kind == ProviderBrokerScopeKind.AUTHENTICATION) {
            "Authentication receipts are only available during provider authentication"
        }
        check(record.authenticationReceipts.isEmpty() && record.capturedHandles.isEmpty()) {
            "Authentication already completed for this broker scope"
        }
        check(record.credentials.size < maximumCredentialsPerScope) {
            "Broker scope contains too many credentials"
        }
        ProviderCredentialMaterial(primaryCredential, opaqueContexts).encode()
        val credentialHandle = nextCredentialHandleLocked()
        val receipt = nextAuthenticationReceiptLocked()
        scopes[scope] = record.copy(
            credentials = record.credentials + (credentialHandle to primaryCredential),
            capturedHandles = setOf(credentialHandle),
            opaqueContexts = opaqueContexts,
            authenticationReceipts = mapOf(receipt to credentialHandle),
        )
        receipt
    }

    fun consumeAuthenticationReceipt(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        receipt: ProviderAuthenticationReceipt,
    ): CapturedProviderAuthentication = synchronized(lock) {
        val record = requireScopeLocked(
            scope,
            principal,
            ExtensionHookIds.SubscriptionProviderValidate,
        )
        check(record.kind == ProviderBrokerScopeKind.AUTHENTICATION) {
            "Only an authentication scope can consume a receipt"
        }
        val credentialHandle = record.authenticationReceipts[receipt]
            ?: throw SecurityException("Authentication receipt is inactive")
        scopes[scope] = record.copy(authenticationReceipts = emptyMap())
        CapturedProviderAuthentication(
            credentialHandle = credentialHandle,
            approvedOrigin = record.approvedOrigin,
            opaqueContexts = record.opaqueContexts,
        )
    }

    fun advanceToInitialRefresh(
        authenticationScope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        capturedHandle: CredentialHandle,
        ttlMillis: Long = defaultTtlMillis,
    ): BrokerScopeHandle = synchronized(lock) {
        val record = requireScopeLocked(
            authenticationScope,
            principal,
            ExtensionHookIds.SubscriptionProviderValidate,
        )
        check(record.kind == ProviderBrokerScopeKind.AUTHENTICATION) {
            "Only an authentication scope can advance to initial refresh"
        }
        check(capturedHandle in record.capturedHandles) {
            "Initial refresh requires a credential captured by this authentication scope"
        }
        val expiresAt = expiresAt(clock(), ttlMillis)
        val newScope = nextScopeHandleLocked(excluding = authenticationScope)
        val capturedSecret = checkNotNull(record.credentials[capturedHandle])
        scopes.remove(authenticationScope)
        scopes[newScope] = ScopeRecord(
            principal = principal,
            kind = ProviderBrokerScopeKind.INITIAL_REFRESH,
            allowedHook = ExtensionHookIds.SubscriptionContentRefresh,
            approvedOrigin = record.approvedOrigin,
            accountId = null,
            credentials = mapOf(capturedHandle to capturedSecret),
            capturedHandles = setOf(capturedHandle),
            opaqueContexts = record.opaqueContexts,
            authenticationReceipts = emptyMap(),
            expiresAtEpochMillis = expiresAt,
        )
        newScope
    }

    fun completeInitialRefresh(
        refreshScope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        capturedHandle: CredentialHandle,
    ): String = synchronized(lock) {
        val record = requireScopeLocked(
            refreshScope,
            principal,
            ExtensionHookIds.SubscriptionContentRefresh,
        )
        check(record.kind == ProviderBrokerScopeKind.INITIAL_REFRESH) {
            "Only an initial refresh scope can be completed"
        }
        check(capturedHandle in record.capturedHandles) {
            "Initial refresh credential was not captured by its authentication scope"
        }
        val secret = checkNotNull(record.credentials[capturedHandle])
        val material = ProviderCredentialMaterial(
            primaryCredential = secret,
            opaqueContexts = record.opaqueContexts,
        ).encode()
        scopes.remove(refreshScope)
        material
    }

    fun close(scope: BrokerScopeHandle): Boolean = synchronized(lock) {
        scopes.remove(scope) != null
    }

    fun closeAll(principal: ExtensionPrincipal): Int = synchronized(lock) {
        val ownedScopes = scopes.filterValues { record -> record.principal == principal }.keys
        ownedScopes.forEach(scopes::remove)
        ownedScopes.size
    }

    private fun requireScopeLocked(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
    ): ScopeRecord {
        requireActive(principal)
        val now = clock()
        val record = scopes[scope]
            ?: throw SecurityException("Broker scope is inactive")
        if (record.expiresAtEpochMillis <= now) {
            scopes.remove(scope)
            throw SecurityException("Broker scope is inactive")
        }
        if (record.principal != principal || record.allowedHook != hook) {
            throw SecurityException("Broker scope does not authorize this invocation")
        }
        return record
    }

    private fun requireActive(principal: ExtensionPrincipal) {
        if (!principalRegistry.isActive(principal)) {
            throw SecurityException("Extension principal is inactive")
        }
    }

    private fun expiresAt(now: Long, ttlMillis: Long): Long {
        require(ttlMillis in 1..maximumTtlMillis) { "Broker scope TTL exceeds the host limit" }
        return try {
            Math.addExact(now, ttlMillis)
        } catch (_: ArithmeticException) {
            error("Broker scope expiry overflow")
        }
    }

    private fun requireCapacityLocked() {
        check(scopes.size < maximumScopes) { "Broker scope capacity is exhausted" }
    }

    private fun purgeExpiredLocked(now: Long) {
        scopes.entries.removeAll { (_, record) -> record.expiresAtEpochMillis <= now }
    }

    private fun nextScopeHandleLocked(
        excluding: BrokerScopeHandle? = null,
    ): BrokerScopeHandle = nextUniqueHandle { candidate ->
        val handle = BrokerScopeHandle("broker-scope:$candidate")
        handle != excluding && handle !in scopes
    }.let { BrokerScopeHandle("broker-scope:$it") }

    private fun nextCredentialHandleLocked(): CredentialHandle = nextUniqueHandle { candidate ->
        val handle = CredentialHandle("broker-captured:$candidate")
        scopes.values.none { record -> handle in record.credentials }
    }.let { CredentialHandle("broker-captured:$it") }

    private fun nextAuthenticationReceiptLocked(): ProviderAuthenticationReceipt =
        nextUniqueHandle { candidate ->
            val receipt = ProviderAuthenticationReceipt("broker-receipt:$candidate")
            scopes.values.none { record -> receipt in record.authenticationReceipts }
        }.let { ProviderAuthenticationReceipt("broker-receipt:$it") }

    private fun nextUniqueHandle(isAvailable: (String) -> Boolean): String {
        repeat(MAXIMUM_HANDLE_GENERATION_ATTEMPTS) {
            val candidate = idFactory()
            require(candidate.isNotBlank()) { "Broker handle id must not be blank" }
            if (isAvailable(candidate)) return candidate
        }
        error("Unable to mint a unique broker handle")
    }

    private fun ScopeRecord.toAccess(handle: BrokerScopeHandle) = ProviderBrokerScopeAccess(
        scope = handle,
        kind = kind,
        allowedHook = allowedHook,
        approvedOrigin = approvedOrigin,
        accountId = accountId,
        credentialHandles = credentials.keys.toSet(),
        opaqueContextKeys = opaqueContexts.keys,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

    private data class ScopeRecord(
        val principal: ExtensionPrincipal,
        val kind: ProviderBrokerScopeKind,
        val allowedHook: Hook,
        val approvedOrigin: String,
        val accountId: String?,
        val credentials: Map<CredentialHandle, String>,
        val capturedHandles: Set<CredentialHandle>,
        val opaqueContexts: Map<String, String>,
        val authenticationReceipts: Map<ProviderAuthenticationReceipt, CredentialHandle>,
        val expiresAtEpochMillis: Long,
    )

    private companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
        const val DEFAULT_MAXIMUM_SCOPES = 128
        const val DEFAULT_MAXIMUM_CREDENTIALS_PER_SCOPE = 16
        const val MAXIMUM_CONTEXTS_PER_SCOPE = 16
        const val MAXIMUM_CONTEXT_VALUE_BYTES = 4 * 1024
        const val MAXIMUM_HANDLE_GENERATION_ATTEMPTS = 16
    }
}

internal data class ProviderBrokerScopeAccess(
    val scope: BrokerScopeHandle,
    val kind: ProviderBrokerScopeKind,
    val allowedHook: Hook,
    val approvedOrigin: String,
    val accountId: String?,
    val credentialHandles: Set<CredentialHandle>,
    val opaqueContextKeys: Set<String>,
    val expiresAtEpochMillis: Long,
)

internal data class CapturedProviderAuthentication(
    val credentialHandle: CredentialHandle,
    val approvedOrigin: String,
    val opaqueContexts: Map<String, String>,
)

internal enum class ProviderBrokerScopeKind {
    AUTHENTICATION,
    INITIAL_REFRESH,
    ACCOUNT,
}

private data class ProviderBrokerScopeConfiguration(
    val clock: () -> Long,
    val idFactory: () -> String,
    val defaultTtlMillis: Long,
    val maximumTtlMillis: Long,
    val maximumScopes: Int,
    val maximumCredentialsPerScope: Int,
)

@Serializable
internal data class ProviderCredentialMaterial(
    val primaryCredential: String,
    val opaqueContexts: Map<String, String> = emptyMap(),
) {
    fun encode(): String {
        val encoded = MATERIAL_PREFIX + MATERIAL_JSON.encodeToString(this)
        require(encoded.encodeToByteArray().size <= MAXIMUM_MATERIAL_BYTES) {
            "Provider credential material exceeds the host limit"
        }
        return encoded
    }

    companion object {
        fun decode(value: String): ProviderCredentialMaterial =
            if (value.startsWith(MATERIAL_PREFIX)) {
                MATERIAL_JSON.decodeFromString(value.removePrefix(MATERIAL_PREFIX))
            } else {
                ProviderCredentialMaterial(primaryCredential = value)
            }

        private const val MATERIAL_PREFIX = "m3u-provider-material:v1:"
        private const val MAXIMUM_MATERIAL_BYTES = 64 * 1024
        private val MATERIAL_JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
    }
}
