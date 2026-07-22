# Android extension developer guide

[简体中文](README.zh-CN.md)

This guide describes the implemented local Android extension contract. External extensions are a developer feature today: users install an APK with the Android package installer, enable **External Extensions**, inspect its identity and requested capabilities, and explicitly trust it.

## Execution and trust model

An extension runs in its own APK process. The host discovers an exported bound service with action `com.m3u.extension.action.BIND_EXTENSION`; it never loads extension bytecode with `DexClassLoader`. The service must require `com.m3u.permission.BIND_EXTENSION_HOST`.

On first enable, the host shows the package, extension name, developer metadata, semantic version, requested capabilities, and signing-certificate SHA-256. The accepted certificate is pinned. A later certificate mismatch disables the extension until the user explicitly trusts it again.

A same-signer upgrade does not silently expand authority. Previously granted capabilities are intersected with the new manifest. Adding a required capability disables automatic restoration and requires a new user confirmation; adding an optional capability leaves it ungranted until the user reauthorizes the extension.

The host and extension exchange a small AIDL control plane and JSON through `ParcelFileDescriptor`. The stream carries a `SerializedExtensionEnvelope` or `SerializedExtensionResult`, avoiding Binder's transaction-size ceiling. Handshake, manifest, invoke, cancel, and health are transport operations.

## Modules and a minimal service

Use the contracts in `:extension:api` and the Android service base in `:extension:sdk-android`. `:testing:extension-reference` is the executable reference implementation and conformance fixture.

Declare the service as follows:

```xml
<service
    android:name=".MyExtensionService"
    android:exported="true"
    android:permission="com.m3u.permission.BIND_EXTENSION_HOST">
    <intent-filter>
        <action android:name="com.m3u.extension.action.BIND_EXTENSION" />
    </intent-filter>
</service>
```

Subclass `ExtensionService` and provide an `ExtensionTransport`. The transport exposes the manifest and handles typed envelopes. Override the SDK service's broker-aware `invoke` only when a hook needs host-mediated networking.

## Manifest and compatibility

An `ExtensionManifest` contains:

- a lowercase stable `ExtensionId`;
- display name and semantic extension version;
- supported extension API range;
- one declaration per hook, including its schema version and required capabilities;
- required or optional capability requests with a user-facing reason;
- optional declarative settings schema and diagnostic metadata such as `developer`.

The current extension API is `1.0`, and the implemented hook schemas are version `1`. A different API major is rejected. Within the same major, the host negotiates each declared hook schema. Unknown required capabilities or unsupported required hook schemas make the extension incompatible. JSON decoders ignore unknown optional fields, but extensions must not depend on the host preserving them.

Use the published `HookSpec<Request, Result>` serializers. Do not cast arbitrary payload objects or define an alternative wire representation.

## Hooks

The typed catalog currently defines:

- subscription provider discovery, validation, content refresh, playback resolution, and playback-session close;
- settings schema contribution;
- EPG refresh;
- channel metadata enrichment;
- search provider query;
- background task execution.

Host integration is intentionally narrower than the catalog. Subscription and playback hooks have production host call sites for the built-in Emby/Jellyfin provider. External plugin lifecycle, transport, cancellation, health, and background task execution are connected. Search contributions are connected on the smartphone surface: an item is displayed only when its opaque `stableReference` resolves to an existing, non-hidden host channel. Provider refresh also invokes metadata and EPG contributors: metadata patches can update only host-approved title/category fields, while EPG results are validated and imported into isolated host-owned sources. These two importers currently run for generic provider playlists; integration with every legacy M3U/Xtream import path is still in progress. Settings contracts are typed, but the complete external settings storage/UI path is not yet production-ready.

Never return a database entity, player object, `DataSource`, password, or token. Return declarative data and stable opaque references; the host owns validation, persistence, import, and playback construction.

## Capabilities and credentials

Capabilities are requested in the manifest and granted by host policy plus user approval. Declaring a capability does not grant it. A hook can run only with the capabilities declared for it and approved by the host.

External extensions must not receive plaintext credentials. Credentials are represented by opaque handles. Network access goes through the host broker, which restricts requests to the account base origin or separately approved origins, strips extension-supplied authentication headers, injects host-held secrets, limits redirects, time, response size, and concurrency, and returns redacted data. Login capture rules may store a header or JSON-pointer value in the host vault and return only a handle.

An extension that must read raw passwords or tokens, manage its own credential encryption, or bypass the broker is incompatible with this platform.

## Invocation rules

- Treat invocation IDs as unique and propagate cancellation promptly.
- Keep request and response data within the declared schema and host size limits.
- Use extensible string values for reasons such as refresh or session close; tolerate values introduced by newer compatible hosts.
- Make close, cleanup, refresh, and retry behavior idempotent.
- Return the stable error envelope rather than leaking exceptions or secrets.
- Do not assume invocations are serial; the host applies bounded concurrency and timeouts.

## Development checklist

Before distributing a test APK:

1. Verify discovery, handshake, manifest decoding, invoke, cancellation, and health against the reference/conformance fixtures.
2. Test an incompatible API range and an unsupported hook schema.
3. Test process death, Binder death, timeout, oversized output, and repeated failures.
4. Confirm logs and errors contain no password, token, authorization header, or captured secret.
5. Confirm every network origin and redirect is rejected unless host-approved.
6. Install an upgraded APK and verify the same signer remains trusted; verify a different signer is disabled.

Do not market an APK as generally supported while the host feature remains behind the developer switch.
