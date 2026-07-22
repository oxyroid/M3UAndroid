# Build an Android extension

[简体中文](README.zh-CN.md)

This guide is for people building an extension APK for M3UAndroid. External extensions are still a developer feature, so the contract may change before the feature is opened to all users.

## The short version

An extension is a separate Android app with one bound service. M3UAndroid finds that service, asks for a manifest, and invokes the hooks declared in the manifest.

The important boundary is simple:

```text
your APK                         M3UAndroid
--------                         ----------
declare hooks  -- typed JSON --> validate result
return data    <-- request ----  save data / build playback
use handles    -- broker call -> hold credentials / perform network I/O
```

Your APK never runs inside the M3UAndroid process. It does not receive Room entities, player objects, passwords, or tokens. It returns plain contract data; the host decides what is safe to import.

## Start from the reference extension

The fastest working example is [`:testing:extension-reference`](../../../testing/extension-reference). It already contains a valid service, manifest, typed hook dispatch, cancellation handling, settings, search, metadata, EPG, and a background task.

The SDK is not published as a stable Maven artifact yet. For now, build the reference extension inside this checkout or include the extension modules from the same source revision. A public artifact and compatibility policy are required before third-party distribution is considered stable.

To create a minimal extension:

1. Depend on `:extension:sdk-android` while developing in this repository. It exposes the API and transport types used by the service.
2. Add an exported service to `AndroidManifest.xml`:

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

3. Extend `ExtensionService` and provide an `ExtensionTransport`:

   ```kotlin
   class MyExtensionService : ExtensionService() {
       override val transport: ExtensionTransport = MyExtensionTransport
   }
   ```

4. Give the transport an `ExtensionManifest` and implement only the hooks declared there.
5. Install the APK with Android's package installer, enable **External Extensions** in M3UAndroid, inspect the certificate and requested permissions, then enable it.

The host uses the service action for discovery. Do not request `QUERY_ALL_PACKAGES`, and do not expect the host to load your classes with `DexClassLoader`.

## What goes in the manifest

Think of `ExtensionManifest` as the extension's ID card and permission request:

| Field | What to provide |
| --- | --- |
| `id` | A lowercase ID that never changes for this extension |
| `displayName` | The name shown to users |
| `extensionVersion` | Your semantic version |
| `apiRange` | Host extension API versions you support |
| `hooks` | Every hook you implement and its schema version |
| `capabilities` | Required/optional permissions, each with a clear reason |
| `settingsSchema` | Optional declarative settings |
| `metadata["developer"]` | Developer name shown during authorization |

The current API is `1.0`; current hook schemas are version `1`. A different API major is rejected. An unsupported required hook or unknown required capability makes the extension incompatible.

Use the published `HookSpec<Request, Result>` serializers. They are the wire contract. Do not invent another JSON shape or cast arbitrary payload objects.

## Choose a hook

| Goal | Hook family | Current host support |
| --- | --- | --- |
| Add a subscription provider | discover, validate, refresh | Built-in Emby/Jellyfin use the full path; external provider work is still being completed |
| Resolve playback and close a server session | playback resolve/close | Production path exists for built-in providers |
| Add settings | settings schema | Rendered on phone and TV |
| Add search results | search provider | Connected on phone; results must point to an existing host channel |
| Improve channel title/category | metadata enrichment | Connected during generic provider refresh |
| Add programmes | EPG refresh | Connected during generic provider refresh |
| Run scheduled work | background task | Connected with host quotas and cancellation |

“Contract exists” does not always mean “every old M3U/Xtream path calls it.” Check the last column before depending on a hook.

Search, metadata, and EPG results use a `stableReference`. This is an opaque bridge back to data the host already owns. An unknown reference is ignored; it is never turned directly into a database row or playable item.

## Permissions, upgrades, and reauthorization

Declaring a capability only asks for it. The hook can use it only after host policy and the user grant it.

On first enable, M3UAndroid shows the package, developer, version, signing-certificate SHA-256, and every requested capability with its reason. The accepted certificate is pinned.

For later upgrades:

- same signer, no new capabilities: existing trust can be restored;
- new optional capability: it stays ungranted until the user reauthorizes;
- new required capability: automatic restore stops until the user reauthorizes;
- different signer or changed extension ID: the extension is disabled and requires a new trust decision.

Write permission reasons for users, not for the runtime. “Read programme data from your configured server” is useful; “needs `epg.read`” is not.

## Credentials and network requests

The host owns all secrets. Your extension receives an opaque `CredentialHandle`, never the underlying password or token.

Network calls go through `HostNetworkBroker`. The broker:

- allows only the account origin and separately approved origins;
- removes authentication headers supplied by the extension;
- injects a host-held secret by reference;
- checks every redirect again;
- limits time, response size, and concurrency;
- can capture a login value from a header or JSON pointer and return only its handle.

A plugin that must read a raw password/token, encrypt credentials itself, or bypass the broker is not compatible with this platform.

## Settings

Describe settings with `ExtensionSettingSchema`; do not build your own settings Activity for host configuration. Phone and TV support boolean, single-choice, text, number, and secret fields.

Keys are scoped as `section/field`. A `SECRET` field cannot have a plaintext default. The host stores it with Android Keystore-backed encryption and gives hook calls only its handle. The UI shows “configured,” replace, and clear actions; it never fills an input with the saved secret.

Changing a section's schema version clears that section's old values and secrets. Treat missing values as normal.

## Write hooks that survive real failures

- Treat invocation IDs as unique and stop work promptly on cancellation.
- Make refresh, retry, close, and cleanup idempotent.
- Expect concurrent calls; do not rely on global mutable request state.
- Keep requests and results inside the declared schema and size limits.
- Treat refresh/close reasons as open strings, not exhaustive enums.
- Return the standard error envelope; never put credentials or response bodies in errors or logs.

## Before sharing an APK

Use the reference extension and conformance tests to verify discovery, handshake, manifest decoding, invocation, cancellation, and health. Also test:

- incompatible API and hook versions;
- process/Binder death, timeout, oversized output, and repeated failure;
- denied origins and cross-origin redirects;
- logs and diagnostics containing no password, token, auth header, or captured secret;
- same-signer and different-signer upgrades;
- phone and TV authorization, settings, disable, reauthorize, clear data, and diagnostics.

Do not describe an APK as generally supported while external extensions remain behind the developer switch.
