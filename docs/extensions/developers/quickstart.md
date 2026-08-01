# Run the Hello extension

[简体中文](quickstart.zh-CN.md) · [Developer guide](README.md)

This check generates the local SDK repository, builds and installs Hello against it, and then uses
M3UAndroid settings. Success means that **Hello Extension** appears and its settings page contains
**Greeting** plus the localized **Phone name** or **手机名称** field.

## 1. Install the Hello sample

From the repository root:

```bash
testing/bin/install-hello-extension.sh
```

Hello is a standalone Gradle build. It resolves
`io.github.oxyroid.m3u:extension-sdk-android:1.0.0-alpha01` from the generated Maven repository.

## 2. Check the result in M3UAndroid

1. In M3UAndroid, open **Settings → Optional features** and enable **External extensions**.
2. Open **Settings → Playlist management**.
3. Swipe to **Extensions**.
4. Select **Hello Extension**, choose **Enable**, and confirm the requested capability.
5. Open **Settings** on the Hello card.

The Hello settings page should contain:

- **Greeting** with `Hello from my extension`;
- **Phone name** with `My phone` in English, or **手机名称** with `我的手机` in Chinese.

This proves both paths: `Greeting` comes from the manifest, while the localized device field comes
from a Hook call.

## 3. Change the Hook result

In the sample's
[`values/strings.xml`](../../../samples/hello-extension/src/main/res/values/strings.xml), change
`Phone name` to `Handset name`. Make the matching Chinese change in
[`values-zh-rCN/strings.xml`](../../../samples/hello-extension/src/main/res/values-zh-rCN/strings.xml).

Run the install command again, refresh the extension list, and reopen Hello settings. The
field should use the changed name for the current app language.

Next: [declare the extension manifest](concepts.md).
