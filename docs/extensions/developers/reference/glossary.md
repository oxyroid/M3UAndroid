# Terms and extension identity

[简体中文](glossary.zh-CN.md) · [Developer guide](../README.md)

| Name | Who uses it | Hello example |
| --- | --- | --- |
| `applicationId` | Package component of the extension's trusted identity | `com.m3u.samples.hello.extension` |
| Service class | Component M3UAndroid binds for this extension | `HelloExtensionService` |
| `ExtensionId` | Identity used by the M3UAndroid runtime and catalog | `com.m3u.samples.hello` |
| `ExtensionManifest` | The extension declares its name, version, settings, and Hooks | The Kotlin object in `HelloExtensionService.kt` |
| Hook | One host call with a defined input and output | `settings.schema.contribute` |
| Capability | A category of work the user authorizes | `settings.contribute` |
| Provider | A service that supplies channel subscriptions, refresh, and playback URLs | Built-in Emby and Jellyfin |

## Two manifests

They define different parts of the M3UAndroid extension:

- [`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml) registers the extension Service.
- `ExtensionManifest` declares the extension's M3UAndroid identity, settings, capabilities, and Hooks.

## Four stable identity fields

Keep these stable after publishing an extension:

1. Android `applicationId`
2. Service class name
3. APK signing certificate
4. `ExtensionId`

Changing any of them can make the host treat the APK as a different extension or ask the user to confirm its identity again.
