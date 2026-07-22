# Contract terms

[简体中文](glossary.zh-CN.md) · [Developer guide](../README.md)

| Term | What extension code uses it for |
| --- | --- |
| `applicationId` | One of the stable identity values kept across updates. |
| Service class | The extension's `TypedExtensionService` implementation. |
| Signing certificate | The publisher identity kept across updates. |
| `ExtensionId` | The stable ID supplied in `ExtensionManifest`. |
| `ExtensionManifest` | Declares the extension version, Hooks, capabilities, settings, and display metadata. |
| `HookSpec<Request, Result>` | Supplies the request type, result type, Hook ID, and current schema version. |
| `ExtensionHookDeclaration` | Adds an implemented `HookSpec` to `ExtensionManifest`. |
| Capability | Declares an operation the Hook needs the user to authorize. |
| `ExtensionCallContext` | Supplies the invocation ID, granted capabilities, and saved extension settings for the current call. |
| `ExtensionSettingsSnapshot` | Contains ordinary setting values and credential handles for secret fields. |
| `CredentialHandle` | A reference used with broker values; it is not the credential text. |
| `BrokerValue` | One literal, credential reference, concatenation, or encoded value in a broker request. |
| `ExtensionHostNetworkBroker` | Sends a provider HTTP request for the current handler call. |
| `ProviderKind` | A stable lowercase ID for one provider variant. |
| `PlaybackReference` | Provider-owned values saved by M3UAndroid and sent back when playback is requested. |

## Service declaration and extension contract

[`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml) contains the Service declaration. The Kotlin `ExtensionManifest` returned by that Service contains the M3UAndroid contract.

The Hello implementation shows both parts:

- [`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml)
- [`HelloExtensionService.kt`](../../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)

Keep the application ID, Service class, signing certificate, and `ExtensionId` stable across updates. See [Prepare a release or update](compatibility.md).
