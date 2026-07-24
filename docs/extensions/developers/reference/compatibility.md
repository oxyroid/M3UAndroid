# Prepare a release or update

[简体中文](compatibility.zh-CN.md) · [Developer guide](../README.md)

Before an update, decide separately whether you are changing extension identity, extension code version, a Hook contract, saved settings, or capabilities.

## Keep identity stable

Keep these values unchanged across updates:

1. Android `applicationId`
2. Service class name
3. Signing certificate
4. `ExtensionId`

Change one only when you intend to create a different extension identity.

## Set each version deliberately

| Value | Update rule |
| --- | --- |
| `extensionVersion` | Increase it for every released feature or fix. |
| `apiRange` | Set it to the host API range supported by this build. |
| Hook `schemaVersion` | Copy it from the selected `HookSpec`; the declaration and handler must match exactly. |
| `ExtensionSettingSchema.version` | Version each manifest or dynamic settings section independently. Increase the affected section when a field is removed, renamed, or changes type or meaning. |

Fixed settings use `manifest.settingsSchema.version`; dynamic settings use each returned
`section.schema.version`. For a compatible field addition, keep that section's version and
existing keys. M3UAndroid applies the new field's default. If you increase a section version,
M3UAndroid clears saved values and credential handles for that section before applying defaults.

## Review capability changes

Adding a required capability changes what the user authorizes. Add the capability to both the Hook
declaration and `manifest.capabilities`, and give it a concrete reason. Verify the authorization
prompt as part of the update test.

When removing a capability, also remove every handler operation that uses it.

## Review network origin changes

Adding an origin to `manifest.networkOrigins` does not add it to an existing approval. Verify the
reauthorization flow before relying on the new origin.

A field marked `networkOrigin` has no default. Increasing its settings section version clears the
saved value and approval, so the user must save that origin again.

## Release checklist

- Build the extension module successfully.
- Trigger every declared Hook from its host feature.
- Test both a first enable and an update over the previous version.
- Confirm existing settings reconcile as intended.
- Confirm every broker-backed Hook works only with its intended origins.
- Confirm the extension keeps the same identity.
- Confirm results and diagnostics contain no secrets or user-identifying request data.

If a setting key is renamed, increase its section schema version. Always take the Hook schema
version from the current `HookSpec`.

For the names used here, see [Contract terms](glossary.md).
