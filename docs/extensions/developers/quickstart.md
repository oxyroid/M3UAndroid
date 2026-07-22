# Run the reference extension

[简体中文](quickstart.zh-CN.md) · [Developer guide](README.md)

This walkthrough ends with **M3U Reference Extension** installed and enabled in a debug build of M3UAndroid.

## What you need

- This repository checked out locally
- JDK 17
- Android SDK and `adb`
- An Android 8.0 (API 26) or newer device or emulator

Confirm that `adb devices` shows the target as `device` before continuing.

## 1. Build and install both APKs

From the repository root, run:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew \
  :app:smartphone:installDebug \
  :testing:extension-reference:installDebug
```

The two packages are independent. Rebuilding the extension does not require rebuilding the host unless the extension contract changed.

## 2. Turn on the preview

In M3UAndroid on the device:

1. Open **Settings → Optional features**.
2. Turn on **External extensions**.
3. Open **Settings → Playlist management** and move to the **Extension plugins** page.
4. Refresh the list if needed.

You should see a plugin card named **Reference Provider**. Its state, service name, and certificate appear below the title.

## 3. Enable the extension

Select **Enable**, review the extension identity and requested capabilities, and confirm. The state should change to **Enabled**.

Open **Settings** for the extension. The reference extension contributes:

- an Enabled switch;
- an API key secret field;
- a Playback section with a Quality choice.

Saving the secret should show only that it is configured. The saved value is never filled back into the form.

## 4. Make one visible change

Edit `displayName` in [`ReferenceExtensionService.kt`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt), then reinstall only the extension:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :testing:extension-reference:installDebug
```

Return to the plugin page and refresh. This gives you a short edit-build-install loop before you create a new module.

If the old name is still registered, disable and enable the extension again, or restart M3UAndroid, before refreshing the page.

## Create your own module

Until the SDK is published, keep your first extension in the same checkout:

1. Create an Android application module modeled on `:testing:extension-reference`.
2. Depend on `project(":extension:sdk-android")`.
3. Give the module its own `applicationId` and service class.
4. Give the `ExtensionManifest` a stable extension ID, display name, developer name, and semantic version.
5. Start with one hook and declare only the capabilities it uses.

Read [Understand the extension model](concepts.md) before changing the service or manifest.

## If the extension is missing

- Confirm that `adb shell pm list packages com.m3u.testing.extension.reference` prints the package.
- Confirm that **External extensions** is still enabled.
- Reopen the plugin page and refresh after installing a new APK.
- If you changed the package, service, signer, or extension ID, use **Forget trust**, then install and authorize the new identity.
