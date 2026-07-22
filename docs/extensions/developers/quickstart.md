# Run the Hello extension

[简体中文](quickstart.zh-CN.md) · [Developer guide](README.md)

Success means that M3UAndroid lists **Hello Extension** and its settings page contains **Greeting** and **Phone name**.

## 1. Build the sample

From the repository root:

```bash
./gradlew :samples:hello-extension:assembleDebug
```

## 2. Check the result in M3UAndroid

1. In M3UAndroid, open **Settings → Optional features** and enable **External extensions**.
2. Open **Settings → Playlist management**.
3. Swipe to **Extension plugins**.
4. Select **Hello Extension**, choose **Enable**, and confirm the requested capability.
5. Open **Settings** on the Hello card.

The Hello settings page should contain:

- **Greeting** with `Hello from my extension`;
- **Phone name** with `My phone`.

`Greeting` is declared in the extension manifest. `Phone name` is returned by the settings Hook.

## 3. Change the Hook result

In [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt), change:

```kotlin
"phone" -> "Phone name" to "My phone"
```

to:

```kotlin
"phone" -> "Handset name" to "My phone"
```

Build the module again, refresh the extension list, and reopen Hello settings. The field should now be named **Handset name**.

Next: [declare the extension manifest](concepts.md).
