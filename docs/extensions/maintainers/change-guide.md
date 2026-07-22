# Change the extension platform safely

[简体中文](change-guide.zh-CN.md) · [Maintainer guide](README.md)

Start from the user-visible behavior, identify the typed hook, and trace it all the way to the host consumer. A change is complete only when the contract and the real product path agree.

## Add or change a hook

1. Define one request type, one result type, and one `HookSpec` in `:extension:api`.
2. Give the hook its own positive schema version.
3. Define the minimum capabilities in host policy, independent of what a manifest chooses to declare.
4. Add the hook to negotiation and manifest validation.
5. Implement or adapt both built-in and Android transport fixtures.
6. Add the host caller and a narrow importer or renderer.
7. Add serialization fixtures, policy tests, importer tests, and an end-to-end path.
8. Update the developer hook matrix and both languages of the affected maintainer page.

If steps 6 and 7 are absent, mark the hook as contract-only rather than connected.

## Version a contract

Use an optional serialized field with a default for a compatible addition. Change the hook schema version when an old implementation cannot safely decode or answer the new shape. Change the extension API major only for a platform-wide break.

Keep refresh, close, and similar reason values as validated open strings. Ignore unknown optional JSON fields. Reject an incompatible API major, an unsupported declared hook schema, or an unknown required capability.

Before freezing a wire field, identify its host consumer. A field such as continuation, diagnostics, expiry, retry delay, or sync metadata should either have tested behavior or stay out of the public 1.0 contract.

## Add a built-in provider

A built-in provider is an extension implementation registered by host code. It follows the same five-hook provider lifecycle as an APK extension:

```text
discover -> validate -> refresh -> resolve playback -> close session
```

The provider result must remain contract data. Put HTTP clients and host credentials behind data adapters, persist generic provider/account identity, and keep UI selection driven by provider descriptors rather than `when` branches on provider kind.

Validate with strict mock-server flows for login, initial import, refresh, playback selection, session close, idempotent import, and local channel-state preservation.

## Connect an external provider

External provider work additionally needs:

- a provisional auth session bound to complete plugin identity and an approved origin;
- host-owned credential capture and promotion into a persisted account;
- contributor/provider ownership checks on every result;
- bounds for descriptors, channels, strings, metadata, URLs, and headers;
- WorkManager restore ordering before refresh or session recovery;
- a reference APK implementing all five hooks through repository, broker, importer, Room, player, and close paths.

Provider discovery alone is not a completed provider integration.

## Add a contribution importer

Keep the importer independent from transport. It receives the contributing extension ID, the original request scope, and a typed result.

For every imported item, verify:

- the item belongs to the contributing extension and request;
- stable and remote IDs are nonblank, unique where required, and bounded;
- counts, strings, maps, time ranges, URLs, and headers are bounded;
- a retry or single-extension failure preserves previously valid data;
- a successful empty result clears only that extension's owned data;
- cancellation propagates rather than becoming an empty contribution.

Use one reusable contribution importer from provider, M3U, and Xtream ingestion paths where the semantics are the same.

## Change Android IPC

Treat Binder calls, PFDs, broker bridges, and lifecycle callbacks as one revocable plugin session.

The session identity needs package, service, certificate set, UID, extension ID, grants, and invocation IDs. Disable, revoke, quarantine, Binder death, and timeout must invalidate the session and close its descriptors. Long extension work runs after a short asynchronous control transaction, on bounded executors.

Exercise the change with cooperative and hostile fixture APKs: hang, ignore cancel, die, return a wrong envelope, exceed limits, retain a broker handle, change signer, and reuse another extension ID.

## Change credentials or broker behavior

One credential registry should cover provider credentials, provisional login input, captured login output, and extension secret settings. Every handle is scoped by owner identity, purpose, account/auth-session/setting scope, key version, and approved origin.

Authentication request headers and capture rules come from host-approved schema. Authentication responses return an allowlisted data shape plus a handle; sensitive-key guessing is not a security boundary. Broker calls require an active invocation/session grant, caller UID verification, deadline, concurrency quota, redirect policy, and response bound.

## Change phone or TV UI

The UI presents repository state and sends lifecycle actions; it does not bind services or access trust storage directly.

For phone, verify full-row touch targets, scrollable authorization, visible errors, settings validation, and lifecycle refresh. For TV, verify DPad focus order, focused-state contrast, long capability reasons, back behavior, and the full settings form. Dynamic provider entries must come from descriptors on both surfaces.

## Validation ladder

Start with the smallest affected layer, then broaden:

```bash
./gradlew :extension:api:test :extension:runtime:test
./gradlew :extension:transport-android:connectedDebugAndroidTest
./gradlew :data:testDebugUnitTest :data:connectedDebugAndroidTest
./gradlew :app:smartphone:assembleDebug :app:tv:assembleDebug
./gradlew :app:smartphone:connectedDebugAndroidTest :app:tv:connectedDebugAndroidTest
```

For Room changes, update entities, migrations, exported schemas, and migration tests together. Before committing, run `git diff --check` and keep generated parser output and unrelated IDE files out of the change.

Next: [Status and release gates](status-and-release.md).
