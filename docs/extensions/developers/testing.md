# Test an extension

[简体中文](testing.zh-CN.md) · [Developer guide](README.md)

Test the contract locally first, then exercise the independently installed APK on a real Android process boundary.

## Fast local checks

From the repository root with JDK 17:

```bash
./gradlew \
  :extension:api:test \
  :extension:runtime:test \
  :testing:extension-reference:assembleDebug
```

These checks cover contract validation, typed runtime behavior, limits, cancellation, and reference APK compilation. They do not prove Android discovery or IPC.

## Android IPC check

Start one clean device or emulator, then run:

```bash
./gradlew :app:smartphone:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.m3u.testing.ExternalExtensionIpcTest
```

The smartphone test task installs the reference extension for the run and removes it afterward. The test exercises discovery, manifest reading, typed invocation, large results, settings, and cancellation.

## Manual device pass

Use [Run the reference extension](quickstart.md), then verify:

1. the installed APK appears only after the preview is enabled;
2. authorization shows the expected package, version, developer, certificate, and capabilities;
3. enable, disable, and re-enable work without reinstalling;
4. settings persist, secret values are not displayed, and clear data resets them;
5. reauthorization reflects a changed capability request;
6. diagnostics contain identity and state, but no setting values or payload bodies;
7. killing or uninstalling the extension leaves M3UAndroid usable.

Repeat the management and settings flow on TV when the extension is intended for TV users.

## Upgrade checks

Keep a test APK for the previous version and cover these cases:

| Upgrade | Expected result |
| --- | --- |
| Same package, service, extension ID, and signer | Existing trust can be restored |
| New optional capability | It remains ungranted until reauthorization |
| New required capability | The host asks for a new authorization decision |
| Changed signer or extension ID | Existing trust is not reused |
| Changed settings section schema version | Old values in that section are cleared |

## Failure cases

At minimum, test:

- unsupported API or hook schema version;
- malformed request and malformed result;
- timeout and explicit cancellation;
- output above the host limit;
- extension process exit during a call;
- repeated hook failures;
- safe, actionable error messages.

These checks are development evidence for one extension. Platform-wide release evidence is tracked separately in the [maintainer release status](../maintainers/status-and-release.md).

## Publishing status

There is no stable external SDK artifact or public compatibility guarantee yet. APKs built against the source modules are development builds. When distribution opens, keep the package name, extension ID, and signing certificate stable, and publish the supported M3UAndroid API range with every release.
