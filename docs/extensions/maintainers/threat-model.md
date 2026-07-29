# External APK extension threat model

[简体中文](threat-model.zh-CN.md) · [Maintainer guide](README.md)

## Current decision

External APK extensions stay behind the opt-in developer switch. The host already isolates their
code, credentials, network access, and persisted results, but that is not enough to make the
feature safe to enable by default.

Before default enablement, maintainers must make explicit product decisions for the unresolved
items in [What remains open](#what-remains-open). In particular, the current broker permits HTTP,
does not defend against DNS rebinding, and cannot prevent an extension from cooperating with an
approved server.

## What this model covers

An external extension is an untrusted Android package, including its service, process, private
storage, and every value it returns. A built-in extension is trusted host code: it uses the same
typed Hook contracts, but it is part of the host's trusted computing base and is not sandboxed by
the external transport.

This model assumes Android's app-process, private-storage, Binder, package-signing, and Keystore
boundaries are working. A rooted or otherwise compromised device, a compromised host APK, and
Android platform vulnerabilities are outside its scope.

## What the host protects

| Asset | Failure we must prevent |
| --- | --- |
| Provider passwords, tokens, and secret settings | An extension reads or persists plaintext credentials |
| Extension identity, certificate pin, grants, approved origins, and enabled state | One package silently inherits another package's trust |
| Accounts, playlists, channels, EPG, metadata, and playback sessions | An extension changes data outside the invocation it owns |
| Search text, channel details, account metadata, and playback references | A Hook receives more user data than its job requires |
| Host process, Binder endpoints, network broker, and background quota | A slow, crashing, or hostile extension makes the app unusable |
| Diagnostic data | Logs or exports disclose credentials or authentication material |

## Trust boundary

```text
untrusted APK service
        │  Binder control messages + PFD JSON
        ▼
Android transport ──► typed runtime ──► feature repository/importer ──► Room/player/UI
        │                    │
        │                    └── revocable request scope ──► HostNetworkBroker
        │                                                     │
        └── package/service/UID/certificate checks             └── approved origin
                                                               │
                                                        CredentialVault
```

The extension proposes data. Only the host may apply it to Room, playback, UI, or scheduled work.
Network and credential access exist only for the current invocation and are revoked when that
invocation ends.

## What each Hook discloses

Capability approval is also data-disclosure approval. Review this table whenever a request field,
capability, or broker scope changes.

| Hook | Data visible to the extension | Network scope | Host acceptance boundary |
| --- | --- | --- | --- |
| Provider discover | Locale tag | None | Validate the descriptor and settings schema |
| Provider validate | Provider kind, ordinary setting values, and opaque credential handles | Login scope for the submitted provider origin | Consume one-time authentication evidence and create the account |
| Provider refresh | Account metadata, opaque credential handle, and refresh reason | That account's origin | Validate source ownership and import channels transactionally |
| Playback resolve | Account metadata, opaque credential handle, playback reference, and preferences | That account's origin | Validate URL, headers, media source, and session before playback |
| Playback close | Account, playback reference, session identifiers, and close reason | That account's origin | Validate account/session ownership and close idempotently |
| Settings schema | Locale tag and UI surface | Approved manifest and saved setting origins when declared | Validate and render a declarative schema |
| EPG refresh | Source IDs, time range, and optional account/credential handle | Account origin or approved origins | Validate time range and ownership before import |
| Metadata enrichment | Stable channel reference, title, category, and optional account/credential handle | Account origin or approved origins | Apply only patches for channels in the request |
| Search | Search text, result limit, and optional account/credential handle | Account origin or approved origins | Accept only valid account and remote IDs |
| Background task | Declared task ID, input, and retry count | Approved origins when declared | Run only declared work within host scheduling and runtime limits |

Opaque handles and stable references are not secrets, but they can still be correlated. They
remain personal metadata and must not be added to unrelated Hooks for convenience.

## Attacker capabilities

Assume an external extension can:

- publish a misleading manifest or claim another extension ID;
- be updated with a different signing certificate;
- return malformed, deeply nested, oversized, late, repeated, or false results;
- ignore cancellation, retain a host bridge, block, crash, or repeatedly reconnect;
- control or cooperate with a server on an approved origin;
- retain any ordinary user data legitimately disclosed to its Hooks;
- consume its own process CPU, memory, storage, or other device resources;
- present misleading names or descriptions to the user.

## Implemented controls

| Threat | Current control | Evidence |
| --- | --- | --- |
| Package or extension-ID impersonation | Revalidate package, service, UID, and certificate at connection time; pin the reviewed certificate; allow only one trusted owner for an extension ID; disable on signer change | `ExtensionTrustStoreTest`, `ExtensionPluginRepositoryLifecycleTest`, `HostileExternalExtensionIpcTest` |
| Host process failure | Run external code in another process; bound calls, stream sizes, nesting depth, deadlines, concurrency, cancellation, Binder death, and late callbacks | `ExtensionRuntimeTest`, `ExtensionConnectionStateTest`, `HostileExternalExtensionIpcTest` |
| Credential disclosure | Store secrets with Keystore-backed AES-GCM; send opaque handles; inject authentication only inside the broker; redact captured authentication material | `CredentialVaultTest`, `HostNetworkBrokerSecurityTest`, `ExtensionHostBridgeTest` |
| Unapproved network access | Grant network per Hook; require an exact scheme/host/port origin; restrict redirects, headers, response sizes, deadlines, concurrency, and cumulative request/response budgets | `ExtensionNetworkOriginContractTest`, `HostNetworkBrokerSecurityTest`, `InvocationBudgetPropagationTest` |
| Persisted-data corruption | Decode typed results, validate request ownership and limits, and apply results through host importers and transactions | Provider and contribution repository/importer tests |
| Access after disable or invocation end | Revoke runtime registration, connection, broker scope, retained bridge, scheduled work, and host-owned extension data | `ExtensionPluginRepositoryLifecycleTest`, `ExtensionHostBridgeTest`, `HostileExternalExtensionIpcTest` |
| Accidental backup disclosure | Exclude extension trust, settings secrets, provider credentials, and tokens from backup; restored providers require authentication | Provider backup/restore and credential tests |

The hostile IPC signer test uses the real discovered service and its real PackageManager
certificate, compared with a seeded stale persisted certificate pin. It verifies the production
repository's disable and reviewed-repin behavior; it does not install a differently signed
replacement APK.

## What remains open

These are current limits, not hypothetical protections:

1. **Transport confidentiality is undecided.** Both HTTP and HTTPS origins are accepted because
   local IPTV servers commonly use HTTP. HTTP traffic can be observed or changed on the network.
2. **Resolved-address policy is missing.** Exact scheme/host/port matching does not prevent DNS
   rebinding or access to loopback, link-local, or private addresses after name resolution.
3. **An approved server can cooperate with an extension.** Response redaction prevents common
   accidental leaks, but a server can encode sensitive information into an otherwise allowed
   response. Redaction is defense in depth, not a confidentiality proof.
4. **Origin approval is broad.** Approval currently covers allowed HTTP methods and paths on that
   origin, including endpoints with side effects. It is not an endpoint-by-endpoint policy.
5. **Package admission is not developer reputation.** Certificate continuity proves that an
   update has the same signer; it does not establish identity, reputation, revocation, or a public
   trust chain.
6. **No complete side-channel guarantee exists.** Rejecting direct network permission on an
   extension package protects the supported host path, but cannot prove that another component,
   app, device channel, or approved server cannot relay data.
7. **Revocation cannot erase copies.** Clearing host data cannot delete information the extension
   previously stored in its own private storage.
8. **Typed data can still be dishonest.** Ownership and shape checks prevent out-of-scope writes,
   not false or low-quality metadata within the approved scope.
9. **External isolation does not apply to built-ins.** A built-in timeout bounds the host call, but
   built-in code still runs inside the trusted host process.

## Requirements before default enablement

The developer switch may be removed only after all of the following have a recorded outcome:

- choose and test an HTTPS/LAN policy, including how HTTP risk is shown to the user;
- define resolved-address rules for loopback, link-local, private, and DNS-changing targets;
- either accept approved-server cooperation as a product risk or move protected-response parsing
  into the host;
- review every Hook disclosure and capability as a privacy decision, with matching authorization
  copy;
- define package admission and certificate-revocation expectations beyond first-use pinning;
- pass the remaining external-extension gates in
  [Current status and release gates](status-and-release.md#before-opening-external-extensions).

Until those decisions are complete, the built-in provider path may ship independently, while
external APK extensions remain a developer preview.
