# Publish and upgrade an extension

[简体中文](compatibility.zh-CN.md) · [Developer guide](../README.md)

Read this after the extension works. The SDK is still a developer preview, so build the host and extension from the same checkout.

## Create your own in-repository extension

The shortest current path is to copy the `samples/hello-extension` module. Give the copy its own package and Service identity, its own `ExtensionId`, display name, and developer name, then register the module in this checkout. Keep its dependency on `:extension:sdk-android`; the sample is an in-repository template while the SDK artifact remains unpublished.

## The three version numbers serve different purposes

| Version | Change it when |
| --- | --- |
| Extension `extensionVersion` | Publishing an extension feature or fix |
| Hook schema version | One Hook's input or output becomes incompatible |
| Extension API major | The platform contract as a whole becomes incompatible |

Give new optional fields defaults. Raise a schema or API major only when an older host or extension cannot safely understand the new shape.

## Upgrade checklist

- The four extension identity fields stay unchanged.
- `extensionVersion` is updated.
- Existing ordinary settings still load.
- Stored settings reconcile after fields are removed or renamed.
- A new required capability asks the user to authorize again.
- After a same-signature update, refreshing the extension list loads the new manifest.
