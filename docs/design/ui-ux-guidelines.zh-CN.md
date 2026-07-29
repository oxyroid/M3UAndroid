# M3UAndroid UI/UX 设计与验收规范

[English](ui-ux-guidelines.md)

这份文档面向 M3UAndroid 的界面维护者。它规定手机、平板和 TV 上的产品层级、布局边界、
组件语义和发布证据。它不是 Android 或 Compose 入门教程。

文中的“必须”是发布门槛，“应该”是默认选择；偏离“应该”时，需要在变更说明中给出可验证的
理由。Android 规范决定实现基线。Apple HIG 只用于交叉检查层级、适应性和可访问性，不能用
Apple 的尺寸替换 Android 指标。

## 开始设计前先回答

每个界面变更先明确以下问题：

1. 当前决策依据是哪一个**可用窗口**分级，而不是设备名称？
2. 这是顶级浏览、列表、详情、输入任务，还是全屏播放？
3. 哪一层拥有系统栏、挖孔、手势区和 IME Insets？
4. 哪个容器滚动？最后一个重要项目如何完整进入专注区域？
5. 页面唯一的主要操作是什么？尾部图标是装饰、行操作，还是独立操作？
6. 每段文字属于什么角色，允许几行，截断后是否仍能完成任务？
7. 加载、空内容、正常内容、刷新失败、不可用和操作失败分别如何呈现？
8. 这次变更要在哪些窗口、语言方向、字体大小和输入方式下验收？

无法回答这些问题时，不应先堆叠 `Card`、固定尺寸或额外 Padding 来“修”视觉结果。

## 自适应层级

M3UAndroid 按应用的**当前可用窗口**布局。手机、平板只是测试设备角色，不能作为
`isTablet` 一类分支依据。窗口可以在旋转、分屏和调整大小时跨越断点，选择、滚动和输入状态
必须保留。Android 的[窗口尺寸分级](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
是断点基线。

| 可用窗口 | M3UAndroid 布局 | 导航 | 内容层级 |
| --- | --- | --- | --- |
| 宽度 `< 600dp` | Compact 单栏 | 顶级页使用叠放在内容上的手机底栏 | 详情、搜索和输入任务替换当前内容；任务进行时隐藏底栏 |
| 宽度 `600–839dp` | Medium | 侧栏与内容在 `Row` 中并排，侧栏实际占宽 | 默认单主栏；空间确实足够且任务受益时才使用列表—详情 |
| 宽度 `840–1199dp` | Expanded | 侧栏与内容并排 | 列表—详情或受控最大宽度内容；不能把手机单栏无限拉宽 |
| 宽度 `≥ 1200dp` | Large/Extra large | 沿用侧栏层级 | 增加留白或辅助区域，不增加无关功能密度 |
| 高度 `< 480dp` | Compact height | 保持当前宽度对应的导航形式 | 不强行双栏；优先保证主要内容、焦点和操作可见 |
| Android TV | TV 专用层级 | `TvNavigationRail` 与浏览 Pane 并排 | DPad-first；不复用手机叠放底栏 |

项目只有首页、收藏和设置三个顶级目的地。手机底栏只在这些目的地的根页面显示。详情、全屏
搜索、IME 输入和沉浸式播放不能与它争夺注意力。底栏滚动时保持稳定，不根据滚动方向忽隐忽现。
三个目的地足够稳定时，Compact 底栏可以只显示图标，但无障碍名称和选中状态始终必须存在。

Medium 以上的侧栏通过排列占据空间，不能作为覆盖内容的浮层。Compact height 可以保留侧栏，
但内容退化为单 Pane。TV 始终保留自己的排列、初始焦点和返回焦点路径。Android TV 的
[10-foot 与 DPad 基线](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
和[焦点系统](https://developer.android.com/design/ui/tv/guides/styles/focus-system)是 TV
实现的最低要求。

## `layoutPadding`、`contentPadding` 与 Insets

这三个概念不能互换：

- `layoutPadding` 缩小组件的测量和放置视口。它决定布局的外边界。
- `contentPadding` 位于滚动内容内部。它移动首项和末项的可到达位置，但不缩小滚动视口。
- Window Insets 是系统环境边界。由最接近该边界的拥有者应用并消费一次，不能在父子层重复。

Compose 的 Lazy 容器把
[`contentPadding` 应用于内容而非容器](https://developer.android.com/develop/ui/compose/lists#content-padding)；
[Window Insets 指南](https://developer.android.com/develop/ui/compose/system/insets-ui)说明了消费
规则以及 IME 场景中使用末尾 Spacer 的注意事项。

| 场景 | `layoutPadding` | 滚动内容的安全尾部 |
| --- | --- | --- |
| Compact 顶级页、底栏可见 | 只处理逻辑横向安全区和必要的挖孔边界；不包含底栏高度 | 已消费关系下的系统底部安全区 + `12dp` + 实测底栏高度 + `12dp` |
| Compact 详情、搜索或底栏隐藏 | 处理一次系统安全边界 | 页面常规间距；有输入时由 IME Insets 和末尾 Spacer 负责 |
| Medium/Expanded | 侧栏由 `Row` 实际占宽，不伪装成 Padding | 页面自身间距和未消费的系统底部安全区 |
| TV | 保持导航和内容 Pane 的排列边界 | 使用 TV 页面自己的安全区与焦点滚动间距 |

Compact 顶级页的列表视口可以延伸到底栏后方。中间项目可以在滚动过程中从半透明底栏后经过，
但以下内容绝不能停留在遮挡区：

- 当前焦点、选中项、正在编辑的输入框及其错误；
- 最后一个项目、最终提交操作和破坏性确认入口；
- Snackbar、远程控制 accessory action 或其他需要立即响应的浮层操作。

需要关注的项目获得焦点或进入编辑状态时，应滚动到安全区域。底栏隐藏后，Snackbar 和独立
浮层操作退回系统安全区。输入页面不能把导航 `contentPadding` 当作键盘避让；对于 Lazy 输入列表，
使用 IME Insets 和末尾 Spacer，避免最后一个输入框被键盘遮住。

`HorizontalPager` 等页面容器不能因为手机导航 Padding 而整体缩小。把滚动安全距离传给每页
自己的滚动容器，页指示器单独避让浮层。

## 导航、行操作与 accessory action

视觉上相邻不代表语义上是同一个操作。先确定交互模型，再选择组件。

导航项负责切换顶级目的地，不执行刷新、开关或一次性命令；这些操作放在当前页面中。每个导航
项只有一个可选择语义节点和一个稳定身份，图标与可见标签不能分别点击。参见 Android 的
[Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)和
Compose 的[语义树](https://developer.android.com/develop/ui/compose/accessibility/semantics)。

| 行的含义 | 点击和语义 | 尾部元素 |
| --- | --- | --- |
| 整行打开详情 | 整行是一个导航语义节点 | Chevron 只是方向提示，不能再创建重复点击节点 |
| 整行打开详情，尾部执行另一操作 | 行和尾部按钮是两个独立节点 | 尾部使用独立 IconButton、明确名称和至少 `48 × 48dp` 的区域 |
| 整行切换一个布尔值 | 整行和 Switch 合并成一个 Toggle 语义 | 避免 TalkBack 遇到两个执行相同切换的节点 |
| 行只展示信息 | 没有点击语义 | 不用 Chevron 暗示可导航 |
| 拖动、滑动或长按提供快捷操作 | 手势是增强交互 | 必须同时有点击、键盘或 DPad 可达的等价操作 |

24dp 的图标不等于 24dp 的按钮。独立尾部操作按不可见的至少 48dp 圆形按钮布局：

- 行的 Start 和 End 外边距相同；
- Leading 图标槽和 trailing accessory 槽使用稳定宽度；
- 尾部按钮的**整体外边界**与行 End 保持规定间距，而不是让可见 glyph 贴边；
- 同一列表中所有 leading glyph 的中心和所有 trailing action 的中心分别共线；
- 相邻扩展触控区域不能重叠，视觉顺序必须与无障碍遍历顺序一致。

导出、删除、刷新等图标即使没有可见背景，只要可独立点击，就是 Button，不是装饰图片。反之，
整行导航时不要给 Chevron 添加无意义的 `contentDescription`。Compose 的
[48dp 触控区域与防重叠说明](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
是最低基线。

手机底栏旁的远程控制按钮遵循同一原则：它可以与玻璃导航在视觉上组成一个 Dock，但必须位于
`selectableGroup` 之外，暴露 `Button` 而不是 `Tab` 语义，并使用逻辑 End 位置随 RTL 镜像。
它出现或消失时可以重排 Dock，但不能把三个导航项压缩到 48dp 触控宽度以下。

## Material 语义色、动态色与玻璃表面

功能页面只能消费 `MaterialTheme.colorScheme` 的语义角色，不能在局部重建整套主题、复制固定
调色板，或为了一个组件改变其他页面的颜色。Material 3 的
[ColorScheme 与动态色](https://developer.android.com/develop/ui/compose/designsystems/material3)
是实现依据。

| 用途 | 默认角色 |
| --- | --- |
| 页面与普通列表背景 | `surface` |
| 分组或低强调容器 | `surfaceContainerLow` / `surfaceContainer` |
| 选中项 | `secondaryContainer` + `onSecondaryContainer` |
| 页面唯一的高强调操作 | `primary` + `onPrimary`，或组件默认高强调角色 |
| 错误容器 | `errorContainer` + `onErrorContainer` |
| 次要文字和图标 | `onSurfaceVariant` |
| 边界 | `outlineVariant`；需要更强分隔时才使用 `outline` |
| 模态遮罩 | `scrim` |

容器色和内容色必须成对使用。禁用、按下、选中和聚焦状态优先采用 Material 组件默认状态层；
不要用任意 Alpha 拼出另一套状态系统。状态不能只靠颜色表达，还需要形状、图标、文字或语义。

动态色只改变主题提供的角色值，不能改变页面的信息层级。浅色、深色、动态色和无动态色回退都
必须可读。项目发布门槛为：普通文字至少 4.5:1，大号或粗体文字至少 3:1，有意义的非文字
元素与相邻表面至少 3:1。参见
[Android 无障碍设计](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility)。

主题风格可以改变标题字形和语义色的生成方式，但不能绕过这些角色。每个风格必须同时提供浅色、
深色和动态色策略；正文、错误色和交互状态仍使用 Material token。暖色编辑主题以纸张、墨色和
陶土色建立气质，不复制外部产品的品牌 token、字体或资产。编辑风格的衬线字形只用于页面标题，
不能扩散到正文、媒体名称或技术标识。

主题卡代表完整且有稳定身份的预设，不是一次孤立的颜色写入。选择时必须把预设身份、种子色、
明暗模式、风格和动态色状态作为一个快照提交，避免出现混合旧新主题的中间帧；无法识别的预设
安全回退到 Material 风格。跟随系统时，每个种子只显示一个与当前系统明暗匹配的代表项。

只有在内容从后方经过时确实产生局部模糊、材质染色或等价的光学分离，界面才应称为“玻璃”。
悬浮玻璃表面还必须满足：

- 颜色来自 Surface 语义角色，不改变根主题；
- 有轮廓或阴影帮助识别边界；
- 模糊不可用时，回退到足够不透明且对比合格的主题表面；
- 明暗、动态壁纸和高对比背景下，图标与选中状态都清晰；
- 模糊区域保持小而稳定，不把大面积滚动内容变成持续高成本效果。

透明本身不是玻璃，也不是可读性保障。

## 文字角色与换行

`maxLines = 1` 不是默认值。只有产品角色要求紧凑、文本来源受控，并且截断不影响任务时才能
限制为一行。Compose 提供
[`maxLines` 与 overflow](https://developer.android.com/develop/ui/compose/text/configure-layout)，
但是否限制必须由下表决定。

| 文字角色 | M3UAndroid 默认规则 |
| --- | --- |
| 固定顶级目的地标题 | 可为 1 行；字符串必须短且在所有语言中验证 |
| Playlist、Provider、服务器等实体标题 | 列表和详情标题默认允许 2 行；详情中必须能看到完整身份 |
| 普通列表 primary text | 默认 2 行；只有可恢复完整值的高密度辅助列表才允许 1 行省略 |
| Supporting text、说明、空状态 | 自然换行；不要用 1 行限制 |
| 错误、权限理由和破坏性后果 | 必须完整显示，不能省略 |
| Button label | 优先 1 行；放不下时重排或纵向堆叠操作，不能缩小字号或省略关键动词 |
| Chip、Badge、短状态 | 仅用于受控短文案并保持 1 行；长状态改用正文或独立行 |
| 底部导航视觉标签 | Compact 三目的地模式可隐藏；显示时为 1 行；语义名称始终完整 |
| URL、包名、指纹等技术值 | 概览可省略，详情必须可读和可复制；不能作为唯一的人类可读标题 |
| TextField 的 supporting/error text | 自然换行；输入值是否单行由字段语义决定 |
| TV 内容标题 | 默认最多 2 行；减少长段说明，但不能隐藏完成任务所需的区别 |

不要为了适配翻译而缩短正确文案、降低 Typography token、压缩字距或关闭系统字体缩放。优先
调整约束、可用宽度、操作排列和组件层级。所有关键流程在 200% 字体下仍必须可完成。

测试至少覆盖无空格的长 CJK 文本、较长德语或罗马尼亚语、Arabic RTL、长 Playlist 标题和
技术标识。视觉省略不能把两个项目变成无法区分的同名项；无障碍完整名称不能替代关键身份的
可见呈现。

## RTL 与 i18n

RTL 是布局方向，不是字符串变换。

**绝对禁止**对单词、句子、URL 或用户输入调用字符反转来“支持 RTL”。自然语言保持原始书写
顺序；布局负责镜像。不得把 bidi override、isolate 或其他不可见控制字符持久化到标题和账号
字段。混合方向内容只在显示边界使用安全格式化，Android 推荐对插入本地化句子的动态片段使用
[`BidiFormatter`](https://developer.android.com/training/basics/supporting-devices/languages)。

| 应随 RTL 镜像 | 不应镜像 |
| --- | --- |
| Start/End 布局、Pane 顺序、返回/前进、Chevron、方向性列表进入动作 | 品牌图、播放/暂停、时间轴含义、时钟、数字本身、封面、技术字符串内容 |

实现和审查规则：

- 使用 Start/End、`Alignment.*Start`、逻辑 Padding 和相对放置，不用 Left/Right 表达布局；
- 使用 AutoMirrored 方向图标；自定义图标必须明确其是否具有语言方向；
- 同一列表的文字对齐保持一致；长段落按内容语言保持自然阅读方向；
- URL、Origin、包名、证书指纹和用户名不反转、不手工插控制字符，并提供安全复制；
- 用户可见文字和无障碍名称来自资源；占位符、复数和语序由各 Locale 决定；
- `ar-XB` 用于施压，但仍需至少一次真实 Arabic 审查；`en-XA` 用于发现长度问题。

Apple 的
[Right to left](https://developer.apple.com/design/human-interface-guidelines/right-to-left)
同样区分界面镜像与内容方向，可作为跨平台审查参考。

## `ListItem`、`Card` 与 `Surface` 的边界

| 组件 | 何时使用 | 不应使用 |
| --- | --- | --- |
| `ListItem` 或统一 Row | 同层级 Playlist、设置、动作和属性列表 | 不因“看起来简单”而丢失 48dp、语义和对齐槽 |
| `Card` | 一个可独立理解的内容单元，例如媒体项目、概览摘要或需要整体选择的对象 | 不给普通设置或详情中的每一行各套一张 Card |
| `Surface` | 表达真实的色调、形状、层级、焦点或点击边界 | 不作为没有视觉或语义作用的万能 Wrapper |
| `Column` / `Row` | 只负责简单排列 | 不直接拼出缺少状态、触控和语义的仿 Material 控件 |

Android 的[设置模式](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
要求用列表或列表—详情组织设置，并以标题、间距或内在分组组织内容，而不是逐项 Card 化。
[Compose Card](https://developer.android.com/develop/ui/compose/components/card)用于一个连贯、独立的
内容单元。

项目约束：

- 普通列表共享一个 Surface，通过分组标题、8/16/24dp 间距或必要的 Divider 建立层级；
- Card 内再套 Card 必须有两个可独立解释的层级，否则应拆平；
- 一个可点击 Card 默认由整个 Card 承担主要动作；独立 accessory action 遵守前述双节点规则；
- Leading 图标、primary/supporting text 和 trailing action 使用统一槽位与垂直居中；
- TV 媒体 Card 可以使用明显焦点层；TV 设置行仍按列表语义设计。

## 状态、错误与操作反馈

每个数据页面都要明确以下状态，而不是用一个全屏 Spinner 覆盖所有情况：

| 状态 | 呈现规则 |
| --- | --- |
| 首次加载 | 保留页面层级，显示有含义的进度；不能让用户误以为内容为空 |
| 空内容 | 说明这里是什么、为什么为空，并在可恢复时提供一个主要操作 |
| 正常内容 | 操作靠近其影响的内容；每页最多一个高强调操作 |
| 后台刷新 | 保留上一次有效内容，在局部显示刷新状态 |
| 刷新失败 | 尽可能保留旧内容，显示可重试的非破坏性错误 |
| 不可用/被禁用 | 说明原因、影响和恢复方式；不能只降低 Alpha |
| 表单验证失败 | 错误贴近字段并有 error 语义；保留输入，必要时把首个错误滚入安全区 |
| 操作进行中 | 防止重复提交，操作附近显示进度；耗时操作提供取消或离开后的可恢复状态 |
| 操作成功 | 立即更新内容；只在需要确认但不打断流程时使用短暂反馈 |
| 破坏性操作 | 说明对象与后果；简短确认后仍要给出成功或失败结果 |

Snackbar 适合短暂、可错过的反馈；阻止任务继续的错误必须留在页面内。错误、选中、禁用和进度
不能只用颜色或动画表达。自定义状态应提供正确的 Role、`stateDescription`、错误语义和适度的
live region；参见
[Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)。

状态变化不得把当前焦点送到不可见位置。手机在 IME 或底栏变化后要保持当前输入可见；TV 在
加载完成、Dialog 关闭和返回后要恢复可预测焦点。旋转或跨断点时不能清空尚未提交的输入。

## 可访问性与输入方式

- 每个触控操作至少 `48 × 48dp`，扩展后的相邻触控区域不能重叠。
- 装饰图标没有无障碍名称；独立操作有本地化名称、正确 Role、启用状态和结果反馈。
- 标题和分组在语义树中表达层级；遍历顺序与视觉阅读顺序一致。
- 选中、聚焦、错误和禁用至少由两种线索表达，不能只靠色差。
- 自定义拖动和动画必须尊重系统动画设置，且任务不能依赖动画才能理解。
- 手机流程同时可由触控和 TalkBack 完成；键盘或遥控存在时不产生焦点陷阱。
- TV 所有操作都可由 DPad 到达，初始焦点明确，焦点态在沙发距离可辨。
- 播放器上的透明控件要有局部 Scrim、胶囊或边缘渐变，不能假设视频背景恒暗。

Apple 的[Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
和[Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)可用于交叉检查
控制尺寸、替代交互和反馈，但 Android 仍使用 48dp 项目门槛。

## 设备与窗口验收矩阵

这是一组边界和压力用例，不要求运行完整笛卡尔积。每个取值都要被覆盖，并且必须运行
“Compact + RTL + 200% 字体”组合。

| 维度 | 必测值 |
| --- | --- |
| 可用宽度 | 320、360、599、600、839、840dp；大屏改动再覆盖 1200dp |
| 可用高度 | 479、480dp，以及正常手机竖屏、平板和 TV 高度 |
| 字体 | 100%、200% |
| Locale/方向 | 英语 LTR、简体中文、`en-XA`、`ar-XB` RTL；RTL 改动补真实 Arabic |
| 主题 | 浅色、深色；支持时覆盖动态色，另测无动态色回退 |
| 系统导航 | 手势导航、三键导航 |
| 输入状态 | IME 关闭、打开、切换字段和提交错误 |
| 内容量 | 空、1 项、足以滚动的长列表；短标题和极长标题 |
| 状态 | 加载、内容、刷新、空、不可用、错误、重试、操作中 |
| 输入设备 | 手机触控/TalkBack、平板触控或键盘、TV DPad |

设备职责：

- 手机体验优先使用项目的 Pixel 6 Pro AVD；320dp 与 599dp 可用可调整窗口或专用小屏配置补齐。
- 6GB RAM AVD 只用于平板和大窗口验证，不作为手机证据。
- 平板验证至少跨过 600dp 和 840dp，检查侧栏真实占位、内容最大宽度和状态保留。
- TV 使用 TV Profile 和 DPad；触控或鼠标点击不能替代焦点路径证据。

每个受影响的形态都保存两张同状态截图：

1. 普通截图：检查层级、语义色、动态色、对比度、换行和焦点视觉；
2. 开启开发者选项 **Show layout bounds / 显示布局边界** 的截图：检查真实约束、触控槽、对齐、
   重复 Insets 和覆盖关系。

显示布局边界时必须确认：

- Leading 与 trailing 图标中心线稳定，独立按钮的 48dp 区域未越界或重叠；
- Compact 列表视口确实延伸到悬浮底栏后方，最后一项又能完整滚到其上方；
- Medium/Expanded 侧栏实际占宽，内容没有额外叠加手机底栏高度；
- IME 打开后当前字段、错误和提交操作仍可到达；
- TV 焦点环没有被裁切，当前焦点不会停在屏幕外或导航背后。

拒绝存在非预期裁切、关键文字省略、重叠、双重 Insets、对齐槽漂移、Baseline 不一致、无依据
居中、普通列表逐项 Card 化或只靠颜色表达状态的变更。

## 发布证据

界面变更说明必须记录：

- 设备或 AVD、API Level、可用窗口宽高；
- Locale、布局方向、字体大小、主题和动态色状态；
- 系统导航、IME 与输入设备；
- 验证命令、结果、普通截图和布局边界截图路径；
- 未覆盖的形态、原因和剩余风险。

自动化至少断言导航可达、语义节点名称/Role/状态、48dp 操作边界、最终操作可滚入安全区、跨
断点状态保留，以及空/错误/进行中状态。自动化不能替代普通截图、布局边界截图、TalkBack、
真实 DPad 和母语审查。

## 官方参考

Android 实现基线：

- [Window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Lazy lists: content padding](https://developer.android.com/develop/ui/compose/lists#content-padding)
- [Window Insets in Compose](https://developer.android.com/develop/ui/compose/system/insets-ui)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Support languages and RTL](https://developer.android.com/training/basics/supporting-devices/languages)
- [Mobile settings pattern](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
- [Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
- [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)

跨平台审查参考：

- [Apple HIG: Layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple HIG: Right to left](https://developer.apple.com/design/human-interface-guidelines/right-to-left)
- [Apple HIG: Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Apple HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Apple HIG: Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)
