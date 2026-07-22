# Extension Runtime Architecture

## Purpose

The formal extension system is a host-controlled runtime-hook platform. The host
defines when a hook may run, grants explicit capabilities, passes a bounded
request, and consumes a declarative result. An extension cannot call arbitrary
host APIs, write host persistence directly, or take ownership of UI and playback
lifecycle boundaries.

The previous RPC, remote service, package scanner, and sample app were an
experimental implementation and have been removed. The formal system starts from
an explicit contract rather than carrying that architecture forward.

## Modules

### `extension/api`

This pure Kotlin module defines:

- `ExtensionId`, `ExtensionApiVersion`, `ExtensionApiRange`, and
  `ExtensionManifest`
- `Hook`, `ExtensionHookIds`, `ExtensionHookDeclaration`, and `ExtensionHook`
- `Capability`, `ExtensionCapabilityIds`, and `ExtensionCapabilityRequest`
- `ExtensionInvocation`, `ExtensionPayload`, `ExtensionHookOutcome`,
  `ExtensionResult`, and `ExtensionError`
- `ExtensionEntrypoint`

The contract has no dependency on Android, app, business, data, Room, DAO,
Compose, player implementations, parsers, or repositories. Payload types should
remain composed of stable Kotlin values with a clear serialized representation.
If binary values are added, their equality and hashing must compare content.

### `extension/runtime`

This pure Kotlin/JVM module is the host runtime. `ExtensionRuntime` registers
entrypoints, exposes providers by supported hook, validates the host API range,
enforces required capabilities, creates an invocation identifier, invokes one
bound hook, and turns provider exceptions into structured failures.

The current registry is for built-in providers. External discovery, process
isolation, IPC, signatures, or transports can be added behind the same entrypoint
and invocation boundaries without exposing host internals to extensions.

## Invocation flow

```text
host call site
  -> ExtensionRuntime.invoke(extensionId, hook, capabilities, payload)
  -> locate registered manifest
  -> validate host API range
  -> locate hook declaration
  -> validate granted capabilities
  -> locate bound hook implementation
  -> invoke with a generated invocation id
  -> return a structured success or failure
  -> owning host layer consumes the declarative result
```

Registration requires the manifest's declared hooks and the entrypoint's bound
hooks to match exactly. A hook capability must also appear in the manifest's
capability requests. Duplicate extension identifiers and duplicate hook bindings
are rejected.

## Ownership boundaries

| Concern | Owner |
| --- | --- |
| Hook identifiers and payload contracts | `extension/api` |
| Registration and invocation policy | `extension/runtime` |
| Credentials and network transport | `data` |
| Database transactions and migrations | `data` |
| Preserving favorites, hidden state, ordering, and history | `data` |
| Feature state and user actions | `business` |
| Routes, forms, permissions, and presentation | `app` |
| Player construction and session callbacks | host playback integration |

An extension-facing type must not contain Room entities or implementation types
from app, business, data, parser, repository, or player modules.

## Emby-compatible validation case

Emby and Jellyfin are user-visible server kinds served by a shared
`EmbyCompatibleProvider`. This provider validates that the hook contract can
support a real authenticated subscription service without becoming a playlist-
only plugin surface.

The provider returns declarative channel descriptors with stable playback
references. A data-layer importer persists provider/account/item/media-source
references and preserves local channel state. It must not put access tokens or
short-lived playback URLs into `Channel.url`.

The current persistence adapter uses three Room tables:

- `provider_accounts` links a provider identity to one host playlist.
- `provider_credentials` stores the account token behind its account reference.
- `channel_playback_references` links a host channel to the stable remote item,
  optional media source, source type, and optional direct fallback.

Provider channels persist `Channel.URL_DYNAMIC`, which is not a network URL. A
missing playback reference is therefore a hard resolution failure rather than a
request for that marker.

Immediately before playback, the host invokes `playback.source.resolve`. The
result may contain a URL, HTTP headers, media-source information, an expiration
hint, and a server session identifier. If a session was opened, the host invokes
`playback.session.close` on stop, release, channel change, playback end, or final
playback failure. Replay resolves the stable reference again instead of reusing
the prior URL or session.

Emby requests use its media-browser authorization headers. Jellyfin requests use
the current standard `Authorization: MediaBrowser …` header so servers that
disable deprecated authorization continue to work. Resolved playback headers
are scoped to the active media source and cleared when the player is released.

## Security and diagnostics

- Grant only capabilities declared by the provider and approved by host policy.
- Do not log credentials, access tokens, resolved URLs containing secrets, or
  request headers.
- Use the platform TLS trust store for authenticated provider API and playback
  traffic. Compatibility settings for ordinary playlist traffic must not weaken
  provider credential transport.
- Keep errors structured with a stable code, a safe message, a recoverable flag,
  and non-sensitive details.
- Use invocation identifiers to correlate host and provider diagnostics without
  embedding secrets.
- Treat external transports as untrusted boundaries when they are introduced.
