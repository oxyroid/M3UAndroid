# Playbook: Extension System

## When to use this playbook

Use this when the task involves extension APIs, plugin runtime, extension discovery, host/plugin contracts, manifests, AIDL/protobuf contracts, or classloader behavior.

## Required context

Read these first:

- `AGENTS.md`
- `core/AGENTS.md`
- Relevant app, business, or data `AGENTS.md` for touched modules
- Nearby extension API, runtime, manifest, and sample/plugin code

## Safe change scope

The agent may modify:

- Extension contract code when compatibility impact is understood
- Host runtime/adapters that load, validate, or invoke extensions
- Tests or fixtures that exercise extension metadata and compatibility

The agent should avoid modifying:

- Public extension APIs without compatibility notes
- Host implementation details from extension API modules
- Classloader, manifest, or IPC assumptions without regression coverage

## Architecture rules

- Keep API/runtime boundaries explicit.
- Do not leak host implementation dependencies into extension APIs.
- Treat AIDL/protobuf schemas and manifest metadata as compatibility-sensitive.
- Keep plugin loading behavior defensive and observable.

## Common mistakes

- Changing a method signature without a migration or compatibility note.
- Assuming host and plugin share the same classloader dependencies.
- Treating manifest metadata as always present and valid.
- Adding app-module dependencies to core extension contracts.

## Validation

Run:

```bash
./gradlew :core:extension:compileDebugKotlin :app:extension:compileDebugKotlin
```

Also compile the host app module affected by the runtime change.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- API or binary compatibility impact
- Manifest, classloader, IPC, or dependency boundary risks
- Validation results for host and extension modules
