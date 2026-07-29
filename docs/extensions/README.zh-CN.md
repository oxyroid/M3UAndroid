# M3UAndroid 插件

[English](README.md)

按你的任务选择文档：

- [开发插件](developers/README.zh-CN.md)：运行 Hello、实现类型化 Hook，并在 M3UAndroid
  中验证结果。
- [维护插件平台](maintainers/README.zh-CN.md)：排查运行时、传输、网络访问、Provider 或结果
  导入问题。

Emby 和 Jellyfin 随 M3UAndroid 作为内置插件发布。外部插件使用相同的类型化 Hook 契约，
目前作为开发者预览提供。两者都只在 smartphone 应用中可用，包括手机和平板布局。
TV 应用不提供也不调用任何插件能力，只支持 M3U 与 Xtream 数据源。
