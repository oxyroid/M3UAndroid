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
| Capability | Names an operation a Hook needs. The Hook declares it, the manifest requests it, and the user approves it. |
| `ExtensionCallContext` | Supplies the invocation ID, this Hook's effective capabilities, and saved extension settings for the current call. |
| `ExtensionSettingsSnapshot` | Contains ordinary setting values and credential handles for secret fields. |
| `CredentialHandle` | An opaque reference to a submitted or saved secret. It has no read API; an authorized Hook may pass it to the broker as a `SecretReference`. |
| Approved origin | An exact HTTP or HTTPS origin that the current Hook may reach through the broker. |
| Network origin setting | A text setting with `networkOrigin = true`. Saving it explicitly approves its current origin. |
| Broker scope | Short-lived authority for one extension, Hook, invocation, and set of origins. Some scopes also bind a provider account. |
| `BrokerValue` | A literal or a scoped secret/context reference used in broker requests and provider playback headers. |
| `ExtensionHostNetworkBroker` | Sends checked HTTP requests for supported Hooks. `Discover` is not one of them. |
| `ProviderKind` | A stable lowercase ID for one provider variant. |
| `PlaybackReference` | Provider-owned values saved by M3UAndroid and sent back when playback is requested. |
| `ExtensionBackgroundTaskDeclaration` | Declares one periodic task that M3UAndroid schedules through WorkManager while the extension is enabled and authorized. |

For extension identity and version rules, see [Prepare a release or update](compatibility.md).
