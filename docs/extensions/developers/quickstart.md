# Run Hello in 10 minutes

[简体中文](quickstart.zh-CN.md) · [Developer guide](README.md)

Done means: **Hello Extension** appears in M3UAndroid and its settings screen contains **Greeting** and **Phone name**.

This tutorial uses the runnable sample already in the repository.

## 1. Build and deploy

Run from the repository root:

```bash
./gradlew \
  :app:smartphone:installDebug \
  :samples:hello-extension:installDebug
```

## 2. Enable Hello in M3UAndroid

1. Open **Settings → Optional features** and enable **External extensions**.
2. Open **Settings → Playlist management**.
3. Swipe left to the **Extension plugins** page.
4. Find **Hello Extension**, press **Enable**, and confirm.
5. Open Hello's **Settings**.

On a phone you should see:

- **Greeting**, with default `Hello from my extension`;
- **Phone name**, with default `My phone`.

These fields prove that discovery, user authorization, and one real Hook call all work.

## 3. Make one change

Open [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt) and change the static field from `label = "Greeting"` to `label = "Message"`. Deploy the updated sample, return to **Extension plugins**, press **Refresh**, and open Hello settings again. The label should now be **Message**.

That is the normal development loop: edit, deploy the sample, refresh, and inspect the real UI.

Next: [Understand and modify your first Hook](first-hook.md).
