# 运行 Hello 插件

[English](quickstart.md) · [插件开发指南](README.zh-CN.md)

这项检查只需要一条构建命令和一次设置页操作。成功标准是：插件列表出现
**Hello Extension**，其设置页包含 **Greeting**，以及本地化后的 **Phone name** 或
**手机名称**字段。

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
- 英文环境显示 **Phone name** 与 `My phone`，中文环境显示 **手机名称** 与 `我的手机`。

这同时验证了两条链路：`Greeting` 来自 Manifest，本地化的设备字段来自一次 Hook 调用。

## 3. 修改 Hook 返回结果

在示例的
[`values/strings.xml`](../../../samples/hello-extension/src/main/res/values/strings.xml) 中，把
`Phone name` 改成 `Handset name`；并在
[`values-zh-rCN/strings.xml`](../../../samples/hello-extension/src/main/res/values-zh-rCN/strings.xml)
中把 `手机名称` 改成 `手持设备名称`。

再次执行 `./gradlew :samples:hello-extension:installDebug`，刷新插件列表并打开 Hello 设置；
字段应按当前应用语言显示修改后的名称。

下一步：[定义插件 Manifest](concepts.zh-CN.md)。
