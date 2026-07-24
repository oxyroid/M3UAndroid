# 运行 Hello 插件

[English](quickstart.md) · [插件开发指南](README.zh-CN.md)

这项检查只需要一条构建命令和一次设置页操作。成功标准是：插件列表出现
**Hello Extension**，其设置页包含 **Greeting** 与 **Phone name**。

## 1. 安装 Hello 示例

在项目根目录执行：

```bash
./gradlew :samples:hello-extension:installDebug
```

## 2. 在 M3UAndroid 中检查结果

1. 在 M3UAndroid 中打开 **设置 → 可选功能**，开启 **外部扩展**。
2. 打开 **设置 → 订阅管理**。
3. 滑动到 **扩展插件**。
4. 选择 **Hello Extension**，点击 **启用**，确认它申请的 capability。
5. 在 Hello 卡片上打开 **设置**。

Hello 设置页应显示：

- **Greeting**，内容为 `Hello from my extension`；
- **Phone name**，内容为 `My phone`。

这同时验证了两条链路：`Greeting` 来自 Manifest，`Phone name` 来自一次 Hook 调用。

## 3. 修改 Hook 返回结果

在 [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt) 中，把：

```kotlin
"phone" -> "Phone name" to "My phone"
```

改成：

```kotlin
"phone" -> "Handset name" to "My phone"
```

再次执行 `./gradlew :samples:hello-extension:installDebug`，刷新插件列表并打开 Hello 设置，字段名称应变为 **Handset name**。

下一步：[定义插件 Manifest](concepts.zh-CN.md)。
