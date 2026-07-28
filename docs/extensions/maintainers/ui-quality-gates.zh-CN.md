# 插件界面质量门槛

[English](ui-quality-gates.md) · [维护者指南](README.zh-CN.md)

发现插件、插件列表、详情、授权、设置或 Provider 入口发生变化时，使用这份清单。所有适用项
通过后，界面改动才达到发布标准。

## Android 官方基线

本节是平台要求或建议，不是本项目自定的视觉风格。

| 范围 | 通过条件 | 官方来源 |
| --- | --- | --- |
| 触控 | 每个可交互目标至少为 48 × 48 dp；相邻控件扩展后的触控区域不能重叠。 | [Compose 无障碍默认行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) |
| 紧凑布局 | 正文和操作保持在左右 16 dp 边距内；窗口变宽时，边距随布局调整。 | [内容组成与结构](https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure) |
| 自适应布局 | 按应用可用窗口判断，而不是按设备类型判断：小于 600 dp 为 Compact，600–839 dp 为 Medium，840 dp 起为 Expanded；高度小于 480 dp 为 Compact。 | [窗口尺寸分级](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) |
| 大字体 | 系统字体调至 200% 时，文字、控件和完整任务流程仍可使用，没有裁切或遮挡。 | [Android 14 非线性字体缩放](https://developer.android.com/about/versions/14/features#non-linear-font-scaling-200) |
| 对比度 | 普通文字至少为 4.5:1；大号或粗体文字可为 3:1；Surface 与有意义的非文字元素至少为 3:1。 | [Android 无障碍设计](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility)、[Android 无障碍 Codelab](https://developer.android.com/codelabs/starting-android-accessibility) |
| Insets | 内容可以延伸到屏幕边缘，但控件不能与系统栏、挖孔、系统手势区域或输入法重叠；由负责该边界的层级应用并消费一次 Insets。 | [Android 系统栏](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)、[Compose 窗口 Insets](https://developer.android.com/develop/ui/compose/system/insets-ui) |

设置使用列表或列表—详情结构。用留白、标题或分隔线组织行，不要给每一行单独套 Card。小窗口
中，详情页替换列表；空间足够时，列表和详情可以并排。单栏表单不能在大窗口上无限拉伸。参见
[设置模式](https://developer.android.com/design/ui/mobile/guides/patterns/settings)与
[列表—详情布局](https://developer.android.com/develop/adaptive-apps/guides/list-detail)。

## M3UAndroid 项目策略

本节是本项目自己的发布门槛，不代表 Material 规范原文。

### 流程与层级

- 设置页只有一个插件管理入口，点击一次即可进入插件列表。
- 列表只承担浏览和选择；点击整行进入插件详情。授权和设置使用独立全屏页面。
- 一个页面最多显示一个高强调操作。次要操作和破坏性操作降低强调；Dialog 只用于简短的
  破坏性确认。
- 每页只有一个页面标题。插件子页面提供返回操作，不在任务上方显示无关的全局搜索、遥控
  操作或顶级导航。
- 加载、空内容、正常内容、不可用和错误是不同状态。刷新失败时，只要安全，就保留上一次
  有效内容。
- 面向用户的文案和无障碍名称来自本地化资源。布局使用逻辑 Start/End 对齐和可镜像的方向
  图标；较长的译文允许自然换行。
- 状态不能只靠颜色表达，还要有文字或语义。界面不得显示 Secret 或 Token；包名、Service
  名、证书指纹和 Origin 必须可读，并采用安全的双向文本格式。
- 拖动、滑动或长按交互必须提供等价的点击、键盘或 DPad 操作。

### 布局行为

- Compact 窗口使用单栏。插件列表独立滚动；详情、授权和设置占据完整任务视口。
- Medium 和 Expanded 窗口保持相同的信息顺序，可以使用列表—详情双栏。跨断点调整窗口
  时，不能丢失已选插件或表单状态。
- 手机悬浮导航可以覆盖滚动视口；滚动容器必须提供足够的底部空间，使最后一项和最终操作
  可以完整滚到悬浮导航与系统安全区上方。
- 输入页面使用窗口 Insets 处理输入法。不能把导航 Padding 当作键盘避让，也不能重复应用
  已经消费的 Insets。
- TV 保持 DPad 操作：初始焦点明确；聚焦态不能只依赖颜色；所有操作都可到达；返回后焦点
  回到打开当前页面的项目。

## 必跑测试矩阵

本节是 M3UAndroid 项目策略。它是断点和压力矩阵，不要求运行所有维度的笛卡尔积；每个取值
都要覆盖，并且必须运行 Compact RTL + 200% 字体组合。

| 维度 | 必测值 |
| --- | --- |
| 可用窗口宽度 | 360、599、600、839、840 dp |
| 受限高度 | 480 dp，以及正常手机竖屏或 TV 高度 |
| 字体 | 100%、200% |
| 方向与语言 | 英语 LTR、简体中文、`en-XA`、`ar-XB` RTL |
| 主题 | 浅色、深色；设备支持时覆盖动态取色 |
| 导航与输入 | 手势导航、三键导航、输入法关闭、输入法打开 |
| 插件数量 | 0、1、30 |
| Capability 数量 | 0、1、12 |
| 设置字段数量 | 0、1、20 |
| 文本 | 短文案、最长本地化文案，以及很长的身份或 Origin 值 |

手机用例必须在手机配置上运行；大窗口用例使用平板或可调整大小的大屏配置。TV 证据必须来自
使用 DPad 输入的 TV 配置。

## 检查必须证明什么

自动化界面测试必须断言：

- 设置中只有一个插件入口，并跑通列表、详情、全屏授权和全屏设置；
- 每个可操作语义节点的边界不小于 48 dp；
- 无障碍名称已本地化，Role、状态、启用状态和遍历顺序正确；
- 系统栏、悬浮导航和输入法都纳入测试时，最终操作仍能在滚动后完整显示；
- 跨窗口分级切换后状态仍保留，并且没有重复的高强调操作；
- 空内容、加载、不可用和错误状态都有覆盖，而不是只测一个插件正常显示。

仍需人工视觉检查。每个发生变化的设备形态都要保存普通截图和开启**显示布局边界**后的截图。
出现裁切、非预期截断、重叠、重复 Insets、行对齐不一致、文字 Baseline 错误、无依据的居中，
或普通列表行被逐个套进 Card 时，不能通过。普通截图用于检查层级、对比度和动态取色；布局
边界用于检查约束和触控几何。Android 建议把自动检查与人工无障碍测试结合使用，参见
[Compose 无障碍测试](https://developer.android.com/develop/ui/compose/accessibility/testing)。

## 发布证据

记录设备或 AVD、API Level、可用窗口尺寸、Locale、布局方向、字体大小、主题、导航模式、
命令、结果与截图路径。资源结构测试不能替代对授权、错误和破坏性操作文案的母语审校。
