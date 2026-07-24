# Test an extension

[简体中文](testing.zh-CN.md) · [Developer guide](README.md)

For each Hook, prove two things: the handler returns the expected typed result, and M3UAndroid applies that result in the feature that calls it.

## 1. Test the result object

Keep parsing, mapping, and validation in ordinary Kotlin functions. Unit-test those functions with request fixtures and assert the exact result object.

Cover at least:

- the smallest valid request;
- empty and boundary values;
- an expected server or validation failure;
- cancellation during long-running work;
- a missing required setting or credential handle.

Use `handleResult(...)` or `handleResultWithBroker(...)` for expected failures. Reserve thrown exceptions for unexpected faults.

## 2. Build the module

For the Hello module:

```bash
./gradlew :samples:hello-extension:assembleDebug
```

Use the corresponding task for your extension module.

## 3. Trigger the Hook from M3UAndroid

Run the current extension build with M3UAndroid, then use the feature that owns the Hook.

| Hook | Acceptance result |
| --- | --- |
| `settings.schema.contribute` | The settings page renders the returned sections and reloads saved values. |
| `search.provider.query` | Returned stable references promote matching visible channels in phone search. |
| `metadata.channel.enrich` | A generic provider refresh applies patches only to channels in the request. |
| `epg.content.refresh` | A refresh imports returned programmes; a failed call preserves the previous contribution. |
| `background.task.run` | Enabling the extension schedules each declaration. Disabling it cancels the work. A network task waits for a connection. |
| Provider discover and validate | Discover returns one descriptor. The form uses its schema. Valid input completes host-managed authentication. |
| Provider refresh | The account is saved only after initial refresh succeeds. The imported playlist contains the complete snapshot. |
| Provider playback and close | A same-origin source plays with host-resolved headers. Stopping playback closes the remote session. |

The reference provider test covers discovery, rejected and successful login, initial and later
refresh, playback resolution, header resolution, and session close through the external transport.

## 4. Check failure behavior

For each Hook, trigger one expected failure and confirm:

- the extension returns a stable `ExtensionError.code`;
- `recoverable` matches whether repeating the same call can succeed;
- no partly valid result is applied;
- cancellation stops long-running work.

For a broker-backed Hook, also confirm that an approved origin works, an unapproved origin and
cross-origin redirect fail. Missing `network` must be rejected; when a request uses a credential
handle, missing `credential.read` must also be rejected.

Provider tests should also cover rejected credentials, a failed refresh that preserves stored data,
an invalid playback result, and repeated session close.

## 5. Verify an update

- Keep the same extension identity and signing certificate.
- Confirm existing settings still load.
- Confirm removed or renamed fields reconcile as intended.
- Confirm a new required capability prompts for authorization.
- Confirm diagnostics contain no secrets, credential handles, or user-identifying request and response data.

Next: [prepare a release or update](reference/compatibility.md).
