# Prepare a release or update

[简体中文](compatibility.zh-CN.md) · [Developer guide](../README.md)

Before an update, decide separately whether you are changing extension identity, extension code version, a Hook contract, saved settings, or capabilities.

## Keep identity stable

Keep Android `applicationId` and `ExtensionId` unchanged across updates. A Service-class rename or
signing-certificate change forces a fresh identity review and invalidates provider credentials.
Treat either change as an explicit migration, not a normal update.

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

## Follow the wire compatibility rules

API version and Hook schema answer different questions:

| Contract change | Host behavior | Extension rule |
| --- | --- | --- |
| Different API major | Registration is rejected. The minimum and maximum of `apiRange` must both use the host major. | Publish a build for the new major. |
| Different API minor, same major | The API line remains eligible. The minor does not select a Hook decoder. | Check every declared Hook schema instead of using the minor as a feature flag. |
| Unknown Hook or unsupported Hook schema | Registration is rejected. Hook schemas are matched exactly. | Use the schema from the SDK `HookSpec`. A required field removal, rename, or meaning change needs a new Hook schema. |
| Unknown JSON field | Host and SDK decoders ignore it. | Add a field without changing the Hook schema only when it is optional and the feature still works when an older peer omits it. Extension decoders must also ignore unknown fields. |
| Unknown capability with `required = true` | Registration is rejected. | Require only capabilities published by the host. |
| Unknown capability with `required = false` | It does not block registration and is not granted by an older host. | API 1 has no Hook-level optional capabilities. A Hook must not depend on it; every capability in `requiredCapabilities` is mandatory. |

Omitting a field is compatible only when the receiving contract defines a default. Adding a field
that every receiver must understand requires a new Hook schema even within the same API major.

The checked-in [API 1 golden wire fixtures](../../../../extension/api/src/jvmTest/resources/golden-wire/v1/README.md)
show the canonical envelope, Hook payload, and broker JSON shapes. A new Hook schema gets a new
`schema-<version>` directory; do not overwrite the older schema's fixtures.

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
- Compare wire changes with the golden fixtures and run `./gradlew :extension:api:jvmTest`.

If a setting key is renamed, increase its section schema version. Always take the Hook schema
version from the current `HookSpec`.

For the names used here, see [Contract terms](glossary.md).
