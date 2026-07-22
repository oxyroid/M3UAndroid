# What happened during that call

[简体中文](concepts.zh-CN.md) · [Developer guide](README.md)

You have already run the dynamic-settings Hook. Now attach code names to that path:

```text
open Hello settings
  -> HostHookSpecs.SettingsSchema
  -> HelloExtensionService
  -> handle { request, context -> result }
  -> SettingsSchemaResult
  -> M3UAndroid renders the Device section
```

## M3UAndroid discovers the Service

[`AndroidManifest.xml`](../../../samples/hello-extension/src/main/AndroidManifest.xml) registers `HelloExtensionService` as the component M3UAndroid discovers and connects to.

## ExtensionManifest describes the extension

The `ExtensionManifest` in [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt) tells the host:

- the extension identity and version;
- which Hooks it implements;
- which capabilities it requests;
- whether it has fixed setting fields.

The Service declaration provides the connection entry; `ExtensionManifest` provides the M3UAndroid extension contract.

## HookSpec fixes the call types

`HostHookSpecs.SettingsSchema` defines:

- Hook name `settings.schema.contribute`;
- schema version 1;
- request type `SettingsSchemaRequest`;
- result type `SettingsSchemaResult`.

Code inside `handle(HostHookSpecs.SettingsSchema)` therefore does not inspect Hook strings or parse JSON manually.

## A capability is user authorization

Hello requests `settings.contribute` because it adds fields to the host settings screen. On first enablement, M3UAndroid shows the reason to the user. The runtime calls the handler only when that capability is granted.

A capability says what category of work the extension may do. A Hook says what this specific call does.

## Fixed and dynamic settings

Hello demonstrates both forms:

- **Greeting** comes from `ExtensionManifest.settingsSchema` and is always present.
- **Phone name** comes from `settings.schema.contribute` and can vary with the request.

Both return declarative schemas. M3UAndroid owns field rendering, validation, and storage.

## Built-in extensions only skip Android IPC

Emby/Jellyfin handlers run in the M3UAndroid process. Hello's handler runs in a separate APK. They use the same `HookSpec` and request/result types; the APK path adds a Service call.

Use [Terms and extension identity](reference/glossary.md) to compare `applicationId`, Service class, and `ExtensionId`. Choose the next feature from the [Hook catalog](hooks.md).
